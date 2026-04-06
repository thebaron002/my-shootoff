package com.shootoff.gui;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import boofcv.alg.drawing.FiducialImageEngine;
import boofcv.alg.fiducial.square.FiducialSquareHammingGenerator;
import boofcv.factory.fiducial.ConfigHammingMarker;
import boofcv.factory.fiducial.HammingDictionary;
import boofcv.struct.image.GrayU8;
import javafx.geometry.Dimension2D;

public final class AutoTagsCalibrationPatternFactory {
	private static final java.awt.Color BACKGROUND_COLOR = new java.awt.Color(223, 229, 238);
	private static final java.awt.Color TEXT_COLOR = new java.awt.Color(20, 24, 28);
	private static final java.awt.Color GUIDE_COLOR = new java.awt.Color(255, 255, 255, 185);
	private static final ConfigHammingMarker MARKER_CONFIG = createMarkerConfig();

	private AutoTagsCalibrationPatternFactory() {}

	public static LocatedImage createPattern(String imageName, Dimension2D resolution) {
		final int width = Math.max((int) Math.round(resolution.getWidth()), 320);
		final int height = Math.max((int) Math.round(resolution.getHeight()), 240);
		final int markerSize = clamp((int) Math.round(Math.min(width, height) * 0.22), 128, 260);
		final BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = canvas.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics.setColor(BACKGROUND_COLOR);
			graphics.fillRect(0, 0, width, height);
			drawGuideGrid(graphics, width, height);
			drawMarker(graphics, renderMarker(0, markerSize), 0, 0);
			drawMarker(graphics, renderMarker(1, markerSize), width - markerSize, 0);
			drawMarker(graphics, renderMarker(2, markerSize), width - markerSize, height - markerSize);
			drawMarker(graphics, renderMarker(3, markerSize), 0, height - markerSize);
			drawInstructionBand(graphics, width, height, markerSize);
		} finally {
			graphics.dispose();
		}
		return writePattern(imageName, canvas);
	}

	private static LocatedImage writePattern(String imageName, BufferedImage canvas) {
		try {
			final File patternFile = new File(System.getProperty("java.io.tmpdir"), imageName + ".png");
			ImageIO.write(canvas, "png", patternFile);
			return new LocatedImage(patternFile.toURI().toString());
		} catch (IOException e) {
			throw new IllegalStateException("Failed to write Auto Tags calibration pattern", e);
		}
	}

	private static ConfigHammingMarker createMarkerConfig() {
		final ConfigHammingMarker config = ConfigHammingMarker.loadDictionary(HammingDictionary.APRILTAG_36h11);
		config.targetWidth = 1.0;
		return config;
	}

	private static BufferedImage renderMarker(int markerId, int markerSize) {
		final int markerBodySize = Math.max(32, (int) Math.round(markerSize * 0.72));
		final int borderSize = Math.max(8, (markerSize - markerBodySize) / 2);
		final FiducialImageEngine engine = new FiducialImageEngine();
		engine.configure(borderSize, markerBodySize);
		final FiducialSquareHammingGenerator generator = new FiducialSquareHammingGenerator(MARKER_CONFIG);
		generator.setRenderer(engine);
		generator.setMarkerWidth(markerBodySize);
		generator.generate(markerId);
		final GrayU8 markerGray = engine.getGray();
		final BufferedImage markerImage = new BufferedImage(markerGray.width, markerGray.height, BufferedImage.TYPE_BYTE_GRAY);
		final byte[] targetPixels = ((DataBufferByte) markerImage.getRaster().getDataBuffer()).getData();
		System.arraycopy(markerGray.data, 0, targetPixels, 0, markerGray.width * markerGray.height);
		return markerImage;
	}

	private static void drawMarker(Graphics2D graphics, BufferedImage markerImage, int x, int y) {
		graphics.drawImage(markerImage, x, y, null);
	}

	private static void drawGuideGrid(Graphics2D graphics, int width, int height) {
		final int divisions = 5;
		graphics.setColor(new java.awt.Color(40, 65, 90, 42));
		graphics.setStroke(new BasicStroke(2f));
		for (int i = 1; i < divisions; i++) {
			final int x = (int) Math.round(width * ((double) i / (double) divisions));
			final int y = (int) Math.round(height * ((double) i / (double) divisions));
			graphics.drawLine(x, 0, x, height);
			graphics.drawLine(0, y, width, y);
		}
	}

	private static void drawInstructionBand(Graphics2D graphics, int width, int height, int markerSize) {
		final int bandHeight = Math.max(42, height / 20);
		final int bandWidth = Math.min(width - markerSize * 2, Math.max(380, width / 2));
		final int bandX = (width - bandWidth) / 2;
		final int bandY = Math.max(markerSize / 6, height / 30);
		graphics.setColor(GUIDE_COLOR);
		graphics.fillRoundRect(bandX, bandY, bandWidth, bandHeight, 24, 24);
		graphics.setColor(new java.awt.Color(35, 35, 35, 60));
		graphics.setStroke(new BasicStroke(2f));
		graphics.drawRoundRect(bandX, bandY, bandWidth, bandHeight, 24, 24);
		final String line1 = "Auto Tags Calibration";
		final Font titleFont = new Font("SansSerif", Font.BOLD, Math.max(14, bandHeight / 3));
		graphics.setColor(new java.awt.Color(TEXT_COLOR.getRed(), TEXT_COLOR.getGreen(), TEXT_COLOR.getBlue(), 150));
		graphics.setFont(titleFont);
		final FontMetrics titleMetrics = graphics.getFontMetrics();
		final int titleX = bandX + (bandWidth - titleMetrics.stringWidth(line1)) / 2;
		final int titleY = bandY + (bandHeight + titleMetrics.getAscent()) / 2 - 3;
		graphics.drawString(line1, titleX, titleY);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
