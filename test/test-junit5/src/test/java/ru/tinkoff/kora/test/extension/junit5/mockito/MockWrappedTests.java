package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class MockWrappedTests {

    @Mock
    @TestComponent
    private TestApplication.SomeWrapped someWrapped;

    @TestComponent
    private TestApplication.SomeContainer someContainer;

    @Test
    void wrappedMocked() {
        Mockito.when(someWrapped.toString()).thenReturn("12345");

        assertEquals("12345", someWrapped.toString());
        assertEquals("12345", someContainer.wrapped().toString());
    }
}
