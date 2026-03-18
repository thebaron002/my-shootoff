# SO-01 Legacy Build Lane

## Goal

Recreate a reliable source build path before larger refactors change behavior.

## Current Repository State

The legacy source snapshot is now inside this repository and includes:

- `build.gradle`
- `src`
- `targets`
- `sounds`
- `courses`
- `shootoff.properties`
- native DLLs and project metadata files

Observed source size:

- `194` Java files in `src\main\java`

## Current Machine Toolchain

Observed on 2026-03-12:

- Default Java:
  - `OpenJDK 25.0.2`
- Additional installed JDKs found:
  - `C:\Program Files\Java\jdk-11.0.7`
  - `C:\Program Files\BellSoft\LibericaJDK-25-Full`
- Not found:
  - global `gradle`
  - `gradlew.bat`
  - an installed JDK 8 in common Windows locations

## Immediate Build Blockers

### 1. No reproducible build entry point

- There is no `gradlew.bat` in the repository.
- `gradle` is not installed globally on the current machine.

Result:

- The project cannot currently be built from a clean checkout in a repeatable way.

### 2. Build script is tied to legacy infrastructure

Observed in `build.gradle`:

- `jcenter()` repository usage
- Bintray-era repositories
- `ant-javafx.jar` packaging path
- deprecated `compile` and `testCompile` dependency configurations
- multiple floating dependency ranges such as `1.+`, `3.+`, and `0.3.+`

Result:

- Even with Gradle installed, dependency resolution and packaging would be fragile.

### 3. Code depends on internal or removed Java APIs

Observed in `src\main\java\com\shootoff\Main.java`:

- `com.sun.deploy.uitoolkit.impl.fx.HostServicesFactory`
- `com.sun.javafx.application.HostServicesDelegate`
- `javawebstart.version` runtime branch

Observed in `src\main\java\com\shootoff\util\SwingFXUtils.java`:

- `sun.awt.image.IntegerComponentRaster`

Result:

- The source cannot move directly to a modern JDK without code changes.

### 4. Runtime compatibility already depends on modern-JDK workaround flags

Observed in the working packaged launcher:

- `--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED`
- native-access flags for current Java

Result:

- The runtime works today by compensating for legacy internals rather than by using a modernized source base.

## Phase Status

Status: in progress

Current state:

- A reproducible source build path now exists on the current machine through the new Gradle wrapper and a temporary bridge to the packaged runtime libs.
- The repository still does not have a self-contained legacy JDK 8 build lane.

## Recommended Next Moves

1. Add a Gradle wrapper to the repository so builds can be invoked consistently.
2. Preserve the packaged app as the runtime reference while modernizing the build.
3. Modernize the build before attempting broad source refactors.
4. Remove internal Java API usage in parallel with JavaFX modernization.

## Exit Criteria For SO-01

This phase can be closed when:

- the repository has a reproducible build entry point
- the intended build toolchain is documented
- a clean checkout can compile the source or the exact blocker is reduced to a single tracked issue

## Handoff

- Task: SO-01 Legacy Build Lane
- Branch:
- Status: partial
- Summary: Imported the source into the repository, added a reproducible Gradle entry point, and proved that the project can compile and test on the current machine by bridging to the packaged runtime libs.
- Validation: Verified Java availability, confirmed there was no global Gradle, added a wrapper, and ran successful `build` and `test` commands from the repository.
- Blockers: The build is not yet self-contained because it still depends on the packaged runtime libs folder and legacy JavaFX packaging tasks remain unresolved.
- Next recommended task: SO-02 Build Modernization
