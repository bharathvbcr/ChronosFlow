# UI Modernization: Hybrid Liquid Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform ChronosFlow into a Hybrid Liquid Glass aesthetic, merging Material You dynamic colors with high-fidelity blur and "liquid" depth.

**Architecture:** Centralize theme logic in `core:ui`, implement a reusable `LiquidGlass` modifier, and redesign signature components (Chronos Dial, Navigation) to use the new "suspended liquid" visual language.

**Tech Stack:** Jetpack Compose, Material 3, Android 16/17 SDKs, RenderEffect (for blur).

---

## Current Commit Status

The remaining unchecked items in this conductor plan are commit-only steps. They are blocked in this exported workspace because `git rev-parse --show-toplevel` fails with `not a git repository`; the current directory has `.github` metadata but no `.git` directory. Implementation and verification status is tracked in `REBUILD_PLAN_EXTENDED.md`.

---

### Task 1: Unified Theme & Liquid Glass Tokens

**Files:**
- Create: `core/ui/src/main/java/com/chronosflow/core/ui/theme/ChronosTheme.kt`
- Modify: `core/ui/src/main/java/com/chronosflow/core/ui/theme/DesignTokens.kt`

- [x] **Step 1: Add Liquid Glass tokens to DesignTokens.kt**

```kotlin
// core/ui/.../theme/DesignTokens.kt
object ChronosGlassTokens {
    val BaseOpacity = 0.85f
    val HighRefractionBorderWidth = 1.5.dp
    val StandardBlur = 25.dp
    val AmbientBlur = 60.dp
    
    @Composable
    fun borderBrush(primary: Color) = Brush.verticalGradient(
        colors = listOf(primary, Color.Transparent)
    )
}
```

- [x] **Step 2: Create the unified ChronosTheme.kt**

```kotlin
// core/ui/.../theme/ChronosTheme.kt
@Composable
fun ChronosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(...) // Use existing dark colors from MainActivity
        else -> lightColorScheme(...) // Use existing light colors from MainActivity
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
```

- [ ] **Step 3: Commit** (blocked in current workspace: no `.git` directory)
```bash
git add core/ui/src/main/java/com/chronosflow/core/ui/theme/*
git commit -m "style: add unified ChronosTheme and Liquid Glass tokens"
```

### Task 2: Reusable Liquid Glass Modifier

**Files:**
- Create: `core/ui/src/main/java/com/chronosflow/core/ui/theme/LiquidGlassModifier.kt`

- [x] **Step 1: Implement the liquidGlass modifier**

```kotlin
// core/ui/.../theme/LiquidGlassModifier.kt
fun Modifier.liquidGlass(
    cornerRadius: Dp = 28.dp,
    blur: Dp = ChronosGlassTokens.StandardBlur
) = this
    .graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderEffect = RenderEffect.createBlurEffect(
                blur.toPx(), blur.toPx(), Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    }
    .clip(RoundedCornerShape(cornerRadius))
    .background(MaterialTheme.colorScheme.surface.copy(alpha = ChronosGlassTokens.BaseOpacity))
    .border(
        width = ChronosGlassTokens.HighRefractionBorderWidth,
        brush = ChronosGlassTokens.borderBrush(MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(cornerRadius)
    )
```

- [ ] **Step 2: Commit** (blocked in current workspace: no `.git` directory)
```bash
git add core/ui/src/main/java/com/chronosflow/core/ui/theme/LiquidGlassModifier.kt
git commit -m "feat: implement reusable liquidGlass modifier"
```

### Task 3: Global Ambient Liquid Backdrop

**Files:**
- Create: `core/ui/src/main/java/com/chronosflow/core/ui/components/LiquidBackdrop.kt`
- Modify: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialScreen.kt`

- [x] **Step 1: Move and upgrade AmbientBlurBackdrop to core:ui**

```kotlin
// core/ui/.../components/LiquidBackdrop.kt
@Composable
fun LiquidBackdrop() {
    val primary = MaterialTheme.colorScheme.primaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    // Implementation with Animatable blobs for "liquid" morphing
}
```

- [x] **Step 2: Update DayDialScreen to use the global backdrop**

- [ ] **Step 3: Commit** (blocked in current workspace: no `.git` directory)
```bash
git add core/ui/src/main/java/com/chronosflow/core/ui/components/LiquidBackdrop.kt
git add feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialScreen.kt
git commit -m "refactor: move and upgrade liquid backdrop to core:ui"
```

### Task 4: Chronos Dial "Vivid Glass" Redesign

**Files:**
- Modify: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ChronosDial.kt`

- [x] **Step 1: Apply liquidGlass to the main dial disk**
- [x] **Step 2: Update time blocks to "liquid pills" (vibrant colors, inner glow)**
- [x] **Step 3: Add outer glow to the "Now" indicator**

- [ ] **Step 4: Commit** (blocked in current workspace: no `.git` directory)
```bash
git add feature/daydial/src/main/java/com/chronosflow/feature/daydial/ChronosDial.kt
git commit -m "feat: redesign Chronos Dial with vivid glass aesthetic"
```

### Task 5: App-wide Theme Integration

**Files:**
- Modify: `app/src/main/java/com/chronosflow/MainActivity.kt`

- [x] **Step 1: Replace root colorScheme logic with ChronosTheme**

```kotlin
// app/.../MainActivity.kt
setContent {
    ChronosTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChronosFlowApp()
        }
    }
}
```

- [ ] **Step 2: Commit** (blocked in current workspace: no `.git` directory)
```bash
git add app/src/main/java/com/chronosflow/MainActivity.kt
git commit -m "refactor: integrate unified ChronosTheme in MainActivity"
```
