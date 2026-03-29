package com.shootoff.plugins;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ArmyTableVIStageEditor {

    private final ArmyTableVIQualification plugin;
    private final List<ArmyTableVIQualification.Engagement> engagements;

    private ListView<ArmyTableVIQualification.Engagement> engagementList;
    private ComboBox<ArmyTableVIQualification.Position> positionCombo;
    private TextField delayField;
    private TextField exposureField;
    private ListView<ArmyTableVIQualification.TargetSpec> targetList;

    public ArmyTableVIStageEditor(ArmyTableVIQualification plugin, List<ArmyTableVIQualification.Engagement> currentEngagements) {
        this.plugin = plugin;
        this.engagements = new ArrayList<>();
        // Deep copy engagements so we don't mutate active training until save
        for (ArmyTableVIQualification.Engagement e : currentEngagements) {
            ArmyTableVIQualification.Engagement copy = new ArmyTableVIQualification.Engagement(e.number, e.position, e.delayBeforeSec, e.exposureSec);
            copy.targets.clear();
            for (ArmyTableVIQualification.TargetSpec t : e.targets) {
                copy.targets.add(new ArmyTableVIQualification.TargetSpec(t.distanceMeters, t.side));
            }
            this.engagements.add(copy);
        }
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Army Table VI - Stage Editor");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // LEFT: Engagements List
        VBox leftPane = new VBox(5);
        leftPane.setPrefWidth(250);
        Label lblList = new Label("Engagements Sequence:");
        engagementList = new ListView<>();
        engagementList.getItems().addAll(engagements);
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

        HBox engButtons = new HBox(5);
        Button btnAddEng = new Button("Add");
        Button btnRemoveEng = new Button("Remove");
        engButtons.getChildren().addAll(btnAddEng, btnRemoveEng);

        leftPane.getChildren().addAll(lblList, engagementList, engButtons);
        root.setLeft(leftPane);

        // CENTER: Editing Form
        VBox centerPane = new VBox(10);
        centerPane.setPadding(new Insets(0, 10, 0, 10));

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        grid.add(new Label("Position:"), 0, 0);
        positionCombo = new ComboBox<>();
        positionCombo.getItems().addAll(ArmyTableVIQualification.Position.values());
        grid.add(positionCombo, 1, 0);

        grid.add(new Label("Pre-Delay (s):"), 0, 1);
        delayField = new TextField();
        grid.add(delayField, 1, 1);

        grid.add(new Label("Exposure (s):"), 0, 2);
        exposureField = new TextField();
        grid.add(exposureField, 1, 2);

        // Targets part
        VBox targetsBox = new VBox(5);
        targetsBox.getChildren().add(new Label("Targets for selected Engagement:"));
        targetList = new ListView<>();
        targetList.setPrefHeight(150);
        targetList.setCellFactory(p -> new ListCell<ArmyTableVIQualification.TargetSpec>() {
             @Override
             protected void updateItem(ArmyTableVIQualification.TargetSpec item, boolean empty) {
                 super.updateItem(item, empty);
                 if (empty || item == null) setText(null);
                 else setText(item.distanceMeters + "m - " + (item.side.isEmpty() ? "CENTER" : item.side));
             }
        });

        HBox tgtButtons = new HBox(5);
        Button btnAddTgt = new Button("Add Target");
        Button btnRemoveTgt = new Button("Remove Target");
        tgtButtons.getChildren().addAll(btnAddTgt, btnRemoveTgt);
        targetsBox.getChildren().addAll(targetList, tgtButtons);

        Button btnApplyEng = new Button("Apply Form to Engagement");
        btnApplyEng.setStyle("-fx-font-weight: bold;");

        centerPane.getChildren().addAll(grid, targetsBox, btnApplyEng);
        root.setCenter(centerPane);

        // BOTTOM: Save/Cancel
        HBox bottomPane = new HBox(10);
        bottomPane.setPadding(new Insets(10, 0, 0, 0));
        Button btnSave = new Button("Save Custom Stage & Close");
        Button btnCancel = new Button("Cancel");
        Button btnRestore = new Button("Restore Default Table VI");
        bottomPane.getChildren().addAll(btnSave, btnCancel, btnRestore);
        root.setBottom(bottomPane);

        // Event Handling
        engagementList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            boolean disable = (newV == null);
            positionCombo.setDisable(disable);
            delayField.setDisable(disable);
            exposureField.setDisable(disable);
            targetList.setDisable(disable);
            btnApplyEng.setDisable(disable);
            btnAddTgt.setDisable(disable);
            btnRemoveTgt.setDisable(disable);

            if (newV != null) {
                positionCombo.setValue(newV.position);
                delayField.setText(String.valueOf(newV.delayBeforeSec));
                exposureField.setText(String.valueOf(newV.exposureSec));
                targetList.getItems().setAll(newV.targets);
            } else {
                targetList.getItems().clear();
            }
        });

        btnAddEng.setOnAction(e -> {
            ArmyTableVIQualification.Engagement newEng = new ArmyTableVIQualification.Engagement(
                engagementList.getItems().size() + 1,
                ArmyTableVIQualification.Position.PRONE_SUPPORTED,
                2.0, 5.0
            );
            engagementList.getItems().add(newEng);
            engagementList.getSelectionModel().select(newEng);
        });

        btnRemoveEng.setOnAction(e -> {
            ArmyTableVIQualification.Engagement sel = engagementList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                engagementList.getItems().remove(sel);
                // renumber
                for(int i=0; i<engagementList.getItems().size(); i++) {
                    engagementList.getItems().get(i).number = i+1;
                }
                engagementList.refresh();
            }
        });

        btnApplyEng.setOnAction(e -> {
            ArmyTableVIQualification.Engagement sel = engagementList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                try {
                    sel.position = positionCombo.getValue();
                    sel.delayBeforeSec = Double.parseDouble(delayField.getText());
                    sel.exposureSec = Double.parseDouble(exposureField.getText());
                    sel.targets = new ArrayList<>(targetList.getItems());
                    engagementList.refresh();
                } catch (NumberFormatException ex) {
                    Alert a = new Alert(Alert.AlertType.ERROR, "Delay and Exposure must be valid numbers.");
                    a.show();
                }
            }
        });

        btnAddTgt.setOnAction(e -> {
            Dialog<ArmyTableVIQualification.TargetSpec> dialog = new Dialog<>();
            dialog.setTitle("Add Target");
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            GridPane dg = new GridPane();
            dg.setHgap(10); dg.setVgap(10);
            TextField distField = new TextField("50");
            ComboBox<String> sideCombo = new ComboBox<>();
            sideCombo.getItems().addAll("LEFT", "RIGHT", "CENTER");
            sideCombo.setValue("CENTER");

            dg.add(new Label("Distance (m):"), 0, 0); dg.add(distField, 1, 0);
            dg.add(new Label("Lane:"), 0, 1); dg.add(sideCombo, 1, 1);
            dialog.getDialogPane().setContent(dg);

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    try {
                        int dist = Integer.parseInt(distField.getText());
                        String side = sideCombo.getValue().equals("CENTER") ? "" : sideCombo.getValue();
                        return new ArmyTableVIQualification.TargetSpec(dist, side);
                    } catch (Exception ex) { return null; }
                }
                return null;
            });
            Optional<ArmyTableVIQualification.TargetSpec> res = dialog.showAndWait();
            res.ifPresent(tgt -> targetList.getItems().add(tgt));
        });

        btnRemoveTgt.setOnAction(e -> {
            ArmyTableVIQualification.TargetSpec sel = targetList.getSelectionModel().getSelectedItem();
            if (sel != null) targetList.getItems().remove(sel);
        });

        btnSave.setOnAction(e -> {
            List<ArmyTableVIQualification.Engagement> toSave = new ArrayList<>(engagementList.getItems());
            try {
                ArmyTableCustomStageManager.saveEngagements(toSave);
                plugin.reloadEngagements(toSave);
                stage.close();
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Failed to save: " + ex.getMessage()).show();
            }
        });

        btnRestore.setOnAction(e -> {
            try {
                java.io.File f = new java.io.File("exercises/army_table_vi_custom.txt");
                if (f.exists()) f.delete();
                plugin.reloadEngagements(null);
                stage.close();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Failed to restore defaults").show();
            }
        });

        btnCancel.setOnAction(e -> stage.close());

        // Initialize state
        engagementList.getSelectionModel().selectFirst();

        Scene scene = new Scene(root, 700, 500);
        stage.setScene(scene);
        stage.show();
    }
}
