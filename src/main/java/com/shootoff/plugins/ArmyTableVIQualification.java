package com.shootoff.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shootoff.camera.Shot;
import com.shootoff.targets.Hit;
import com.shootoff.targets.Target;
import com.shootoff.util.NamedThreadFactory;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

public class ArmyTableVIQualification extends ProjectorTrainingExerciseBase implements TrainingExercise {
	private static final String BACKGROUND_RESOURCE = "/arena/backgrounds/army_qualification_range_2.png";
	private static final int TOTAL_TARGETS = 40;
	private static final int FIFTY_METER_DISTANCE_METERS = 50;
	private static final int MAX_QUALIFICATION_DISTANCE_METERS = 300;
	private static final int DEFAULT_PROJECTION_WIDTH_MM = 1200;
	private static final int DEFAULT_SHOOTER_DISTANCE_MM = 3000;
	private static final double DEFAULT_LEFT_ANCHOR_X = 0.18;
	private static final double DEFAULT_RIGHT_ANCHOR_X = 0.82;
	private static final double DEFAULT_CENTER_MIN_X = 0.24;
	private static final double DEFAULT_CENTER_MAX_X = 0.76;
	private static final double DEFAULT_FIFTY_METER_LEFT_X = 0.272;
	private static final double DEFAULT_FIFTY_METER_LEFT_Y = 0.485;
	private static final double DEFAULT_FIFTY_METER_RIGHT_X = 0.734;
	private static final double DEFAULT_FIFTY_METER_RIGHT_Y = 0.485;
	private static final double DEFAULT_NEAR_FLOOR_Y = 0.92;
	private static final double DEFAULT_FAR_DEPTH_Y = 0.31;
	private static final Pattern DIMENSION_PATTERN =
		Pattern.compile(".*_(\\d+(?:[,.]\\d+)?)X(\\d+(?:[,.]\\d+)?)in\\.[^.]+$", Pattern.CASE_INSENSITIVE);

	private ScheduledExecutorService executorService;
	private final AtomicBoolean continueExercise = new AtomicBoolean(true);
	private final AtomicInteger hitsThisEngagement = new AtomicInteger(0);
	private final List<Target> activeTargets = new ArrayList<>();

	private int currentEngagement = 0;
	private int score = 0;
	private boolean exerciseRunning = false;

	private TextField projectionWidthField;
	private TextField shooterDistanceField;
	private ComboBox<String> unitSelector;
	private Button startButton;

	private ProjectorTrainingExerciseBase thisSuper;
	private List<Engagement> activeEngagements = new ArrayList<>();
	private RangeBackgroundCalibration activeCalibration = defaultBackgroundCalibration();
	private Optional<ArmySessionListener> sessionListener = Optional.empty();
	private ArmySessionStatus lastSessionStatus = new ArmySessionStatus(ArmySessionState.SETUP,
		"Setup Army Qualification",
		"Configure projection width, shooter distance, calibration, and stage layout before starting.",
		0, TOTAL_TARGETS, 0, 0, Optional.empty());

	public enum Position { PRONE_UNSUPPORTED, PRONE_SUPPORTED, KNEELING_SUPPORTED, STANDING_SUPPORTED }

	public enum ArmySessionState { SETUP, RUNNING, COMPLETED, ERROR }

	public interface ArmySessionListener {
		void onStatusUpdated(ArmySessionStatus status);
	}

	public static class ArmySessionStatus {
		public final ArmySessionState state;
		public final String headline;
		public final String detail;
		public final int score;
		public final int totalTargets;
		public final int currentEngagement;
		public final int totalEngagements;
		public final Optional<String> qualification;

		public ArmySessionStatus(ArmySessionState state, String headline, String detail, int score, int totalTargets,
				int currentEngagement, int totalEngagements, Optional<String> qualification) {
			this.state = state;
			this.headline = headline;
			this.detail = detail;
			this.score = score;
			this.totalTargets = totalTargets;
			this.currentEngagement = currentEngagement;
			this.totalEngagements = totalEngagements;
			this.qualification = qualification;
		}
	}

	public enum ArmyTargetType {
		E1("E-1", "targets/Army_E1.target", "targets/E-1_19,5X40in.png"),
		F_TARGET("F-target", "targets/Army_F.target", "targets/f-target_26x21in.jpg");

		private final String displayName;
		private final String targetFilePath;
		private final String imagePath;
		private final double realWidthMeters;
		private final double realHeightMeters;

		ArmyTargetType(String displayName, String targetFilePath, String imagePath) {
			this.displayName = displayName;
			this.targetFilePath = targetFilePath;
			this.imagePath = imagePath;

			final double[] dimensionsInches = parseDimensionsInches(imagePath);
			this.realWidthMeters = inchesToMeters(dimensionsInches[0]);
			this.realHeightMeters = inchesToMeters(dimensionsInches[1]);
		}

		public String getDisplayName() {
			return displayName;
		}

		public File getTargetFile() {
			return new File(targetFilePath);
		}

		public String getImagePath() {
			return imagePath;
		}

		public int getRealWidthMillimeters() {
			return (int) Math.round(realWidthMeters * 1000.0);
		}

		public int getRealHeightMillimeters() {
			return (int) Math.round(realHeightMeters * 1000.0);
		}

		public double getRealWidthMeters() {
			return realWidthMeters;
		}

		public double getRealHeightMeters() {
			return realHeightMeters;
		}

		public static ArmyTargetType fromName(String rawValue) {
			if (rawValue == null || rawValue.trim().isEmpty()) return E1;

			final String normalized = rawValue.trim().toUpperCase(Locale.US)
				.replace('-', '_')
				.replace(' ', '_');

			for (ArmyTargetType type : values()) {
				if (type.name().equals(normalized)) return type;
			}

			if ("F".equals(normalized) || "FTARGET".equals(normalized)) return F_TARGET;
			return E1;
		}

		private static double[] parseDimensionsInches(String imagePath) {
			final Matcher matcher = DIMENSION_PATTERN.matcher(imagePath);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Could not parse real-world size from " + imagePath);
			}

			return new double[] {
				Double.parseDouble(matcher.group(1).replace(',', '.')),
				Double.parseDouble(matcher.group(2).replace(',', '.'))
			};
		}

		private static double inchesToMeters(double inches) {
			return inches * 0.0254;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	public static class StagePoint {
		public double xNormalized;
		public double yNormalized;

		public StagePoint(double xNormalized, double yNormalized) {
			this.xNormalized = xNormalized;
			this.yNormalized = yNormalized;
		}

		public StagePoint(StagePoint other) {
			this(other.xNormalized, other.yNormalized);
		}
	}

	public static class RangeBackgroundCalibration {
		public StagePoint fiftyMeterLeftPoint;
		public StagePoint fiftyMeterRightPoint;
		public int projectionWidthMm;
		public int shooterDistanceMm;

		public RangeBackgroundCalibration(StagePoint fiftyMeterLeftPoint, StagePoint fiftyMeterRightPoint,
				int projectionWidthMm, int shooterDistanceMm) {
			this.fiftyMeterLeftPoint = fiftyMeterLeftPoint;
			this.fiftyMeterRightPoint = fiftyMeterRightPoint;
			this.projectionWidthMm = projectionWidthMm;
			this.shooterDistanceMm = shooterDistanceMm;
		}

		public RangeBackgroundCalibration(RangeBackgroundCalibration other) {
			this(new StagePoint(other.fiftyMeterLeftPoint), new StagePoint(other.fiftyMeterRightPoint),
				other.projectionWidthMm, other.shooterDistanceMm);
		}

		public double averageFiftyMeterY() {
			return (fiftyMeterLeftPoint.yNormalized + fiftyMeterRightPoint.yNormalized) / 2.0;
		}

		public double farDepthYNormalized() {
			return clampNormalized(Math.min(DEFAULT_FAR_DEPTH_Y, averageFiftyMeterY() - 0.14));
		}

		public double nearFloorYNormalized() {
			return DEFAULT_NEAR_FLOOR_Y;
		}

		public double fiftyMeterYForX(double xNormalized) {
			final double dx = fiftyMeterRightPoint.xNormalized - fiftyMeterLeftPoint.xNormalized;
			if (Math.abs(dx) < 0.0001) return averageFiftyMeterY();

			final double t = clamp((xNormalized - fiftyMeterLeftPoint.xNormalized) / dx, 0.0, 1.0);
			return fiftyMeterLeftPoint.yNormalized
				+ (t * (fiftyMeterRightPoint.yNormalized - fiftyMeterLeftPoint.yNormalized));
		}
	}

	public static class TargetSpec {
		public int distanceMeters;
		public String side;
		public ArmyTargetType targetType;
		public double laneXNormalized;

		public TargetSpec(int distanceMeters, String side) {
			this(ArmyTargetType.E1, distanceMeters, side, Double.NaN);
		}

		public TargetSpec(int distanceMeters) {
			this(distanceMeters, "");
		}

		public TargetSpec(ArmyTargetType targetType, int distanceMeters, String side, double laneXNormalized) {
			this.targetType = targetType == null ? ArmyTargetType.E1 : targetType;
			this.distanceMeters = distanceMeters;
			this.side = side == null ? "" : side;
			this.laneXNormalized = laneXNormalized;
		}

		public TargetSpec(ArmyTargetType targetType, int distanceMeters, String side, double laneXNormalized,
				double legacyDepthNormalized) {
			this(targetType, distanceMeters, side, laneXNormalized);
		}

		public TargetSpec(TargetSpec other) {
			this(other.targetType, other.distanceMeters, other.side, other.laneXNormalized);
		}
	}

	public static class Engagement {
		public int number;
		public Position position;
		public List<TargetSpec> targets;
		public double exposureSec;
		public double delayBeforeSec;

		public Engagement(int number, Position position, double delayBeforeSec, double exposureSec,
				TargetSpec... targetsArray) {
			this.number = number;
			this.position = position;
			this.delayBeforeSec = delayBeforeSec;
			this.exposureSec = exposureSec;
			this.targets = new ArrayList<>(Arrays.asList(targetsArray));
		}

		public Engagement(Engagement other) {
			this(other.number, other.position, other.delayBeforeSec, other.exposureSec);
			this.targets.clear();
			for (TargetSpec target : other.targets) {
				this.targets.add(new TargetSpec(target));
			}
		}
	}

	public static class ArmyTableStageData {
		public RangeBackgroundCalibration backgroundCalibration;
		public List<Engagement> engagements;

		public ArmyTableStageData(RangeBackgroundCalibration backgroundCalibration, List<Engagement> engagements) {
			this.backgroundCalibration = backgroundCalibration;
			this.engagements = engagements;
		}

		public ArmyTableStageData(ArmyTableStageData other) {
			this(new RangeBackgroundCalibration(other.backgroundCalibration), copyEngagements(other.engagements));
		}
	}

	private static final Engagement[] DEFAULT_ENGAGEMENTS = {
		new Engagement(1,  Position.PRONE_UNSUPPORTED, 0, 3,  new TargetSpec(50, "RIGHT")),
		new Engagement(2,  Position.PRONE_UNSUPPORTED, 5, 3,  new TargetSpec(100)),
		new Engagement(3,  Position.PRONE_UNSUPPORTED, 2, 3,  new TargetSpec(150)),
		new Engagement(4,  Position.PRONE_UNSUPPORTED, 2, 12, new TargetSpec(50, "LEFT"), new TargetSpec(150), new TargetSpec(200)),
		new Engagement(5,  Position.PRONE_UNSUPPORTED, 2, 16, new TargetSpec(150), new TargetSpec(200), new TargetSpec(250), new TargetSpec(300)),
		new Engagement(6,  Position.PRONE_SUPPORTED, 8, 3,  new TargetSpec(100)),
		new Engagement(7,  Position.PRONE_SUPPORTED, 2, 5,  new TargetSpec(150), new TargetSpec(300)),
		new Engagement(8,  Position.PRONE_SUPPORTED, 2, 5,  new TargetSpec(200), new TargetSpec(300)),
		new Engagement(9,  Position.PRONE_SUPPORTED, 2, 5,  new TargetSpec(250), new TargetSpec(300)),
		new Engagement(10, Position.PRONE_SUPPORTED, 2, 12, new TargetSpec(150), new TargetSpec(250), new TargetSpec(300)),
		new Engagement(11, Position.KNEELING_SUPPORTED, 8, 12, new TargetSpec(50, "LEFT"), new TargetSpec(100), new TargetSpec(200)),
		new Engagement(12, Position.KNEELING_SUPPORTED, 2, 5,  new TargetSpec(50, "RIGHT"), new TargetSpec(200)),
		new Engagement(13, Position.KNEELING_SUPPORTED, 2, 5,  new TargetSpec(150), new TargetSpec(250)),
		new Engagement(14, Position.KNEELING_SUPPORTED, 2, 12, new TargetSpec(100), new TargetSpec(150), new TargetSpec(200)),
		new Engagement(15, Position.STANDING_SUPPORTED, 8, 5,  new TargetSpec(50, "LEFT"), new TargetSpec(100)),
		new Engagement(16, Position.STANDING_SUPPORTED, 2, 5,  new TargetSpec(200), new TargetSpec(250)),
		new Engagement(17, Position.STANDING_SUPPORTED, 2, 12, new TargetSpec(50, "RIGHT"), new TargetSpec(100), new TargetSpec(150)),
		new Engagement(18, Position.STANDING_SUPPORTED, 2, 12, new TargetSpec(100), new TargetSpec(200), new TargetSpec(250)),
	};

	public ArmyTableVIQualification() {}

	public ArmyTableVIQualification(List<Target> targets) {
		super(targets);
		thisSuper = super.getInstance();
	}

	public void setSessionListener(ArmySessionListener sessionListener) {
		this.sessionListener = Optional.ofNullable(sessionListener);
		notifySessionListener();
	}

	public ArmySessionStatus getLastSessionStatus() {
		return lastSessionStatus;
	}

	@Override
	public void init() {
		if (thisSuper == null) thisSuper = super.getInstance();

		try {
			final ArmyTableStageData savedStage = ArmyTableCustomStageManager.loadStage();
			reloadStageData(savedStage);
		} catch (Exception e) {
			reloadStageData(null);
		}

		super.setArenaBackground(BACKGROUND_RESOURCE);
		addSettingControls();
		final String introMessage = "Army Table VI Qualification\n\nSet projection width & shooter distance, then click Start.";
		thisSuper.showTextOnFeed(introMessage, true);
		updateSessionStatus(ArmySessionState.SETUP, "Army Trainer Ready",
			"Set projection width and shooter distance, calibrate the arena, then open the stage editor if needed.");
	}

	public static RangeBackgroundCalibration defaultBackgroundCalibration() {
		return new RangeBackgroundCalibration(
			new StagePoint(DEFAULT_FIFTY_METER_LEFT_X, DEFAULT_FIFTY_METER_LEFT_Y),
			new StagePoint(DEFAULT_FIFTY_METER_RIGHT_X, DEFAULT_FIFTY_METER_RIGHT_Y),
			DEFAULT_PROJECTION_WIDTH_MM,
			DEFAULT_SHOOTER_DISTANCE_MM
		);
	}

	private static List<Engagement> copyEngagements(List<Engagement> engagements) {
		final List<Engagement> copy = new ArrayList<>();
		if (engagements == null) return copy;

		for (Engagement engagement : engagements) {
			copy.add(new Engagement(engagement));
		}

		return copy;
	}

	private static ArmyTableStageData defaultStageData() {
		return new ArmyTableStageData(defaultBackgroundCalibration(), copyEngagements(Arrays.asList(DEFAULT_ENGAGEMENTS)));
	}

	public void reloadStageData(ArmyTableStageData customStage) {
		final ArmyTableStageData stageData = customStage == null ? defaultStageData() : new ArmyTableStageData(customStage);
		if (stageData.backgroundCalibration == null) {
			stageData.backgroundCalibration = defaultBackgroundCalibration();
		}

		normalizeEngagementLayout(stageData.engagements, stageData.backgroundCalibration);
		activeEngagements = stageData.engagements;
		activeCalibration = stageData.backgroundCalibration;
	}

	public void reloadEngagements(List<Engagement> customEngagements) {
		if (customEngagements == null) {
			reloadStageData(null);
			return;
		}

		final ArmyTableStageData stageData = new ArmyTableStageData(getActiveCalibrationCopy(), copyEngagements(customEngagements));
		reloadStageData(stageData);
	}

	public ArmyTableStageData getActiveStageDataCopy() {
		return new ArmyTableStageData(getActiveCalibrationCopy(), copyEngagements(activeEngagements));
	}

	public RangeBackgroundCalibration getActiveCalibrationCopy() {
		return new RangeBackgroundCalibration(activeCalibration == null ? defaultBackgroundCalibration() : activeCalibration);
	}

	private void addSettingControls() {
		final GridPane pane = new GridPane();
		pane.setHgap(8);
		pane.setVgap(5);
		pane.setPadding(new Insets(5, 10, 5, 10));
		pane.getColumnConstraints().addAll(
			new ColumnConstraints(135), new ColumnConstraints(70),
			new ColumnConstraints(140), new ColumnConstraints(70),
			new ColumnConstraints(50), new ColumnConstraints(70),
			new ColumnConstraints(150)
		);

		pane.add(new Label("Projection Width:"), 0, 0);
		projectionWidthField = new TextField(formatDistanceForField(activeCalibration.projectionWidthMm));
		projectionWidthField.setPrefWidth(65);
		pane.add(projectionWidthField, 1, 0);

		pane.add(new Label("Shooter Distance:"), 2, 0);
		shooterDistanceField = new TextField(formatDistanceForField(activeCalibration.shooterDistanceMm));
		shooterDistanceField.setPrefWidth(65);
		pane.add(shooterDistanceField, 3, 0);

		pane.add(new Label("Unit:"), 4, 0);
		unitSelector = new ComboBox<>();
		unitSelector.getItems().addAll("Meters", "Feet");
		unitSelector.getSelectionModel().select(0);
		unitSelector.setPrefWidth(65);
		pane.add(unitSelector, 5, 0);

		startButton = new Button("Start Qualification");
		startButton.setOnAction(e -> onStartClicked());
		pane.add(startButton, 6, 0);

		final Button editButton = new Button("Open Stage Editor");
		editButton.setOnAction(e -> new ArmyTableVIStageEditor(this, getActiveStageDataCopy()).show());
		pane.add(editButton, 7, 0);

		super.addExercisePane(pane);
	}

	private String formatDistanceForField(int valueMm) {
		return String.format(Locale.US, "%.2f", valueMm / 1000.0);
	}

	private void onStartClicked() {
		if (exerciseRunning) return;

		try {
			activeCalibration.projectionWidthMm = readDistanceFieldInMillimeters(projectionWidthField);
			activeCalibration.shooterDistanceMm = readDistanceFieldInMillimeters(shooterDistanceField);
		} catch (NumberFormatException e) {
			thisSuper.showTextOnFeed("Invalid values! Enter numbers only.", true);
			updateSessionStatus(ArmySessionState.ERROR, "Invalid setup values",
				"Projection width and shooter distance must be numeric.");
			return;
		}

		if (activeCalibration.projectionWidthMm <= 0 || activeCalibration.shooterDistanceMm <= 0) {
			thisSuper.showTextOnFeed("Values must be > 0!", true);
			updateSessionStatus(ArmySessionState.ERROR, "Invalid setup values",
				"Projection width and shooter distance must both be greater than zero.");
			return;
		}

		projectionWidthField.setDisable(true);
		shooterDistanceField.setDisable(true);
		unitSelector.setDisable(true);
		startButton.setDisable(true);
		startButton.setText("Running...");

		startExercise();
	}

	private int readDistanceFieldInMillimeters(TextField field) {
		double value = Double.parseDouble(field.getText().trim());
		if (!"Meters".equals(unitSelector.getValue())) {
			value *= 0.3048;
		}
		return (int) Math.round(value * 1000.0);
	}

	double getEditorProjectionWidthMm() {
		try {
			return readDistanceFieldInMillimeters(projectionWidthField);
		} catch (Exception ignored) {
			return activeCalibration.projectionWidthMm;
		}
	}

	double getEditorShooterDistanceMm() {
		try {
			return readDistanceFieldInMillimeters(shooterDistanceField);
		} catch (Exception ignored) {
			return activeCalibration.shooterDistanceMm;
		}
	}

	RangeBackgroundCalibration buildEditorCalibrationSnapshot() {
		final RangeBackgroundCalibration calibration = getActiveCalibrationCopy();
		calibration.projectionWidthMm = (int) Math.round(getEditorProjectionWidthMm());
		calibration.shooterDistanceMm = (int) Math.round(getEditorShooterDistanceMm());
		return calibration;
	}

	private void startExercise() {
		exerciseRunning = true;
		currentEngagement = 0;
		score = 0;
		updateSessionStatus(ArmySessionState.RUNNING, "Qualification starting",
			"Prepare to fire. The first engagement begins after the startup countdown.");

		super.addShotTimerColumn("HIT", 60);
		super.addShotTimerColumn("DIST", 100);
		super.addShotTimerColumn("ENG", 40);

		continueExercise.set(true);
		executorService = Executors.newScheduledThreadPool(2, new NamedThreadFactory("ArmyTableVIExercise"));

		showEngagementInfo("Starting in 5 seconds...\n\nProne Unsupported");
		executorService.schedule(this::runNextEngagement, 5, TimeUnit.SECONDS);
	}

	static void normalizeEngagementLayout(List<Engagement> engagements, RangeBackgroundCalibration calibration) {
		if (engagements == null) return;
		for (Engagement engagement : engagements) {
			normalizeTargetLayout(engagement.targets, calibration);
		}
	}

	static void normalizeTargetLayout(List<TargetSpec> targets, RangeBackgroundCalibration calibration) {
		if (targets == null || targets.isEmpty()) return;

		for (int index = 0; index < targets.size(); index++) {
			final TargetSpec target = targets.get(index);
			if (target.targetType == null) target.targetType = ArmyTargetType.E1;
			if (target.side == null) target.side = "";

			if (Double.isNaN(target.laneXNormalized) || target.laneXNormalized <= 0.0 || target.laneXNormalized >= 1.0) {
				target.laneXNormalized = defaultLaneX(target, index, targets.size());
			}
		}
	}

	static double defaultLaneX(TargetSpec target, int index, int totalCount) {
		if ("LEFT".equalsIgnoreCase(target.side)) return DEFAULT_LEFT_ANCHOR_X;
		if ("RIGHT".equalsIgnoreCase(target.side)) return DEFAULT_RIGHT_ANCHOR_X;
		if (totalCount <= 1) return 0.5;

		final double step = (DEFAULT_CENTER_MAX_X - DEFAULT_CENTER_MIN_X) / (totalCount - 1);
		return DEFAULT_CENTER_MIN_X + (index * step);
	}

	static double defaultDepthForDistance(int distanceMeters, RangeBackgroundCalibration calibration) {
		final RangeBackgroundCalibration safeCalibration = calibration == null ? defaultBackgroundCalibration() : calibration;
		final double line50 = safeCalibration.averageFiftyMeterY();
		final double farTop = safeCalibration.farDepthYNormalized();
		final double nearBottom = safeCalibration.nearFloorYNormalized();
		final double shooterDistanceMeters = Math.max(1.0, safeCalibration.shooterDistanceMm / 1000.0);
		final double clampedDistance = clamp(distanceMeters, shooterDistanceMeters, MAX_QUALIFICATION_DISTANCE_METERS);

		if (clampedDistance <= FIFTY_METER_DISTANCE_METERS) {
			final double numerator = (1.0 / clampedDistance) - (1.0 / FIFTY_METER_DISTANCE_METERS);
			final double denominator = (1.0 / shooterDistanceMeters) - (1.0 / FIFTY_METER_DISTANCE_METERS);
			final double t = denominator == 0 ? 0.0 : clamp(numerator / denominator, 0.0, 1.0);
			return clampNormalized(line50 + (t * (nearBottom - line50)));
		}

		final double numerator = (1.0 / clampedDistance) - (1.0 / MAX_QUALIFICATION_DISTANCE_METERS);
		final double denominator = (1.0 / FIFTY_METER_DISTANCE_METERS) - (1.0 / MAX_QUALIFICATION_DISTANCE_METERS);
		final double t = denominator == 0 ? 1.0 : clamp(numerator / denominator, 0.0, 1.0);
		return clampNormalized(farTop + (t * (line50 - farTop)));
	}

	static double[] targetPixelSize(TargetSpec spec, double arenaWidthPx, double arenaHeightPx,
			RangeBackgroundCalibration calibration) {
		final RangeBackgroundCalibration safeCalibration = calibration == null ? defaultBackgroundCalibration() : calibration;
		final double projectionWidthMeters = Math.max(0.1, safeCalibration.projectionWidthMm / 1000.0);
		final double shooterDistanceMeters = Math.max(1.0, safeCalibration.shooterDistanceMm / 1000.0);
		final double effectiveDistanceMeters = Math.max(shooterDistanceMeters, spec.distanceMeters);
		final double pixelsPerProjectedMeter = arenaWidthPx / projectionWidthMeters;
		final double apparentScale = shooterDistanceMeters / effectiveDistanceMeters;

		double widthPx = spec.targetType.getRealWidthMeters() * pixelsPerProjectedMeter * apparentScale;
		double heightPx = spec.targetType.getRealHeightMeters() * pixelsPerProjectedMeter * apparentScale;
		widthPx = Math.max(6, Math.min(widthPx, arenaWidthPx * 0.5));
		heightPx = Math.max(10, Math.min(heightPx, arenaHeightPx * 0.7));
		return new double[] { widthPx, heightPx };
	}

	private String posName(Position position) {
		switch (position) {
		case PRONE_UNSUPPORTED:
			return "Prone Unsupported";
		case PRONE_SUPPORTED:
			return "Prone Supported";
		case KNEELING_SUPPORTED:
			return "Kneeling Supported";
		case STANDING_SUPPORTED:
			return "Standing Supported";
		default:
			return position.name();
		}
	}

	private String buildDistLabel(Engagement engagement) {
		final StringBuilder builder = new StringBuilder();
		for (TargetSpec spec : engagement.targets) {
			if (builder.length() > 0) builder.append(", ");
			builder.append(spec.distanceMeters);
			if (!spec.side.isEmpty()) builder.append(" ").append(spec.side);
		}
		return builder.toString();
	}

	private void showEngagementInfo(String text) {
		thisSuper.showTextOnFeed(text, true);
		final ArmySessionState state = exerciseRunning ? ArmySessionState.RUNNING : ArmySessionState.SETUP;
		final String headline = exerciseRunning ? "Qualification in progress" : "Army Trainer Ready";
		updateSessionStatus(state, headline, text);
	}

	private void runNextEngagement() {
		if (!continueExercise.get()) return;

		if (currentEngagement >= activeEngagements.size()) {
			finishExercise();
			return;
		}

		Platform.runLater(() -> {
			for (Target target : activeTargets) thisSuper.removeTarget(target);
			activeTargets.clear();
		});

		final Engagement engagement = activeEngagements.get(currentEngagement);

		try {
			if (engagement.delayBeforeSec > 0) {
				if (engagement.delayBeforeSec >= 8) {
					Platform.runLater(() -> showEngagementInfo(
						"MAGAZINE & POSITION CHANGE\n" + posName(engagement.position)
							+ "\n" + (int) engagement.delayBeforeSec + " Seconds"));
				}
				Thread.sleep((long) (engagement.delayBeforeSec * 1000));
			}
		} catch (InterruptedException e) {
			return;
		}

		if (!continueExercise.get()) return;

		hitsThisEngagement.set(0);
		final int engagementIndex = currentEngagement;
		final RangeBackgroundCalibration calibrationSnapshot = getActiveCalibrationCopy();

		Platform.runLater(() -> {
			final double arenaWidth = thisSuper.getArenaWidth();
			final double arenaHeight = thisSuper.getArenaHeight();

			for (TargetSpec spec : engagement.targets) {
				final Optional<Target> optionalTarget = thisSuper.addTarget(spec.targetType.getTargetFile(), 0, 0);
				if (!optionalTarget.isPresent()) continue;

				final Target target = optionalTarget.get();
				final double[] size = targetPixelSize(spec, arenaWidth, arenaHeight, calibrationSnapshot);
				target.setDimensions(size[0], size[1]);

				final double anchorX = clamp(spec.laneXNormalized * arenaWidth, size[0] / 2.0, arenaWidth - (size[0] / 2.0));
				final double anchorY = clamp(defaultDepthForDistance(spec.distanceMeters, calibrationSnapshot) * arenaHeight,
					size[1], arenaHeight);
				target.setPosition(anchorX - (size[0] / 2.0), anchorY - size[1]);
				activeTargets.add(target);
			}

			showEngagementInfo(buildDistLabel(engagement) + "\n" + (int) engagement.exposureSec + " Seconds");
			executorService.schedule(() -> engagementTimeout(engagementIndex),
				(long) (engagement.exposureSec * 1000), TimeUnit.MILLISECONDS);
		});
	}

	private void engagementTimeout(int engagementIndex) {
		if (!continueExercise.get() || engagementIndex != currentEngagement) return;

		final Engagement engagement = activeEngagements.get(currentEngagement);
		final int hits = hitsThisEngagement.get();

		Platform.runLater(() -> {
			setShotTimerColumnText("ENG", String.valueOf(engagement.number));
			setShotTimerColumnText("HIT", hits + "/" + engagement.targets.size());

			final StringBuilder distances = new StringBuilder();
			for (TargetSpec spec : engagement.targets) {
				if (distances.length() > 0) distances.append(",");
				distances.append(spec.distanceMeters).append("m");
			}
			setShotTimerColumnText("DIST", distances.toString());
		});

		currentEngagement++;
		executorService.execute(this::runNextEngagement);
	}

	private void finishExercise() {
		Platform.runLater(() -> {
			for (Target target : activeTargets) thisSuper.removeTarget(target);
			activeTargets.clear();
		});

		final String qualification = determineQualification(score);

		showEngagementInfo("Qualification Complete!\nScore: " + score + " / " + TOTAL_TARGETS + "\nRating: " + qualification);
		updateSessionStatus(ArmySessionState.COMPLETED, "Qualification complete",
			String.format("Score %d / %d. Rating: %s.", score, TOTAL_TARGETS, qualification), Optional.of(qualification));

		Platform.runLater(() -> {
			exerciseRunning = false;
			startButton.setDisable(false);
			startButton.setText("Start Qualification");
			projectionWidthField.setDisable(false);
			shooterDistanceField.setDisable(false);
			unitSelector.setDisable(false);
		});
	}

	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {
		if (!hit.isPresent() || activeTargets.isEmpty()) return;

		final Target hitTarget = hit.get().getTarget();
		if (activeTargets.contains(hitTarget)) {
			score++;
			hitsThisEngagement.incrementAndGet();

			Platform.runLater(() -> {
				thisSuper.removeTarget(hitTarget);
				activeTargets.remove(hitTarget);
			});
		}
	}

	@Override
	public void reset(List<Target> targets) {
		continueExercise.set(false);
		if (executorService != null) executorService.shutdownNow();

		currentEngagement = 0;
		score = 0;
		exerciseRunning = false;

		Platform.runLater(() -> {
			for (Target target : activeTargets) thisSuper.removeTarget(target);
			activeTargets.clear();
			if (startButton != null) {
				startButton.setDisable(false);
				startButton.setText("Start Qualification");
			}
			if (projectionWidthField != null) projectionWidthField.setDisable(false);
			if (shooterDistanceField != null) shooterDistanceField.setDisable(false);
			if (unitSelector != null) unitSelector.setDisable(false);
		});

		updateSessionStatus(ArmySessionState.SETUP, "Army Trainer Ready",
			"Session reset. Reconfirm setup, calibration, and stage layout before starting.");

		init();
	}

	@Override
	public void destroy() {
		continueExercise.set(false);
		if (executorService != null) executorService.shutdownNow();
		super.destroy();
	}

	@Override
	public void targetUpdate(Target target, TargetChange change) {}

	@Override
	public ExerciseMetadata getInfo() {
		return new ExerciseMetadata("Army Table VI Qualification", "1.0", "Contributor",
			"U.S. Army Rifle/Carbine Qualification per TC 3-20.40 Table E-38 with a calibrated range background.");
	}

	private static double clampNormalized(double value) {
		return clamp(value, 0.01, 0.99);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String determineQualification(int score) {
		if (score >= 36) return "Expert";
		if (score >= 30) return "Sharpshooter";
		if (score >= 23) return "Marksman";
		return "Unqualified";
	}

	private void updateSessionStatus(ArmySessionState state, String headline, String detail) {
		updateSessionStatus(state, headline, detail, Optional.empty());
	}

	private void updateSessionStatus(ArmySessionState state, String headline, String detail,
			Optional<String> qualification) {
		lastSessionStatus = new ArmySessionStatus(state, headline, detail, score, TOTAL_TARGETS,
			Math.min(currentEngagement, activeEngagements.size()), activeEngagements.size(), qualification);
		notifySessionListener();
	}

	private void notifySessionListener() {
		if (!sessionListener.isPresent()) return;

		final ArmySessionStatus status = lastSessionStatus;
		final Runnable notifier = () -> sessionListener.ifPresent(listener -> listener.onStatusUpdated(status));
		if (Platform.isFxApplicationThread()) {
			notifier.run();
		} else {
			Platform.runLater(notifier);
		}
	}
}
