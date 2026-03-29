package com.shootoff.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ArmyTableCustomStageManager {
	private static final String FILE_PATH = "exercises/army_table_vi_custom.txt";
	private static final String CALIBRATION_PREFIX = "CALIBRATION";
	private static final String TARGET_V4_PREFIX = "TARGET_V4";
	private static final String TARGET_V3_PREFIX = "TARGET_V3";
	private static final String TARGET_V2_PREFIX = "TARGET_V2";

	public static void saveStage(ArmyTableVIQualification.ArmyTableStageData stageData) throws IOException {
		saveStage(new File(FILE_PATH), stageData);
	}

	static void saveStage(File stageFile, ArmyTableVIQualification.ArmyTableStageData stageData) throws IOException {
		if (!stageFile.getParentFile().exists()) {
			stageFile.getParentFile().mkdirs();
		}

		try (PrintWriter out = new PrintWriter(new FileWriter(stageFile))) {
			final ArmyTableVIQualification.RangeBackgroundCalibration calibration = stageData.backgroundCalibration;
			out.printf(Locale.US, "%s,%d,%d,%.6f,%.6f,%.6f,%.6f%n",
				CALIBRATION_PREFIX,
				calibration.projectionWidthMm,
				calibration.shooterDistanceMm,
				calibration.fiftyMeterLeftPoint.xNormalized,
				calibration.fiftyMeterLeftPoint.yNormalized,
				calibration.fiftyMeterRightPoint.xNormalized,
				calibration.fiftyMeterRightPoint.yNormalized);

			for (ArmyTableVIQualification.Engagement engagement : stageData.engagements) {
				out.printf(Locale.US, "ENGAGEMENT,%d,%s,%.3f,%.3f%n",
					engagement.number, engagement.position.name(), engagement.delayBeforeSec, engagement.exposureSec);

				for (ArmyTableVIQualification.TargetSpec target : engagement.targets) {
					out.printf(Locale.US, "%s,%s,%d,%.6f,%s%n",
						TARGET_V4_PREFIX,
						target.targetType.name(),
						target.distanceMeters,
						target.laneXNormalized,
						target.side == null ? "" : target.side);
				}
			}
		}
	}

	public static ArmyTableVIQualification.ArmyTableStageData loadStage() throws IOException {
		return loadStage(new File(FILE_PATH));
	}

	static ArmyTableVIQualification.ArmyTableStageData loadStage(File stageFile) throws IOException {
		if (!stageFile.exists()) return null;

		final List<ArmyTableVIQualification.Engagement> engagements = new ArrayList<>();
		ArmyTableVIQualification.RangeBackgroundCalibration calibration = ArmyTableVIQualification.defaultBackgroundCalibration();

		try (BufferedReader reader = new BufferedReader(new FileReader(stageFile))) {
			String line;
			ArmyTableVIQualification.Engagement currentEngagement = null;

			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;

				final String[] parts = line.split(",");
				switch (parts[0]) {
				case CALIBRATION_PREFIX:
					calibration = new ArmyTableVIQualification.RangeBackgroundCalibration(
						new ArmyTableVIQualification.StagePoint(Double.parseDouble(parts[3]), Double.parseDouble(parts[4])),
						new ArmyTableVIQualification.StagePoint(Double.parseDouble(parts[5]), Double.parseDouble(parts[6])),
						Integer.parseInt(parts[1]),
						Integer.parseInt(parts[2]));
					break;
				case "ENGAGEMENT":
					currentEngagement = new ArmyTableVIQualification.Engagement(
						Integer.parseInt(parts[1]),
						ArmyTableVIQualification.Position.valueOf(parts[2]),
						Double.parseDouble(parts[3]),
						Double.parseDouble(parts[4]));
					engagements.add(currentEngagement);
					break;
				case TARGET_V4_PREFIX:
					if (currentEngagement != null) {
						currentEngagement.targets.add(new ArmyTableVIQualification.TargetSpec(
							ArmyTableVIQualification.ArmyTargetType.fromName(parts[1]),
							Integer.parseInt(parts[2]),
							parts.length > 4 ? parts[4] : "",
							Double.parseDouble(parts[3])));
					}
					break;
				case TARGET_V3_PREFIX:
					if (currentEngagement != null) {
						currentEngagement.targets.add(new ArmyTableVIQualification.TargetSpec(
							ArmyTableVIQualification.ArmyTargetType.fromName(parts[1]),
							Integer.parseInt(parts[2]),
							parts.length > 5 ? parts[5] : "",
							Double.parseDouble(parts[3])));
					}
					break;
				case TARGET_V2_PREFIX:
					if (currentEngagement != null) {
						currentEngagement.targets.add(new ArmyTableVIQualification.TargetSpec(
							ArmyTableVIQualification.ArmyTargetType.fromName(parts[1]),
							Integer.parseInt(parts[2]),
							parts.length > 5 ? parts[5] : "",
							Double.parseDouble(parts[3])));
					}
					break;
				case "TARGET":
					if (currentEngagement != null) {
						currentEngagement.targets.add(new ArmyTableVIQualification.TargetSpec(
							Integer.parseInt(parts[1]),
							parts.length > 2 ? parts[2] : ""));
					}
					break;
				default:
					break;
				}
			}
		} catch (Exception ex) {
			System.err.println("Failed to parse custom army table stage: " + ex.getMessage());
			return null;
		}

		final ArmyTableVIQualification.ArmyTableStageData stageData =
			new ArmyTableVIQualification.ArmyTableStageData(calibration, engagements);
		ArmyTableVIQualification.normalizeEngagementLayout(stageData.engagements, stageData.backgroundCalibration);
		return stageData;
	}

	public static void saveEngagements(List<ArmyTableVIQualification.Engagement> engagements) throws IOException {
		saveStage(new ArmyTableVIQualification.ArmyTableStageData(
			ArmyTableVIQualification.defaultBackgroundCalibration(), engagements));
	}

	public static List<ArmyTableVIQualification.Engagement> loadEngagements() throws IOException {
		final ArmyTableVIQualification.ArmyTableStageData stageData = loadStage();
		return stageData == null ? null : stageData.engagements;
	}
}
