# Gemini Nano Device Verification Checklist

Use this checklist on a device that is expected to support Gemini Nano through AICore.

## Preconditions

- Install a debug build of ChronosFlow.
- Confirm the device has network access for any first-time AICore configuration download.
- Keep ChronosFlow in the foreground for inference checks.

## Verification Steps

1. Open the Day Dial planning surface and confirm the AI banner appears.
2. Set privacy mode to `Gemini Nano`.
3. Observe the initial runtime state:
   - `Gemini Nano ready`, or
   - `Gemini Nano available to download`, or
   - `Downloading Gemini Nano`, or
   - `Gemini Nano unsupported on this device`
4. If the model is downloadable, trigger planning and confirm the app surfaces the download state before inference completes.
5. After the model becomes ready, request a plan and confirm:
   - suggested blocks are generated
   - the explanation mentions Gemini Nano on-device through AICore
   - suggestions still require user review before apply
6. Put the app in the background and verify on-device planning is blocked instead of silently running.
7. Return to the foreground and request a plan again to confirm inference resumes normally.
8. If the device reports `unsupported` or planning fails, confirm the explanation clearly says ChronosFlow fell back to local heuristics.
9. Switch to `Cloud Gemini` without `GEMINI_API_KEY` configured and confirm the banner explains that the app will fall back to Gemini Nano or heuristics.

## Pass Criteria

- Runtime state is explicit and understandable.
- Unsupported-device behavior is clearly surfaced.
- Download, ready, and fallback states are distinguishable.
- Background use is blocked.
- No AI suggestion is applied without review.
