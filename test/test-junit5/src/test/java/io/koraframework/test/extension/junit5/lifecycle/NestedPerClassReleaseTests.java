package io.koraframework.test.extension.junit5.lifecycle;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.LifecycleTestApplication;
import io.koraframework.test.extension.junit5.testdata.LifecycleTestApplication.CountingLifecycleComponent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NestedPerClassReleaseTests {

    @Test
    public void nestedPerClassGraphReleased() {
        int initBefore = CountingLifecycleComponent.INIT.get();
        int releaseBefore = CountingLifecycleComponent.RELEASE.get();

        var request = LauncherDiscoveryRequestBuilder.request()
            .configurationParameter("junit.jupiter.conditions.deactivate", "*")
            .selectors(DiscoverySelectors.selectClass(PerMethodOuterTest.class)).build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);

        assertEquals(2, listener.getSummary().getTestsSucceededCount());
        assertEquals(1, CountingLifecycleComponent.INIT.get() - initBefore);
        assertEquals(1, CountingLifecycleComponent.RELEASE.get() - releaseBefore);
    }

    @Disabled("IS CHECKED IN SELECTOR")
    @KoraAppTest(LifecycleTestApplication.class)
    static class PerMethodOuterTest {

        @Nested
        @TestInstance(TestInstance.Lifecycle.PER_CLASS)
        class NestedPerClass {

            @TestComponent
            private CountingLifecycleComponent component;

            @Test
            void test1() {
                assertNotNull(component);
            }

            @Test
            void test2() {
                assertNotNull(component);
            }
        }
    }
}
