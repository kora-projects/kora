package io.koraframework.test.extension.junit5.mockito;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent1;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReportMockStrictStubbingReporterPerClassTests {

    @Test
    public void mockStrictPerClass() {
        var request = LauncherDiscoveryRequestBuilder.request()
            .configurationParameter("junit.jupiter.conditions.deactivate", "*")
            .selectors(DiscoverySelectors.selectClass(MockStrictPerClassTest.class)).build();

        var launcher = LauncherFactory.create();
        var listener = new SummaryGeneratingListener();

        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        assertEquals(3, listener.getSummary().getTestsStartedCount());
        assertEquals(3, listener.getSummary().getTestsFailedCount());
    }

    @Disabled("IS CHECKED IN SELECTOR")
    @KoraAppTest(value = TestApplication.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @MockitoStrictness(Strictness.STRICT_STUBS)
    static class MockStrictPerClassTest {

        @Mock
        @TestComponent
        private TestComponent1 mock;

        @TestComponent
        private TestComponent12 component12;

        @Test
        void test1() {
            Mockito.when(mock.get()).thenReturn("1");
        }

        @Test
        void test2() {
            Mockito.when(mock.get()).thenReturn("2");
        }

        @Test
        void test3() {
            Mockito.when(mock.get()).thenReturn("3");
        }
    }
}
