package com.chronosflow.core.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun ChronosSpeechInputButton(
    prompt: String,
    onTranscript: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Dictate",
    unavailableLabel: String = "Speech unavailable",
    languageTag: String = Locale.getDefault().toLanguageTag(),
    preferOffline: Boolean = false,
    onSpeechUnavailable: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val speechIntent = remember(prompt, languageTag, preferOffline) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        }
    }
    val canRecognizeSpeech = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    val canLaunchSpeech = remember(context, speechIntent, canRecognizeSpeech) {
        canRecognizeSpeech && speechIntent.resolveActivity(context.packageManager) != null
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val transcript = chronosSpeechTranscriptFromResults(
            result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        )
        if (transcript.isNotBlank()) {
            onTranscript(transcript)
        }
    }

    ChronosFilledTonalButton(
        onClick = {
            try {
                launcher.launch(speechIntent)
            } catch (_: ActivityNotFoundException) {
                onSpeechUnavailable?.invoke()
            }
        },
        enabled = canLaunchSpeech,
        modifier = modifier
    ) {
        Text(if (canLaunchSpeech) label else unavailableLabel)
    }
}

internal fun chronosSpeechTranscriptFromResults(results: List<String>?): String {
    return results
        .orEmpty()
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .withIndex()
        .filter { it.value.isNotBlank() }
        .sortedWith(
            compareByDescending<IndexedValue<String>> { chronosSpeechCaptureScore(it.value) }
                .thenBy { it.index }
        )
        .firstOrNull()
        ?.value
        .orEmpty()
}

private fun chronosSpeechCaptureScore(transcript: String): Int {
    val normalized = transcript.lowercase(Locale.getDefault())
    val tokenCount = normalized.split(Regex("\\s+")).count(String::isNotBlank)
    var score = tokenCount.coerceAtMost(6)
    if (Regex("""\d""").containsMatchIn(normalized)) score += 4
    if (normalized.hasChronosSpeechTerm(CHRONOS_SPEECH_MEDICATION_WORDS)) score += 4
    if (normalized.hasChronosSpeechTerm(CHRONOS_SPEECH_HABIT_WORDS)) score += 4
    if (normalized.hasChronosSpeechTerm(CHRONOS_SPEECH_TASK_WORDS)) score += 3
    if (CHRONOS_SPEECH_APP_ACTION_PATTERN.containsMatchIn(normalized)) score += 5
    if (CHRONOS_SPEECH_HABIT_APP_PATTERN.containsMatchIn(normalized)) score += 5
    if (normalized.hasChronosSpeechTerm(CHRONOS_SPEECH_TIME_WORDS)) score += 3
    if (CHRONOS_SPEECH_UNIT_PATTERN.containsMatchIn(normalized)) score += 4
    if (CHRONOS_SPEECH_HYDRATION_ML_PATTERN.containsMatchIn(normalized) &&
        normalized.hasChronosSpeechTerm(CHRONOS_SPEECH_HYDRATION_WORDS)
    ) {
        score += 5
    }
    if (CHRONOS_SPEECH_CADENCE_PATTERN.containsMatchIn(normalized)) score += 4
    return score
}

private fun String.hasChronosSpeechTerm(terms: Set<String>): Boolean =
    terms.any { term ->
        if (term.any(Char::isWhitespace)) {
            contains(term)
        } else {
            Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(this)
        }
    }

private val CHRONOS_SPEECH_TASK_WORDS = setOf(
    "call",
    "email",
    "text",
    "message",
    "sms",
    "dm",
    "ping",
    "reply",
    "respond",
    "ask",
    "tell",
    "send",
    "remind",
    "reminder",
    "follow up",
    "pay",
    "buy",
    "shop",
    "order",
    "ship",
    "package",
    "review",
    "submit",
    "schedule",
    "pickup",
    "pick up",
    "drop off",
    "drive to",
    "go to",
    "navigate to",
    "directions to",
    "meet at",
    "meeting",
    "appointment",
    "invite",
    "meeting invite",
    "calendar invite",
    "save the date",
    "send invite",
    "send calendar invite",
    "send meeting invite",
    "event invite",
    "rsvp",
    "r s v p",
    "accept invite",
    "decline invite",
    "birthday",
    "anniversary",
    "gift",
    "present",
    "birthday gift",
    "birthday card",
    "party",
    "celebration",
    "doctor",
    "dentist",
    "clinic",
    "therapy",
    "lab",
    "bill",
    "invoice",
    "rent",
    "subscription",
    "document",
    "pdf",
    "p d f",
    "brief",
    "proposal",
    "link",
    "website",
    "u r l",
    "w w w",
    "h t t p",
    "h t t p s",
    "shipping",
    "mail",
    "post office",
    "ups",
    "u p s",
    "fedex",
    "usps",
    "u s p s",
    "laundry",
    "dishes",
    "trash",
    "repair",
    "return",
    "bring",
    "bring to",
    "deliver",
    "deliver to",
    "take to",
    "visit",
    "urgent",
    "high priority",
    "important",
    "asap",
    "critical"
)

private val CHRONOS_SPEECH_MEDICATION_WORDS = setOf(
    "med",
    "medication",
    "medicine",
    "pill",
    "tablet",
    "capsule",
    "cream",
    "ointment",
    "gel",
    "topical",
    "patch",
    "liquid",
    "syrup",
    "solution",
    "drop",
    "drops",
    "vitamin",
    "vitamin d",
    "vitamin c",
    "vitamin sea",
    "vit sea",
    "supplement",
    "magnesium",
    "iron",
    "calcium",
    "zinc",
    "b12",
    "b 12",
    "b twelve",
    "be twelve",
    "d 3",
    "d three",
    "dee three",
    "cbd",
    "c b d",
    "omega",
    "probiotic",
    "melatonin",
    "allergy",
    "allergies",
    "antihistamine",
    "zyrtec",
    "claritin",
    "cetirizine",
    "loratadine",
    "benadryl",
    "ibuprofen",
    "advil",
    "tylenol",
    "acetaminophen",
    "naproxen",
    "aleve",
    "cough",
    "cold",
    "fever",
    "headache",
    "migraine",
    "pain",
    "rash",
    "itching",
    "heartburn",
    "antacid",
    "rescue",
    "inhaler",
    "insulin",
    "injection",
    "inject",
    "shot",
    "syringe",
    "pen needle",
    "epipen",
    "epi pen",
    "epinephrine",
    "dose",
    "refill",
    "rx",
    "r x",
    "prescription",
    "pharmacy",
    "pharmacy pickup",
    "pick up prescription",
    "pickup prescription",
    "renew prescription",
    "refill prescription",
    "as needed",
    "with food",
    "before bed"
)

private val CHRONOS_SPEECH_HABIT_WORDS = setOf(
    "habit",
    "routine",
    "gym",
    "workout",
    "exercise",
    "fitness",
    "strength",
    "training",
    "cardio",
    "walk",
    "run",
    "running",
    "jog",
    "jogging",
    "yoga",
    "swim",
    "swimming",
    "bike",
    "cycling",
    "cycle",
    "stretch",
    "mobility",
    "drink",
    "drinking",
    "fluid",
    "fluids",
    "hydrate",
    "water",
    "cups",
    "ounces",
    "oz",
    "liters",
    "litres",
    "meditate",
    "breathing",
    "journal",
    "read",
    "sleep",
    "sleep routine",
    "wake up",
    "wind down",
    "screen free",
    "screen-free",
    "steps",
    "pilates",
    "weights",
    "lift",
    "lifting",
    "floss",
    "flossing",
    "brush teeth",
    "brush my teeth",
    "dental",
    "meal prep",
    "meal planning",
    "prep meals",
    "nutrition",
    "healthy eating",
    "eat breakfast",
    "eat lunch",
    "eat dinner",
    "eat vegetables",
    "eat fruit",
    "reduce caffeine",
    "cut caffeine",
    "limit caffeine",
    "reduce sugar",
    "cut sugar",
    "limit sugar",
    "study",
    "learn",
    "learning",
    "spanish",
    "guitar",
    "piano",
    "coding practice"
)

private val CHRONOS_SPEECH_TIME_WORDS = setOf(
    "today",
    "tomorrow",
    "tonight",
    "this",
    "by",
    "on",
    "morning",
    "afternoon",
    "evening",
    "night",
    "breakfast",
    "lunch",
    "dinner",
    "bedtime"
)

private val CHRONOS_SPEECH_HYDRATION_WORDS = setOf(
    "drink",
    "drinking",
    "fluid",
    "fluids",
    "glass",
    "glasses",
    "hydrate",
    "hydration",
    "water"
)

private val CHRONOS_SPEECH_UNIT_PATTERN = Regex(
    """\b(?:mg|mcg|g|ml|teaspoon|teaspoons|tsp|tsps|tablespoon|tablespoons|tbsp|tbsps|iu|unit|units|tablet|tablets|capsule|capsules|drop|drops|puff|puffs|spray|sprays|glass|glasses)\b"""
)

private val CHRONOS_SPEECH_HYDRATION_ML_PATTERN = Regex(
    """\b\d+(?:\.\d+)?\s*(?:ml|milliliters?)\b"""
)

private val CHRONOS_SPEECH_CADENCE_PATTERN = Regex(
    """\b(?:daily|nightly|weekly|monthly|weekdays?|weekends?|every\s+(?:day|weekday|weekend|morning|night|week|month|other\s+day)|once\s+(?:(?:a|per)\s+)?day|twice\s+(?:(?:a|per)\s+day|daily)|(?:two|three|four|five|six|\d+)\s+(?:x|times?)\s*/?\s*(?:(?:a|per)\s+)?(?:day|daily|week|weekly)|(?:two|three|four|five|six|\d+)x\s*/?\s*(?:day|daily|week|weekly)|twice\s+(?:(?:a|per)\s+)?week|every\s+(?:6|six|8|eight|12|twelve)\s+hours?)\b"""
)

private val CHRONOS_SPEECH_APP_ACTION_PATTERN = Regex(
    """\b(?:open|launch|start|join|check|review)\s+(?:spotify|youtube|you\s+tube|zoom|teams|microsoft\s+teams|slack|notion|gmail|google\s+calendar|google\s+drive|google\s+docs|google\s+sheets|google\s+slides|google\s+maps|maps)\b"""
)

private val CHRONOS_SPEECH_HABIT_APP_PATTERN = Regex(
    """\b(?:with|using|in|on)\s+(?:duolingo|strava|headspace|calm|spotify|youtube|you\s+tube|google\s+fit|fitbit|myfitnesspal|my\s+fitness\s+pal)\b"""
)
