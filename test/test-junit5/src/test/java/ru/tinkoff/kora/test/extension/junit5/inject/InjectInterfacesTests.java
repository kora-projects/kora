package ru.tinkoff.kora.test.extension.junit5.inject;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

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
