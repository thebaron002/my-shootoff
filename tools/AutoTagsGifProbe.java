import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.opencv.core.Core;

import boofcv.abst.fiducial.SquareHamming_to_FiducialDetector;
import boofcv.factory.fiducial.ConfigFiducialHammingDetector;
import boofcv.factory.fiducial.ConfigHammingMarker;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.factory.fiducial.HammingDictionary;
import boofcv.struct.image.GrayU8;
import georegression.struct.shapes.Polygon2D_F64;

import com.shootoff.camera.CameraCalibrationListener;
import com.shootoff.camera.Frame;
import com.shootoff.camera.autocalibration.AutoTagsCalibrationManager;
import com.shootoff.gui.AutoTagsCalibrationPatternFactory;
import com.shootoff.gui.LocatedImage;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;

public class AutoTagsGifProbe {
	public static void main(String[] args) throws Exception {
		File imageFile = resolveInputImage(args);
		if (!imageFile.isFile()) {
			throw new IllegalArgumentException("Image not found: " + imageFile.getAbsolutePath());
		}

		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		BufferedImage image = ImageIO.read(imageFile);
		if (image == null) {
			throw new IllegalArgumentException("Could not read image: " + imageFile.getAbsolutePath());
		}

		printRawBoofCvDetections(image);

		AtomicReference<List<Point2D>> detected = new AtomicReference<>();
		AutoTagsCalibrationManager manager = new AutoTagsCalibrationManager(new CameraCalibrationListener() {
			@Override
			public void calibrate(Bounds arenaBounds, Optional<Dimension2D> perspectivePaperDims,
					boolean calibratedFromCanvas, long delay) {}

			@Override
			public void setArenaBackground(String resourceFilename) {}

			@Override
			public void calibrationCornersDetected(List<Point2D> cornerPoints, boolean calibratedFromCanvas) {
				detected.set(cornerPoints);
			}
		});

		for (int i = 0; i < 8 && detected.get() == null; i++) {
			manager.processFrame(new Frame(image, 1000L + i * 200L));
		}

		System.out.println("Image: " + imageFile.getAbsolutePath());
		System.out.println("Size: " + image.getWidth() + "x" + image.getHeight());
		if (detected.get() == null) {
			System.out.println("Auto Tags: NO LOCK");
			System.out.println("This means the detector did not accept all four tags from this captured frame.");
			Platform.exit();
			System.exit(2);
		}

		System.out.println("Auto Tags: LOCKED");
		String[] names = { "top-left", "top-right", "bottom-right", "bottom-left" };
		List<Point2D> corners = detected.get();
		for (int i = 0; i < corners.size(); i++) {
			Point2D p = corners.get(i);
			System.out.printf("%s: %.1f, %.1f%n", names[i], p.getX(), p.getY());
		}
		Platform.exit();
	}

	private static File resolveInputImage(String[] args) throws Exception {
		if (args.length == 0 || "--generate".equals(args[0])) {
			try {
				Platform.startup(() -> {});
			} catch (IllegalStateException alreadyStarted) {
				// JavaFX was already initialized by another test in this JVM.
			}
			double width = args.length > 1 ? Double.parseDouble(args[1]) : 640.0;
			double height = args.length > 2 ? Double.parseDouble(args[2]) : 480.0;
			LocatedImage generated = AutoTagsCalibrationPatternFactory.createPattern("autotags-probe-reference",
					new Dimension2D(width, height));
			File generatedFile = new File(new URI(generated.getURL()));
			System.out.println("Generated reference pattern: " + generatedFile.getAbsolutePath());
			return generatedFile;
		}

		return new File(args[0]);
	}

	private static void printRawBoofCvDetections(BufferedImage image) {
		ConfigHammingMarker markerConfig = ConfigHammingMarker.loadDictionary(HammingDictionary.APRILTAG_36h11);
		markerConfig.targetWidth = 1.0;
		ConfigFiducialHammingDetector detectorConfig = new ConfigFiducialHammingDetector();
		SquareHamming_to_FiducialDetector<GrayU8> detector = FactoryFiducial.squareHamming(markerConfig, detectorConfig,
				GrayU8.class);

		detector.detect(convertToGray(image));

		System.out.println("Raw BoofCV detections: " + detector.totalFound());
		for (int i = 0; i < detector.totalFound(); i++) {
			Polygon2D_F64 bounds = detector.getBounds(i, null);
			System.out.println("  id=" + detector.getId(i) + " message=" + detector.getMessage(i) + " bounds=" + bounds);
		}
	}

	private static GrayU8 convertToGray(BufferedImage image) {
		GrayU8 gray = new GrayU8(image.getWidth(), image.getHeight());
		int index = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int rgb = image.getRGB(x, y);
				int red = (rgb >> 16) & 0xFF;
				int green = (rgb >> 8) & 0xFF;
				int blue = rgb & 0xFF;
				gray.data[index++] = (byte) ((red * 299 + green * 587 + blue * 114) / 1000);
			}
		}
		return gray;
	}
}
