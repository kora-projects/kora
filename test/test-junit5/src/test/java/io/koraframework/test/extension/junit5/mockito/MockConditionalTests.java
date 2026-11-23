package io.koraframework.test.extension.junit5.mockito;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@KoraAppTest(TestApplication.class)
public class MockConditionalTests {

    @Test
    void conditionalComponentMockedAndInjectedIntoConsumer(@Mock @TestComponent TestApplication.ConditionalService service,
                                                          @TestComponent TestApplication.ConditionalServiceConsumer consumer) {
        Mockito.when(service.get()).thenReturn("mock");
        assertEquals("mock", consumer.service().get());
    }

    @Test
    void conditionalComponentMockedAndInjectedIntoNullableConsumer(@Mock @TestComponent TestApplication.ConditionalService service,
                                                                  @TestComponent TestApplication.NullableConditionalServiceConsumer consumer) {
        assertSame(service, consumer.service());
    }

    @Test
    void conditionalAlternativeMockedAndInjectedIntoConsumer(@Mock @TestComponent TestApplication.EnabledAlternative alternative,
                                                            @TestComponent TestApplication.ConditionalAlternativeConsumer consumer) {
        assertSame(alternative, consumer.alternative());
    }

    @Test
    void conditionalAlternativeMockedAndInjectedIntoAll(@Mock @TestComponent TestApplication.EnabledAlternative alternative,
                                                       @TestComponent TestApplication.AllConditionalAlternativeConsumer consumer) {
        var alternatives = new ArrayList<TestApplication.ConditionalAlternative>();
        consumer.alternatives().forEach(alternatives::add);
        assertEquals(List.of(alternative), alternatives);
    }

    @Test
    void conditionalAlternativeMockedAndInjectedIntoAllValues(@Mock @TestComponent TestApplication.EnabledAlternative alternative,
                                                             @TestComponent TestApplication.AllConditionalAlternativeValuesConsumer consumer) {
        var alternatives = new ArrayList<TestApplication.ConditionalAlternative>();
        consumer.alternatives().forEach(v -> alternatives.add(v.get()));
        assertEquals(List.of(alternative), alternatives);
    }
}
