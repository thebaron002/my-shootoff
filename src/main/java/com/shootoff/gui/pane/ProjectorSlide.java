/*
 * ShootOFF - Software for Laser Dry Fire Training
 * Copyright (C) 2016 phrack
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.shootoff.gui.pane;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shootoff.AppBranding;
import com.shootoff.camera.CameraManager;
import com.shootoff.config.ConfigurationException;
import com.shootoff.camera.perspective.PerspectiveManager;
import com.shootoff.config.Configuration;
import com.shootoff.gui.CalibrationConfigurator;
import com.shootoff.gui.CalibrationListener;
import com.shootoff.gui.CalibrationManager;
import com.shootoff.gui.CalibrationMode;
import com.shootoff.gui.CalibrationOption;
import com.shootoff.gui.CanvasManager;
import com.shootoff.gui.MirroredCanvasManager;
import com.shootoff.gui.Resetter;
import com.shootoff.plugins.ProjectorTrainingExerciseBase;
import com.shootoff.targets.CameraViews;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class ProjectorSlide extends Slide implements CalibrationConfigurator {
	private static final Logger logger = LoggerFactory.getLogger(ProjectorSlide.class);

	private final Pane parentControls;
	private final Pane parentBody;
	private final Configuration config;
	private final CameraViews cameraViews;
	private final Stage shootOffStage;
	private final Pane trainingExerciseContainer;
	private final Resetter resetter;
	private final ExerciseSlide exerciseSlide;
	private final SplitMenuButton calibrateButton;
	private final RadioMenuItem autoGreenCalibrationItem;
	private final RadioMenuItem autoTagsCalibrationItem;
	private final RadioMenuItem manualCalibrationItem;

	private ArenaBackgroundsSlide backgroundsSlide;

	private ProjectorArenaPane arenaPane;
	private Optional<CalibrationManager> calibrationManager = Optional.empty();

	public ProjectorSlide(Pane parentControls, Pane parentBody, CameraViews cameraViews, Stage shootOffStage,
			Pane trainingExerciseContainer, Resetter resetter, ExerciseSlide exerciseSlide) {
		super(parentControls, parentBody);

		this.parentControls = parentControls;
		this.parentBody = parentBody;
		config = Configuration.getConfig();
		this.cameraViews = cameraViews;
		this.shootOffStage = shootOffStage;
		this.trainingExerciseContainer = trainingExerciseContainer;
		this.resetter = resetter;
		this.exerciseSlide = exerciseSlide;

		calibrateButton = addSlideControlNode(new SplitMenuButton());
		calibrateButton.setWrapText(true);
		calibrateButton.setOnAction((event) -> {
			if (!calibrationManager.isPresent()) return;

			final CalibrationManager calibrator = calibrationManager.get();

			if (!calibrator.isCalibrating()) {
				calibrator.enableCalibration();
			} else {
				calibrator.stopCalibration();
			}
		});
		final ToggleGroup calibrationModeToggle = new ToggleGroup();
		autoGreenCalibrationItem = new RadioMenuItem(CalibrationMode.AUTO_GREEN.getDisplayName());
		autoGreenCalibrationItem.setToggleGroup(calibrationModeToggle);
		autoGreenCalibrationItem.setOnAction((event) -> setCalibrationMode(CalibrationMode.AUTO_GREEN, true));
		autoTagsCalibrationItem = new RadioMenuItem(CalibrationMode.AUTO_TAGS.getDisplayName());
		autoTagsCalibrationItem.setToggleGroup(calibrationModeToggle);
		autoTagsCalibrationItem.setOnAction((event) -> setCalibrationMode(CalibrationMode.AUTO_TAGS, true));
		manualCalibrationItem = new RadioMenuItem(CalibrationMode.MANUAL.getDisplayName());
		manualCalibrationItem.setToggleGroup(calibrationModeToggle);
		manualCalibrationItem.setOnAction((event) -> setCalibrationMode(CalibrationMode.MANUAL, true));
		calibrateButton.getItems().addAll(autoGreenCalibrationItem, autoTagsCalibrationItem, manualCalibrationItem);
		setCalibrationMode(config.getCalibrationMode(), false);

		addSlideControlButton("Background", (event) -> {
			backgroundsSlide.showControls();
			backgroundsSlide.showBody();
		});

		addSlideControlButton("Courses", (event) -> {
			final ArenaCoursesSlide coursesSlide = new ArenaCoursesSlide(parentControls, parentBody, arenaPane,
					shootOffStage);
			coursesSlide.setOnSlideHidden(() -> {
				if (coursesSlide.choseCourse()) {
					hide();
				}
			});
			coursesSlide.showControls();
			coursesSlide.showBody();
		});
	}

	@Override
	public CalibrationOption getCalibratedFeedBehavior() {
		return config.getCalibratedFeedBehavior();
	}

	@Override
	public CalibrationMode getCalibrationMode() {
		return config.getCalibrationMode();
	}

	@Override
	public void calibratedFeedBehaviorsChanged() {
		if (calibrationManager.isPresent())
			calibrationManager.get().configureArenaCamera(config.getCalibratedFeedBehavior());

		if (arenaPane != null) arenaPane.getCanvasManager().setShowShots(config.showArenaShotMarkers());
	}

	@Override
	public void toggleCalibrating(boolean isCalibrating) {
		final Runnable toggleCalibrationAction = () -> updateCalibrationButtonText(isCalibrating);

		if (Platform.isFxApplicationThread()) {
			toggleCalibrationAction.run();
		} else {
			Platform.runLater(toggleCalibrationAction);
		}
	}

	public ProjectorArenaPane getArenaPane() {
		return arenaPane;
	}

	public Optional<CalibrationManager> getCalibrationManager() {
		return calibrationManager;
	}

	private void setCalibrationMode(CalibrationMode calibrationMode, boolean persist) {
		config.setCalibrationMode(calibrationMode);

		if (CalibrationMode.AUTO_GREEN.equals(calibrationMode)) {
			autoGreenCalibrationItem.setSelected(true);
		} else if (CalibrationMode.AUTO_TAGS.equals(calibrationMode)) {
			autoTagsCalibrationItem.setSelected(true);
		} else {
			manualCalibrationItem.setSelected(true);
		}

		updateCalibrationButtonText(calibrationManager.isPresent() && calibrationManager.get().isCalibrating());

		if (persist) persistCalibrationMode();
	}

	private void persistCalibrationMode() {
		try {
			config.writeConfigurationFile();
		} catch (ConfigurationException | IOException e) {
			logger.warn("Failed to persist calibration mode preference", e);
		}
	}

	private void updateCalibrationButtonText(boolean isCalibrating) {
		if (isCalibrating) {
			calibrateButton.setText("Stop\nCalibration");
		} else {
			calibrateButton.setText(String.format("Calibrate\n%s", config.getCalibrationMode().getDisplayName()));
		}
	}

	public void startArena() {
		startArena(true);
	}

	public void startArena(boolean autoStartCalibration) {
		if (arenaPane != null) {
			// Already started
			return;
		}

		final Stage arenaStage = new Stage();

		arenaPane = new ProjectorArenaPane(arenaStage, shootOffStage, trainingExerciseContainer, resetter, null);

		// Prepare calibrating manager up front so that we can switch
		// to the arena tab when it's ready (otherwise
		// getSelectedCameraManager() will fail)
		final CameraManager calibratingCameraManager = cameraViews.getSelectedCameraManager();

		// Mirror panes so that anything that happens to one also
		// happens to the other
		final ProjectorArenaPane arenaTabPane = new ProjectorArenaPane(arenaStage, shootOffStage,
				trainingExerciseContainer, resetter, cameraViews.getShotTimerModel());

		arenaTabPane.prefWidthProperty().bind(arenaPane.prefWidthProperty());
		arenaTabPane.prefHeightProperty().bind(arenaPane.prefHeightProperty());

		cameraViews.addNonCameraView("Arena", arenaTabPane, arenaTabPane.getCanvasManager(), true, true);

		arenaPane.setArenaPaneMirror(arenaTabPane);

		final CanvasManager arenaCanvasManager = arenaPane.getCanvasManager();

		arenaCanvasManager.setCameraManager(calibratingCameraManager);

		if (!(arenaCanvasManager instanceof MirroredCanvasManager)) {
			throw new AssertionError("Arena canvas manager is not of type MirroredCanvasManager");
		}

		final MirroredCanvasManager projectorCanvasManager = (MirroredCanvasManager) arenaCanvasManager;

		final CanvasManager tabArenaCanvasManager = arenaTabPane.getCanvasManager();

		if (!(tabArenaCanvasManager instanceof MirroredCanvasManager)) {
			throw new AssertionError("Tab arena canvas manager is not of type MirroredCanvasManager");
		}

		final MirroredCanvasManager tabCanvasManager = (MirroredCanvasManager) tabArenaCanvasManager;

		projectorCanvasManager.setMirroredManager(tabCanvasManager);
		tabCanvasManager.setMirroredManager(projectorCanvasManager);
		projectorCanvasManager.updateBackground(null, Optional.empty());
		// This camera manager must be set to enable click-to-shoot for
		// the arena tab
		tabCanvasManager.setCameraManager(calibratingCameraManager);

		// Final preparation to display
		arenaStage.setTitle(AppBranding.getArenaName());
		arenaStage.setScene(new Scene(arenaPane));
		arenaStage.setFullScreenExitHint("");

		calibrationManager = Optional.of(new CalibrationManager(this, calibratingCameraManager, arenaPane, cameraViews,
				null, exerciseSlide.getExerciseListener()));

		calibrationManager.get().addCalibrationListener(new CalibrationListener() {
			@Override
			public void startCalibration() {}

			@Override
			public void calibrated(Optional<PerspectiveManager> perspectiveManager) {
				if (Platform.isFxApplicationThread()) {
					hide();
				} else {
					Platform.runLater(() -> hide());
				}
			}
		});

		arenaPane.setCalibrationManager(calibrationManager.get());

		exerciseSlide.toggleProjectorExercises(false);
		arenaPane.getCanvasManager().setShowShots(config.showArenaShotMarkers());

		backgroundsSlide = new ArenaBackgroundsSlide(parentControls, parentBody, arenaPane, shootOffStage);
		backgroundsSlide.setOnSlideHidden(() -> {
			if (backgroundsSlide.choseBackground()) {
				backgroundsSlide.setChoseBackground(false);
				hide();
			}
		});

		// Display the arena
		if (autoStartCalibration) {
			calibrateButton.fire();
		}
		arenaPane.toggleArena();
		arenaPane.autoPlaceArena();

		arenaStage.setOnCloseRequest((e) -> {
			arenaStage.setOnCloseRequest(null);

			arenaPane.close();
			arenaPane.setFeedCanvasManager(null);
			arenaPane = null;

			cameraViews.removeCameraView("Arena");

			if (config.getExercise().isPresent()
					&& config.getExercise().get() instanceof ProjectorTrainingExerciseBase) {
				exerciseSlide.toggleProjectorExercises(true);
			}

			if (calibrationManager.isPresent()) {
				if (calibrationManager.get().isCalibrating()) {
					calibrationManager.get().stopCalibration();
				} else {
					calibrationManager.get().arenaClosing();
				}
			}

			exerciseSlide.toggleProjectorExercises(true);
		});
	}

	public void closeArena() {
		if (arenaPane != null) {
			arenaPane.getCanvasManager().close();
			arenaPane.close();
		}
	}
}
