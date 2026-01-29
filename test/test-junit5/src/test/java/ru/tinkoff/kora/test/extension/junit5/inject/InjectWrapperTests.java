package ru.tinkoff.kora.test.extension.junit5.inject;

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
    private TestApplication.ComplexWrapped someWrapped;
    @TestComponent
    private Integer someInt;

    @Test
    void testWrapped() {
        assertNotNull(someInt);
        assertNotNull(someChild);
        assertNotNull(someWrapped);
    }
}
