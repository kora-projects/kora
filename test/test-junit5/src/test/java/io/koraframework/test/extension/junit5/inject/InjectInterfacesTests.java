package io.koraframework.test.extension.junit5.inject;

import org.junit.jupiter.api.Test;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(TestApplication.class)
public class InjectInterfacesTests {

    @TestComponent
    private Function<String, Integer> consumerExample;
    @TestComponent
    private Function<Supplier<String>, Supplier<Integer>> consumerMegaExample;

    @Test
    void testInjected() {
        assertNotNull(consumerExample);
        assertNotNull(consumerMegaExample);
    }
}
