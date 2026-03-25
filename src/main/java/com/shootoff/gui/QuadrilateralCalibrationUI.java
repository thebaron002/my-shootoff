package com.shootoff.gui;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;

import java.util.ArrayList;
import java.util.List;

public class QuadrilateralCalibrationUI extends Group {

	private final Polygon polygon;
	private final List<Circle> anchors = new ArrayList<>();

	public QuadrilateralCalibrationUI(double initX, double initY, double width, double height) {
		polygon = new Polygon();
		polygon.setFill(Color.PURPLE);
		polygon.setOpacity(0.5);
		polygon.setStroke(Color.WHITE);
		polygon.setStrokeWidth(2);

		getChildren().add(polygon);

		addAnchor(initX, initY);
		addAnchor(initX + width, initY);
		addAnchor(initX + width, initY + height);
		addAnchor(initX, initY + height);

		updatePolygon();
	}

	private void addAnchor(double x, double y) {
		Circle anchor = new Circle(x, y, 10);
		anchor.setFill(Color.GREEN);
		anchor.setStroke(Color.WHITE);
		anchor.setStrokeWidth(2);
		
		final DoubleProperty dragDeltaX = new SimpleDoubleProperty();
		final DoubleProperty dragDeltaY = new SimpleDoubleProperty();

		anchor.setOnMousePressed(event -> {
			dragDeltaX.set(anchor.getCenterX() - event.getSceneX());
			dragDeltaY.set(anchor.getCenterY() - event.getSceneY());
			anchor.toFront();
		});

		anchor.setOnMouseDragged(event -> {
			double newX = event.getSceneX() + dragDeltaX.get();
			double newY = event.getSceneY() + dragDeltaY.get();
			
			// Basic bounds checking could go here if needed, but for now allow free drag
			anchor.setCenterX(newX);
			anchor.setCenterY(newY);
			updatePolygon();
		});
		
		anchor.setOnMouseEntered(event -> anchor.setRadius(12));
		anchor.setOnMouseExited(event -> anchor.setRadius(10));

		anchors.add(anchor);
		getChildren().add(anchor);
	}

	private void updatePolygon() {
		ObservableList<Double> points = polygon.getPoints();
		points.clear();
		for (Circle anchor : anchors) {
			points.addAll(anchor.getCenterX(), anchor.getCenterY());
		}
	}

	public List<javafx.geometry.Point2D> getCornerPoints() {
		List<javafx.geometry.Point2D> pts = new ArrayList<>();
		for (Circle anchor : anchors) {
			pts.add(new javafx.geometry.Point2D(anchor.getCenterX(), anchor.getCenterY()));
		}
		return pts;
	}
}
