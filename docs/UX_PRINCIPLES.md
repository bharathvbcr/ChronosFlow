# ChronosFlow UX Principles

> The single source of truth for how every page, modal, button, surface, and animation in
> ChronosFlow looks and behaves. If a screen disagrees with this document, the screen is wrong.

ChronosFlow's visual language is a deliberate blend:

- **iOS "liquid glass"** — translucent, blurred, light-refracting surfaces that float above a living
  backdrop; fluid, **bouncy spring** motion; tactile press-and-release feedback on everything you touch.
- **Material You** — dynamic color sourced from the user's wallpaper, Material 3 color roles, type
  scale, shapes, and accessibility semantics.

The two are not in tension: Material You supplies the **color, type, and a11y system**; the liquid-glass
layer supplies the **surface treatment and motion personality**. Brand colors are a *fallback* for when
dynamic color is unavailable or disabled — never a replacement for the color roles.

This document is descriptive of the canonical system in `core/ui` **and** prescriptive for every screen.
The code primitives named below already exist; the rule is **use them, do not re-implement them**.

---

## 1. Foundations (tokens — never hardcode)

| Concern | Canonical source | Rule |
|---|---|---|
| Color roles | `MaterialTheme.colorScheme` | Prefer roles (`primary`, `surface`, `surfaceContainerHigh`, `onSurfaceVariant`…) over literals. |
| Brand palette | `ChronosColors` (`theme/DesignTokens.kt`) | Branded fallbacks + semantic accents (category, provenance, quick-item, dial-ring, backdrop). |
| Spacing | `ChronosSpacing` | `Micro 4 · Small 8 · Compact 12 · Standard 16 · Medium 24 · Large 32 · Hero 48`. Structural spacing (page/section/card/stack gaps) uses the scale; only small optical nudges *inside* dense components may deviate. |
| Glass | `ChronosGlassTokens`, `GlassTone`, `GlassElevation` | Opacity, blur, radius, border come from tokens. |
| Shape | `MaterialTheme.shapes` / `ChronosGlassTokens.*Radius` | `extraSmall 8 · small 12 · medium 16 · large 20 · extraLarge 28`. |
| Type | `ChronosTypography` (`MaterialTheme.typography`) | Use the role (`titleMedium`, `bodySmall`…). Don't set arbitrary `fontSize`. |
| Motion | `ChronosMotionDefaults` / `ChronosTransitionFactory` / `ChronosValueAnimationFactory` | No raw `tween(300)`/`spring()` in screens. |

**Hard rules**

- ❌ No `Color(0xFF…)` literals in feature/UI code. Add a named token to `DesignTokens.kt` instead.
- ❌ No magic dp spacing. Use `ChronosSpacing`.
- ❌ No magic animation durations/specs. Use the motion factory.
- ✅ One source of truth: if a value is duplicated across two files, it belongs in a token.

---

## 2. Color & theming (Material You first)

1. **Dynamic color is default-on** (Android 12+). The theme derives the scheme from the wallpaper; brand
   violet/teal/coral are the fallback for older devices or when the user disables dynamic color.
2. **Speak in roles, not hues.** Backgrounds use `surface` / `surfaceContainer*`; text uses
   `onSurface` / `onSurfaceVariant`; the one emphasis color is `primary`. Accents that carry *meaning*
   (a category, a data provenance, a quick-item type) come from named `ChronosColors` tokens so the
   meaning is consistent everywhere and survives a theme change.
3. **Contrast is non-negotiable.** Body text and essential labels/times must clear WCAG AA (4.5:1).
   `chronosGlassContentColor()` picks a readable on-color for glass; use it rather than guessing alpha.
4. **High contrast mode** disables glass/blur, drops decorative alpha, and uses solid `surface`,
   `surfaceContainer`, `surfaceContainerHigh`. Every custom surface must honor
   `LocalFocusAwareColorState.current.isHighContrast`.

---

## 3. Liquid glass (the surface language)

Surfaces float; they don't sit flat. The blur is **real** (Haze backdrop blur on Android 12+) and falls
back to a tinted translucency on older devices and in tests.

- **Real backdrop blur:** `Modifier.chronosFrostedGlass(shape, tone, elevation)` — only for
  **shell-level chrome that is guaranteed to sit over the shell's `hazeSource`** (the floating nav bar
  and the overflow menu). It needs a live `hazeSource` to sample; gate it on
  `LocalChronosHazeState.current != null` and make the host `Surface` transparent so the blur shows.
- **Faux glass / cards:** `Modifier.chronosGlass(...)` and `Modifier.liquidGlass(...)` — translucency +
  vibrancy border, no backdrop sampling. Use for in-content cards/panels **and for per-screen chrome that
  cannot guarantee a `hazeSource`** — notably the top bar (`ChronosGlassTopBar`, reused by every
  `ChronosScreenScaffold`) and overlays in their own windows (bottom sheets, dialogs, command palette).
  Do **not** "upgrade" the top bar to frosted glass: feature screens have no hazeSource, so it would
  sample nothing and render wrong.
- **Tone** = how see-through: `QUIET` (incidental) · `STANDARD` (default card) · `PROMINENT` (panels,
  sheets, hero chrome).
- **Elevation** = border weight only (`LOW`/`MEDIUM`/`HIGH`); glass elevates with light, not shadow.
- Every glass surface carries the **vibrancy border** (`ChronosGlassTokens.borderBrush`) — a 1px
  light-refraction edge. This is the signature; don't drop it.

**Always go through a component, not a raw modifier, when one exists:** `ChronosCardSurface`,
`ChronosGlassPanel`, `ChronosGlassTopBar`. They already wire glass-on/off, high-contrast fallback, and
press feedback.

The backdrop itself (`ChronosScreenBackdrop` / `LiquidBackdrop`) is what the glass refracts. It is alive
but quiet — it must never compete with content, and it stops animating under reduced motion.

---

## 4. Motion (fluid & bouncy — the iOS personality)

Motion is **spring-first**. Springs (not linear tweens) give the slightly overshooting, settle-into-place
feel that reads as "alive." Reserve tweens for full-screen route changes where a directional shared-axis
glide is clearer than a bounce.

| Situation | Spec | Where |
|---|---|---|
| Press / release on any tappable surface | `chronosPressScale` (spring, scale → 0.96) | every button, card, row, chip |
| In-shell tab switch (Today/Plan/Focus) | spring slide+zoom, damping `0.72`, stiffness `480` (a few % overshoot) | `DayDialMainContent` |
| Selection (pills, chips, segmented) | `ChronosValueAnimationFactory.selection` (180ms) | drawer, filters |
| Color / accent state change | `ChronosValueAnimationFactory.stateChange` | focus accent, progress |
| Chrome scale (FAB, quick-add) | spring, `navigationChromeScale` | nav chrome |
| Dial hand | `dialHand` (fully-damped spring, no bounce) | the dial — precision beats bounce here |
| Full-screen route change | `materialSharedAxis*` tween + directional slide | `ChronosNavTransitions` |

**Rules**

- Every tappable thing **scales down on press and springs back** (`chronosPressScale` /
  `chronosHapticClick`). No flat taps.
- Pair meaningful taps with a haptic: the canonical `Chronos*Button` family, `ChronosFilterChip` /
  `ChronosAssistChip`, and `chronosHapticClick` all emit a `Confirm` tick on click; sheets emit a
  `TextHandleMove` tick on open. Use them — don't sprinkle ad-hoc `performHapticFeedback`, and don't
  add one inside a `Chronos*Button`/`Chronos*Chip` `onClick` (it already ticks — a second call
  double-fires).
- **Reduced motion is a first-class branch.** Every factory takes `reducedMotion` and collapses to
  `snap()` / no-slide / no-overshoot. Never animate decoratively when it's on; keep essential progress
  readable. Read it from `rememberChronosUiSettings().reduceMotionEnabled`.
- Direction carries meaning: forward = slide/zoom from the trailing edge, back = from the leading edge.

---

## 5. Components (one canonical answer per pattern)

Don't reinvent these. If a pattern below is built ad-hoc in a screen, replace it with the component.

| You need… | Use | Not |
|---|---|---|
| A page (backdrop + glass top bar + scaffold) | `ChronosScreenScaffold` | bare `Scaffold` |
| A page header (icon chip + title + subtitle) | `ChronosPageHeader` | hand-rolled `Row` |
| A section heading | `ChronosSectionHeader` / `ChronosSectionTitle` | bare `Text` |
| A standalone button | `ChronosButton` / `ChronosFilledTonalButton` / `ChronosOutlinedButton` / `ChronosElevatedButton` / `ChronosTextButton` / `ChronosIconButton` / `ChronosFilledTonalIconButton` | raw Material `Button`/`TextButton`/`FilledTonalIconButton`/… |
| A filter / choice / action chip | `ChronosFilterChip` / `ChronosAssistChip` | raw Material `FilterChip`/`AssistChip` |
| A single-choice segmented control | `ChronosSegmentedButton` | raw Material `SegmentedButton` |
| A tappable content card | `ChronosCardSurface` (or `ChronosListCard`) | raw `Card`/`Surface` + `clickable` |
| A metric / stat | `ChronosMetricTile` | hand-rolled card |
| A primary tappable action card | `ChronosActionTile` | raw row |
| An empty state | `ChronosEmptyState` (with a next-action button) | a lone "nothing here" `Text` |
| A warning / overlap banner | `ChronosWarningBanner` | custom tinted box |
| A bottom sheet | `ChronosModalBottomSheet` (and `ChronosFormBottomSheet` for forms) | raw `ModalBottomSheet` |
| A destructive confirm | `ChronosConfirmBottomSheet` | `AlertDialog` |
| A settings toggle row | `ChronosSettingsRow` | raw `Row` + `Switch` |
| A standalone switch / checkbox | `ChronosSwitch` / `ChronosCheckbox` | raw Material `Switch`/`Checkbox` |
| A top bar | `ChronosTopBar` / `ChronosGlassTopBar` | `TopAppBar` |
| An icon button w/ tooltip | `ChronosTooltipIconButton` | bare `IconButton` |
| An overflow / context menu item | `ChronosDropdownMenuItem` | raw `DropdownMenuItem` |
| Global commands | `CommandPalette` | new nav surface |

**Buttons.** Every standalone button goes through the **`Chronos*` button wrappers** in
`components/ChronosButtons.kt` — drop-in equivalents of the Material 3 buttons that inherit dynamic color
and the Material ripple *and* add the signature bouncy **press-scale** (dip on press, spring back on
release; collapses to a snap under reduced motion). Raw Material `Button`/`TextButton`/… in feature/app
UI is rejected by `ChronosUxPrinciplesAuditTest`. Hierarchy per state: exactly **one** filled primary
action (`ChronosButton`) visible; everything else tonal / outlined / text. Labels are verbs and reused
verbatim (`Add block`, `Fill gap`, `Start focus`, `Complete`, `Review missed`). (Glance home-screen
widgets use the unrelated `androidx.glance` button API and are exempt.)

**Modals & sheets.** Sheets are `PROMINENT` glass, spring in, emit the open haptic, and suppress shell
chrome while open. Every sheet has a clear title, one primary action, and a dismiss. Forms go through
`ChronosFormBottomSheet`; confirmations through `ChronosConfirmBottomSheet`. No raw `AlertDialog` for
routine flows.

---

## 6. Layout & hierarchy

- **One obvious primary action per state.** Today answers "what now?" with a single next-best action;
  Plan leads with the planning controls then the timeline; Focus shows one dominant timer surface.
- **DayDial is the spine.** Today / Plan / Focus are the only primary destinations; everything else is
  reachable via drawer + command palette and stays subordinate.
- **Fewer competing surfaces.** Prefer one glass panel grouping related actions over many small cards.
- **Empty states propose the next action**, they don't just describe absence.
- **Edge-to-edge**, insets respected (`chronosSheetBottomInsets`, scaffold padding). Content never hides
  behind system bars.
- **Adaptive:** compact = stacked; expanded/foldable = navigation rail / supporting pane, not a stretched
  phone layout. The dial stays balanced in landscape/split-screen. Controls survive large font scale.

---

## 7. Accessibility (built in, not bolted on)

- Honor **reduced motion** and **high contrast** everywhere (see §2, §4).
- Minimum touch target 48dp; icon-only controls carry a `contentDescription`; headings use
  `semantics { heading() }`.
- Don't encode meaning in color alone — pair with icon/label/text.
- Body text ≥ AA contrast; never use low-alpha text for essential labels or times.

---

## 8. Enforcement

- `ChronosColorTokenAuditTest` fails the build on any `Color(0xFF…)` literal in `core:ui` outside
  `DesignTokens.kt` — so shared primitives stay token-pure. Semantic accents that used to be duplicated
  across feature screens (provenance, quick-item) now live in `ChronosColors` and are covered by it.
- `ChronosUxPrinciplesAuditTest` source-scans every feature/app/core:ui UI file and fails the build on a
  raw Material button (must use the `Chronos*` wrappers) or a raw `ModalBottomSheet` (must use
  `ChronosModalBottomSheet`) — so "every button/modal follows it" is mechanically enforced, not just
  documented.
- `DesignTokensTest`, `LiquidGlassModifierTest`, `ChronosThemeTest`, `ChronosReadableSurfaceTest` guard
  color/glass/contrast; `ChronosTransitionMotionTest` guards the motion contract (underdamped spring
  tuning, reduced-route timing).
- Before changing a shared `core:ui` primitive, run GitNexus impact analysis (see `CLAUDE.md`) — these
  symbols have wide blast radius.

**Definition of done for any UI change:** uses tokens (no literals), uses the canonical component, has
press-scale + haptic on tappables, springs where motion is interactive, collapses correctly under reduced
motion + high contrast, proposes a next action when empty, and meets AA contrast.
</content>
</invoke>
