package ru.tinkoff.kora.test.extension.junit5.inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class InjectFieldTests {

    @BeforeEach
    void beforeEach(@TestComponent TestComponent1 component1) {
        assertEquals("1", component1.get());
    }

    @AfterEach
    void afterEach(@TestComponent TestComponent1 component1) {
        assertEquals("1", component1.get());
    }

    @Test
    void injectOne(@TestComponent TestComponent1 component1) {
        assertEquals("1", component1.get());
    }

    @Test
    void injectBoth(@TestComponent TestComponent1 component1,
                    @TestComponent TestComponent12 component12) {
        assertEquals("1", component1.get());
        assertEquals("12", component12.get());
    }
}
