# Google Stitch UI Brief

## Project

Design the UI for a Windows desktop application called `ShootOFF 5.0`.

This is an `Army-first laser dry fire training app`.

The app helps a shooter:

1. Set up projection width and shooter distance
2. Calibrate the arena with one camera
3. Edit an Army qualification stage
4. Run the `Army Table VI Qualification`
5. Review score and result

This is not a generic shooting app homepage.
It should feel like a focused training workstation for a real qualification workflow.

## Product Context

- Platform: `Windows desktop`
- Input context: `1 local camera`
- Main use case: `Army Table VI Qualification`
- Priority: `clarity, confidence, training focus, clean workflow`
- Tone: `professional, tactical, modern, calm, serious`

Avoid making it feel like:

- a gaming launcher
- a mobile app
- a generic admin dashboard
- an old Java desktop app

## UX Goal

The user should feel:

- guided
- in control
- confident about calibration and setup
- able to move step by step without confusion

The UI should reduce complexity.
The old app had too many unrelated options.
This new version should feel like a dedicated Army trainer.

## Primary Navigation

Design the app around these 6 steps:

1. `Home Army`
2. `Setup`
3. `Calibrate`
4. `Stage Editor`
5. `Train`
6. `Results`

Use a left sidebar or another strong workflow navigation pattern.
The current step should always be obvious.

## Required Screens

Create mockups for these screens.

### 1. Home Army

Purpose:

- explain what the app does
- give a clear start point
- show the recommended workflow

Must include:

- app title: `ShootOFF 5.0`
- subtitle focused on Army qualification
- primary CTA: `Start Setup`
- secondary CTA: `Open Stage Editor`
- small workflow summary
- clean product overview

### 2. Setup

Purpose:

- configure core session parameters before calibration or training

Must include:

- projection width
- shooter distance
- units
- camera status
- readiness state
- quick access to continue to calibration

The layout should feel like a real setup workstation, not a form-only screen.

### 3. Calibrate

Purpose:

- guide the user through arena calibration

Must include:

- live preview area placeholder
- camera feed / arena preview concept
- calibration mode options:
  - `Auto Green`
  - `Manual`
- clear instructions
- visible current status
- recovery/error message space

This screen should feel reassuring and operational.

### 4. Stage Editor

Purpose:

- let the user configure the Army qualification stage visually

Must include:

- large scenario preview
- editable 50 m reference line
- target placement workflow
- target types:
  - `E-1`
  - `F-target`
- zoom controls
- engagement list
- target properties panel
- save action

Important:

- this screen is one of the most important in the whole app
- it should feel precise, visual, and practical
- think of a hybrid between a tactical editor and a projection layout tool

### 5. Train

Purpose:

- run the qualification with minimum distraction

Must include:

- arena/camera preview area
- session controls
- qualification status
- shot timeline or session events
- prominent `Start Qualification`
- clean visibility of current phase

This screen should feel live and mission-critical.

### 6. Results

Purpose:

- show the outcome after a qualification run

Must include:

- final score
- rating:
  - `Expert`
  - `Sharpshooter`
  - `Marksman`
  - `Unqualified`
- quick summary
- CTA to `Run Again`
- CTA to `Adjust Stage`

This screen should feel conclusive and satisfying, but still professional.

## Visual Direction

Use a strong desktop application aesthetic.

### Style

- dark tactical interface
- modern control-room feel
- polished but not flashy
- high contrast
- quiet confidence

### Color direction

Use colors similar to:

- deep navy
- graphite
- muted steel blue
- controlled electric blue for highlights
- white and soft gray for readable text

Avoid:

- purple-heavy palettes
- neon gamer styles
- bright military camouflage motifs
- generic SaaS white cards everywhere

### Typography

Use typography with authority and clarity.
It should feel operational and premium, not playful.

### Mood references

Think:

- modern mission planning software
- premium desktop calibration tools
- clean tactical range control UI

Not:

- arcade shooter
- old enterprise admin tool
- Bootstrap dashboard

## Layout Guidance

- desktop-first
- wide layout
- strong hierarchy
- large central workspace
- persistent navigation
- clear state panels
- no clutter

Important:

- the `Stage Editor` and `Train` screens should prioritize the central visual area
- forms and controls should support the visual workspace, not dominate it

## Components To Explore

Use concepts like:

- sidebar step navigation
- large status cards
- workspace header
- operational info panels
- sticky action bars
- segmented controls
- calibration state chips
- result summary panels

## Brand Words

Use this brand language:

- precise
- focused
- tactical
- modern
- calm
- competent

## Assets And Context

The app already has:

- Army range background image
- Army target images
- calibration concepts
- existing icons and imagery from the current codebase

You can reference those as inspiration, but the new UI should feel intentionally redesigned.

## Output Requested From Google Stitch

Generate:

1. a cohesive desktop app design system direction
2. the 6 main screens listed above
3. one consistent visual language across all screens
4. a polished Windows desktop app feel

If possible, make the mockups feel implementation-ready for a JavaFX desktop app.

## Important Constraints

- This is a desktop app, not a responsive marketing site
- Keep the experience focused on `Army Table VI`
- Do not add unnecessary features unrelated to the Army MVP
- The UI must look significantly better than the legacy ShootOFF interface
- The user should always know what step they are in and what to do next

## Short Prompt Version

Design a modern Windows desktop UI for `ShootOFF 5.0`, an Army-first laser dry fire training app focused on `Army Table VI Qualification`. Create a guided workflow with 6 screens: `Home Army`, `Setup`, `Calibrate`, `Stage Editor`, `Train`, and `Results`. The UI should feel like a premium tactical training workstation: dark, precise, calm, high-contrast, and professional. Use a strong sidebar workflow, a large central workspace, clean operational panels, clear status indicators, and a polished stage editor with target placement, zoom, and a 50 m reference line. Avoid generic dashboards, gaming launcher vibes, and old desktop UI patterns.
