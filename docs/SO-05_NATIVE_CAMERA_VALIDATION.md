# SO-05 Native and Camera Validation

## Goal

Validate that the modernized repository can still load native dependencies and exercise the main camera-related paths on the current Windows machine.

## Validation Performed

### OpenCV native loading

- The Gradle test task was configured with the vendored OpenCV native directory
- OpenCV-dependent tests passed after the runtime path was aligned with the application launcher

### Webcam enumeration

- A local probe against `CameraFactory.getWebcams()` was run from the modernized build
- Result on 2026-03-13:
  - `1` webcam enumerated on the current machine

### Smoke run of the staged runtime

- The staged launcher in `build/modern-runtime/Run-ShootOFF.bat` was started successfully
- The Java process remained running long enough to confirm startup
- `log/server.log` in the staged runtime showed MaryTTS startup completion

## Current Limits

- PS3 Eye-specific validation was not performed with real hardware attached
- Validation was performed on the current Windows machine only

## Handoff

- Task: SO-05 Native and Camera Validation
- Branch:
- Status: done
- Summary: Confirmed OpenCV native loading, webcam enumeration, and staged-runtime startup on the current Windows machine.
- Validation: Successful `test`, successful staged launcher smoke run, and a direct webcam enumeration probe returning `1`.
- Blockers: Device-specific validation for PS3 Eye hardware remains hardware-dependent.
- Next recommended task: SO-06 Packaging and Launcher
