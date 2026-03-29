package com.shootoff.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.shootoff.camera.Shot;
import com.shootoff.targets.Hit;
import com.shootoff.targets.Target;
import com.shootoff.util.NamedThreadFactory;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

/**
 * U.S. Army Rifle/Carbine Qualification per TC 3-20.40 Table E-38.
 * 18 engagements, 40 total targets, 4 firing positions.
 *
 * Targets are positioned on a single horizontal "berm line" across the arena,
 * with size decreasing proportionally by distance — matching the visual style
 * of a real pop-up target range.
 *
 * No perspective calibration needed — uses geometric scaling:
 *   apparentSize = realSize × (shooterDistance / targetDistance)
 *   pixels = apparentSize × (arenaWidthPx / screenWidthMeters)
 */
public class ArmyTableVIQualification extends ProjectorTrainingExerciseBase implements TrainingExercise {

	// E-type silhouette real dimensions (meters)
	private static final double ETYPE_WIDTH_M  = 0.495;
	private static final double ETYPE_HEIGHT_M = 1.016;

	// Berm line vertical position (fraction of arena height from top)
	// All targets sit here regardless of distance, just like a real range
	private static final double BERM_LINE = 0.50;

	private ScheduledExecutorService executorService;
	private final AtomicBoolean continueExercise = new AtomicBoolean(true);

	private int currentEngagement = 0;
	private int score = 0;
	private static final int TOTAL_TARGETS = 40;
	private final List<Target> activeTargets = new ArrayList<>();
	private final AtomicInteger hitsThisEngagement = new AtomicInteger(0);

	private double screenWidthM = 1.2;  // meters
	private double shooterDistM = 3.0;  // meters
	private boolean exerciseRunning = false;

	private TextField screenWidthField;
	private TextField shooterDistanceField;
	private ComboBox<String> unitSelector;
	private Button startButton;

	private ProjectorTrainingExerciseBase thisSuper;

	// ─── Engagement data model ───────────────────────────────────────

	public enum Position { PRONE_UNSUPPORTED, PRONE_SUPPORTED, KNEELING_SUPPORTED, STANDING_SUPPORTED }

	public static class TargetSpec {
		public int distanceMeters;
		public String side; // "LEFT", "RIGHT", or ""
		public TargetSpec(int dist, String side) { this.distanceMeters = dist; this.side = side; }
		public TargetSpec(int dist)              { this(dist, ""); }
	}

	public static class Engagement {
		public int number;
		public Position position;
		public List<TargetSpec> targets;
		public double exposureSec;
		public double delayBeforeSec;

		public Engagement(int num, Position pos, double delay, double exposure, TargetSpec... targetsArray) {
			this.number = num;
			this.position = pos;
			this.delayBeforeSec = delay;
			this.exposureSec = exposure;
			this.targets = new ArrayList<>(java.util.Arrays.asList(targetsArray));
		}
	}

	// Default TC 3-20.40 Table E-38 — exact engagement sequence ──────────────
	private static final Engagement[] DEFAULT_ENGAGEMENTS = {
		// ── Engagements 1-5: React to Contact → Prone Unsupported ──
		// 5s delay after eng1; 2s others; 8s mag change after eng5
		new Engagement(1,  Position.PRONE_UNSUPPORTED, 0, 3,  new TargetSpec(50, "RIGHT")),
		new Engagement(2,  Position.PRONE_UNSUPPORTED, 5, 3,  new TargetSpec(100)),
		new Engagement(3,  Position.PRONE_UNSUPPORTED, 2, 3,  new TargetSpec(150)),
		new Engagement(4,  Position.PRONE_UNSUPPORTED, 2, 12, new TargetSpec(50, "LEFT"), new TargetSpec(150), new TargetSpec(200)),
		new Engagement(5,  Position.PRONE_UNSUPPORTED, 2, 16, new TargetSpec(150), new TargetSpec(200), new TargetSpec(250), new TargetSpec(300)),

		// ── Engagements 6-10: Prone Supported ──
		new Engagement(6,  Position.PRONE_SUPPORTED, 8, 3,  new TargetSpec(100)),
		new Engagement(7,  Position.PRONE_SUPPORTED, 2, 5,  new TargetSpec(150), new TargetSpec(300)),
		new Engagement(8,  Position.PRONE_SUPPORTED, 2, 5,  new TargetSpec(200), new TargetSpec(300)),
		new Engagement(9,  Position.PRONE_SUPPORTED, 2, 5,  new TargetSpec(250), new TargetSpec(300)),
		new Engagement(10, Position.PRONE_SUPPORTED, 2, 12, new TargetSpec(150), new TargetSpec(250), new TargetSpec(300)),

		// ── Engagements 11-14: Kneeling Supported ──
		new Engagement(11, Position.KNEELING_SUPPORTED, 8, 12, new TargetSpec(50, "LEFT"), new TargetSpec(100), new TargetSpec(200)),
		new Engagement(12, Position.KNEELING_SUPPORTED, 2, 5,  new TargetSpec(50, "RIGHT"), new TargetSpec(200)),
		new Engagement(13, Position.KNEELING_SUPPORTED, 2, 5,  new TargetSpec(150), new TargetSpec(250)),
		new Engagement(14, Position.KNEELING_SUPPORTED, 2, 12, new TargetSpec(100), new TargetSpec(150), new TargetSpec(200)),

		// ── Engagements 15-18: Standing Supported ──
		new Engagement(15, Position.STANDING_SUPPORTED, 8, 5,  new TargetSpec(50, "LEFT"), new TargetSpec(100)),
		new Engagement(16, Position.STANDING_SUPPORTED, 2, 5,  new TargetSpec(200), new TargetSpec(250)),
		new Engagement(17, Position.STANDING_SUPPORTED, 2, 12, new TargetSpec(50, "RIGHT"), new TargetSpec(100), new TargetSpec(150)),
		new Engagement(18, Position.STANDING_SUPPORTED, 2, 12, new TargetSpec(100), new TargetSpec(200), new TargetSpec(250)),
	};

	// ─── Construction ────────────────────────────────────────────────

	public ArmyTableVIQualification() {}

	public ArmyTableVIQualification(List<Target> targets) {
		super(targets);
		thisSuper = super.getInstance();
	}

	private List<Engagement> activeEngagements;

	public void reloadEngagements(List<Engagement> custom) {
		if (custom != null) {
			this.activeEngagements = new ArrayList<>(custom);
		} else {
			this.activeEngagements = new ArrayList<>(java.util.Arrays.asList(DEFAULT_ENGAGEMENTS));
		}
	}

	// ─── Init & Settings UI ──────────────────────────────────────────

	@Override
	public void init() {
		if (thisSuper == null) thisSuper = super.getInstance();

		try {
			List<Engagement> saved = ArmyTableCustomStageManager.loadEngagements();
			reloadEngagements(saved);
		} catch (Exception e) {
			reloadEngagements(null);
		}

		// Set the Army qualification range background
		super.setArenaBackground("/arena/backgrounds/army_qualification_range_2.png");

		addSettingControls();
		thisSuper.showTextOnFeed("Army Table VI Qualification\n\nSet screen width & shooter distance, then click Start.", true);
	}

	private void addSettingControls() {
		final GridPane p = new GridPane();
		p.setHgap(8);
		p.setVgap(5);
		p.setPadding(new Insets(5, 10, 5, 10));

		p.getColumnConstraints().addAll(
			new ColumnConstraints(120), new ColumnConstraints(60),
			new ColumnConstraints(130), new ColumnConstraints(60),
			new ColumnConstraints(50),  new ColumnConstraints(70),
			new ColumnConstraints(130)
		);

		p.add(new Label("Screen Width:"), 0, 0);
		screenWidthField = new TextField("1.2");
		screenWidthField.setPrefWidth(55);
		p.add(screenWidthField, 1, 0);

		p.add(new Label("Shooter Distance:"), 2, 0);
		shooterDistanceField = new TextField("3.0");
		shooterDistanceField.setPrefWidth(55);
		p.add(shooterDistanceField, 3, 0);

		p.add(new Label("Unit:"), 4, 0);
		unitSelector = new ComboBox<>();
		unitSelector.getItems().addAll("Meters", "Feet");
		unitSelector.getSelectionModel().select(0);
		unitSelector.setPrefWidth(65);
		p.add(unitSelector, 5, 0);

		startButton = new Button("Start Qualification");
		startButton.setOnAction(e -> onStartClicked());
		p.add(startButton, 6, 0);

		Button editButton = new Button("Open Stage Editor");
		editButton.setOnAction(e -> {
			new ArmyTableVIStageEditor(this, activeEngagements).show();
		});
		p.add(editButton, 7, 0);

		super.addExercisePane(p);
	}

	private void onStartClicked() {
		if (exerciseRunning) return;

		try {
			double sw = Double.parseDouble(screenWidthField.getText().trim());
			double sd = Double.parseDouble(shooterDistanceField.getText().trim());

			if (!"Meters".equals(unitSelector.getValue())) {
				sw *= 0.3048;
				sd *= 0.3048;
			}

			screenWidthM = sw;
			shooterDistM = sd;
		} catch (NumberFormatException e) {
			thisSuper.showTextOnFeed("Invalid values! Enter numbers only.", true);
			return;
		}

		if (screenWidthM <= 0 || shooterDistM <= 0) {
			thisSuper.showTextOnFeed("Values must be > 0!", true);
			return;
		}

		screenWidthField.setDisable(true);
		shooterDistanceField.setDisable(true);
		unitSelector.setDisable(true);
		startButton.setDisable(true);
		startButton.setText("Running...");

		startExercise();
	}

	// ─── Geometric scaling ───────────────────────────────────────────

	/**
	 * apparentSize = realSize × (shooterDist / targetDist)
	 * pixels       = apparentSize × (arenaWidthPx / screenWidthM)
	 */
	private double[] targetPixelSize(int targetDistM, double arenaW, double arenaH) {
		double scale = shooterDistM / (double) targetDistM;
		double wPx = ETYPE_WIDTH_M  * scale * (arenaW / screenWidthM);
		double hPx = ETYPE_HEIGHT_M * scale * (arenaW / screenWidthM);

		// Clamp: minimum visible, maximum fits on screen
		wPx = Math.max(6,  Math.min(wPx, arenaW * 0.5));
		hPx = Math.max(10, Math.min(hPx, arenaH * 0.7));
		return new double[]{ wPx, hPx };
	}

	// ─── Exercise lifecycle ──────────────────────────────────────────

	private void startExercise() {
		exerciseRunning = true;
		currentEngagement = 0;
		score = 0;

		super.addShotTimerColumn("HIT", 60);
		super.addShotTimerColumn("DIST", 100);
		super.addShotTimerColumn("ENG", 40);

		continueExercise.set(true);
		executorService = Executors.newScheduledThreadPool(2, new NamedThreadFactory("ArmyTableVIExercise"));

		showEngagementInfo("Starting in 5 seconds...\n\nProne Unsupported");
		executorService.schedule(this::runNextEngagement, 5, TimeUnit.SECONDS);
	}

	private String posName(Position p) {
		switch (p) {
			case PRONE_UNSUPPORTED:  return "Prone Unsupported";
			case PRONE_SUPPORTED:    return "Prone Supported";
			case KNEELING_SUPPORTED: return "Kneeling Supported";
			case STANDING_SUPPORTED: return "Standing Supported";
			default: return p.name();
		}
	}

	/** Builds distance label like "50 Left" or "100, 150, 200, 250" */
	private String buildDistLabel(Engagement eng) {
		StringBuilder sb = new StringBuilder();
		for (TargetSpec s : eng.targets) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(s.distanceMeters);
			if (!s.side.isEmpty()) sb.append(" ").append(s.side);
		}
		return sb.toString();
	}

	/** Shows clean info text in bottom-left region matching the video style */
	private void showEngagementInfo(String text) {
		thisSuper.showTextOnFeed(text, true);
	}

	// ─── Engagement runner ───────────────────────────────────────────

	private void runNextEngagement() {
		if (!continueExercise.get()) return;

		if (currentEngagement >= activeEngagements.size()) {
			finishExercise();
			return;
		}

		// Clear previous targets
		Platform.runLater(() -> {
			for (Target t : activeTargets) thisSuper.removeTarget(t);
			activeTargets.clear();
		});

		final Engagement eng = activeEngagements.get(currentEngagement);

		// Pre-engagement delay
		try {
			if (eng.delayBeforeSec > 0) {
				if (eng.delayBeforeSec >= 8) {
					Platform.runLater(() -> showEngagementInfo(
						"MAGAZINE & POSITION CHANGE\n" + posName(eng.position)
						+ "\n" + (int) eng.delayBeforeSec + " Seconds"));
				}
				Thread.sleep((long)(eng.delayBeforeSec * 1000));
			}
		} catch (InterruptedException e) { return; }

		if (!continueExercise.get()) return;

		hitsThisEngagement.set(0);
		final int engIdx = currentEngagement;

		Platform.runLater(() -> {
			final double arenaW = thisSuper.getArenaWidth();
			final double arenaH = thisSuper.getArenaHeight();

			// The vertical berm line where ALL targets sit
			double bermY = arenaH * BERM_LINE;

			// Define horizontal lanes for target placement
			// Targets spread evenly across the arena width
			int count = eng.targets.size();

			for (int i = 0; i < count; i++) {
				TargetSpec spec = eng.targets.get(i);
				File targetFile = new File("targets/Swedish_Soldier.target");
				Optional<Target> t = thisSuper.addTarget(targetFile, 0, 0);

				if (t.isPresent()) {
					Target target = t.get();
					double[] sz = targetPixelSize(spec.distanceMeters, arenaW, arenaH);
					target.setDimensions(sz[0], sz[1]);

					// Horizontal position
					double x;
					if ("LEFT".equals(spec.side)) {
						// Left side of lane (first ~25% of arena)
						x = arenaW * 0.05 + new Random().nextDouble() * (arenaW * 0.15);
					} else if ("RIGHT".equals(spec.side)) {
						// Right side of lane (last ~25% of arena)
						x = arenaW * 0.75 + new Random().nextDouble() * (arenaW * 0.15);
					} else {
						// Spread center targets evenly across the arena
						double segW = arenaW * 0.7 / count; // use 70% center area
						x = arenaW * 0.15 + segW * i + new Random().nextDouble() * Math.max(1, segW - sz[0]);
					}
					x = Math.max(0, Math.min(x, arenaW - sz[0]));

					// Vertical: target bottom sits at the berm line
					double y = bermY - sz[1];
					y = Math.max(0, Math.min(y, arenaH - sz[1]));

					target.setPosition(x, y);
					activeTargets.add(target);
				}
			}

			// Show info in video style: "150, 200, 250\n16 Seconds"
			String distLabel = buildDistLabel(eng);
			showEngagementInfo(distLabel + "\n" + (int) eng.exposureSec + " Seconds");

			// Schedule timeout
			executorService.schedule(() -> engagementTimeout(engIdx),
				(long)(eng.exposureSec * 1000), TimeUnit.MILLISECONDS);
		});
	}

	private void engagementTimeout(int engIdx) {
		if (!continueExercise.get() || engIdx != currentEngagement) return;

		final Engagement eng = activeEngagements.get(currentEngagement);
		int hits = hitsThisEngagement.get();

		Platform.runLater(() -> {
			setShotTimerColumnText("ENG", String.valueOf(eng.number));
			setShotTimerColumnText("HIT", hits + "/" + eng.targets.size());

			StringBuilder d = new StringBuilder();
			for (TargetSpec s : eng.targets) {
				if (d.length() > 0) d.append(",");
				d.append(s.distanceMeters).append("m");
			}
			setShotTimerColumnText("DIST", d.toString());
		});

		currentEngagement++;
		executorService.execute(this::runNextEngagement);
	}

	private void finishExercise() {
		Platform.runLater(() -> {
			for (Target t : activeTargets) thisSuper.removeTarget(t);
			activeTargets.clear();
		});

		String qual = "Unqualified";
		if (score >= 36) qual = "Expert";
		else if (score >= 30) qual = "Sharpshooter";
		else if (score >= 23) qual = "Marksman";

		showEngagementInfo("Qualification Complete!\nScore: " + score + " / " + TOTAL_TARGETS + "\nRating: " + qual);

		Platform.runLater(() -> {
			exerciseRunning = false;
			startButton.setDisable(false);
			startButton.setText("Start Qualification");
			screenWidthField.setDisable(false);
			shooterDistanceField.setDisable(false);
			unitSelector.setDisable(false);
		});
	}

	// ─── Shot handling ───────────────────────────────────────────────

	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {
		if (!hit.isPresent() || activeTargets.isEmpty()) return;

		final Target hitTgt = hit.get().getTarget();
		if (activeTargets.contains(hitTgt)) {
			score++;
			hitsThisEngagement.incrementAndGet();

			Platform.runLater(() -> {
				thisSuper.removeTarget(hitTgt);
				activeTargets.remove(hitTgt);
			});
		}
	}

	// ─── Reset / Destroy ─────────────────────────────────────────────

	@Override
	public void reset(List<Target> targets) {
		continueExercise.set(false);
		if (executorService != null) executorService.shutdownNow();

		currentEngagement = 0;
		score = 0;
		exerciseRunning = false;

		Platform.runLater(() -> {
			for (Target t : activeTargets) thisSuper.removeTarget(t);
			activeTargets.clear();
			if (startButton != null) { startButton.setDisable(false); startButton.setText("Start Qualification"); }
			if (screenWidthField != null) screenWidthField.setDisable(false);
			if (shooterDistanceField != null) shooterDistanceField.setDisable(false);
			if (unitSelector != null) unitSelector.setDisable(false);
		});

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
			"U.S. Army Rifle/Carbine Qualification per TC 3-20.40 Table E-38. "
			+ "18 engagements, 40 targets, 4 positions. Targets are geometrically "
			+ "scaled and placed on a single berm line like a real pop-up range.");
	}
}
