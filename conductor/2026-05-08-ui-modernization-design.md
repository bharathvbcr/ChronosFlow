# Spec: UI Modernization - Soft Focus Adaptive Dynamic Glass

**Date:** 2026-05-08
**Status:** Draft
**Topic:** UI/UX Modernization for ChronosFlow

## 1. Background & Motivation
ChronosFlow is currently using a baseline Material 3 implementation. To elevate the user experience to a premium "daily operating system" feel, we are transitioning to a **Soft Focus Adaptive Dynamic Glass** aesthetic. This design prioritizes calm, readability, and system integration via Material You.

## 2. Design Principles
- **Clarity over Clutter:** Use translucency to show hierarchy without adding visual noise.
- **Dynamic Integration:** Glass tints must derive from the user's Material You palette.
- **Calm Interaction:** High-fidelity blur (20-30dp) and soft edge-to-edge layouts.
- **Native Familiarity:** Maintain native Android 16/17 picker behaviors but wrap them in the glass aesthetic.

## 3. Visual Language

### 3.1 Surfaces & Glassmorphism
- **Standard Glass Container:** 
  - Fill: `MaterialTheme.colorScheme.surface` at 85% opacity.
  - Blur: 25dp Gaussian blur (using `Modifier.blur` or custom RenderEffect for pre-S).
  - Border: 1dp stroke, vertical gradient from `surfaceVariant` (top) to `transparent` (bottom).
  - Corner Radius: 28dp (Standard) / 32dp (Large).

### 3.2 Background Strategy
- **Ambient Blur Backdrop:** 
  - Two large, slow-drifting blobs using `primary` and `secondary` container colors.
  - Opacity: 15% (Light Mode) / 25% (Dark Mode).
  - Blur: 60dp+.

### 3.3 Typography & Accents
- **Material 3 Expressive:** Use heavier weights for headlines to cut through the glass translucency.
- **Glow Effects:** The "Now" indicator on the Chronos Dial will feature a subtle 8dp outer glow using the `primary` color.

## 4. Key Component Redesigns

### 4.1 The Chronos Dial
- **Disk:** A large frosted glass disk.
- **Segments:** Time blocks as semi-transparent "liquid pills" with inner glow when active.
- **Animations:** Smooth transitions when dragging or resizing blocks, with a "ripple" effect on the glass surface.

### 4.2 Navigation (Bottom Bar & Sidebar)
- **Floating Glass Nav:** The bottom navigation will be a floating glass pod rather than a docked bar.
- **Sidebar:** Full-height glass sheet with 90% opacity to maintain context of the underlying plan.

### 4.3 Pickers & Sheets
- **Native Integration:** Use `DatePickerDialog` and `TimePickerDialog` but apply a custom theme overlay that uses a glass-tinted background.
- **Bottom Sheets:** All sheets (Block Editor, AI Review) will use the `GlassSurface` with a prominent "grabber" handle.

## 5. Technical Implementation Strategy
- **`ChronosTheme` Refactor:** Centralize color schemes and shape definitions in `core:ui`.
- **`LiquidGlass` Modifier:** Create a reusable `Modifier` that applies the specific blur, tint, and border logic.
- **Theme Toggle:** Ensure seamless transition between Light, Dark, and Dynamic (Material You) modes.

## 6. Success Criteria
- The app feels unified across all modules (DayDial, Focus, Tasks).
- Navigation is fluid and "floats" above the ambient background.
- Text remains highly readable (WCAG 2.1 compliant) on all glass surfaces.
