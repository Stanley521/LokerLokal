package com.example.lokerlokal.ui.map

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lokerlokal.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapFragmentSheetBehaviorTest {

    @Test
    fun markerSelection_opensApplySheet() {
        val scenario = launchFragmentInContainer<MapFragment>(themeResId = R.style.Theme_LokerLokal)

        scenario.onFragment { fragment ->
            fragment.selectJobForTest(fakeJob())
            fragment.childFragmentManager.executePendingTransactions()
        }

        val state = waitForApplySheetState(scenario, BottomSheetBehavior.STATE_HALF_EXPANDED)
        assertEquals(BottomSheetBehavior.STATE_HALF_EXPANDED, state)
    }

    @Test
    fun mapInteraction_collapsesExpandedApplySheetToPeek() {
        val scenario = launchFragmentInContainer<MapFragment>(themeResId = R.style.Theme_LokerLokal)

        scenario.onFragment { fragment ->
            fragment.selectJobForTest(fakeJob())
            fragment.childFragmentManager.executePendingTransactions()
            fragment.expandApplySheetForTest()
            fragment.simulateMapInteractionForTest()
        }

        val state = waitForApplySheetState(scenario, BottomSheetBehavior.STATE_COLLAPSED)
        assertEquals(BottomSheetBehavior.STATE_COLLAPSED, state)
    }

    @Test
    fun backAndCloseButton_hideApplySheet() {
        val scenario = launchFragmentInContainer<MapFragment>(themeResId = R.style.Theme_LokerLokal)

        scenario.onFragment { fragment ->
            fragment.selectJobForTest(fakeJob())
            fragment.childFragmentManager.executePendingTransactions()
        }
        waitForApplySheetState(scenario, BottomSheetBehavior.STATE_HALF_EXPANDED)

        scenario.onFragment { fragment ->
            fragment.requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val hiddenAfterBack = waitForApplySheetState(scenario, BottomSheetBehavior.STATE_HIDDEN)
        assertEquals(BottomSheetBehavior.STATE_HIDDEN, hiddenAfterBack)

        scenario.onFragment { fragment ->
            fragment.selectJobForTest(fakeJob())
            fragment.childFragmentManager.executePendingTransactions()
        }
        waitForApplySheetState(scenario, BottomSheetBehavior.STATE_HALF_EXPANDED)

        scenario.onFragment { fragment ->
            val applyFragment = fragment.childFragmentManager.findFragmentByTag(JobApplyBottomSheetFragment.TAG)
            applyFragment?.view?.findViewById<android.view.View>(R.id.button_close_apply_sheet)?.performClick()
        }
        val hiddenAfterCloseButton = waitForApplySheetState(scenario, BottomSheetBehavior.STATE_HIDDEN)
        assertEquals(BottomSheetBehavior.STATE_HIDDEN, hiddenAfterCloseButton)
    }

    private fun waitForApplySheetState(
        scenario: androidx.fragment.app.testing.FragmentScenario<MapFragment>,
        expectedState: Int,
        timeoutMs: Long = 2_500L,
    ): Int {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val start = System.currentTimeMillis()
        var lastState = BottomSheetBehavior.STATE_HIDDEN

        while (System.currentTimeMillis() - start < timeoutMs) {
            instrumentation.waitForIdleSync()
            scenario.onFragment { fragment ->
                lastState = fragment.applySheetStateForTest()
            }
            if (lastState == expectedState) return lastState
            Thread.sleep(30L)
        }

        return lastState
    }

    private fun fakeJob(): MapJobItem =
        MapJobItem(
            id = 999L,
            title = "Kasir",
            businessName = "Warung Uji",
            description = "Test job",
            jobType = "Full-time",
            payText = "Rp3.000.000",
            distanceText = "500 m",
            addressText = "Jl. Test No. 1",
            whatsapp = "628123456789",
            phone = "08123456789",
            expiresAt = "2026-12-31",
            createdAt = "2026-01-01",
            latitude = -6.2,
            longitude = 106.8,
            businessPlaceId = "place-id-test",
        )
}




