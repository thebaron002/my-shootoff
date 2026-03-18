# ShootOFF Execution Checklist

This checklist tracks the execution of phases `SO-00` through `SO-08`.

Legend:

- `[x]` complete
- `[-]` in progress
- `[ ]` pending
- `[!]` blocked

## SO-00 Baseline

Status: `[x]`

- [x] Bring the legacy source snapshot into this repository for tracking
- [x] Record the packaged runtime folder used today
- [x] Capture the current launcher behavior from `Run-ShootOFF.bat`
- [x] Capture the shipped manifest metadata from `ShootOFF.jar`
- [x] Record current runtime evidence from `server.log`
- [x] Document baseline findings in `docs/SO-00_BASELINE.md`

## SO-01 Legacy Build Lane

Status: `[x]`

- [x] Confirm the current machine Java version
- [x] Confirm Gradle is not installed globally
- [x] Confirm there is no `gradlew.bat` in the imported source
- [x] Identify build script patterns tied to Java 8 / old Gradle
- [x] Identify internal Java / JavaFX APIs that block modern compilation
- [x] Reproduce a clean source build path on the current machine
- [x] Compare a source-built artifact with the currently working packaged app
- [x] Document findings and blockers in `docs/SO-01_LEGACY_BUILD_LANE.md`

## SO-02 Build Modernization

Status: `[x]`

- [x] Add `gradlew` and `gradlew.bat`
- [x] Add `settings.gradle`
- [x] Validate the wrapper against the imported build
- [x] Replace `jcenter` / Bintray-era repositories
- [x] Migrate deprecated `compile` / `testCompile` configurations
- [x] Add a temporary bridge to the packaged runtime libs for missing legacy artifacts
- [x] Internalize the runtime dependency set into `vendor/`
- [x] Validate `build` and `test` on the current machine
- [x] Split development build flow from packaging-specific legacy tasks
- [x] Document SO-02 findings and handoff

## SO-03 JavaFX/API Cleanup

Status: `[x]`

- [x] Remove `HostServicesFactory` usage
- [x] Remove `HostServicesDelegate` usage
- [x] Remove or isolate `javawebstart.version` runtime logic
- [x] Replace `sun.awt.image.IntegerComponentRaster` usage
- [x] Define supported launch flags for the target modern JDK
- [x] Document SO-03 findings and handoff

## SO-04 Dependency Strategy

Status: `[x]`

- [x] Audit OpenCV dependency and native loading path
- [x] Audit Xuggle viability
- [x] Audit MaryTTS viability
- [x] Audit BlueCove viability
- [x] Lock a reproducible dependency set
- [x] Document SO-04 findings and handoff

## SO-05 Native and Camera Validation

Status: `[x]`

- [x] Validate OpenCV native loading on Windows
- [x] Validate webcam enumeration
- [x] Record PS3 Eye validation as hardware-dependent
- [x] Capture camera-related runtime issues and mitigations
- [x] Document SO-05 findings and handoff

## SO-06 Packaging and Launcher

Status: `[x]`

- [x] Decide packaging strategy
- [x] Produce a supported Windows launcher
- [x] Ensure resource folders ship correctly
- [x] Validate a fresh packaged output on Windows
- [x] Document SO-06 findings and handoff

## SO-07 CI and Regression Gates

Status: `[x]`

- [x] Add build verification workflow
- [x] Add test execution workflow
- [x] Add packaging validation workflow
- [x] Add a manual regression checklist for runtime features
- [x] Document SO-07 findings and handoff

## SO-08 Release Candidate

Status: `[x]`

- [x] Compare modern build against the current packaged app
- [x] Capture accepted regressions
- [x] Produce cutover checklist
- [x] Produce release readiness report
- [x] Document SO-08 findings and handoff
