package com.shootoff.gui.controller;

import java.io.IOException;
import java.util.Optional;

import com.shootoff.AppBranding;
import com.shootoff.Main;
import com.shootoff.config.Configuration;
import com.shootoff.gui.pane.ProjectorSlide;
import com.shootoff.plugins.ArmyTableVIQualification;
import com.shootoff.plugins.ArmyTableVIStageEditor;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class ShootOFFArmyController extends ShootOFFController {
	private static final String NAV_BUTTON_STYLE =
		"-fx-background-color: transparent; -fx-text-fill: #d0d7e2; -fx-font-size: 13px; "
			+ "-fx-font-weight: bold; -fx-alignment: CENTER-LEFT; -fx-padding: 10 14 10 14; "
			+ "-fx-background-radius: 14;";
	private static final String NAV_BUTTON_ACTIVE_STYLE =
		"-fx-background-color: #2a7fff; -fx-text-fill: white; -fx-font-size: 13px; "
			+ "-fx-font-weight: bold; -fx-alignment: CENTER-LEFT; -fx-padding: 10 14 10 14; "
			+ "-fx-background-radius: 14;";
	private static final String PRIMARY_ACTION_STYLE =
		"-fx-background-color: #2a7fff; -fx-text-fill: white; -fx-font-size: 14px; "
			+ "-fx-font-weight: bold; -fx-padding: 12 18 12 18; -fx-background-radius: 14;";
	private static final String SECONDARY_ACTION_STYLE =
		"-fx-background-color: #1e2633; -fx-text-fill: #eef2f7; -fx-font-size: 14px; "
			+ "-fx-font-weight: bold; -fx-padding: 12 18 12 18; -fx-background-radius: 14; "
			+ "-fx-border-color: #334155; -fx-border-radius: 14;";

	private enum ArmyShellStep {
		HOME, SETUP, CALIBRATE, STAGE_EDITOR, TRAIN, RESULT
	}

	@FXML private Label appTitleLabel;
	@FXML private Label appSubtitleLabel;
	@FXML private Label workflowStatusLabel;
	@FXML private Label workflowDetailLabel;
	@FXML private Label homeVersionLabel;
	@FXML private Label resultSummaryLabel;
	@FXML private Label resultDetailLabel;
	@FXML private Label workspaceHintLabel;
	@FXML private StackPane armyContentStack;
	@FXML private VBox homePane;
	@FXML private VBox workspacePane;
	@FXML private VBox resultPane;
	@FXML private Button homeNavButton;
	@FXML private Button setupNavButton;
	@FXML private Button calibrateNavButton;
	@FXML private Button stageNavButton;
	@FXML private Button trainNavButton;
	@FXML private Button resultNavButton;

	private ArmyShellStep currentStep = ArmyShellStep.HOME;
	private boolean armyExperienceReady = false;
	private ArmyTableVIQualification armyExercise;
	private ArmyTableVIQualification.ArmySessionStatus latestArmyStatus =
		new ArmyTableVIQualification.ArmySessionStatus(
			ArmyTableVIQualification.ArmySessionState.SETUP,
			"Army Trainer Ready",
			"Open Setup to initialize the arena, then calibrate and train.",
			0, 40, 0, 18, Optional.empty());

	@Override
	public void init(Configuration config) throws IOException {
		super.init(config);
		configureArmyShell();
		showStep(ArmyShellStep.HOME);
	}

	@FXML
	public void showHome(ActionEvent event) {
		showStep(ArmyShellStep.HOME);
	}

	@FXML
	public void showSetup(ActionEvent event) {
		showStep(ArmyShellStep.SETUP);
	}

	@FXML
	public void showCalibrate(ActionEvent event) {
		showStep(ArmyShellStep.CALIBRATE);
	}

	@FXML
	public void showStageEditor(ActionEvent event) {
		showStep(ArmyShellStep.STAGE_EDITOR);
	}

	@FXML
	public void showTrain(ActionEvent event) {
		showStep(ArmyShellStep.TRAIN);
	}

	@FXML
	public void showResults(ActionEvent event) {
		showStep(ArmyShellStep.RESULT);
	}

	@FXML
	public void launchSetup(ActionEvent event) {
		showStep(ArmyShellStep.SETUP);
	}

	@FXML
	public void launchCalibration(ActionEvent event) {
		showStep(ArmyShellStep.CALIBRATE);
	}

	@FXML
	public void launchStageEditor(ActionEvent event) {
		showStep(ArmyShellStep.STAGE_EDITOR);
	}

	@FXML
	public void launchTraining(ActionEvent event) {
		showStep(ArmyShellStep.TRAIN);
	}

	@FXML
	public void launchResults(ActionEvent event) {
		showStep(ArmyShellStep.RESULT);
	}

	private void configureArmyShell() {
		final String versionLabel = Main.getVersion().map(version -> "Build " + version).orElse("Army MVP");
		appTitleLabel.setText(AppBranding.getAppName());
		appSubtitleLabel.setText("Army-first qualification workflow with guided setup, calibrated stage editing, and cleaner results.");
		homeVersionLabel.setText(versionLabel);
		resultSummaryLabel.setText("No qualification completed yet.");
		resultDetailLabel.setText("Your final score and rating will appear here after a live qualification run.");

		homeNavButton.setStyle(NAV_BUTTON_STYLE);
		setupNavButton.setStyle(NAV_BUTTON_STYLE);
		calibrateNavButton.setStyle(NAV_BUTTON_STYLE);
		stageNavButton.setStyle(NAV_BUTTON_STYLE);
		trainNavButton.setStyle(NAV_BUTTON_STYLE);
		resultNavButton.setStyle(NAV_BUTTON_STYLE);

		getControlsContainerNode().setSpacing(12);
		getControlsContainerNode().getChildren().clear();
		getControlsContainerNode().getChildren().addAll(
			createQuickAction("Setup", this::launchSetup, true),
			createQuickAction("Calibrate", this::launchCalibration, false),
			createQuickAction("Stage Editor", this::launchStageEditor, false),
			createQuickAction("Train", this::launchTraining, false),
			createQuickAction("Results", this::launchResults, false));

		getTrainingExerciseScrollPaneNode().setFitToWidth(true);
		getTrainingExerciseScrollPaneNode().setPrefHeight(220);
	}

	private Button createQuickAction(String label, javafx.event.EventHandler<ActionEvent> handler, boolean primary) {
		final Button button = new Button(label);
		button.setOnAction(handler);
		button.setStyle(primary ? PRIMARY_ACTION_STYLE : SECONDARY_ACTION_STYLE);
		return button;
	}

	private void showStep(ArmyShellStep step) {
		currentStep = step;
		highlightStep(step);

		switch (step) {
		case HOME:
			showHomePane();
			break;
		case SETUP:
			ensureArmyExperienceReady();
			showWorkspace("Setup your range",
				"Confirm projection width, shooter distance, and units. The Army controls are ready below.");
			break;
		case CALIBRATE:
			ensureArmyExperienceReady();
			showWorkspace("Calibrate the arena",
				"Use Auto Green or Manual calibration. Keep the projection clean and let the camera settle.");
			startCalibrationIfPossible();
			break;
		case STAGE_EDITOR:
			ensureArmyExperienceReady();
			showWorkspace("Edit the Army stage",
				"Adjust the 50 m reference line, place E-1 and F-targets, then save the stage.");
			stopCalibrationIfRunning();
			openStageEditorIfAvailable();
			break;
		case TRAIN:
			ensureArmyExperienceReady();
			showWorkspace("Run the qualification",
				"Press Start Qualification in the Army controls when the setup and stage are ready.");
			stopCalibrationIfRunning();
			selectArenaTab();
			break;
		case RESULT:
			showResultPane();
			break;
		default:
			showHomePane();
		}
	}

	private void showHomePane() {
		homePane.setVisible(true);
		homePane.setManaged(true);
		workspacePane.setVisible(false);
		workspacePane.setManaged(false);
		resultPane.setVisible(false);
		resultPane.setManaged(false);
		updateWorkflowStatus("Army Trainer Ready",
			"Open Setup to initialize the arena and prepare the Army qualification workflow.");
	}

	private void showWorkspace(String headline, String detail) {
		homePane.setVisible(false);
		homePane.setManaged(false);
		workspacePane.setVisible(true);
		workspacePane.setManaged(true);
		resultPane.setVisible(false);
		resultPane.setManaged(false);
		workspaceHintLabel.setText(detail);
		updateWorkflowStatus(headline, detail);
	}

	private void showResultPane() {
		homePane.setVisible(false);
		homePane.setManaged(false);
		workspacePane.setVisible(false);
		workspacePane.setManaged(false);
		resultPane.setVisible(true);
		resultPane.setManaged(true);

		if (latestArmyStatus.qualification.isPresent()) {
			resultSummaryLabel.setText(String.format("Score %d / %d | %s",
				latestArmyStatus.score, latestArmyStatus.totalTargets, latestArmyStatus.qualification.get()));
			resultDetailLabel.setText(latestArmyStatus.detail);
		} else {
			resultSummaryLabel.setText("No qualification completed yet.");
			resultDetailLabel.setText("Run the Army qualification to generate a final score and rating.");
		}

		updateWorkflowStatus("Results",
			"Review the latest qualification outcome, then return to Setup or Stage Editor for another run.");
	}

	private void highlightStep(ArmyShellStep selected) {
		homeNavButton.setStyle(selected == ArmyShellStep.HOME ? NAV_BUTTON_ACTIVE_STYLE : NAV_BUTTON_STYLE);
		setupNavButton.setStyle(selected == ArmyShellStep.SETUP ? NAV_BUTTON_ACTIVE_STYLE : NAV_BUTTON_STYLE);
		calibrateNavButton.setStyle(selected == ArmyShellStep.CALIBRATE ? NAV_BUTTON_ACTIVE_STYLE : NAV_BUTTON_STYLE);
		stageNavButton.setStyle(selected == ArmyShellStep.STAGE_EDITOR ? NAV_BUTTON_ACTIVE_STYLE : NAV_BUTTON_STYLE);
		trainNavButton.setStyle(selected == ArmyShellStep.TRAIN ? NAV_BUTTON_ACTIVE_STYLE : NAV_BUTTON_STYLE);
		resultNavButton.setStyle(selected == ArmyShellStep.RESULT ? NAV_BUTTON_ACTIVE_STYLE : NAV_BUTTON_STYLE);
	}

	private void updateWorkflowStatus(String headline, String detail) {
		workflowStatusLabel.setText(headline);
		workflowDetailLabel.setText(detail);
	}

	private void ensureArmyExperienceReady() {
		if (armyExperienceReady) return;

		final ProjectorSlide projectorSlide = getProjectorSlideInternal();
		projectorSlide.startArena(false);
		setProjectorExercise(new ArmyTableVIQualification());

		if (getConfigurationInternal().getExercise().isPresent()
				&& getConfigurationInternal().getExercise().get() instanceof ArmyTableVIQualification) {
			armyExercise = (ArmyTableVIQualification) getConfigurationInternal().getExercise().get();
			armyExercise.setSessionListener(this::onArmyStatusUpdated);
			armyExperienceReady = true;
			selectArenaTab();
		} else {
			updateWorkflowStatus("Army exercise unavailable",
				"The Army qualification could not be initialized. Reopen Setup to try again.");
		}
	}

	private void onArmyStatusUpdated(ArmyTableVIQualification.ArmySessionStatus status) {
		latestArmyStatus = status;

		if (status.qualification.isPresent()) {
			resultSummaryLabel.setText(String.format("Score %d / %d | %s",
				status.score, status.totalTargets, status.qualification.get()));
			resultDetailLabel.setText(status.detail);
		}

		if (currentStep == ArmyShellStep.TRAIN || currentStep == ArmyShellStep.CALIBRATE || currentStep == ArmyShellStep.SETUP) {
			updateWorkflowStatus(status.headline, status.detail);
		}

		if (status.state == ArmyTableVIQualification.ArmySessionState.COMPLETED) {
			showStep(ArmyShellStep.RESULT);
		}
	}

	private void startCalibrationIfPossible() {
		final Optional<com.shootoff.gui.CalibrationManager> calibrationManager =
			getProjectorSlideInternal().getCalibrationManager();
		if (calibrationManager.isPresent() && !calibrationManager.get().isCalibrating()) {
			calibrationManager.get().enableCalibration();
		}
		selectArenaTab();
	}

	private void stopCalibrationIfRunning() {
		final Optional<com.shootoff.gui.CalibrationManager> calibrationManager =
			getProjectorSlideInternal().getCalibrationManager();
		if (calibrationManager.isPresent() && calibrationManager.get().isCalibrating()) {
			calibrationManager.get().stopCalibration();
		}
	}

	private void openStageEditorIfAvailable() {
		if (armyExercise == null) return;

		new ArmyTableVIStageEditor(armyExercise, armyExercise.getActiveStageDataCopy()).show();
	}

	private void selectArenaTab() {
		for (Tab tab : getCameraTabPaneNode().getTabs()) {
			if ("Arena".equals(tab.getText())) {
				getCameraTabPaneNode().getSelectionModel().select(tab);
				return;
			}
		}
	}
}
