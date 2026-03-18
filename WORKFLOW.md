# ShootOFF Modernization Workflow

This repository tracks the modernization of the legacy ShootOFF 4.0 codebase.

The workflow is inspired by [openai/symphony](https://github.com/openai/symphony):

- Keep the workflow repo-owned and explicit.
- Break work into small, reviewable tasks.
- Give each task a clear owner, scope, dependencies, and validation gate.
- Require a handoff note before moving to the next phase.

## Goal

Modernize the ShootOFF source project so it can be built, run, tested, and packaged reliably on a current Windows toolchain without depending on the old prebuilt release folder.

## Current Context

- Working app today: `C:\Users\henri\Downloads\shootoff-4.0-final`
- Legacy source snapshot: `C:\Users\henri\Downloads\ShootOFF-4.0-FINAL (1)\ShootOFF-4.0-FINAL`
- Known blockers:
  - Legacy Gradle conventions and repositories
  - Java 8 / JavaFX-era packaging
  - Use of removed or internal Java APIs
  - Native camera and OpenCV dependencies

## Delivery Model

Each work item should follow this lifecycle:

1. Capture intent and acceptance criteria.
2. Create an isolated branch using the `codex/` prefix.
3. Implement the smallest viable change for that task.
4. Run the task validation gate.
5. Record results and blockers in a handoff note.
6. Merge only after the gate passes or the failure is explicitly accepted.

## Branch Naming

Use:

- `codex/so-00-baseline`
- `codex/so-01-legacy-build-lane`
- `codex/so-02-build-modernization`
- `codex/so-03-javafx-api-cleanup`
- `codex/so-04-dependency-strategy`
- `codex/so-05-native-camera-validation`
- `codex/so-06-packaging-launcher`
- `codex/so-07-ci-regression-gates`
- `codex/so-08-release-candidate`

## Definition Of Done

A task is done when:

- The scoped code or docs changes are committed in an isolated branch.
- The validation gate for that task has been run.
- The result is documented in the handoff section for that task.
- Risks and follow-up work are called out clearly.

## Validation Gates

Minimum validation by phase:

- Baseline:
  - Existing packaged app launches from the current `.bat`
  - Log startup behavior and required runtime files
- Build:
  - Source compiles from a clean checkout
  - Build steps are documented and repeatable
- Runtime:
  - App starts from source-built artifacts
  - Core UI opens without fatal errors
- Native:
  - OpenCV native loading works
  - Webcam enumeration works on Windows
- Packaging:
  - A new runnable package can be produced
  - Launcher works without manual patching
- Release:
  - Regression checklist is complete
  - Open issues are explicitly accepted or fixed

## Handoff Template

Use this template at the end of each task:

```md
## Handoff

- Task: SO-XX Name
- Branch:
- Status: done | blocked | partial
- Summary:
- Validation:
- Blockers:
- Next recommended task:
```

## Task Order

Execute tasks in this order unless a blocker forces a change:

1. SO-00 Baseline
2. SO-01 Legacy Build Lane
3. SO-02 Build Modernization
4. SO-03 JavaFX/API Cleanup
5. SO-04 Dependency Strategy
6. SO-05 Native and Camera Validation
7. SO-06 Packaging and Launcher
8. SO-07 CI and Regression Gates
9. SO-08 Release Candidate

## Tracking

The canonical task breakdown lives in `docs/MODERNIZATION_PLAN.md`.
