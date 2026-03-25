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

import com.shootoff.camera.Shot;
import com.shootoff.targets.Hit;
import com.shootoff.targets.Target;
import com.shootoff.targets.TargetRegion;
import com.shootoff.util.NamedThreadFactory;
import javafx.application.Platform;

public class ArmyTableVIQualification extends ProjectorTrainingExerciseBase implements TrainingExercise {
	private static final int ETYPE_WIDTH_MM = 495;
	private static final int ETYPE_HEIGHT_MM = 1016;
	private static final int TOTAL_ROUNDS = 40;

	private ScheduledExecutorService executorService;
	private final AtomicBoolean continueExercise = new AtomicBoolean(true);
	
	private int currentRound = 0;
	private int score = 0;
	private int currentDistance = 50;
	private Optional<Target> activeTarget = Optional.empty();

	private static class Engagement {
		int distanceMeters;
		double durationSeconds;
		public Engagement(int distanceMeters, double durationSeconds) { 
			this.distanceMeters = distanceMeters; 
			this.durationSeconds = durationSeconds; 
		}
	}

	private final List<Engagement> engagements = new ArrayList<>();
	private ProjectorTrainingExerciseBase thisSuper;

	public ArmyTableVIQualification() {
		buildEngagements();
	}

	public ArmyTableVIQualification(List<Target> targets) {
		super(targets);
		thisSuper = super.getInstance();
		buildEngagements();
	}

	private void buildEngagements() {
		engagements.clear();
		Random rng = new Random();
		int[] availableDistances = {50, 100, 150, 200, 250, 300};
		for (int i = 0; i < TOTAL_ROUNDS; i++) {
			int dist = availableDistances[rng.nextInt(availableDistances.length)];
			// Approximation of standard exposure durations 
			// 50m = 3s, 100m = 4s, 150m = 5s, 200m = 6s, 250m = 7s, 300m = 8s
			double dur = 3.0 + ((dist - 50) / 50.0); 
			engagements.add(new Engagement(dist, dur));
		}
	}

	@Override
	public void init() {
		if (thisSuper == null) thisSuper = super.getInstance();

		if (!isPerspectiveInitialized()) {
			thisSuper.showTextOnFeed("Perspective calibration required!\nPlease run Calibrate Projection -> Perspective\nto configure screen size and shooter distance first.", true);
			return;
		}

		super.addShotTimerColumn("HIT", 60);
		super.addShotTimerColumn("DISTANCE", 120);

		continueExercise.set(true);
		executorService = Executors.newScheduledThreadPool(2, new NamedThreadFactory("ArmyTableVIExercise"));
		
		thisSuper.showTextOnFeed("Army Table VI Qualification\nScore: 0 / 40\nStage: Prone Supported\nStarting in 5 seconds...", true);
		executorService.schedule(new NextRound(), 5, TimeUnit.SECONDS);
	}

	private class NextRound implements Runnable {
		@Override
		public void run() {
			if (!continueExercise.get()) return;

			if (currentRound >= TOTAL_ROUNDS) {
				finishExercise();
				return;
			}
			
			if (activeTarget.isPresent()) {
				final Target t = activeTarget.get();
				Platform.runLater(() -> thisSuper.removeTarget(t));
				activeTarget = Optional.empty();
			}

			// Random delay between 2 and 5 seconds before target exposure
			long delay = 2000 + new Random().nextInt(3000);
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				return;
			}
			
			if (!continueExercise.get()) return;

			Engagement eng = engagements.get(currentRound);
			currentDistance = eng.distanceMeters;
			
			Platform.runLater(() -> {
				final double arenaWidth = thisSuper.getArenaWidth();
				final double arenaHeight = thisSuper.getArenaHeight();
				
				// Using the green silhouette as an E-Type equivalent
				File targetFile = new File("targets/Swedish_Soldier.target");
				
				Optional<Target> t = thisSuper.addTarget(targetFile, 0, 0);
				if (t.isPresent()) {
					Target target = t.get();
					boolean scaled = thisSuper.setTargetDistance(target, ETYPE_WIDTH_MM, ETYPE_HEIGHT_MM, eng.distanceMeters * 1000);
					if (scaled) {
						double maxW = arenaWidth - target.getDimension().getWidth();
						double maxH = arenaHeight - target.getDimension().getHeight();
						double rx = maxW > 0 ? new Random().nextDouble() * maxW : 0;
						double ry = maxH > 0 ? (new Random().nextDouble() * maxH * 0.5) + (maxH * 0.5) : 0; 
						
						target.setPosition(rx, ry);
						activeTarget = Optional.of(target);
						
						String stageName = "Prone Supported";
						if (currentRound >= 10) stageName = "Prone Unsupported";
						if (currentRound >= 20) stageName = "Kneeling Supported";
						if (currentRound >= 30) stageName = "Standing Supported";
						
						thisSuper.showTextOnFeed(String.format("Army Table VI Qualification\nScore: %d / 40\nStage: %s\nTarget: %dm", score, stageName, currentDistance), true);
						
						executorService.schedule(new TargetTimeout(currentRound), (long)(eng.durationSeconds * 1000), TimeUnit.MILLISECONDS);
					} else {
						thisSuper.removeTarget(target);
					}
				}
			});
		}
	}

	private class TargetTimeout implements Runnable {
		private final int roundIdx;
		public TargetTimeout(int roundIdx) { this.roundIdx = roundIdx; }
		
		@Override
		public void run() {
			if (!continueExercise.get() || roundIdx != currentRound) return;
			// Time expired without a hit
			
			// Optional: log "MISS" to the table
			Platform.runLater(() -> {
				setShotTimerColumnText("HIT", "MISS");
				setShotTimerColumnText("DISTANCE", currentDistance + "m");
			});
			
			currentRound++;
			executorService.execute(new NextRound());
		}
	}
	
	private void finishExercise() {
		String qual = "Unqualified";
		if (score >= 36) qual = "Expert";
		else if (score >= 30) qual = "Sharpshooter";
		else if (score >= 23) qual = "Marksman";
		
		thisSuper.showTextOnFeed(String.format("Qualification Complete!\nTotal Score: %d / 40\nRating: %s", score, qual), true);
	}

	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {
		if (hit.isPresent() && activeTarget.isPresent()) {
			final Target currentTgt = activeTarget.get();
			final Target hitTgt = hit.get().getTarget();

			if (hitTgt == currentTgt) { // identity check is sufficient for ShootOFF targets
				score++;
				
				Platform.runLater(() -> {
					thisSuper.removeTarget(currentTgt);
					activeTarget = Optional.empty();
					
					super.setShotTimerColumnText("HIT", "HIT");
					super.setShotTimerColumnText("DISTANCE", currentDistance + "m");
				});
				
				currentRound++;
				executorService.execute(new NextRound());
			}
		}
	}

	@Override
	public void reset(List<Target> targets) {
		continueExercise.set(false);
		if (executorService != null) executorService.shutdownNow();

		currentRound = 0;
		score = 0;
		buildEngagements();
		
		if (activeTarget.isPresent()) {
			thisSuper.removeTarget(activeTarget.get());
			activeTarget = Optional.empty();
		}

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
				"Simulates the 40-round U.S. Army Rifle Qualification (TC 3-20.40 Table VI) using mathematically scaled E-Type silhouette targets from 50m to 300m. Requires perspective calibration to calculate exact scaling based on shooter distance.");
	}
}
