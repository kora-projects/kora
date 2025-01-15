package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.Test;
import org.mockito.Spy;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class SpyWrappedTests {

    @Spy
    @TestComponent
    private TestApplication.SomeWrapped someWrapped = new TestApplication.SomeWrapped() {
        @Override
        public String toString() {
            return "12345";
        }
    };

    @TestComponent
    private TestApplication.SomeContainer someContainer;

    @Test
    void wrappedMocked() {
        assertEquals("12345", someWrapped.toString());
        assertEquals("12345", someContainer.wrapped().toString());
    }
}
