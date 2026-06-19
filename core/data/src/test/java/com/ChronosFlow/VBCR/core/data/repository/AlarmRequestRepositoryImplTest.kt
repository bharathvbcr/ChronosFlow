package com.ChronosFlow.VBCR.core.data.repository

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.dao.AlarmDao
import com.ChronosFlow.VBCR.core.data.model.AlarmRequestEntity
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AlarmRequestRepositoryImplTest {
    private val dao: AlarmDao = mockk()
    private val repository = AlarmRequestRepositoryImpl(dao)

    @Test
    fun `observe pending requests maps request entities`() = runTest {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        every { dao.observePendingRequests(now) } returns flowOf(
            listOf(
                requestEntity(
                    id = "alarm-1",
                    type = AlarmRequestType.MEDICATION,
                    delivery = AlarmDeliveryState.PENDING
                )
            )
        )

        repository.observePendingRequests(now).test {
            val requests = awaitItem()
            assertEquals(1, requests.size)
            assertEquals(AlarmRequestType.MEDICATION, requests[0].type)
            assertEquals(AlarmDeliveryState.PENDING, requests[0].deliveryState)
            awaitComplete()
        }
    }

    @Test
    fun `observe by type maps request entities`() = runTest {
        every { dao.observeRequestsByType("BLOCK_START") } returns flowOf(
            listOf(
                requestEntity(
                    id = "alarm-2",
                    type = AlarmRequestType.BLOCK_START,
                    delivery = AlarmDeliveryState.SCHEDULED
                )
            )
        )

        repository.observeRequestsByType(AlarmRequestType.BLOCK_START).test {
            val requests = awaitItem()
            assertEquals(AlarmRequestType.BLOCK_START, requests[0].type)
            assertEquals(AlarmDeliveryState.SCHEDULED, requests[0].deliveryState)
            awaitComplete()
        }
    }

    @Test
    fun `save request persists mapped entity`() = runTest {
        coEvery { dao.insertAlarmRequest(any()) } returns Unit

        repository.saveAlarmRequest(
            AlarmRequest(
                id = "alarm-3",
                type = AlarmRequestType.URGENT_TASK,
                scheduledFor = Instant.parse("2026-01-01T14:00:00Z"),
                title = "Review docs",
                message = "Task review required",
                medicationPlanId = null,
                blockId = null,
                reliability = AlarmReliability.EXACT,
                deliveryState = AlarmDeliveryState.DELIVERED,
                createdAt = Instant.parse("2026-01-01T14:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T14:00:00Z")
            )
        )

        coVerify {
            dao.insertAlarmRequest(
                match {
                    it.id == "alarm-3" && it.type == AlarmRequestType.URGENT_TASK.name
                }
            )
        }
    }

    @Test
    fun `lookup and pruning calls dao functions`() = runTest {
        val threshold = Instant.parse("2026-01-05T00:00:00Z")
        coEvery { dao.getAlarmRequest("alarm-3") } returns null
        coEvery { dao.deleteExpiredAlarmRequests(threshold) } returns Unit

        repository.pruneExpiredAlarmRequests(threshold)
        val found = repository.getAlarmRequest("alarm-3")

        assertEquals(null, found)
        coVerify { dao.deleteExpiredAlarmRequests(threshold) }
        coVerify { dao.getAlarmRequest("alarm-3") }
    }

    private fun requestEntity(
        id: String,
        type: AlarmRequestType,
        delivery: AlarmDeliveryState
    ): AlarmRequestEntity {
        return AlarmRequestEntity(
            id = id,
            type = type.name,
            scheduledFor = Instant.parse("2026-01-01T13:00:00Z"),
            title = "Reminder",
            message = "Take it",
            medicationPlanId = null,
            blockId = null,
            reliability = AlarmReliability.DEGRADED_WINDOW.name,
            deliveryState = delivery.name,
            createdAt = Instant.parse("2026-01-01T13:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T13:00:00Z"),
            deliveredAt = null,
            failureReason = null
        )
    }
}

