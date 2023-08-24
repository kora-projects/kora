package ru.tinkoff.kora.test.extension.junit5.initializemode;

import org.junit.jupiter.api.*;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@KoraAppTest(value = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConstructorPerClassTests {

    static volatile TestComponent1 prevComponent1;
    static volatile TestComponent12 prevComponent12;

    private final TestComponent1 component1;
    private final TestComponent12 component12;

    public ConstructorPerClassTests(@TestComponent TestComponent1 component1,
                                    @TestComponent TestComponent12 component12) {
        this.component1 = component1;
        this.component12 = component12;
    }

    @Test
    @Order(1)
    void test1() {
        assertNotNull(component1);
        assertNotNull(component12);
        prevComponent1 = component1;
        prevComponent12 = component12;
    }

    @Test
    @Order(2)
    void test2() {
        assertNotNull(component1);
        assertNotNull(component12);
        assertSame(prevComponent1, component1);
        assertSame(prevComponent12, component12);
    }
}
