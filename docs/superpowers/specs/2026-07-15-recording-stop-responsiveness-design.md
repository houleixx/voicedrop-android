# Recording Stop Responsiveness Design

## Goal

Make the main Android recording screen reliably accept the stop action and provide immediate feedback while the recording engine finalizes the audio file.

## Scope

- Change only the main recording flow in `RecordingsActivity`.
- Preserve recording formats, filenames, duration calculation, silence detection, captured photos, interview shutdown, upload behavior, tags, and navigation results.
- Keep the current user-facing action as stop; this change does not add pause/resume recording support.
- Do not modify the iOS, mini-program, or backend repositories because no cross-platform contract changes.

## Design

### Stable recording view

`showRecording(boolean first)` continues to build the recording screen, but it retains references to the timer text and a waveform host. The 500 ms `timerTick` calls a focused update method that changes only the timer text and replaces the waveform content inside that host. It must not call `showRecording(false)` or remove the recording page.

Explicit state changes that genuinely alter the screen structure, such as adding a captured photo or toggling interview state, may continue to rebuild the screen. They are user-driven and do not create the periodic touch-cancellation window responsible for the reported failure.

### Non-blocking stop

The first stop request sets a `recordingStopInProgress` guard, removes pending timer callbacks, disables every stop click target, and changes the label to `正在保存…`. Further stop requests while the guard is set are ignored.

The recording backend's `stop(null)` call runs on the existing I/O executor because `EngineRecorder.stop()` can wait for the encoder worker. UI-owned state is captured before dispatch. When stopping finishes, the result is posted to the main thread, where the existing home navigation, silence confirmation, upload, photo handling, and deferred refresh behavior continue in their current order.

The stop guard is reset when a new recording starts. If backend finalization returns no take, the existing behavior of returning to the home screen remains unchanged.

## Error and lifecycle behavior

- The UI gives immediate feedback before background finalization starts.
- No new network or storage contract is introduced.
- The completion callback checks that the activity is not finishing or destroyed before attempting to render follow-up UI. Audio finalization itself is allowed to complete so a valid take is not abandoned solely because of a lifecycle transition.
- Existing recording cancellation behavior remains unchanged.

## Testing

Add JVM source-contract regression tests that verify:

1. `timerTick` updates recording UI without calling `showRecording(false)`.
2. The recording screen retains timer and waveform references used by the focused updater.
3. The stop path guards duplicate requests, removes timer callbacks, and presents `正在保存…` before dispatching work.
4. `recorder.stop(null)` executes inside the I/O executor and UI completion is posted to the main thread.

Run the focused regression test first, then the full `./gradlew testDebugUnitTest` suite. Because touch dispatch and visual feedback are Android UI behavior, final validation also requires a device or emulator check: begin recording, repeatedly tap stop at different timer phases, confirm immediate `正在保存…` feedback, confirm only one take is produced, and verify the resulting recording follows the normal silence/upload flow.
