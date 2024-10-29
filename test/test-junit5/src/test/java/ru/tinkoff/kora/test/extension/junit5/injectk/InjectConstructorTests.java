package ru.tinkoff.kora.test.extension.junit5.injectk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(value = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InjectConstructorTests {

    private final TestComponent1 component1;
    private final TestComponent12 component12;

    public InjectConstructorTests(@TestComponent TestComponent1 component1,
                                  @TestComponent TestComponent12 component12) {
        this.component1 = component1;
        this.component12 = component12;
    }

    @Test
    void injectBoth() {
        assertEquals("1", component1.get());
        assertEquals("12", component12.get());
    }
}
