package org.acme

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTestProfile


class MockSocketProfile: QuarkusTestProfile {


    /**
     * Allows the default config profile to be overridden. This basically just sets the quarkus.test.profile system
     * property before the test is run.
     *
     * Here we are setting the profile to test-mocked
     */
    override fun getConfigProfile(): String {
        return "mocked-socket";
    }

    /**
     * Additional [QuarkusTestResourceLifecycleManager] classes (along with their init params) to be used from this
     * specific test profile.
     *
     * If this method is not overridden, then only the [QuarkusTestResourceLifecycleManager] classes enabled via the [io.quarkus.test.common.QuarkusTestResource] class
     * annotation will be used for the tests using this profile (which is the same behavior as tests that don't use a profile at all).
     */
    override fun testResources(): List<QuarkusTestProfile.TestResourceEntry> {
        return listOf(
            QuarkusTestProfile.TestResourceEntry(
                SocketTestResource::class.java
            )
        )
    }


    /**
     * If this returns true then only the test resources returned from [.testResources] will be started,
     * global annotated test resources will be ignored.
     */
    override fun disableGlobalTestResources(): Boolean {
        return true
    }
}