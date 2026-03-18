# SO-08 Release Candidate

## Goal

Compare the modernized output against the currently working packaged app and capture what is ready for cutover.

## Comparison Summary

Reference packaged app:

- `C:\Users\henri\Downloads\shootoff-4.0-final`

Modern staged runtime:

- `build/modern-runtime`

Observed comparison:

- Library count:
  - packaged app: `76`
  - modern staged runtime: `76`
- Library name diff:
  - no differences detected
- Main jar size:
  - packaged app: `9868678`
  - modern staged runtime: `9868702`
- Modern zip artifact produced:
  - `build/dist/shootoff-4.0-modern.zip`
- Modern MSI artifact produced:
  - `build/installer/ShootOFF-4.0.0.msi`

## Runtime Readiness

- The modern staged runtime starts on the current machine
- The modern jpackage app-image starts on the current machine
- The source repository passes `test` and `build`
- The staged launcher starts successfully and produces expected startup log activity

## Accepted Differences

- The modern build uses a new staging and zip flow instead of the old JavaFX Ant packaging path
- Legacy Java Web Start remains disabled by default
- Some legacy dependencies are still old, but they are now vendored and reproducible

## Cutover Checklist

- Use `Run-ShootOFF-Source.bat` when running from the repository
- Use `.\gradlew.bat --no-daemon build` before staging a release
- Use `.\gradlew.bat --no-daemon stageModernRuntime modernDistZip` to prepare the runnable package
- Use `.\gradlew.bat --no-daemon modernMsi` to generate the Windows installer
- Close any existing ShootOFF process before restaging so Windows can release the staged jars
- Validate the staged launcher on the target Windows machine
- Validate the generated MSI on the target Windows machine
- If PS3 Eye support is required, perform one hardware-specific validation pass before shipping

## Release Recommendation

Technical status:

- ready for a controlled cutover on the current Windows machine

Operational caveat:

- if your release depends on PS3 Eye or other specific camera hardware, run one final hardware-specific acceptance pass first

## Handoff

- Task: SO-08 Release Candidate
- Branch:
- Status: done
- Summary: Compared the staged modern runtime against the known-good packaged app, confirmed matching library inventory, successful startup, and generated both a shippable modern zip and MSI installer.
- Validation: Successful build, successful tests, successful staged smoke run, successful jpackage app-image smoke run, matching runtime lib set, and successful modern zip/MSI generation.
- Blockers: Hardware-specific acceptance remains advisable if shipping to environments that depend on specific camera models.
- Next recommended task: Optional post-S08 cleanup or dependency replacement work
