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

package com.shootoff.gui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shootoff.camera.CameraCalibrationListener;
import com.shootoff.camera.CameraManager;
import com.shootoff.camera.CameraView;
import com.shootoff.camera.perspective.PerspectiveManager;
import com.shootoff.config.Configuration;
import com.shootoff.gui.pane.ProjectorArenaPane;
import com.shootoff.gui.targets.TargetView;
import com.shootoff.plugins.ProjectorTrainingExerciseBase;
import com.shootoff.plugins.TrainingExercise;
import com.shootoff.targets.CameraViews;
import com.shootoff.targets.RectangleRegion;
import com.shootoff.targets.io.TargetIO;
import com.shootoff.util.TimerPool;

import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Screen;
import javafx.stage.WindowEvent;
import javafx.scene.paint.Color;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.imgproc.Imgproc;
import javafx.util.Pair;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class CalibrationManager implements CameraCalibrationListener {
	private static final int MAX_AUTO_CALIBRATION_TIME = 12 * 1000;
	private static final int MAX_AUTO_CALIBRATION_TIME_HEADLESS = 45 * 1000;
	private static final String AUTO_GREEN_BACKGROUND = "__auto_green_background__";
	private static final int GENERATED_BACKGROUND_SIZE = 64;
	private static final Logger logger = LoggerFactory.getLogger(CalibrationManager.class);

	private final CalibrationConfigurator calibrationConfigurator;
	private final CameraManager calibratingCameraManager;
	private final CanvasManager calibratingCanvasManager;
	private final CameraViews cameraViews;
	private final Configuration config;
	private final Optional<AutocalibrationListener> autocalibrationListener;
	private final ExerciseListener exerciseListener;
	private final List<CalibrationListener> calibrationListeners = new ArrayList<>();
	private final Map<CalibrationMode, CalibrationStrategy> calibrationStrategies = new EnumMap<>(CalibrationMode.class);
	private final ProjectorArenaPane arenaPane;

	private ScheduledFuture<?> autoCalibrationFuture = null;

	private Optional<TrainingExercise> savedExercise = Optional.empty();
	private Optional<QuadrilateralCalibrationUI> quadrilateralUI = Optional.empty();
	private Optional<QuadrilateralCalibrationUI> projectorQuadrilateralUI = Optional.empty();
	private Optional<List<Point2D>> lastCalibrationCorners = Optional.empty();
	private Optional<CameraView> originalView = Optional.empty();
	private Optional<Dimension2D> perspectivePaperDims = Optional.empty();

	private final AtomicBoolean isCalibrating = new AtomicBoolean(false);
	private final AtomicBoolean isShowingPattern = new AtomicBoolean(false);

	public CalibrationManager(CalibrationConfigurator calibrationConfigurator, CameraManager calibratingCameraManager,
			ProjectorArenaPane arenaPane, CameraViews cameraViews, AutocalibrationListener autocalibrationListener,
			ExerciseListener exerciseListener) {
		this.calibrationConfigurator = calibrationConfigurator;
		this.calibratingCameraManager = calibratingCameraManager;
		calibratingCanvasManager = (CanvasManager) calibratingCameraManager.getCameraView();
		calibrationListeners.add(arenaPane);
		this.arenaPane = arenaPane;
		this.cameraViews = cameraViews;
		config = Configuration.getConfig();
		this.autocalibrationListener = Optional.ofNullable(autocalibrationListener);
		this.exerciseListener = exerciseListener;
		calibrationStrategies.put(CalibrationMode.MANUAL, new ManualCalibrationStrategy());
		calibrationStrategies.put(CalibrationMode.AUTO_GREEN, new GreenScreenCalibrationStrategy());
		calibrationStrategies.put(CalibrationMode.AUTO_TAGS, new AutoTagsCalibrationStrategy());

		arenaPane.setFeedCanvasManager(calibratingCanvasManager);
		calibratingCameraManager.setCalibrationManager(this);
		calibratingCameraManager.setOnCloseListener(() -> Platform
				.runLater(() -> arenaPane.fireEvent(new WindowEvent(null, WindowEvent.WINDOW_CLOSE_REQUEST))));
	}

	public void addCalibrationListener(CalibrationListener calibrationListener) {
		calibrationListeners.add(calibrationListener);
	}

	public void enableCalibration() {
		// Projector exercises can alter what is on the arena, thereby
		// interfearing with calibration. Thus, if an projector exercise
		// is set, we unset it for calibration, and reset it afterwards.
		if (config.getExercise().isPresent() && config.getExercise().get() instanceof ProjectorTrainingExerciseBase) {
			savedExercise = config.getExercise();
			exerciseListener.setExercise(null);
		} else {
			// CalibrationManager is re-used when the user hits the
			// calibration button on the projector slide, thus we need
			// be sure to have clean state
			savedExercise = Optional.empty();
		}

		arenaPane.getCanvasManager().setShowShots(false);

		isCalibrating.set(true);

		calibrationConfigurator.toggleCalibrating(true);

		// Sets calibrating and not detecting
		calibratingCameraManager.setCalibrating(true);
		calibratingCameraManager.setProjectionBounds(null);
		calibratingCameraManager.setManualPerspectiveMatrix(null);
		Platform.runLater(this::removeActiveProjectionBorder);

		if (arenaPane.isFullScreen() || Screen.getScreens().size() == 1) {
			enableConfiguredCalibration();
		} else {
			showFullScreenRequest();
		}
	}

	public void stopCalibration() {
		isCalibrating.set(false);

		if (quadrilateralUI.isPresent()) {
			List<Point2D> pts = quadrilateralUI.get().getCornerPoints();
			lastCalibrationCorners = Optional.of(copyCorners(pts));
			Point[] cvPts = new Point[4];
			for(int i=0; i<4; i++) {
				Bounds translatedPt = calibratingCanvasManager.translateCanvasToCamera(new BoundingBox(pts.get(i).getX(), pts.get(i).getY(), 0, 0));
				cvPts[i] = new Point(translatedPt.getMinX(), translatedPt.getMinY());
			}
			
			MatOfPoint2f sourceCorners = new MatOfPoint2f();
			sourceCorners.fromArray(cvPts);
			
			int width = calibratingCameraManager.getFeedWidth();
			int height = calibratingCameraManager.getFeedHeight();

			MatOfPoint2f destCorners = new MatOfPoint2f();
			destCorners.fromArray(
				new Point(0, 0), 
				new Point(width, 0),
				new Point(width, height), 
				new Point(0, height)
			);

			Mat manualPerspectiveMatrix = Imgproc.getPerspectiveTransform(sourceCorners, destCorners);
			calibratingCameraManager.setManualPerspectiveMatrix(manualPerspectiveMatrix);
			
			// After warp, the TV fills the full frame. Set projection bounds directly
			// in camera space — do NOT go through calibrate() which double-translates.
			Bounds cameraBounds = new BoundingBox(0, 0, width, height);
			calibratingCameraManager.setProjectionBounds(cameraBounds);
			configureArenaCamera(calibrationConfigurator.getCalibratedFeedBehavior());
			
			// Set the canvas projection for display (translate camera→canvas)
			Bounds canvasBounds = calibratingCanvasManager.translateCameraToCanvas(cameraBounds);
			calibratingCanvasManager.setProjectorArena(arenaPane, canvasBounds);
			Platform.runLater(() -> drawActiveProjectionBorder(cameraBounds));
			
			this.perspectivePaperDims = Optional.empty();
		}

		calibratingCameraManager.disableAutoCalibration();

		TimerPool.cancelTimer(autoCalibrationFuture);

		calibrationConfigurator.toggleCalibrating(false);

		removeFullScreenRequest();
		removeAutoCalibrationMessage();
		removeManualCalibrationRequestMessage();
		removeCalibrationTargetIfPresent();

		if (originalView.isPresent()) {
			cameraViews.selectCameraView(originalView.get());
		}

		PerspectiveManager pm = null;

		final Dimension2D feedDim = new Dimension2D(calibratingCameraManager.getFeedWidth(),
				calibratingCameraManager.getFeedHeight());

		if (calibratingCameraManager.getProjectionBounds().isPresent()) {
			Bounds bounds = calibratingCameraManager.getProjectionBounds().get();
			if (PerspectiveManager.isCameraSupported(calibratingCameraManager.getName(), feedDim)) {
				if (perspectivePaperDims.isPresent()) {
					pm = new PerspectiveManager(calibratingCameraManager.getName(),
							bounds, feedDim, perspectivePaperDims.get(),
							arenaPane.getArenaStageResolution());
				} else {
					pm = new PerspectiveManager(calibratingCameraManager.getName(),
							bounds, feedDim,
							arenaPane.getArenaStageResolution());
				}
			} else {
				if (perspectivePaperDims.isPresent()) {
					pm = new PerspectiveManager(bounds, feedDim,
							perspectivePaperDims.get(), arenaPane.getArenaStageResolution());
				} else {
					// FALLBACK: Create a basic PerspectiveManager so exercises like Army Table VI can function.
					// We use a default 2m width assumption (2000mm).
					pm = new PerspectiveManager(bounds);
					pm.setCameraFeedSize((int)feedDim.getWidth(), (int)feedDim.getHeight());
					pm.setProjectorResolution(arenaPane.getArenaStageResolution());
					pm.setProjectionSize(2000, 1500); // 2000mm x 1500mm default
					pm.calculateRealWorldSize();
					
					logger.info("Created fallback PerspectiveManager for manual calibration (2m width assumed).");
				}
			}
		}

		for (final CalibrationListener c : calibrationListeners)
			c.calibrated(Optional.ofNullable(pm));

		arenaPane.restoreCurrentBackground();

		calibratingCameraManager.setCalibrating(false);

		isShowingPattern.set(false);

		// We disable shot detection briefly because the pattern going away can
		// cause false shots. This statement applies to all the cam feeds rather
		// than just the arena. I don't think that should be a problem?
		calibratingCameraManager.setDetecting(false);
		TimerPool.schedule(() -> calibratingCameraManager.setDetecting(true), 600);

		arenaPane.getCanvasManager().setShowShots(config.showArenaShotMarkers());

		if (savedExercise.isPresent()) exerciseListener.setProjectorExercise(savedExercise.get());

		// If we created a fallback PerspectiveManager, or if we want to confirm, 
		// show the dialog for screen width and shooter distance.
		if (pm != null && !pm.isInitialized()) {
			showManualPerspectiveDialog(pm);
		}
	}

	private void showManualPerspectiveDialog(PerspectiveManager pm) {
		Platform.runLater(() -> {
			Dialog<Pair<String, String>> dialog = new Dialog<>();
			dialog.setTitle("Manual Perspective Calibration");
			dialog.setHeaderText("Please provide the physical screen dimensions and shooter distance.\n" +
			                   "This is required for targets to scale correctly to real-world distances.");

			ButtonType okButtonType = new ButtonType("Save", ButtonData.OK_DONE);
			dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

			GridPane grid = new GridPane();
			grid.setHgap(10);
			grid.setVgap(10);

			TextField widthField = new TextField("2000"); // 2m default
			TextField distanceField = new TextField("3000"); // 3m default

			grid.add(new Label("Screen Width (mm):"), 0, 0);
			grid.add(widthField, 1, 0);
			grid.add(new Label("Shooter Distance (mm):"), 0, 1);
			grid.add(distanceField, 1, 1);

			dialog.getDialogPane().setContent(grid);

			dialog.setResultConverter(dialogButton -> {
				if (dialogButton == okButtonType) {
					return new Pair<>(widthField.getText(), distanceField.getText());
				}
				return null;
			});

			Optional<Pair<String, String>> result = dialog.showAndWait();

			result.ifPresent(widthDistance -> {
				try {
					int width = Integer.parseInt(widthDistance.getKey());
					int distance = Integer.parseInt(widthDistance.getValue());
					
					Dimension2D res = arenaPane.getArenaStageResolution();
					double ratio = res.getHeight() / res.getWidth();
					int height = (int)(width * ratio);

					pm.setProjectionSize(width, height);
					pm.setShooterDistance(distance);
					pm.calculateRealWorldSize();
					
					logger.info("Manual Perspective Initialized: Width={}mm, Height={}mm, Distance={}mm", 
						width, height, distance);
				} catch (NumberFormatException e) {
					logger.error("Invalid numbers entered for manual perspective", e);
				}
			});
		});
	}

	@Override
	public void calibrate(Bounds arenaBounds, Optional<Dimension2D> perspectivePaperDims, boolean calibratedFromCanvas,
			long delay) {
		removeCalibrationTargetIfPresent();

		if (!calibratedFromCanvas) arenaBounds = calibratingCanvasManager.translateCameraToCanvas(arenaBounds);

		configureArenaCamera(calibrationConfigurator.getCalibratedFeedBehavior(), arenaBounds);

		logger.debug("calibrate {} {} {}", arenaBounds, perspectivePaperDims, calibratedFromCanvas);

		this.perspectivePaperDims = perspectivePaperDims;

		if (isCalibrating()) stopCalibration();
	}

	public void configureArenaCamera(CalibrationOption option) {
		calibratingCameraManager.setCropFeedToProjection(CalibrationOption.CROP.equals(option));
		calibratingCameraManager.setLimitDetectProjection(CalibrationOption.ONLY_IN_BOUNDS.equals(option));
	}

	private Optional<javafx.scene.shape.Rectangle> activeProjectionBorder = Optional.empty();

	private void drawActiveProjectionBorder(Bounds bounds) {
		removeActiveProjectionBorder();
		Bounds translated = calibratingCanvasManager.translateCameraToCanvas(bounds);
		javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(
				translated.getMinX(), translated.getMinY(), translated.getWidth(), translated.getHeight());
		rect.setFill(Color.TRANSPARENT);
		rect.setStroke(Color.RED);
		rect.setStrokeWidth(2.0);
		rect.getStrokeDashArray().addAll(10d, 5d);
		activeProjectionBorder = Optional.of(rect);
		calibratingCanvasManager.addChild(rect);
	}

	private void removeActiveProjectionBorder() {
		if (activeProjectionBorder.isPresent()) {
			calibratingCanvasManager.removeChild(activeProjectionBorder.get());
			activeProjectionBorder = Optional.empty();
		}
	}

	public void arenaClosing() {
		calibratingCameraManager.setProjectionBounds(null);
		Platform.runLater(this::removeActiveProjectionBorder);
	}

	private void createCalibrationTarget(double x, double y, double width, double height) {
		List<Point2D> corners = new ArrayList<>(4);
		corners.add(new Point2D(x, y));
		corners.add(new Point2D(x + width, y));
		corners.add(new Point2D(x + width, y + height));
		corners.add(new Point2D(x, y + height));
		createCalibrationQuadrilateral(corners);
	}

	private void createCalibrationQuadrilateral(List<Point2D> corners) {
		removeCalibrationTargetIfPresent();

		QuadrilateralCalibrationUI previewOverlay = new QuadrilateralCalibrationUI(corners, true);
		QuadrilateralCalibrationUI projectorOverlay = new QuadrilateralCalibrationUI(
				projectorOverlayCornersFromPreviewCorners(corners), false);

		previewOverlay.setOnCornersChanged(() -> {
			lastCalibrationCorners = Optional.of(copyCorners(previewOverlay.getCorners()));
			if (projectorQuadrilateralUI.isPresent()) {
				projectorQuadrilateralUI.get().setCorners(
						projectorOverlayCornersFromPreviewCorners(previewOverlay.getCorners()));
			}
		});
		previewOverlay.addEventFilter(KeyEvent.KEY_PRESSED, this::handleQuadrilateralKeyPressed);

		calibratingCanvasManager.addChild(previewOverlay);
		arenaPane.getCanvasManager().addChild(projectorOverlay);

		quadrilateralUI = Optional.of(previewOverlay);
		projectorQuadrilateralUI = Optional.of(projectorOverlay);
		lastCalibrationCorners = Optional.of(copyCorners(corners));
		Platform.runLater(previewOverlay::requestFocus);
	}

	private void removeCalibrationTargetIfPresent() {
		if (quadrilateralUI.isPresent()) {
			calibratingCanvasManager.removeChild(quadrilateralUI.get());
			quadrilateralUI = Optional.empty();
		}
		if (projectorQuadrilateralUI.isPresent()) {
			arenaPane.getCanvasManager().removeChild(projectorQuadrilateralUI.get());
			projectorQuadrilateralUI = Optional.empty();
		}
	}

	private void configureArenaCamera(CalibrationOption option, Bounds bounds) {
		final Bounds translatedToCameraBounds = calibratingCanvasManager.translateCanvasToCamera(bounds);

		calibratingCanvasManager.setProjectorArena(arenaPane, bounds);
		configureArenaCamera(option);
		calibratingCameraManager.setProjectionBounds(translatedToCameraBounds);

		Platform.runLater(() -> drawActiveProjectionBorder(translatedToCameraBounds));
	}

	void beginManualCalibration() {
		logger.trace("enableManualCalibration");

		final int DEFAULT_DIM = 75;
		final int DEFAULT_POS = 150;

		removeAutoCalibrationMessage();
		calibratingCameraManager.disableAutoCalibration();

		if (isShowingPattern.get() && !CalibrationMode.AUTO_TAGS.equals(calibrationConfigurator.getCalibrationMode())) {
			arenaPane.restoreCurrentBackground();
			isShowingPattern.set(false);
		}

		originalView = Optional.of(cameraViews.getSelectedCameraView());
		cameraViews.selectCameraView(calibratingCanvasManager);

		showManualCalibrationRequestMessage();

		if (!quadrilateralUI.isPresent()) {
			if (lastCalibrationCorners.isPresent()) {
				createCalibrationQuadrilateral(lastCalibrationCorners.get());
			} else {
				createCalibrationTarget(DEFAULT_DIM, DEFAULT_DIM, DEFAULT_POS, DEFAULT_POS);
			}
		} else {
			calibratingCameraManager.getCameraView().addChild(quadrilateralUI.get());
		}
	}

	private void disableManualCalibration() {
		removeCalibrationTargetIfPresent();

		removeManualCalibrationRequestMessage();
	}

	private Label manualCalibrationRequestMessage = null;
	private volatile boolean showingManualCalibrationRequestMessage = false;

	private void showManualCalibrationRequestMessage() {
		if (showingManualCalibrationRequestMessage) return;

		showingManualCalibrationRequestMessage = true;
		manualCalibrationRequestMessage = calibratingCanvasManager
				.addDiagnosticMessage("Please manually calibrate the projection region", 20000, Color.ORANGE);
	}

	private void removeManualCalibrationRequestMessage() {
		logger.trace("removeFullScreenRequest {}", manualCalibrationRequestMessage);

		if (showingManualCalibrationRequestMessage) {
			showingManualCalibrationRequestMessage = false;
			calibratingCanvasManager.removeDiagnosticMessage(manualCalibrationRequestMessage);
			manualCalibrationRequestMessage = null;
		}
	}

	private Label fullScreenRequestMessage = null;
	private volatile boolean showingFullScreenRequestMessage = false;

	private void showFullScreenRequest() {
		if (showingFullScreenRequestMessage) return;

		showingFullScreenRequestMessage = true;
		fullScreenRequestMessage = calibratingCanvasManager
				.addDiagnosticMessage("Please move the arena to your projector and hit F11", Color.YELLOW);
	}

	private void removeFullScreenRequest() {
		logger.trace("removeFullScreenRequest {}", fullScreenRequestMessage);

		if (showingFullScreenRequestMessage) {
			showingFullScreenRequestMessage = false;

			calibratingCanvasManager.removeDiagnosticMessage(fullScreenRequestMessage);
			fullScreenRequestMessage = null;
		}
	}

	void beginAutoGreenCalibration() {
		logger.trace("beginAutoGreenCalibration");

		disableManualCalibration();

		for (final CalibrationListener c : calibrationListeners)
			c.startCalibration();
		arenaPane.setCalibrationMessageVisible(false);
		// We may already be calibrating if the user decided to move the arena
		// to another screen while calibrating. If we save the background in
		// that case we are saving the calibration pattern as the background.
		if (!isShowingPattern.get()) arenaPane.saveCurrentBackground();
		setArenaBackground(AUTO_GREEN_BACKGROUND);
		isShowingPattern.set(true);

		calibratingCameraManager.enableAutoCalibration(CalibrationMode.AUTO_GREEN, false);

		showAutoCalibrationMessage();

		launchAutoCalibrationTimer();
	}

	void beginAutoTagsCalibration() {
		logger.trace("beginAutoTagsCalibration");

		disableManualCalibration();

		for (final CalibrationListener c : calibrationListeners)
			c.startCalibration();
		arenaPane.setCalibrationMessageVisible(false);
		if (!isShowingPattern.get()) arenaPane.saveCurrentBackground();
		setAutoTagsArenaBackground();
		isShowingPattern.set(true);

		calibratingCameraManager.enableAutoCalibration(CalibrationMode.AUTO_TAGS, false);

		showAutoCalibrationMessage();

		launchAutoCalibrationTimer();
	}

	private void enableConfiguredCalibration() {
		final CalibrationStrategy calibrationStrategy = calibrationStrategies.get(calibrationConfigurator.getCalibrationMode());
		if (calibrationStrategy != null) {
			calibrationStrategy.start(this);
		} else {
			beginManualCalibration();
		}
	}

	private void launchAutoCalibrationTimer() {
		TimerPool.cancelTimer(autoCalibrationFuture);

		autoCalibrationFuture = TimerPool.schedule(() -> {
			Platform.runLater(() -> {
				if (isCalibrating.get() && (isFullScreen || Screen.getScreens().size() == 1)) {
					if (autocalibrationListener.isPresent()) {
						autocalibrationListener.get().autocalibrationTimedOut();
						stopCalibration();
					} else {
						calibratingCameraManager.disableAutoCalibration();
						beginManualCalibration();
					}
				}
				// Keep waiting
				else if (!isFullScreen && Screen.getScreens().size() > 1) launchAutoCalibrationTimer();
			});
		}, autocalibrationListener.isPresent() ? MAX_AUTO_CALIBRATION_TIME_HEADLESS : MAX_AUTO_CALIBRATION_TIME);
	}

	@Override
	public void setArenaBackground(String resourceFilename) {
		if (resourceFilename != null) {
			final LocatedImage img;

			if (AUTO_GREEN_BACKGROUND.equals(resourceFilename)) {
				img = createSolidBackground(resourceFilename, Color.web("#00ff41"));
			} else {
				final InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourceFilename);
				img = new LocatedImage(is, resourceFilename);
			}

			arenaPane.setArenaBackground(img);
		} else {
			arenaPane.setArenaBackground(null);
		}
	}

	private LocatedImage createSolidBackground(String imageName, Color color) {
		final WritableImage image = new WritableImage(GENERATED_BACKGROUND_SIZE, GENERATED_BACKGROUND_SIZE);
		final PixelWriter pixelWriter = image.getPixelWriter();

		for (int x = 0; x < GENERATED_BACKGROUND_SIZE; x++) {
			for (int y = 0; y < GENERATED_BACKGROUND_SIZE; y++) {
				pixelWriter.setColor(x, y, color);
			}
		}

		return new LocatedImage(image, imageName, false);
	}

	private void setAutoTagsArenaBackground() {
		arenaPane.setArenaBackground(AutoTagsCalibrationPatternFactory.createPattern("autotags-calibration",
				arenaPane.getArenaStageResolution()));
	}

	private Label autoCalibrationMessage = null;
	private volatile boolean showingAutoCalibrationMessage = false;

	private void showAutoCalibrationMessage() {
		logger.trace("showAutoCalibrationMessage - showingAutoCalibrationMessage {} autoCalibrationMessage {}",
				showingAutoCalibrationMessage, autoCalibrationMessage);

		if (showingAutoCalibrationMessage) return;

		showingAutoCalibrationMessage = true;
		final String message;
		if (CalibrationMode.AUTO_TAGS.equals(calibrationConfigurator.getCalibrationMode())) {
			message = "Attempting Auto Tags calibration";
		} else if (CalibrationMode.AUTO_GREEN.equals(calibrationConfigurator.getCalibrationMode())) {
			message = "Attempting green autocalibration";
		} else {
			message = "Attempting autocalibration";
		}
		autoCalibrationMessage = calibratingCanvasManager.addDiagnosticMessage(message, 11000, Color.CYAN);
	}

	private void removeAutoCalibrationMessage() {
		logger.trace("removeAutoCalibrationMessage - showingAutoCalibrationMessage {} autoCalibrationMessage {}",
				showingAutoCalibrationMessage, autoCalibrationMessage);

		if (showingAutoCalibrationMessage) {
			showingAutoCalibrationMessage = false;

			if (logger.isTraceEnabled()) logger.trace("removeAutoCalibrationMessage {} ", autoCalibrationMessage);
			calibratingCanvasManager.removeDiagnosticMessage(autoCalibrationMessage);
			autoCalibrationMessage = null;
		}
	}

	private boolean isFullScreen = false;

	public void setFullScreenStatus(boolean fullScreen) {
		isFullScreen = fullScreen;

		logger.trace("setFullScreenStatus - {} {}", fullScreen, isCalibrating);

		if (!isCalibrating.get()) {
			enableCalibration();
		} else if (!fullScreen && Screen.getScreens().size() > 1) {
			calibratingCameraManager.disableAutoCalibration();

			removeCalibrationTargetIfPresent();

			removeAutoCalibrationMessage();

			disableManualCalibration();

			showFullScreenRequest();
		} else {
			removeFullScreenRequest();
			// Delay slightly to prevent #444 bug
			TimerPool.schedule(() -> {
				Platform.runLater(() -> {
					if (isCalibrating.get()) enableConfiguredCalibration();
				});
			}, 100);
		}
	}

	@Override
	public void calibrationCornersDetected(List<Point2D> cornerPoints, boolean calibratedFromCanvas) {
		Platform.runLater(() -> {
			if (!isCalibrating.get() || !CalibrationMode.AUTO_TAGS.equals(calibrationConfigurator.getCalibrationMode())) {
				return;
			}
			if (cornerPoints == null || cornerPoints.size() != 4) {
				logger.warn("Auto Tags returned invalid corner list: {}", cornerPoints);
				return;
			}

			final List<Point2D> canvasCorners = calibratedFromCanvas ? copyCorners(cornerPoints)
					: cameraCornersToCanvas(cornerPoints);

			calibratingCameraManager.disableAutoCalibration();
			removeAutoCalibrationMessage();

			originalView = Optional.of(cameraViews.getSelectedCameraView());
			cameraViews.selectCameraView(calibratingCanvasManager);
			showManualCalibrationRequestMessage();
			createCalibrationQuadrilateral(canvasCorners);
			calibratingCanvasManager.addDiagnosticMessage(
					"Auto Tags locked. Ajuste os cantos e clique Stop Calibration.", 7000, Color.CYAN);
		});
	}

	private void handleQuadrilateralKeyPressed(KeyEvent event) {
		if (!CalibrationMode.AUTO_TAGS.equals(calibrationConfigurator.getCalibrationMode())) return;
		if (event.getCode() != KeyCode.R) return;

		removeCalibrationTargetIfPresent();
		removeManualCalibrationRequestMessage();
		removeAutoCalibrationMessage();
		calibratingCameraManager.setManualPerspectiveMatrix(null);
		setAutoTagsArenaBackground();
		isShowingPattern.set(true);
		calibratingCameraManager.enableAutoCalibration(CalibrationMode.AUTO_TAGS, false);
		showAutoCalibrationMessage();
		launchAutoCalibrationTimer();
		event.consume();
	}

	private List<Point2D> cameraCornersToCanvas(List<Point2D> cameraCorners) {
		final List<Point2D> canvasCorners = new ArrayList<>(4);
		for (Point2D cameraCorner : cameraCorners) {
			final Bounds canvasPointBounds = calibratingCanvasManager.translateCameraToCanvas(new BoundingBox(
					cameraCorner.getX(), cameraCorner.getY(), 0, 0));
			canvasCorners.add(new Point2D(canvasPointBounds.getMinX(), canvasPointBounds.getMinY()));
		}
		return canvasCorners;
	}

	private List<Point2D> projectorOverlayCornersFromPreviewCorners(List<Point2D> previewCorners) {
		final double previewWidth = Math.max(1.0, config.getDisplayWidth());
		final double previewHeight = Math.max(1.0, config.getDisplayHeight());
		Dimension2D projectorResolution = arenaPane.getArenaStageResolution();
		final double projectorWidth = Math.max(1.0, projectorResolution.getWidth());
		final double projectorHeight = Math.max(1.0, projectorResolution.getHeight());

		final List<Point2D> projectorCorners = new ArrayList<>(4);
		for (Point2D previewCorner : previewCorners) {
			projectorCorners.add(new Point2D(previewCorner.getX() / previewWidth * projectorWidth,
					previewCorner.getY() / previewHeight * projectorHeight));
		}
		return projectorCorners;
	}

	private List<Point2D> copyCorners(List<Point2D> source) {
		return new ArrayList<>(source);
	}

	public boolean isCalibrating() {
		return isCalibrating.get();
	}
}
