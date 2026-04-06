package com.shootoff.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineJoin;

public class QuadrilateralCalibrationUI extends Group {
	private static final double HANDLE_RADIUS = 7.0;
	private static final double NUDGE_SMALL = 1.0;
	private static final double NUDGE_LARGE = 10.0;
	private static final int MESH_DIVISIONS = 5;

	private final Polygon polygon = new Polygon();
	private final Group meshGroup = new Group();
	private final List<Point2D> corners = new ArrayList<>(4);
	private final List<Circle> handles = new ArrayList<>(4);
	private final boolean interactive;
	private Optional<Runnable> cornersChangedCallback = Optional.empty();
	private int selectedCorner = 0;

	public QuadrilateralCalibrationUI(double initX, double initY, double width, double height) {
		this(rectangleCorners(initX, initY, width, height), true);
	}

	public QuadrilateralCalibrationUI(List<Point2D> initialCorners) {
		this(initialCorners, true);
	}

	public QuadrilateralCalibrationUI(List<Point2D> initialCorners, boolean interactive) {
		if (initialCorners == null || initialCorners.size() != 4) {
			throw new IllegalArgumentException("Quadrilateral calibration requires four corners");
		}

		this.interactive = interactive;
		corners.addAll(initialCorners);
		setFocusTraversable(interactive);
		setOnMouseClicked(event -> {
			if (!this.interactive) return;
			requestFocus();
			event.consume();
		});
		setOnKeyPressed(this::handleKeyPressed);

		meshGroup.setMouseTransparent(true);
		getChildren().add(meshGroup);

		polygon.setFill(Color.rgb(128, 0, 128, interactive ? 0.28 : 0.12));
		polygon.setStroke(interactive ? Color.YELLOW : Color.CYAN);
		polygon.setStrokeWidth(interactive ? 2.0 : 3.0);
		polygon.setStrokeLineJoin(StrokeLineJoin.ROUND);
		getChildren().add(polygon);

		for (int i = 0; i < 4; i++) {
			final int cornerIndex = i;
			final Circle handle = new Circle(HANDLE_RADIUS);
			handle.setFill(Color.YELLOW);
			handle.setStroke(Color.BLACK);
			handle.setStrokeWidth(1.5);
			handle.setOnMousePressed(event -> {
				if (!this.interactive) return;
				selectedCorner = cornerIndex;
				requestFocus();
				updateHandleStyles();
				event.consume();
			});
			handle.setOnMouseDragged(event -> {
				if (!this.interactive) return;
				setCorner(cornerIndex, sceneToLocal(event.getSceneX(), event.getSceneY()));
				event.consume();
			});
			handles.add(handle);
			if (interactive) getChildren().add(handle);
		}

		refresh();
	}

	public List<Point2D> getCornerPoints() {
		return getCorners();
	}

	public List<Point2D> getCorners() {
		return Collections.unmodifiableList(new ArrayList<>(corners));
	}

	public void setCorners(List<Point2D> newCorners) {
		if (newCorners == null || newCorners.size() != 4) {
			throw new IllegalArgumentException("Quadrilateral calibration requires four corners");
		}
		corners.clear();
		corners.addAll(newCorners);
		refresh();
	}

	public void setMeshVisible(boolean visible) {
		meshGroup.setVisible(visible);
	}

	public void setOnCornersChanged(Runnable cornersChangedCallback) {
		this.cornersChangedCallback = Optional.ofNullable(cornersChangedCallback);
	}

	public Bounds getCalibrationBounds() {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (Point2D corner : corners) {
			minX = Math.min(minX, corner.getX());
			minY = Math.min(minY, corner.getY());
			maxX = Math.max(maxX, corner.getX());
			maxY = Math.max(maxY, corner.getY());
		}
		return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
	}

	public boolean handleKeyPressed(KeyEvent event) {
		if (!interactive) return false;

		if (event.getCode() == KeyCode.TAB) {
			selectedCorner = (selectedCorner + 1) % 4;
			updateHandleStyles();
			event.consume();
			return true;
		}

		final double delta = event.isShiftDown() ? NUDGE_LARGE : NUDGE_SMALL;
		double dx = 0.0;
		double dy = 0.0;
		if (event.getCode() == KeyCode.LEFT) {
			dx = -delta;
		} else if (event.getCode() == KeyCode.RIGHT) {
			dx = delta;
		} else if (event.getCode() == KeyCode.UP) {
			dy = -delta;
		} else if (event.getCode() == KeyCode.DOWN) {
			dy = delta;
		} else {
			return false;
		}

		setCorner(selectedCorner, corners.get(selectedCorner).add(dx, dy));
		event.consume();
		return true;
	}

	private void setCorner(int index, Point2D point) {
		corners.set(index, point);
		refresh();
		cornersChangedCallback.ifPresent(Runnable::run);
	}

	private void refresh() {
		polygon.getPoints().clear();
		for (Point2D corner : corners) {
			polygon.getPoints().add(corner.getX());
			polygon.getPoints().add(corner.getY());
		}

		redrawMesh();

		for (int i = 0; i < handles.size(); i++) {
			final Point2D corner = corners.get(i);
			final Circle handle = handles.get(i);
			handle.setCenterX(corner.getX());
			handle.setCenterY(corner.getY());
		}

		updateHandleStyles();
	}

	private void redrawMesh() {
		meshGroup.getChildren().clear();

		for (int i = 0; i <= MESH_DIVISIONS; i++) {
			final double t = (double) i / (double) MESH_DIVISIONS;
			meshGroup.getChildren().add(createMeshLine(interpolate(corners.get(0), corners.get(1), t),
					interpolate(corners.get(3), corners.get(2), t)));
			meshGroup.getChildren().add(createMeshLine(interpolate(corners.get(0), corners.get(3), t),
					interpolate(corners.get(1), corners.get(2), t)));
		}
	}

	private Line createMeshLine(Point2D start, Point2D end) {
		final Line line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
		line.setStroke(Color.rgb(0, 255, 255, interactive ? 0.65 : 0.9));
		line.setStrokeWidth(interactive ? 1.2 : 2.0);
		line.setMouseTransparent(true);
		return line;
	}

	private void updateHandleStyles() {
		for (int i = 0; i < handles.size(); i++) {
			final Circle handle = handles.get(i);
			handle.setRadius(i == selectedCorner ? HANDLE_RADIUS + 3.0 : HANDLE_RADIUS);
			handle.setFill(i == selectedCorner ? Color.ORANGE : Color.YELLOW);
		}
	}

	private static Point2D interpolate(Point2D start, Point2D end, double t) {
		return new Point2D(start.getX() + (end.getX() - start.getX()) * t,
				start.getY() + (end.getY() - start.getY()) * t);
	}

	private static List<Point2D> rectangleCorners(double x, double y, double width, double height) {
		final List<Point2D> points = new ArrayList<>(4);
		points.add(new Point2D(x, y));
		points.add(new Point2D(x + width, y));
		points.add(new Point2D(x + width, y + height));
		points.add(new Point2D(x, y + height));
		return points;
	}
}
