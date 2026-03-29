package com.shootoff.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class TestArmyTableVIQualification {
	@Test
	public void testTargetTypeDimensionsComeFromFilename() {
		assertEquals(0.4953, ArmyTableVIQualification.ArmyTargetType.E1.getRealWidthMeters(), 0.0001);
		assertEquals(1.0160, ArmyTableVIQualification.ArmyTargetType.E1.getRealHeightMeters(), 0.0001);
		assertEquals(0.6604, ArmyTableVIQualification.ArmyTargetType.F_TARGET.getRealWidthMeters(), 0.0001);
		assertEquals(0.5334, ArmyTableVIQualification.ArmyTargetType.F_TARGET.getRealHeightMeters(), 0.0001);
	}

	@Test
	public void testNormalizeTargetLayoutMigratesLegacySpecsIntoDepthModel() {
		final ArmyTableVIQualification.RangeBackgroundCalibration calibration =
			ArmyTableVIQualification.defaultBackgroundCalibration();

		final List<ArmyTableVIQualification.TargetSpec> targets = new ArrayList<>();
		targets.add(new ArmyTableVIQualification.TargetSpec(50, "LEFT"));
		targets.add(new ArmyTableVIQualification.TargetSpec(100));
		targets.add(new ArmyTableVIQualification.TargetSpec(150, "RIGHT"));

		ArmyTableVIQualification.normalizeTargetLayout(targets, calibration);

		assertEquals(ArmyTableVIQualification.ArmyTargetType.E1, targets.get(0).targetType);
		assertEquals(0.18, targets.get(0).laneXNormalized, 0.0001);
		assertEquals(0.82, targets.get(2).laneXNormalized, 0.0001);
		assertTrue(ArmyTableVIQualification.defaultDepthForDistance(100, calibration)
			< ArmyTableVIQualification.defaultDepthForDistance(50, calibration));
	}

	@Test
	public void testDefaultDepthForDistanceUsesFiftyMeterReference() {
		final ArmyTableVIQualification.RangeBackgroundCalibration calibration =
			ArmyTableVIQualification.defaultBackgroundCalibration();

		final double fiftyMeterDepth = ArmyTableVIQualification.defaultDepthForDistance(50, calibration);
		final double threeHundredMeterDepth = ArmyTableVIQualification.defaultDepthForDistance(300, calibration);
		final double tenMeterDepth = ArmyTableVIQualification.defaultDepthForDistance(10, calibration);

		assertEquals(calibration.averageFiftyMeterY(), fiftyMeterDepth, 0.0001);
		assertTrue(threeHundredMeterDepth < fiftyMeterDepth);
		assertTrue(tenMeterDepth > fiftyMeterDepth);
	}

	@Test
	public void testTargetPixelSizeShrinksWithDistance() {
		final ArmyTableVIQualification.RangeBackgroundCalibration calibration =
			ArmyTableVIQualification.defaultBackgroundCalibration();
		final ArmyTableVIQualification.TargetSpec near = new ArmyTableVIQualification.TargetSpec(
			ArmyTableVIQualification.ArmyTargetType.E1, 50, "", 0.5);
		final ArmyTableVIQualification.TargetSpec far = new ArmyTableVIQualification.TargetSpec(
			ArmyTableVIQualification.ArmyTargetType.E1, 300, "", 0.5);

		final double[] nearSize = ArmyTableVIQualification.targetPixelSize(near, 1280, 720, calibration);
		final double[] farSize = ArmyTableVIQualification.targetPixelSize(far, 1280, 720, calibration);

		assertTrue(nearSize[0] > farSize[0]);
		assertTrue(nearSize[1] > farSize[1]);
	}

	@Test
	public void testTargetsAtSameDistanceAlwaysHaveSameSize() {
		final ArmyTableVIQualification.RangeBackgroundCalibration calibration =
			ArmyTableVIQualification.defaultBackgroundCalibration();
		final ArmyTableVIQualification.TargetSpec leftLane = new ArmyTableVIQualification.TargetSpec(
			ArmyTableVIQualification.ArmyTargetType.E1, 300, "LEFT", 0.18);
		final ArmyTableVIQualification.TargetSpec rightLane = new ArmyTableVIQualification.TargetSpec(
			ArmyTableVIQualification.ArmyTargetType.E1, 300, "RIGHT", 0.82);

		final double[] leftSize = ArmyTableVIQualification.targetPixelSize(leftLane, 1280, 720, calibration);
		final double[] rightSize = ArmyTableVIQualification.targetPixelSize(rightLane, 1280, 720, calibration);

		assertEquals(leftSize[0], rightSize[0], 0.0001);
		assertEquals(leftSize[1], rightSize[1], 0.0001);
	}

	@Test
	public void testCustomStageRoundTripPersistsCalibrationAndLane() throws Exception {
		final File tempFile = Files.createTempFile("army-table-vi-stage", ".txt").toFile();
		tempFile.deleteOnExit();

		final ArmyTableVIQualification.RangeBackgroundCalibration calibration =
			new ArmyTableVIQualification.RangeBackgroundCalibration(
				new ArmyTableVIQualification.StagePoint(0.30, 0.46),
				new ArmyTableVIQualification.StagePoint(0.71, 0.47),
				1800,
				4200);

		final List<ArmyTableVIQualification.Engagement> engagements = new ArrayList<>();
		final ArmyTableVIQualification.Engagement engagement = new ArmyTableVIQualification.Engagement(
			1,
			ArmyTableVIQualification.Position.PRONE_SUPPORTED,
			2.0,
			5.0);
		engagement.targets.clear();
		engagement.targets.add(new ArmyTableVIQualification.TargetSpec(
			ArmyTableVIQualification.ArmyTargetType.F_TARGET,
			200,
			"",
			0.61));
		engagements.add(engagement);

		final ArmyTableVIQualification.ArmyTableStageData stageData =
			new ArmyTableVIQualification.ArmyTableStageData(calibration, engagements);

		ArmyTableCustomStageManager.saveStage(tempFile, stageData);
		final ArmyTableVIQualification.ArmyTableStageData loaded = ArmyTableCustomStageManager.loadStage(tempFile);

		assertNotNull(loaded);
		assertEquals(1800, loaded.backgroundCalibration.projectionWidthMm);
		assertEquals(4200, loaded.backgroundCalibration.shooterDistanceMm);
		assertEquals(0.30, loaded.backgroundCalibration.fiftyMeterLeftPoint.xNormalized, 0.0001);
		assertEquals(ArmyTableVIQualification.ArmyTargetType.F_TARGET, loaded.engagements.get(0).targets.get(0).targetType);
		assertEquals(0.61, loaded.engagements.get(0).targets.get(0).laneXNormalized, 0.0001);
	}
}
