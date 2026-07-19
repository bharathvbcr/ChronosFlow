package com.ChronosFlow.VBCR.feature.goals

import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.domain.model.GoalLinkedWork
import com.ChronosFlow.VBCR.core.domain.model.GoalWithProgress
import com.ChronosFlow.VBCR.core.domain.repository.GoalRepository
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveGoalLinkedWorkUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveGoalsWithProgressUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalViewModelTest {
    private val goalRepository: GoalRepository = mockk(relaxed = true)
    private val observeGoalsWithProgressUseCase: ObserveGoalsWithProgressUseCase = mockk()
    private val observeGoalLinkedWorkUseCase: ObserveGoalLinkedWorkUseCase = mockk()
    private val goalsSource = MutableStateFlow<List<GoalWithProgress>>(emptyList())

    private lateinit var viewModel: GoalViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { observeGoalsWithProgressUseCase() } returns goalsSource
        every { observeGoalLinkedWorkUseCase(any()) } returns flowOf(GoalLinkedWork())
        viewModel = GoalViewModel(
            goalRepository = goalRepository,
            observeGoalsWithProgressUseCase = observeGoalsWithProgressUseCase,
            observeGoalLinkedWorkUseCase = observeGoalLinkedWorkUseCase
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `isListLoading clears after first goals emission`() = runTest(testDispatcher) {
        var latest: Boolean? = null
        val job = launch {
            viewModel.isListLoading.collect { latest = it }
        }
        runCurrent()

        assertFalse(latest == true)
        assertEquals(false, latest)
        job.cancel()
    }
}
