package com.shootoff.plugins;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ArmyTableVIStageEditor {
	private static final double PREVIEW_WIDTH = 720;
	private static final double PREVIEW_HEIGHT = 405;
	private static final double HANDLE_RADIUS = 10.0;
	private static final double HANDLE_HIT_RADIUS = 18.0;
	private static final double MIN_ZOOM = 1.0;
	private static final double MAX_ZOOM = 5.0;
	private static final double ZOOM_STEP = 0.25;

	private final ArmyTableVIQualification plugin;
	private final ArmyTableVIQualification.ArmyTableStageData stageData;

	private ListView<ArmyTableVIQualification.Engagement> engagementList;
	private ComboBox<ArmyTableVIQualification.Position> positionCombo;
	private TextField delayField;
	private TextField exposureField;

	private ListView<ArmyTableVIQualification.TargetSpec> targetList;
	private ComboBox<ArmyTableVIQualification.ArmyTargetType> targetTypeCombo;
	private TextField targetDistanceField;
	private Label targetPositionLabel;

	private TextField projectionWidthField;
	private TextField shooterDistanceField;
	private Label calibrationStatusLabel;

	private Pane overlayPane;
	private Pane previewContentPane;
	private ScrollPane previewScrollPane;
	private Slider zoomSlider;
	private Label zoomValueLabel;
	private double previewZoom = 1.0;
	private Line calibrationLine;
	private Line calibrationLineDragArea;
	private Label calibrationLineLabel;
	private Circle leftCalibrationHandle;
	private Circle rightCalibrationHandle;

	private ArmyTableVIQualification.TargetSpec draggingTarget;
	private double dragOffsetX;
	private double dragOffsetY;
	private Point2D calibrationLineDragStart;
	private ArmyTableVIQualification.StagePoint calibrationLineLeftStart;
	private ArmyTableVIQualification.StagePoint calibrationLineRightStart;

	public ArmyTableVIStageEditor(ArmyTableVIQualification plugin,
			ArmyTableVIQualification.ArmyTableStageData currentStageData) {
		this.plugin = plugin;
		this.stageData = new ArmyTableVIQualification.ArmyTableStageData(currentStageData);
		ArmyTableVIQualification.normalizeEngagementLayout(stageData.engagements, stageData.backgroundCalibration);
	}

	public void show() {
		final Stage stage = new Stage();
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.setTitle("Army Table VI - Stage Editor");

		final BorderPane root = new BorderPane();
		root.setPadding(new Insets(10));
		root.setLeft(buildEngagementPane());
		root.setCenter(buildCenterPane());
		root.setBottom(buildBottomBar(stage));

		bindEvents();
		engagementList.getSelectionModel().selectFirst();

		stage.setScene(new Scene(root, 1460, 760));
		stage.show();
	}

	private VBox buildEngagementPane() {
		final VBox leftPane = new VBox(6);
		leftPane.setPrefWidth(300);

		engagementList = new ListView<>();
		engagementList.getItems().addAll(stageData.engagements);
		engagementList.setCellFactory(param -> new ListCell<ArmyTableVIQualification.Engagement>() {
			@Override
			protected void updateItem(ArmyTableVIQualification.Engagement item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				} else {
					setText("Eng: " + item.number + " - " + item.position + " (" + item.targets.size() + " tgts)");
				}
			}
		});

		final HBox buttons = new HBox(6);
		final Button addButton = new Button("Add");
		final Button removeButton = new Button("Remove");
		buttons.getChildren().addAll(addButton, removeButton);

		addButton.setOnAction(e -> {
			final ArmyTableVIQualification.Engagement newEngagement = new ArmyTableVIQualification.Engagement(
				engagementList.getItems().size() + 1,
				ArmyTableVIQualification.Position.PRONE_SUPPORTED,
				2.0,
				5.0
			);
			stageData.engagements.add(newEngagement);
			engagementList.getItems().setAll(stageData.engagements);
			engagementList.getSelectionModel().select(newEngagement);
		});

		removeButton.setOnAction(e -> {
			final ArmyTableVIQualification.Engagement selected = engagementList.getSelectionModel().getSelectedItem();
			if (selected == null) return;

			stageData.engagements.remove(selected);
			for (int i = 0; i < stageData.engagements.size(); i++) {
				stageData.engagements.get(i).number = i + 1;
			}

			engagementList.getItems().setAll(stageData.engagements);
			engagementList.refresh();
			engagementList.getSelectionModel().selectFirst();
		});

		leftPane.getChildren().addAll(new Label("Engagements Sequence:"), engagementList, buttons);
		return leftPane;
	}

	private BorderPane buildCenterPane() {
		final BorderPane centerPane = new BorderPane();
		centerPane.setPadding(new Insets(0, 0, 0, 12));
		centerPane.setLeft(buildEditorTabs());
		centerPane.setCenter(buildPreviewPane());
		return centerPane;
	}

	private TabPane buildEditorTabs() {
		final TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabs.setPrefWidth(400);

		final Tab stageTab = new Tab("Stage Editor", buildStageEditorPane());
		final Tab calibrationTab = new Tab("Calibrate Range", buildCalibrationPane());
		tabs.getTabs().addAll(stageTab, calibrationTab);
		return tabs;
	}

	private VBox buildStageEditorPane() {
		final VBox pane = new VBox(10);

		final GridPane engagementForm = new GridPane();
		engagementForm.setHgap(10);
		engagementForm.setVgap(10);

		positionCombo = new ComboBox<>();
		positionCombo.getItems().addAll(ArmyTableVIQualification.Position.values());
		delayField = new TextField();
		exposureField = new TextField();

		engagementForm.add(new Label("Position:"), 0, 0);
		engagementForm.add(positionCombo, 1, 0);
		engagementForm.add(new Label("Pre-Delay (s):"), 0, 1);
		engagementForm.add(delayField, 1, 1);
		engagementForm.add(new Label("Exposure (s):"), 0, 2);
		engagementForm.add(exposureField, 1, 2);

		final Button applyEngagementButton = new Button("Apply Engagement");
		applyEngagementButton.setOnAction(e -> applyEngagementForm());

		targetList = new ListView<>();
		targetList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		targetList.setPrefHeight(190);
		targetList.setCellFactory(param -> new ListCell<ArmyTableVIQualification.TargetSpec>() {
			@Override
			protected void updateItem(ArmyTableVIQualification.TargetSpec item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				} else {
					setText(item.targetType + " - " + item.distanceMeters + "m @ lane "
						+ Math.round(item.laneXNormalized * 100) + "%");
				}
			}
		});

		final HBox targetButtons = new HBox(6);
		final Button addTargetButton = new Button("Add Target");
		final Button removeTargetButton = new Button("Remove Target");
		targetButtons.getChildren().addAll(addTargetButton, removeTargetButton);

		addTargetButton.setOnAction(e -> showAddTargetDialog());
		removeTargetButton.setOnAction(e -> {
			final ArmyTableVIQualification.TargetSpec selected = targetList.getSelectionModel().getSelectedItem();
			if (selected == null) return;

			final ArmyTableVIQualification.Engagement engagement = engagementList.getSelectionModel().getSelectedItem();
			if (engagement == null) return;

			engagement.targets.remove(selected);
			targetList.getItems().setAll(engagement.targets);
			targetList.getSelectionModel().selectFirst();
			renderPreview();
		});

		final GridPane targetForm = new GridPane();
		targetForm.setHgap(10);
		targetForm.setVgap(10);

		targetTypeCombo = new ComboBox<>();
		targetTypeCombo.getItems().addAll(ArmyTableVIQualification.ArmyTargetType.values());
		targetDistanceField = new TextField();
		targetPositionLabel = new Label("Drag left or right in the preview. Depth is automatic.");

		targetForm.add(new Label("Target Model:"), 0, 0);
		targetForm.add(targetTypeCombo, 1, 0);
		targetForm.add(new Label("Distance (m):"), 0, 1);
		targetForm.add(targetDistanceField, 1, 1);
		targetForm.add(new Label("Lane / Auto Depth:"), 0, 2);
		targetForm.add(targetPositionLabel, 1, 2);

		final Button applyTargetButton = new Button("Apply Target");
		applyTargetButton.setOnAction(e -> applyTargetForm());

		pane.getChildren().addAll(
			engagementForm,
			applyEngagementButton,
			new Label("Targets for Selected Engagement:"),
			targetList,
			targetButtons,
			new Label("Selected Target:"),
			targetForm,
			applyTargetButton
		);
		return pane;
	}

	private VBox buildCalibrationPane() {
		final VBox pane = new VBox(10);

		final InputStream helpStream = ArmyTableVIStageEditor.class.getResourceAsStream("/images/perspective_settings.png");
		if (helpStream != null) {
			final ImageView helpImage = new ImageView(new Image(helpStream));
			helpImage.setPreserveRatio(true);
			helpImage.setFitWidth(320);
			pane.getChildren().add(helpImage);
		}

		final GridPane form = new GridPane();
		form.setHgap(10);
		form.setVgap(10);

		projectionWidthField = new TextField(String.valueOf(stageData.backgroundCalibration.projectionWidthMm));
		shooterDistanceField = new TextField(String.valueOf(stageData.backgroundCalibration.shooterDistanceMm));
		calibrationStatusLabel = new Label(buildCalibrationStatusText());

		form.add(new Label("Projection Width (mm):"), 0, 0);
		form.add(projectionWidthField, 1, 0);
		form.add(new Label("Shooter Distance (mm):"), 0, 1);
		form.add(shooterDistanceField, 1, 1);

		final HBox calibrationButtons = new HBox(6);
		final Button applyCalibrationButton = new Button("Apply Calibration");
		final Button resetLineButton = new Button("Reset 50m Line");
		calibrationButtons.getChildren().addAll(applyCalibrationButton, resetLineButton);

		applyCalibrationButton.setOnAction(e -> applyCalibrationForm());
		resetLineButton.setOnAction(e -> {
			final ArmyTableVIQualification.RangeBackgroundCalibration defaults =
				ArmyTableVIQualification.defaultBackgroundCalibration();
			stageData.backgroundCalibration.fiftyMeterLeftPoint = new ArmyTableVIQualification.StagePoint(defaults.fiftyMeterLeftPoint);
			stageData.backgroundCalibration.fiftyMeterRightPoint = new ArmyTableVIQualification.StagePoint(defaults.fiftyMeterRightPoint);
			calibrationStatusLabel.setText(buildCalibrationStatusText());
			renderPreview();
		});

		pane.getChildren().addAll(
			new Label("Adjust the line that represents the real 50 m reference on the range background."),
			form,
			calibrationButtons,
			calibrationStatusLabel
		);
		return pane;
	}

	private VBox buildPreviewPane() {
		final VBox previewBox = new VBox(8);
		previewBox.setPadding(new Insets(0, 0, 0, 14));
		previewBox.setAlignment(Pos.TOP_LEFT);

		final HBox zoomBar = new HBox(6);
		zoomBar.setAlignment(Pos.CENTER_LEFT);
		final Button zoomOutButton = new Button("-");
		final Button zoomResetButton = new Button("100%");
		final Button zoomInButton = new Button("+");
		zoomSlider = new Slider(MIN_ZOOM, MAX_ZOOM, 1.0);
		zoomSlider.setBlockIncrement(ZOOM_STEP);
		zoomSlider.setPrefWidth(180);
		zoomValueLabel = new Label("100%");

		zoomOutButton.setOnAction(e -> setPreviewZoom(previewZoom - ZOOM_STEP));
		zoomResetButton.setOnAction(e -> setPreviewZoom(1.0));
		zoomInButton.setOnAction(e -> setPreviewZoom(previewZoom + ZOOM_STEP));
		zoomSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
			final double snappedZoom = snapZoom(newValue.doubleValue());
			if (Math.abs(snappedZoom - previewZoom) > 0.0001) {
				applyPreviewZoom(snappedZoom, true);
			}
		});
		zoomBar.getChildren().addAll(new Label("Zoom:"), zoomOutButton, zoomResetButton, zoomInButton, zoomSlider,
			zoomValueLabel);

		final StackPane previewFrame = new StackPane();
		previewFrame.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		previewFrame.setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		previewFrame.setMaxSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		previewFrame.setStyle("-fx-background-color: #1d242b; -fx-border-color: #8592a6;");

		previewContentPane = new Pane();
		previewContentPane.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		previewContentPane.setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		previewContentPane.setMaxSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);

		final InputStream backgroundStream = ArmyTableVIStageEditor.class
			.getResourceAsStream("/arena/backgrounds/army_qualification_range_2.png");
		if (backgroundStream != null) {
			final ImageView background = new ImageView(new Image(backgroundStream));
			background.setMouseTransparent(true);
			background.setFitWidth(PREVIEW_WIDTH);
			background.setFitHeight(PREVIEW_HEIGHT);
			background.setPreserveRatio(false);
			previewContentPane.getChildren().add(background);
		}

		overlayPane = new Pane();
		overlayPane.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		overlayPane.setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		overlayPane.setMaxSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		previewContentPane.getChildren().add(overlayPane);

		previewScrollPane = new ScrollPane(new Group(previewContentPane));
		previewScrollPane.setPannable(true);
		previewScrollPane.setFitToHeight(false);
		previewScrollPane.setFitToWidth(false);
		previewScrollPane.setPrefViewportWidth(PREVIEW_WIDTH);
		previewScrollPane.setPrefViewportHeight(PREVIEW_HEIGHT);
		previewScrollPane.setStyle("-fx-background: #1d242b; -fx-background-color: #1d242b;");
		previewScrollPane.setOnScroll(event -> {
			if (!event.isControlDown()) return;
			setPreviewZoom(previewZoom + (event.getDeltaY() > 0 ? ZOOM_STEP : -ZOOM_STEP));
			event.consume();
		});
		previewFrame.getChildren().add(previewScrollPane);

		previewBox.getChildren().addAll(
			new Label("Stage Preview:"),
			zoomBar,
			previewFrame,
			new Label("Ctrl + mouse wheel zooms. Drag the blue 50 m line or its handles freely.")
		);

		applyPreviewZoom(1.0, false);
		return previewBox;
	}

	private HBox buildBottomBar(Stage stage) {
		final HBox bottomBar = new HBox(10);
		bottomBar.setPadding(new Insets(10, 0, 0, 0));

		final Button saveButton = new Button("Save Custom Stage & Close");
		final Button cancelButton = new Button("Cancel");
		final Button restoreButton = new Button("Restore Default Table VI");

		saveButton.setOnAction(e -> {
			applyCalibrationForm();
			applyEngagementForm();
			applyTargetForm();
			ArmyTableVIQualification.normalizeEngagementLayout(stageData.engagements, stageData.backgroundCalibration);

			try {
				ArmyTableCustomStageManager.saveStage(stageData);
				plugin.reloadStageData(stageData);
				stage.close();
			} catch (IOException ex) {
				new Alert(Alert.AlertType.ERROR, "Failed to save: " + ex.getMessage()).show();
			}
		});

		cancelButton.setOnAction(e -> stage.close());

		restoreButton.setOnAction(e -> {
			final File file = new File("exercises/army_table_vi_custom.txt");
			if (file.exists()) file.delete();
			plugin.reloadStageData(null);
			stage.close();
		});

		bottomBar.getChildren().addAll(saveButton, cancelButton, restoreButton);
		return bottomBar;
	}

	private void bindEvents() {
		engagementList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
			final boolean disable = newValue == null;
			positionCombo.setDisable(disable);
			delayField.setDisable(disable);
			exposureField.setDisable(disable);
			targetList.setDisable(disable);
			targetTypeCombo.setDisable(disable);
			targetDistanceField.setDisable(disable);

			if (newValue == null) {
				targetList.getItems().clear();
				overlayPane.getChildren().clear();
				return;
			}

			positionCombo.setValue(newValue.position);
			delayField.setText(String.valueOf(newValue.delayBeforeSec));
			exposureField.setText(String.valueOf(newValue.exposureSec));
			ArmyTableVIQualification.normalizeTargetLayout(newValue.targets, stageData.backgroundCalibration);
			targetList.getItems().setAll(newValue.targets);
			targetList.getSelectionModel().selectFirst();
			renderPreview();
		});

		targetList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
			updateTargetForm(newValue);
			renderPreview();
		});
	}

	private void applyCalibrationForm() {
		try {
			stageData.backgroundCalibration.projectionWidthMm = Integer.parseInt(projectionWidthField.getText().trim());
			stageData.backgroundCalibration.shooterDistanceMm = Integer.parseInt(shooterDistanceField.getText().trim());
			calibrationStatusLabel.setText(buildCalibrationStatusText());
			renderPreview();
		} catch (NumberFormatException ex) {
			new Alert(Alert.AlertType.ERROR, "Projection width and shooter distance must be valid integers in mm.").show();
		}
	}

	private String buildCalibrationStatusText() {
		return "50m line: "
			+ Math.round(stageData.backgroundCalibration.fiftyMeterLeftPoint.xNormalized * 100) + "% / "
			+ Math.round(stageData.backgroundCalibration.fiftyMeterLeftPoint.yNormalized * 100) + "%  ->  "
			+ Math.round(stageData.backgroundCalibration.fiftyMeterRightPoint.xNormalized * 100) + "% / "
			+ Math.round(stageData.backgroundCalibration.fiftyMeterRightPoint.yNormalized * 100) + "%";
	}

	private void applyEngagementForm() {
		final ArmyTableVIQualification.Engagement selected = engagementList.getSelectionModel().getSelectedItem();
		if (selected == null) return;

		try {
			selected.position = positionCombo.getValue();
			selected.delayBeforeSec = Double.parseDouble(delayField.getText().trim());
			selected.exposureSec = Double.parseDouble(exposureField.getText().trim());
			selected.targets = new java.util.ArrayList<>(targetList.getItems());
			ArmyTableVIQualification.normalizeTargetLayout(selected.targets, stageData.backgroundCalibration);
			engagementList.refresh();
			renderPreview();
		} catch (NumberFormatException ex) {
			new Alert(Alert.AlertType.ERROR, "Delay and exposure must be valid numbers.").show();
		}
	}

	private void updateTargetForm(ArmyTableVIQualification.TargetSpec target) {
		if (target == null) {
			targetTypeCombo.setValue(ArmyTableVIQualification.ArmyTargetType.E1);
			targetDistanceField.clear();
			targetPositionLabel.setText("Drag left or right in the preview. Depth is automatic.");
			return;
		}

		targetTypeCombo.setValue(target.targetType);
		targetDistanceField.setText(String.valueOf(target.distanceMeters));
		targetPositionLabel.setText(Math.round(target.laneXNormalized * 100) + "% lane | "
			+ target.distanceMeters + " m depth");
	}

	private void applyTargetForm() {
		final ArmyTableVIQualification.TargetSpec target = targetList.getSelectionModel().getSelectedItem();
		if (target == null) return;

		try {
			target.targetType = targetTypeCombo.getValue() == null
				? ArmyTableVIQualification.ArmyTargetType.E1
				: targetTypeCombo.getValue();
			target.distanceMeters = Integer.parseInt(targetDistanceField.getText().trim());
			ArmyTableVIQualification.normalizeTargetLayout(targetList.getItems(), stageData.backgroundCalibration);
			targetList.refresh();
			updateTargetForm(target);
			renderPreview();
		} catch (NumberFormatException ex) {
			new Alert(Alert.AlertType.ERROR, "Target distance must be a valid integer.").show();
		}
	}

	private void showAddTargetDialog() {
		final Dialog<ArmyTableVIQualification.TargetSpec> dialog = new Dialog<>();
		dialog.setTitle("Add Target");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		final GridPane form = new GridPane();
		form.setHgap(10);
		form.setVgap(10);

		final ComboBox<ArmyTableVIQualification.ArmyTargetType> typeCombo = new ComboBox<>();
		typeCombo.getItems().addAll(ArmyTableVIQualification.ArmyTargetType.values());
		typeCombo.setValue(ArmyTableVIQualification.ArmyTargetType.E1);

		final TextField distanceField = new TextField("50");

		form.add(new Label("Target Model:"), 0, 0);
		form.add(typeCombo, 1, 0);
		form.add(new Label("Distance (m):"), 0, 1);
		form.add(distanceField, 1, 1);
		dialog.getDialogPane().setContent(form);

		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;

			try {
				final int distanceMeters = Integer.parseInt(distanceField.getText().trim());
				return new ArmyTableVIQualification.TargetSpec(
					typeCombo.getValue(),
					distanceMeters,
					"",
					Double.NaN);
			} catch (NumberFormatException ex) {
				return null;
			}
		});

		final Optional<ArmyTableVIQualification.TargetSpec> result = dialog.showAndWait();
		result.ifPresent(target -> {
			final ArmyTableVIQualification.Engagement engagement = engagementList.getSelectionModel().getSelectedItem();
			if (engagement == null) return;

			engagement.targets.add(target);
			ArmyTableVIQualification.normalizeTargetLayout(engagement.targets, stageData.backgroundCalibration);
			targetList.getItems().setAll(engagement.targets);
			targetList.getSelectionModel().select(target);
			renderPreview();
		});
	}

	private void renderPreview() {
		overlayPane.getChildren().clear();
		renderCalibrationOverlay();

		final ArmyTableVIQualification.Engagement engagement = engagementList.getSelectionModel().getSelectedItem();
		if (engagement == null) return;

		ArmyTableVIQualification.normalizeTargetLayout(engagement.targets, stageData.backgroundCalibration);
		for (ArmyTableVIQualification.TargetSpec target : engagement.targets) {
			overlayPane.getChildren().add(createPreviewTarget(target));
		}
	}

	private void renderCalibrationOverlay() {
		final double leftX = stageData.backgroundCalibration.fiftyMeterLeftPoint.xNormalized * PREVIEW_WIDTH;
		final double leftY = stageData.backgroundCalibration.fiftyMeterLeftPoint.yNormalized * PREVIEW_HEIGHT;
		final double rightX = stageData.backgroundCalibration.fiftyMeterRightPoint.xNormalized * PREVIEW_WIDTH;
		final double rightY = stageData.backgroundCalibration.fiftyMeterRightPoint.yNormalized * PREVIEW_HEIGHT;

		calibrationLineDragArea = new Line(leftX, leftY, rightX, rightY);
		calibrationLineDragArea.setStroke(Color.rgb(0, 191, 255, 0.001));
		calibrationLineDragArea.setStrokeWidth(20);
		calibrationLineDragArea.setOnMousePressed(event -> {
			calibrationLineDragStart = overlayPane.sceneToLocal(event.getSceneX(), event.getSceneY());
			calibrationLineLeftStart = new ArmyTableVIQualification.StagePoint(
				stageData.backgroundCalibration.fiftyMeterLeftPoint);
			calibrationLineRightStart = new ArmyTableVIQualification.StagePoint(
				stageData.backgroundCalibration.fiftyMeterRightPoint);
			event.consume();
		});
		calibrationLineDragArea.setOnMouseDragged(event -> {
			if (calibrationLineDragStart == null) return;

			final Point2D local = overlayPane.sceneToLocal(event.getSceneX(), event.getSceneY());
			final double deltaX = (local.getX() - calibrationLineDragStart.getX()) / PREVIEW_WIDTH;
			final double deltaY = (local.getY() - calibrationLineDragStart.getY()) / PREVIEW_HEIGHT;

			stageData.backgroundCalibration.fiftyMeterLeftPoint.xNormalized =
				clamp(calibrationLineLeftStart.xNormalized + deltaX, 0.01, 0.99);
			stageData.backgroundCalibration.fiftyMeterLeftPoint.yNormalized =
				clamp(calibrationLineLeftStart.yNormalized + deltaY, 0.01, 0.99);
			stageData.backgroundCalibration.fiftyMeterRightPoint.xNormalized =
				clamp(calibrationLineRightStart.xNormalized + deltaX, 0.01, 0.99);
			stageData.backgroundCalibration.fiftyMeterRightPoint.yNormalized =
				clamp(calibrationLineRightStart.yNormalized + deltaY, 0.01, 0.99);

			calibrationStatusLabel.setText(buildCalibrationStatusText());
			updateCalibrationOverlayGeometry();
			event.consume();
		});
		calibrationLineDragArea.setOnMouseReleased(event -> {
			calibrationLineDragStart = null;
			calibrationLineLeftStart = null;
			calibrationLineRightStart = null;
			renderPreview();
			event.consume();
		});

		calibrationLine = new Line(leftX, leftY, rightX, rightY);
		calibrationLine.setStroke(Color.rgb(0, 191, 255, 0.95));
		calibrationLine.setStrokeWidth(2.5);
		calibrationLine.setMouseTransparent(true);

		calibrationLineLabel = new Label("50 m");
		calibrationLineLabel.setStyle("-fx-background-color: rgba(10,15,20,0.75); -fx-text-fill: white; -fx-padding: 3 6 3 6;");
		calibrationLineLabel.setMouseTransparent(true);

		leftCalibrationHandle = createCalibrationHandle(stageData.backgroundCalibration.fiftyMeterLeftPoint);
		rightCalibrationHandle = createCalibrationHandle(stageData.backgroundCalibration.fiftyMeterRightPoint);

		overlayPane.getChildren().add(calibrationLineDragArea);
		overlayPane.getChildren().add(calibrationLine);
		overlayPane.getChildren().add(calibrationLineLabel);
		overlayPane.getChildren().add(leftCalibrationHandle);
		overlayPane.getChildren().add(rightCalibrationHandle);
		updateCalibrationOverlayGeometry();
	}

	private Circle createCalibrationHandle(ArmyTableVIQualification.StagePoint point) {
		final Circle handle = new Circle(HANDLE_HIT_RADIUS, Color.rgb(18, 198, 255, 0.14));
		handle.setStroke(Color.WHITE);
		handle.setStrokeWidth(2.0);
		handle.setCenterX(point.xNormalized * PREVIEW_WIDTH);
		handle.setCenterY(point.yNormalized * PREVIEW_HEIGHT);

		handle.setOnMouseDragged(event -> {
			final Point2D local = overlayPane.sceneToLocal(event.getSceneX(), event.getSceneY());
			point.xNormalized = clamp(local.getX() / PREVIEW_WIDTH, 0.01, 0.99);
			point.yNormalized = clamp(local.getY() / PREVIEW_HEIGHT, 0.01, 0.99);
			calibrationStatusLabel.setText(buildCalibrationStatusText());
			updateCalibrationOverlayGeometry();
			event.consume();
		});

		handle.setOnMouseReleased(event -> {
			renderPreview();
			event.consume();
		});

		return handle;
	}

	private StackPane createPreviewTarget(ArmyTableVIQualification.TargetSpec target) {
		if (target.targetType == null) {
			target.targetType = ArmyTableVIQualification.ArmyTargetType.E1;
		}

		final double[] size = ArmyTableVIQualification.targetPixelSize(target, PREVIEW_WIDTH, PREVIEW_HEIGHT,
			stageData.backgroundCalibration);
		final StackPane view = new StackPane();
		view.setPrefSize(size[0], size[1]);
		view.setMinSize(size[0], size[1]);
		view.setMaxSize(size[0], size[1]);
		view.setStyle(target == targetList.getSelectionModel().getSelectedItem()
			? "-fx-background-color: rgba(18,26,39,0.92); -fx-border-color: gold; -fx-border-width: 2; "
				+ "-fx-effect: dropshadow(gaussian, rgba(255,215,0,0.85), 14, 0.45, 0, 0);"
			: "-fx-background-color: rgba(18,26,39,0.92); -fx-border-color: rgba(12,18,28,0.95); -fx-border-width: 1; "
				+ "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 8, 0.3, 0, 1);");

		final Optional<Image> previewImage = loadPreviewImage(target.targetType, size[0], size[1]);
		if (previewImage.isPresent()) {
			final ImageView imageView = new ImageView(previewImage.get());
			imageView.setPreserveRatio(true);
			imageView.setFitWidth(size[0]);
			imageView.setFitHeight(size[1]);
			view.getChildren().add(imageView);
		} else {
			final Label fallback = new Label(target.targetType.getDisplayName());
			fallback.setWrapText(true);
			fallback.setTextFill(Color.WHITE);
			fallback.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
			view.getChildren().add(fallback);
		}

		positionPreviewTarget(view, target);

		view.setOnMouseClicked(event -> {
			targetList.getSelectionModel().select(target);
			event.consume();
		});

		view.setOnMousePressed(event -> {
			draggingTarget = target;
			dragOffsetX = event.getX();
			dragOffsetY = event.getY();
			targetList.getSelectionModel().select(target);
			event.consume();
		});

		view.setOnMouseDragged(event -> {
			if (draggingTarget != target) return;

			double newX = view.getLayoutX() + event.getX() - dragOffsetX;
			newX = clamp(newX, 0.0, PREVIEW_WIDTH - view.getPrefWidth());

			target.laneXNormalized = (newX + (view.getPrefWidth() / 2.0)) / PREVIEW_WIDTH;
			positionPreviewTarget(view, target);
			updateTargetForm(target);
			targetList.refresh();
			event.consume();
		});

		return view;
	}

	private void positionPreviewTarget(StackPane view, ArmyTableVIQualification.TargetSpec target) {
		final double computedDepth = ArmyTableVIQualification.defaultDepthForDistance(
			target.distanceMeters, stageData.backgroundCalibration);
		final double anchorX = clamp(target.laneXNormalized * PREVIEW_WIDTH, view.getPrefWidth() / 2.0,
			PREVIEW_WIDTH - (view.getPrefWidth() / 2.0));
		final double anchorY = clamp(computedDepth * PREVIEW_HEIGHT, view.getPrefHeight(), PREVIEW_HEIGHT);

		view.setLayoutX(anchorX - (view.getPrefWidth() / 2.0));
		view.setLayoutY(anchorY - view.getPrefHeight());
	}

	private Optional<Image> loadPreviewImage(ArmyTableVIQualification.ArmyTargetType targetType, double width, double height) {
		File imageFile = new File(targetType.getImagePath());
		if (!imageFile.isAbsolute()) {
			imageFile = new File(System.getProperty("shootoff.home") + File.separator + imageFile.getPath());
		}

		if (!imageFile.exists()) return Optional.empty();

		final Image image = new Image(imageFile.toURI().toString(), width, height, true, true, false);
		return image.isError() ? Optional.empty() : Optional.of(image);
	}

	private void updateCalibrationOverlayGeometry() {
		if (calibrationLine == null || calibrationLineDragArea == null || calibrationLineLabel == null
				|| leftCalibrationHandle == null || rightCalibrationHandle == null) return;

		final double leftX = stageData.backgroundCalibration.fiftyMeterLeftPoint.xNormalized * PREVIEW_WIDTH;
		final double leftY = stageData.backgroundCalibration.fiftyMeterLeftPoint.yNormalized * PREVIEW_HEIGHT;
		final double rightX = stageData.backgroundCalibration.fiftyMeterRightPoint.xNormalized * PREVIEW_WIDTH;
		final double rightY = stageData.backgroundCalibration.fiftyMeterRightPoint.yNormalized * PREVIEW_HEIGHT;

		calibrationLine.setStartX(leftX);
		calibrationLine.setStartY(leftY);
		calibrationLine.setEndX(rightX);
		calibrationLine.setEndY(rightY);

		calibrationLineDragArea.setStartX(leftX);
		calibrationLineDragArea.setStartY(leftY);
		calibrationLineDragArea.setEndX(rightX);
		calibrationLineDragArea.setEndY(rightY);

		calibrationLineLabel.setLayoutX(((leftX + rightX) / 2.0) - 18);
		calibrationLineLabel.setLayoutY(((leftY + rightY) / 2.0) - 26);

		leftCalibrationHandle.setCenterX(leftX);
		leftCalibrationHandle.setCenterY(leftY);
		rightCalibrationHandle.setCenterX(rightX);
		rightCalibrationHandle.setCenterY(rightY);
	}

	private void setPreviewZoom(double zoom) {
		applyPreviewZoom(snapZoom(zoom), false);
	}

	private void applyPreviewZoom(double zoom, boolean fromSlider) {
		previewZoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);

		if (previewContentPane != null) {
			previewContentPane.setScaleX(previewZoom);
			previewContentPane.setScaleY(previewZoom);
		}

		if (zoomValueLabel != null) {
			zoomValueLabel.setText(String.format("%.0f%%", previewZoom * 100));
		}

		if (!fromSlider && zoomSlider != null && Math.abs(zoomSlider.getValue() - previewZoom) > 0.0001) {
			zoomSlider.setValue(previewZoom);
		}
	}

	private double snapZoom(double zoom) {
		return Math.round(zoom / ZOOM_STEP) * ZOOM_STEP;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
