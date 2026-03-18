# SO-04 Dependency Strategy

## Goal

Make the dependency set reproducible on the current machine even though several legacy artifacts are no longer conveniently resolvable from modern repositories.

## Completed Changes

- Captured the exact runtime jar set from the working packaged app into:
  - `vendor/runtime-libs`
- Captured the OpenCV native runtime into:
  - `vendor/native/opencv`
- Updated the build to prefer vendored runtime assets before any external fallback
- Kept legacy library versions aligned with the currently working packaged application

## Dependency Posture

The current strategy is:

- Prefer exact, known-good binaries from the working ShootOFF package
- Treat those binaries as the compatibility baseline
- Defer library replacement projects until after the app is buildable and runnable end to end

This is intentionally conservative. It optimizes for reproducibility first, modernization second.

## Known Legacy Libraries Still Present

- Xuggle
- MaryTTS 5.1.2 family
- BlueCove
- OpenCV 2.4.13-0
- Older webcam-capture stack

These are still legacy dependencies, but they are now pinned and reproducible inside the repository.

## Validation

- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon build`

## Handoff

- Task: SO-04 Dependency Strategy
- Branch:
- Status: done
- Summary: Locked the runtime dependency set by vendoring the jar and native assets from the known-good packaged build.
- Validation: The repository builds and tests against the vendored dependency set.
- Blockers: Long-term modernization of legacy libraries remains future work, but it is no longer blocking build reproducibility.
- Next recommended task: SO-05 Native and Camera Validation
