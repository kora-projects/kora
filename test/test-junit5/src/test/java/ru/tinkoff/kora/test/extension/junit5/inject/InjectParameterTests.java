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
public class InjectParameterTests {

    @TestComponent
    private TestComponent1 component1;
    @TestComponent
    private TestComponent12 component12;

    @BeforeEach
    void beforeEach() {
        assertEquals("1", component1.get());
    }

    @AfterEach
    void afterEach() {
        assertEquals("1", component1.get());
    }

    @Test
    void emptyTest() {
        // do nothing
    }

    @Test
    void testBean() {
        assertEquals("1", component1.get());
        assertEquals("12", component12.get());
    }
}
