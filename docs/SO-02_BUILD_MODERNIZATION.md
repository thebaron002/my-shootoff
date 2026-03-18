# SO-02 Build Modernization

## Goal

Create a reproducible modern build entry point and remove the first layer of obsolete Gradle infrastructure.

## Progress So Far

Completed in this phase:

- Added `settings.gradle`
- Added `gradlew`
- Added `gradlew.bat`
- Added `gradle\wrapper\gradle-wrapper.jar`
- Added `gradle\wrapper\gradle-wrapper.properties`
- Replaced `jcenter()` and Bintray-era repository usage
- Migrated `compile` / `testCompile` to modern configurations
- Added a temporary dependency bridge to the packaged runtime libs
- Added runtime flags and native library setup for tests on the current machine

Validation performed:

- `.\gradlew.bat -v` succeeded
- `.\gradlew.bat --no-daemon tasks` succeeded
- `.\gradlew.bat --no-daemon test` succeeded
- `.\gradlew.bat --no-daemon build` succeeded

Result:

- The repository now has a reproducible Gradle entry point instead of depending on a global Gradle installation.

## First Build Failure With Wrapper

Command:

- `.\gradlew.bat --no-daemon tasks`

Observed failure:

- Build failed while evaluating `build.gradle`
- First error:
  - `Could not find method jcenter() for arguments [] on repository container`

Implication:

- The build became executable through the wrapper, and repository migration was confirmed as the next required step.

## Current Outcome

The repository now builds successfully on the current machine.

Important caveat:

- This success currently depends on the packaged runtime libs in `C:\Users\henri\Downloads\shootoff-4.0-final\libs`
- JavaFX Ant packaging tasks still warn because `ant-javafx.jar` is not present in the modern JDK
- Packaging modernization is still pending

## Current SO-02 Priorities

1. Replace `jcenter()` with supported repositories.
2. Remove Bintray-era repository references.
3. Update old dependency configurations such as `compile` and `testCompile`.
4. Reduce floating dependency ranges to reproducible versions.

## Phase Status

Status: in progress

## Handoff

- Task: SO-02 Build Modernization
- Branch:
- Status: partial
- Summary: Modernized the Gradle entry point enough for the project to load, compile, test, and build on the current machine. The build now uses a temporary bridge to the packaged runtime libs while the old JavaFX packaging layer remains unresolved.
- Validation: `.\gradlew.bat -v`, `.\gradlew.bat --no-daemon tasks`, `.\gradlew.bat --no-daemon test`, and `.\gradlew.bat --no-daemon build` all completed successfully.
- Blockers: Legacy packaging tasks still depend on JavaFX Ant tooling that is not available in the modern JDK, and the build still relies on the packaged runtime libs folder outside the repo.
- Next recommended task: Separate normal build/test from legacy packaging tasks and start replacing internal Java APIs in SO-03
