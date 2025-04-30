package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@KoraAppTest(TestApplication.class)
public class MockWrappedTests {

    @TestComponent
    private TestApplication.SomeContainer someContainer;
    @Mock
    @TestComponent
    private TestApplication.SomeChild someChild;
    @Mock
    @TestComponent
    private TestApplication.SomeWrapped someWrapped;
    @Mock
    @TestComponent
    private TestApplication.SomeContract someContract;

    @Test
    void wrappedMocked() {
        assertNotNull(someContainer);
        assertNotNull(someChild);
        assertNotNull(someWrapped);
        assertNotNull(someContract);
    }

    @Test
    void wrappedMockedValue(@TestComponent TestApplication.CustomWrapper wrapper) {
        assertSame(someContract, wrapper.value());
    }

    @Test
    void wrappedMockedWrapperMockedValue(@Mock @TestComponent TestApplication.CustomWrapper wrapper) {
        assertSame(someContract, wrapper.value());
    }
}
