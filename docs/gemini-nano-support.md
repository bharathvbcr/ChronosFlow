# Gemini Nano Support Contract

ChronosFlow supports on-device planning through Google's recommended Android path:

- `ML Kit GenAI Prompt API`
- `AICore` for Gemini Nano execution and model delivery
- local heuristic fallback when on-device AI is unavailable
- optional cloud Gemini fallback when the app is explicitly configured for it

ChronosFlow does not currently ship a direct integration with the experimental Google AI Edge SDK stack. There are no app-managed Gemini Nano model assets, LiteRT bundles, MediaPipe pipelines, or TensorFlow Lite inference graphs in the app.

## Current implementation

Today the app's production AI path is:

1. `ML Kit Prompt API` on top of `AICore` for Gemini Nano
2. `Cloud Gemini` only when the user or build enables it
3. local heuristics when the selected model path is unavailable

This keeps ChronosFlow on the official Android AI surface area while preserving explicit review before any AI-generated schedule changes are applied.

## LiteRT-LM status

LiteRT-LM is intentionally deferred in ChronosFlow's current production architecture.

- The app does not ship app-managed LiteRT or LiteRT-LM model assets today.
- The primary on-device path remains `ML Kit Prompt API + AICore`.
- LiteRT-LM should only be introduced as a clearly gated experimental phase if ML Kit becomes a concrete product bottleneck for local assistant capabilities.

## What "supported" means

Gemini Nano support in ChronosFlow is device-dependent.

The app can only use on-device planning when all of the following are true:

- the device supports Gemini Nano through AICore
- the required on-device model is available or downloadable
- ChronosFlow is in the foreground

When those conditions are not met, ChronosFlow must make that state explicit in the UI and fall back safely instead of implying that on-device AI is active.

## Runtime behavior

ChronosFlow uses three planning paths:

1. `Gemini Nano (on-device)` through ML Kit on top of AICore
2. `Cloud Gemini` when explicitly enabled and configured
3. local planning heuristics as the final fallback

Expected fallback behavior:

- `Cloud Gemini` (when explicitly enabled) -> fall back to `Gemini Nano` -> fall back to heuristics
- `Gemini Nano` only -> fall back to heuristics
- `Disabled` -> return no AI-generated planning suggestion

## UX expectations

User-facing copy should clearly distinguish these cases:

- Gemini Nano is ready on this device
- Gemini Nano can be downloaded for this device
- Gemini Nano is downloading
- Gemini Nano is unavailable on this device
- planning fell back to local heuristics

The UI should not imply AI Edge SDK support or guarantee Gemini Nano availability on unsupported devices.

## Assist surfaces (GenAiAssistCoordinator)

All user-facing assist flows route through `GenAiAssistCoordinator` in `core:ai`, respecting `PrivacyMode` from assistant settings:

| Surface | Planner | Privacy banner | Source label |
|---------|---------|----------------|--------------|
| Day Dial AI plan | `ChronosAIPlanner` | Yes | Yes |
| Task / Habit / Medication forms | `TaskAssistPlanner`, `RoutineAssistPlanner` | Yes | Yes |
| Focus next block | `FocusNextBlockPlanner` | Yes | Yes |
| Focus session guidance | `FocusGuidancePlanner` | Yes | Yes |
| Habit repair | `HabitRepairAssistPlanner` | Yes | Yes |
| Daily review summary | `ReviewAssistPlanner` | Yes | Yes |
| Energy review insights | `EnergyCorrelationEngine.analyzeWithAssist` | — | On insight rows |
| Insights tab recommendations | `InsightsRecommendationsPlanner` | Yes | Yes |
| Deep-work window insights | `DeepWorkAssistPlanner` | — | On insight rows |
| Command palette routing | `CommandAssistPlanner` + `SemanticPlanningIndex` | — | — |
| Plan explanation (Day Dial) | `PlanExplainAssistPlanner` | Yes | Yes |
| Insight recommendation → plan goals | `RecommendationPlanInterpreter` | Yes (AI plan sheet) | Yes (prefill + suggested goals) |
| Insight quick apply (fill gaps / break) | `RecommendationPlanInterpreter.inferQuickAction` | — | Snackbar on Day Dial |
| Mood & energy check-in (Day Dial + Focus) | `MoodEnergyCheckInAssistPlanner` | Yes | Yes |

Command palette search combines AppSearch/BM25 semantic hits with `CommandAssistPlanner` (local keyword rank + optional GenAI command-id routing). It does not replace indexed search with a standalone LLM retrieval stack.

Semantic planning index and local heuristics remain the fallback whenever GenAI is disabled, unavailable, or returns no parseable output.
