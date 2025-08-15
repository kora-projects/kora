package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.mockito.Mock;
import org.mockito.Mockito;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings(value = {"JUnitMalformedDeclaration", "EqualsWithItself"})
public class ReportMockDefaultStubbingReporterTests {

    @Test
    public void mockDefault() {
        var request = LauncherDiscoveryRequestBuilder.request()
            .configurationParameter("junit.jupiter.conditions.deactivate", "*")
            .selectors(DiscoverySelectors.selectClass(MockDefaultTest.class)).build();

        var launcher = LauncherFactory.create();
        var listener = new SummaryGeneratingListener();

        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        assertEquals(0, listener.getSummary().getTestsFailedCount());
    }

    @KoraAppTest(value = TestApplication.class, components = TestComponent12.class)
    static class MockDefaultTest {

        @Mock
        @TestComponent
        private TestComponent1 mock;

        @Test
        void mockField() {
            Mockito.when(mock.get()).thenReturn("?");
            assertEquals("?", "?");
        }

        @Test
        void mockParameter(@Mock @TestComponent TestComponent1 mock) {
            Mockito.when(mock.get()).thenReturn("?");
            assertEquals("?", "?");
        }
    }
}
