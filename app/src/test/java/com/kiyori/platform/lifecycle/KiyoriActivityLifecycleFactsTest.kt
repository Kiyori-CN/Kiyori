package com.kiyori.platform.lifecycle

import android.app.Activity
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class KiyoriActivityLifecycleFactsTest {
    @Test
    fun currentActivityTracksResumePauseAndDestroyIdentity() {
        val facts = KiyoriActivityLifecycleFacts()
        val first = mock<Activity>()
        val second = mock<Activity>()

        facts.onActivityCreated()
        facts.onActivityCreated()
        facts.onActivityResumed(first)
        assertSame(first, facts.getCurrentActivity())

        facts.onActivityPaused(second)
        assertSame(first, facts.getCurrentActivity())

        facts.onActivityPaused(first)
        assertNull(facts.getCurrentActivity())

        facts.onActivityResumed(first)
        facts.onActivityDestroyed(second)
        assertSame(first, facts.getCurrentActivity())

        facts.onActivityDestroyed(first)
        assertNull(facts.getCurrentActivity())
    }

    @Test
    fun foregroundTransitionsOnlyAtZeroOneBoundaries() {
        val facts = KiyoriActivityLifecycleFacts()

        assertTrue(facts.onActivityStarted())
        assertFalse(facts.onActivityStarted())
        assertFalse(facts.onActivityStopped())
        assertTrue(facts.onActivityStopped())
        assertFalse(facts.onActivityStopped())
        assertTrue(facts.onActivityStarted())
    }

    @Test
    fun activityCountPreservesLastActivityCleanupBoundary() {
        val facts = KiyoriActivityLifecycleFacts()
        val first = mock<Activity>()
        val second = mock<Activity>()

        assertTrue(facts.onActivityCreated() > 0)
        assertTrue(facts.onActivityCreated() > 0)
        assertTrue(facts.onActivityDestroyed(first) > 0)
        assertTrue(facts.onActivityDestroyed(second) == 0)
        assertTrue(facts.onActivityDestroyed(second) < 0)
    }

    @Test
    fun platformRegistrationUsesThePlatformCallbackOwner() {
        val application = mock<Application>()
        val observer =
            object : KiyoriActivityLifecycleObserver {
                override fun onActivityCreated(
                    activity: Activity,
                    activeActivityCount: Int,
                ) = Unit

                override fun onActivityStarted(
                    activity: Activity,
                    enteredForeground: Boolean,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(
                    activity: Activity,
                    enteredBackground: Boolean,
                ) = Unit

                override fun onActivityDestroyed(
                    activity: Activity,
                    activeActivityCount: Int,
                ) = Unit
            }

        KiyoriActivityLifecycle.initialize(application, observer)

        verify(application).registerActivityLifecycleCallbacks(KiyoriActivityLifecycle)
    }
}
