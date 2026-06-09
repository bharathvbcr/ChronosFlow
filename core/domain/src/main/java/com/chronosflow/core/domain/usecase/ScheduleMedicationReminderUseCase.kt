package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import javax.inject.Inject

class ScheduleMedicationReminderUseCase @Inject constructor(
    private val alarmRequestRepository: AlarmRequestRepository
) {
    suspend operator fun invoke(request: AlarmRequest) {
        require(request.medicationPlanId != null) { "Medication reminders require medicationPlanId" }
        alarmRequestRepository.saveAlarmRequest(request)
    }
}
