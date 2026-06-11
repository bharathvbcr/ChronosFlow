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

## Purpose-built ML Kit GenAI feature APIs

In addition to the Prompt API, ChronosFlow now uses the dedicated on-device ML Kit GenAI feature APIs (all `1.0.0-beta1`, on top of the same AICore / Gemini Nano stack):

| Feature API | Gateway | Coordinator entry point | Used by |
|-------------|---------|-------------------------|---------|
| Summarization (`genai-summarization`) | `MlKitTextToolsGateway` | `GenAiAssistCoordinator.summarize` | `ReviewAssistPlanner.suggestDigest` (review insight digest) |
| Proofreading (`genai-proofreading`) | `MlKitTextToolsGateway` | `GenAiAssistCoordinator.proofread` | `TaskAssistPlanner.refineTitle`, `HabitAssistPlanner.refineTitle`, `MedicationAssistPlanner.refineName` — tidy captured task/habit titles and medication names, surfaced through each form's existing "Suggest" action |
| Rewriting (`genai-rewriting`) | `MlKitTextToolsGateway` | `GenAiAssistCoordinator.rewrite` | `TaskAssistPlanner.rewriteText` (tone/length rewrite of task text) |

These are **on-device only** (no cloud equivalent is wired). They respect `PrivacyMode` (`DISABLED` returns the original text untouched) and the same foreground gate as the Prompt API. A failed `Result` means "keep the original text," not an error to surface.

## Streaming output

`OnDeviceGeminiGateway.generateTextStream` and `GenAiAssistCoordinator.generateAssistTextStream` expose Gemini Nano's `generateContentStream` as a cumulative `Flow<String>` for longer outputs (perceived-latency win). The flow is empty when AI is disabled or Nano is not ready, so callers fall back to the one-shot path or local copy. Cloud streaming is not wired; streaming always uses the on-device path. The conversational assistant is the primary consumer.

## Proactive (pre-generated, cached) AI

Because Nano inference is foreground-only, `ProactiveAssistGenerator` pre-generates a short daily digest **while the app is foregrounded** (`ProactiveAssistForegroundRefresher`, on ProcessLifecycle `ON_START`) and caches it via `ProactiveAssistStore` (`PreferencesProactiveAssistStore`). Background surfaces read the cache with no live inference; the cache always holds at least a deterministic local digest, and stale / wrong-day entries are rejected.

The **end-of-day review reminder** (`AlarmRequestType.DAILY_REVIEW`) consumes it: `AlarmDeliveryCoordinator.dailyReviewDigestOverride` reads the cached digest at delivery and shows it as the notification body (falling back to the static copy when there is no fresh same-day digest). The cache coordinates live in `core:domain` (`ProactiveDigestKeys`) so the `core:ai` writer and the `core:notifications` reader share one source of truth without a module dependency.

## Conversational assistant

`ConversationalAssistant` is a free-text, multi-turn surface. It converses with Gemini Nano (streaming) and, when the user wants to *do* something, maps the request onto one of the supplied `CommandAssistCandidate`s — the same command catalog the command palette / AppFunctions use — surfacing it as an `AssistantActionProposal` that **always requires explicit user confirmation** (the assistant never executes anything itself). It falls back to deterministic local command-routing (`CommandAssistPlanner`) whenever on-device AI is disabled or unavailable.

It is reachable from the **command palette**: an "Ask the assistant" button (`CommandSearchViewModel.askAssistant` → `assistantPanel` state) runs the current query through the assistant and renders the reply with a **"Run: <command>"** confirm button (which dispatches the resolved `CommandPaletteItem`) plus a Dismiss. The palette component (`core:ui`) stays generic — it takes only primitive params/callbacks; the app layer (`CommandPaletteHost`) owns the AI wiring.
