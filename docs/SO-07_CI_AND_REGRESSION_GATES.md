# SO-07 CI And Regression Gates

## Goal

Automate the modernized build path so regressions are caught without relying on manual setup.

## Completed Changes

- Added GitHub Actions workflow:
  - `.github/workflows/ci.yml`
- The workflow:
  - checks out the repository
  - sets up Liberica JDK with JavaFX
  - validates the Gradle wrapper
  - runs `test`
  - runs `build`
  - stages the modern runtime
  - generates the modern distribution zip
  - uploads the zip as a CI artifact

## Manual Regression Gate Retained

The following manual checks are still important:

- staged runtime launcher starts
- native OpenCV path works on Windows
- webcam enumeration works on the target machine
- packaged resources are present in the staged runtime

## Handoff

- Task: SO-07 CI and Regression Gates
- Branch:
- Status: done
- Summary: Added a Windows CI workflow that validates the modern build, tests, and packaging path with a JavaFX-enabled JDK.
- Validation: Workflow file added and aligned with the current successful local commands.
- Blockers: CI does not replace real-hardware validation for every supported camera device.
- Next recommended task: SO-08 Release Candidate
