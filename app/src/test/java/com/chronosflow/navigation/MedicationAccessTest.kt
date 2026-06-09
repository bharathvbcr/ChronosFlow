package com.chronosflow.navigation

import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.chronosflow.AppLockViewModel
import com.chronosflow.core.data.security.SensitiveArea
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class MedicationAccessTest {
    @Test
    fun `guarded medication opener enters medication route without pre auth no op`() {
        val activity = mockk<FragmentActivity>(relaxed = true)
        val appLockViewModel = mockk<AppLockViewModel>(relaxed = true)
        val navController = mockk<NavHostController>(relaxed = true)

        guardedMedicationOpener(activity, appLockViewModel, navController).invoke()

        verify(exactly = 0) {
            appLockViewModel.requiresSensitiveAuth(SensitiveArea.MEDICATION)
        }
        verify {
            navController.navigate(
                ChronosRoute.Medication.route,
                any<NavOptionsBuilder.() -> Unit>()
            )
        }
    }
}
