# ShootOFF Modernization Plan

This plan translates the modernization effort into staged tasks with dependencies, outputs, and validation gates.

## Execution Status

- Checklist tracker: `docs/EXECUTION_CHECKLIST.md`
- Baseline notes: `docs/SO-00_BASELINE.md`
- Legacy build lane notes: `docs/SO-01_LEGACY_BUILD_LANE.md`
- Build modernization notes: `docs/SO-02_BUILD_MODERNIZATION.md`
- API cleanup notes: `docs/SO-03_JAVAFX_API_CLEANUP.md`
- Dependency strategy notes: `docs/SO-04_DEPENDENCY_STRATEGY.md`
- Native validation notes: `docs/SO-05_NATIVE_CAMERA_VALIDATION.md`
- Packaging notes: `docs/SO-06_PACKAGING_AND_LAUNCHER.md`
- CI notes: `docs/SO-07_CI_AND_REGRESSION_GATES.md`
- Release notes: `docs/SO-08_RELEASE_CANDIDATE.md`

## Scope

Target outcome:

- Build the legacy ShootOFF source on a modern machine
- Remove dependence on the old handcrafted runtime folder
- Preserve the current runnable behavior wherever practical
- Document known regressions and tradeoffs

Non-goals for the first pass:

- Redesigning the user interface
- Rewriting camera features from scratch
- Adding new product features before the build is stable

## Phase Overview

| ID | Task | Purpose | Depends On |
| --- | --- | --- | --- |
| SO-00 | Baseline | Capture current runtime truth | None |
| SO-01 | Legacy Build Lane | Reproduce source build with minimum change | SO-00 |
| SO-02 | Build Modernization | Replace fragile build conventions | SO-01 |
| SO-03 | JavaFX/API Cleanup | Remove removed/internal Java APIs | SO-02 |
| SO-04 | Dependency Strategy | Stabilize external libraries | SO-02 |
| SO-05 | Native and Camera Validation | Verify runtime hardware behavior | SO-03, SO-04 |
| SO-06 | Packaging and Launcher | Produce a maintainable runnable package | SO-05 |
| SO-07 | CI and Regression Gates | Automate confidence checks | SO-06 |
| SO-08 | Release Candidate | Compare modern build against current runnable app | SO-07 |

## Task Breakdown

### SO-00 Baseline

Purpose:

- Record exactly how the current packaged app behaves today.

Actions:

- Inventory the contents of `C:\Users\henri\Downloads\shootoff-4.0-final`
- Document what `Run-ShootOFF.bat` configures
- Capture startup logs, native DLL expectations, and resource folders
- Record current Java runtime assumptions

Deliverables:

- Baseline runtime notes
- Launch checklist
- Known runtime dependencies list

Validation gate:

- The packaged app launches successfully through the existing `.bat`
- Required runtime files are identified

### SO-01 Legacy Build Lane

Purpose:

- Make the old source build reproducible before larger refactors.

Actions:

- Identify the minimum compatible JDK and Gradle setup
- Add local build instructions
- Verify whether the project can still compile with a legacy toolchain
- Compare generated artifacts with the existing packaged app

Deliverables:

- Reproducible legacy build instructions
- Build output notes
- Gap list between source build and shipped folder

Validation gate:

- A clean checkout can compile using the documented legacy toolchain

### SO-02 Build Modernization

Purpose:

- Remove obsolete build infrastructure and make local builds predictable.

Actions:

- Add `gradlew` / `gradlew.bat`
- Replace `jcenter` and Bintray-era repository usage
- Migrate `compile` and `testCompile` configurations
- Separate build, runtime, and packaging concerns
- Pin versions where floating versions are risky

Deliverables:

- Updated Gradle build
- Repeatable local build commands
- Dependency resolution notes

Validation gate:

- Modernized build resolves dependencies and compiles from a clean checkout

### SO-03 JavaFX/API Cleanup

Purpose:

- Remove code paths that rely on removed Java Web Start or internal JDK APIs.

Actions:

- Replace `HostServicesFactory` and `HostServicesDelegate` usage
- Remove or isolate `javawebstart.version` flows
- Replace `sun.awt.image.IntegerComponentRaster` usage with supported APIs
- Review module-path and runtime launch flags needed for current Java

Deliverables:

- Modern JavaFX-compatible code paths
- Notes on removed legacy features

Validation gate:

- Application compiles and launches on the target modern JDK

### SO-04 Dependency Strategy

Purpose:

- Decide which dependencies stay, move, or need replacement.

Actions:

- Audit OpenCV, Xuggle, MaryTTS, BlueCove, webcam-capture, and logging
- Check which libraries are still resolvable and which are effectively dead
- Prefer minimal change first, then replace only proven blockers
- Lock chosen versions

Deliverables:

- Dependency decision record
- Replacement plan for unsupported libraries

Validation gate:

- Build uses a documented and reproducible dependency set

### SO-05 Native and Camera Validation

Purpose:

- Prove that the modernized app still interacts correctly with Windows native dependencies and webcams.

Actions:

- Validate OpenCV native extraction/loading
- Test webcam discovery
- Test PS3 Eye and fallback camera paths if hardware is available
- Confirm startup does not depend on manual folder surgery

Deliverables:

- Native runtime checklist
- Camera compatibility notes

Validation gate:

- Core camera startup path works on the target Windows machine

### SO-06 Packaging and Launcher

Purpose:

- Replace the fragile manually maintained runtime folder with a maintainable package.

Actions:

- Decide between `jar + libs + launcher` and `jpackage`
- Build a supported Windows launcher
- Ensure resources such as `targets`, `sounds`, `courses`, and `exercises` are included
- Keep local debug launching simple

Deliverables:

- New runnable package
- Launcher documentation

Validation gate:

- Fresh packaged output launches successfully on Windows

### SO-07 CI and Regression Gates

Purpose:

- Add automation so modernization does not drift or silently break.

Actions:

- Add compile and test workflow
- Add smoke startup validation where feasible
- Add artifact packaging validation
- Add a regression checklist for manual camera features

Deliverables:

- CI workflow
- Regression checklist

Validation gate:

- CI passes from a clean checkout and produces expected artifacts

### SO-08 Release Candidate

Purpose:

- Compare the new build with the currently working packaged app before cutover.

Actions:

- Run side-by-side launch checks
- Compare runtime resources and logs
- Verify core user paths
- Document accepted regressions if any remain

Deliverables:

- Release readiness report
- Cutover checklist

Validation gate:

- Release checklist is complete and accepted

## Risks

- JavaFX migration may surface more API incompatibilities than the first compile check showed.
- Some old repositories or artifacts may no longer exist.
- Xuggle and camera-related native integrations may become the biggest runtime blocker.
- The current packaged app may contain manual fixes that are not represented in source control.

## Recommended Execution Strategy

1. Preserve the currently working packaged app as the runtime reference.
2. Restore a reproducible source build with the least change possible.
3. Modernize build and Java APIs before changing runtime behavior.
4. Tackle native and camera validation only after the app can compile and launch cleanly.
5. Package last, after runtime behavior is stable.

## First Practical Milestone

The first meaningful milestone is:

- Compile the source project from a clean checkout
- Launch the app from a source-built artifact
- Document every gap relative to the packaged app that currently works
