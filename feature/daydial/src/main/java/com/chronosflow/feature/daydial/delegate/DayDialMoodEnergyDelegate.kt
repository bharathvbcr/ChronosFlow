package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.data.focus.FocusMoodAccentCache
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import com.chronosflow.core.domain.repository.MoodEnergyRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialMoodEnergyDelegate @Inject constructor(
    private val moodEnergyRepository: MoodEnergyRepository,
    private val focusMoodAccentCache: FocusMoodAccentCache
) {
    fun observeCheckIns(
        scope: CoroutineScope,
        selectedDate: StateFlow<LocalDate>
    ) = selectedDate.flatMapLatest { date ->
        moodEnergyRepository.observeForDate(date)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveCheckIn(
        scope: CoroutineScope,
        date: LocalDate,
        blockId: String?,
        moodScore: Int,
        stressScore: Int,
        energyScore: Int,
        focusScore: Int,
        notes: String? = null
    ) {
        val normalizedMood = moodScore.coerceIn(1, 5)
        val normalizedEnergy = energyScore.coerceIn(1, 5)
        focusMoodAccentCache.save(blockId, normalizedMood, normalizedEnergy)
        scope.launch {
            moodEnergyRepository.save(
                MoodEnergyCheckIn(
                    id = UUID.randomUUID().toString(),
                    blockId = blockId,
                    moodScore = normalizedMood,
                    stressScore = stressScore.coerceIn(1, 5),
                    energyScore = normalizedEnergy,
                    focusScore = focusScore.coerceIn(1, 5),
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    recordedAt = LocalDateTime.now(),
                    checkInDate = date
                )
            )
        }
    }
}
