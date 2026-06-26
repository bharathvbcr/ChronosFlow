import Foundation
import AVFoundation
import Speech
import Observation

/// On-device speech-to-text for the journal composer — the iOS analogue of Android's
/// `ChronosSpeechInputButton`. Wraps `SFSpeechRecognizer` + an `AVAudioEngine` tap, requests
/// authorization lazily, and prefers on-device recognition so reflections never leave the phone.
///
/// Info.plist keys required (added by the app target, not here):
///   • `NSSpeechRecognitionUsageDescription` — why we transcribe speech.
///   • `NSMicrophoneUsageDescription` — why we record audio.
///
/// The view observes `transcript`; when a non-empty value lands it appends it to the draft and
/// calls `clearTranscript()` so the next dictation starts clean.
@MainActor
@Observable
final class DictationController {

    enum Availability: Equatable {
        /// Recognizer exists and the user hasn't denied access (request happens on first use).
        case available
        /// The user denied speech or microphone access in Settings.
        case denied
        /// No recognizer for this locale / device.
        case unsupported
    }

    private(set) var isRecording = false
    /// The latest finalized transcript chunk. The view appends it and then calls `clearTranscript()`.
    private(set) var transcript = ""

    private let recognizer = SFSpeechRecognizer()
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    var availability: Availability {
        guard let recognizer, recognizer.isAvailable else { return .unsupported }
        switch SFSpeechRecognizer.authorizationStatus() {
        case .denied, .restricted: return .denied
        default: return .available
        }
    }

    func clearTranscript() { transcript = "" }

    func toggle() {
        isRecording ? stop() : start()
    }

    func start() {
        guard !isRecording, availability != .unsupported else { return }
        // Request both permissions before opening the audio session.
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            guard status == .authorized else { return }
            AVAudioApplication.requestRecordPermission { granted in
                guard granted else { return }
                Task { @MainActor in self?.beginSession() }
            }
        }
    }

    private func beginSession() {
        guard let recognizer, recognizer.isAvailable else { return }

        // Tear down anything in flight, then configure the audio session for recording.
        stop()

        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            return
        }

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = false
        // Keep audio on-device when the model supports it (privacy: reflections stay local).
        if recognizer.supportsOnDeviceRecognition {
            request.requiresOnDeviceRecognition = true
        }
        self.request = request

        let input = audioEngine.inputNode
        let format = input.outputFormat(forBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
        } catch {
            stop()
            return
        }
        isRecording = true

        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self else { return }
            if let result, result.isFinal {
                let text = result.bestTranscription.formattedString
                Task { @MainActor in
                    self.transcript = text
                    self.stop()
                }
            } else if error != nil {
                Task { @MainActor in self.stop() }
            }
        }
    }

    func stop() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        isRecording = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
