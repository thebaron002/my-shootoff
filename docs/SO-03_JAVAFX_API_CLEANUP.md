# SO-03 JavaFX/API Cleanup

## Goal

Remove the highest-risk internal Java and JavaFX dependencies from the runtime path used by the modern build.

## Completed Changes

- Replaced internal JavaFX host services usage in `Main` with `Application#getHostServices()`
- Isolated the legacy Java Web Start branch behind the opt-in flag:
  - `-Dshootoff.enableLegacyJws=true`
- Reworked `com.shootoff.util.SwingFXUtils` to use supported AWT raster APIs instead of `sun.awt.image.IntegerComponentRaster`
- Simplified compile and test JVM arguments so they no longer depend on those internal packages

## Validation

- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon build`

Both commands completed successfully after the cleanup.

## Remaining Risk

- The legacy JWS code still exists, but it is now isolated and no longer part of the default runtime path.
- The codebase still contains some deprecated Java APIs, but not the internal desktop APIs that previously required export hacks.

## Handoff

- Task: SO-03 JavaFX/API Cleanup
- Branch:
- Status: done
- Summary: Removed the main internal Java/JavaFX API usages from the modern runtime path and isolated Java Web Start behind an opt-in system property.
- Validation: Successful `test` and `build` runs after replacing internal host services and raster access.
- Blockers: None for the default modern runtime; legacy JWS remains legacy-only by design.
- Next recommended task: SO-04 Dependency Strategy
