package ru.tinkoff.kora.test.extension.junit5.injectk;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(TestApplication.class)
public class InjectWrapperTests {

    @TestComponent
    private TestApplication.SomeChild someChild;
    @TestComponent
    private Integer someInt;
    @TestComponent
    private Float someFloat;

    @Test
    void emptyTest() {
        // do nothing
    }

    @Test
    void testBean() {
        assertNotNull(someInt);
        assertNotNull(someChild);
    }
}
