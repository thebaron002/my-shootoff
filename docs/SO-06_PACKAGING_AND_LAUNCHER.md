# SO-06 Packaging and Launcher

## Goal

Produce a maintainable package and launcher path for the modernized source build without relying on the original handcrafted release directory.

## Completed Changes

- Added a modern staged runtime task:
  - `stageModernRuntime`
- Added a modern zip packaging task:
  - `modernDistZip`
- Added a modern Windows app-image task:
  - `modernAppImage`
- Added a modern Windows MSI task:
  - `modernMsi`
- Added a root launcher for the source repository:
  - `Run-ShootOFF-Source.bat`
- Added a staged runtime launcher:
  - `build/modern-runtime/Run-ShootOFF.bat`

## Packaging Layout

The staged runtime includes:

- `ShootOFF.jar`
- `libs`
- `native/opencv`
- `courses`
- `sounds`
- `targets`
- `shootoff.properties`
- `eyeCam32.dll`
- `eyeCam64.dll`
- `LICENSE`

## Validation

- `.\gradlew.bat --no-daemon stageModernRuntime`
- `.\gradlew.bat --no-daemon modernDistZip`
- `.\gradlew.bat --no-daemon modernAppImage`
- `.\gradlew.bat --no-daemon modernMsi`
- Smoke run of `build/modern-runtime/Run-ShootOFF.bat`
- Smoke run of `build/jpackage/app-image/ShootOFF/ShootOFF.exe`
- If restaging fails on Windows, close any already-running ShootOFF instance and rerun the staging command
- If WiX is not installed globally, the build can use a portable WiX 3 layout in `.tools/wix3/extract`

Created artifact:

- `build/dist/shootoff-4.0-modern.zip`
- `build/installer/ShootOFF-4.0.0.msi`

## Handoff

- Task: SO-06 Packaging and Launcher
- Branch:
- Status: done
- Summary: Added a modern staged runtime, app-image, zip, and MSI packaging flow, plus launchers for running the app from the source repository and from packaged output.
- Validation: Successfully staged the runtime, created the zip artifact and MSI installer, and smoke-tested both the staged launcher and the jpackage app-image launcher.
- Blockers: Legacy JavaFX Ant packaging tasks remain intentionally isolated and are not part of the modern default package flow.
- Next recommended task: SO-07 CI and Regression Gates
