# SO-00 Baseline

## Goal

Capture the current runnable behavior before any modernization work changes the system.

## Runtime Reference

- Packaged app in active use:
  - `C:\Users\henri\Downloads\shootoff-4.0-final`
- Legacy source snapshot originally outside the repo:
  - `C:\Users\henri\Downloads\ShootOFF-4.0-FINAL (1)\ShootOFF-4.0-FINAL`
- Source snapshot imported into this repository on 2026-03-12 for tracking and modernization.

## Packaged Runtime Inventory

Observed in `C:\Users\henri\Downloads\shootoff-4.0-final`:

- `ShootOFF.jar`
- `ShootOFF-diagnostics.jar`
- `Run-ShootOFF.bat`
- `shootoff.properties`
- `eyeCam32.dll`
- `eyeCam64.dll`
- Resource folders:
  - `courses`
  - `exercises`
  - `libs`
  - `log`
  - `META-INF`
  - `native`
  - `sounds`
  - `targets`

Library count:

- `76` jars in `libs`

## Launcher Behavior

The current launcher is `Run-ShootOFF.bat`.

Observed behavior:

- Prefers `C:\Program Files\BellSoft\LibericaJDK-25-Full\bin\java.exe` when available
- Extracts `opencv_java2413.dll` from `libs\opencv-2.4.13-0.jar` into `native\opencv`
- Starts the app with:
  - `--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED`
  - `--enable-native-access=ALL-UNNAMED`
  - `--enable-native-access=javafx.graphics`
  - `-Djava.library.path="%NATIVE_DIR%"`
  - `-jar "%APP_JAR%"`

Implication:

- The runnable app already relies on compatibility flags to tolerate legacy internal API usage on a modern JDK.

## Manifest Baseline

Observed in `META-INF\MANIFEST.MF`:

- `Main-Class: com.shootoff.Main`
- `JavaFX-Version: 8.0`
- `Application-Name: ShootOFF`
- `Permissions: all-permissions`
- `Trusted-Library: true`

Implication:

- The shipped artifact was built in the JavaFX 8 era and assumes the legacy packaging model.

## Runtime Evidence

The existing packaged app is reported by the user as working from the `.bat`.

Additional local evidence:

- `log\server.log` contains a successful startup sequence dated 2026-03-12
- The tail of the log shows MaryTTS initialization and `marytts.main Startup complete.`

Inference:

- The packaged runtime can start successfully on this machine through the custom launcher, even though the source project is not yet modernized.

## Baseline Conclusion

The packaged app is the current source of truth for runtime behavior.

This baseline should be preserved for comparison during later phases, especially:

- startup flags
- native DLL extraction
- JavaFX compatibility assumptions
- presence of resource folders
- logging behavior after launch

## Handoff

- Task: SO-00 Baseline
- Branch:
- Status: done
- Summary: Imported the legacy source snapshot into the repository and documented the currently working packaged runtime used as the behavior reference.
- Validation: Used the user-confirmed working launcher, inspected the shipped manifest, and captured startup evidence from the packaged app log dated 2026-03-12.
- Blockers: None for documentation; runtime comparison remains dependent on preserving the packaged app folder as the reference.
- Next recommended task: SO-01 Legacy Build Lane
