package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.*;

@KoraAppTest(TestApplication.class)
public class MockWrappedParentTests {

    @TestComponent
    private TestApplication.SomeContainer someContainer;

    @Mock
    @TestComponent
    private TestApplication.SomeWrapped someWrappedInterface;
    @Mock
    @TestComponent
    private TestApplication.SomeChild someChildInterface;
    @Mock
    @TestComponent
    private TestApplication.CustomWrapper customWrapper;

    @Test
    void wrappedMocked() {
        MockUtil.isMock(someChildInterface);
        assertNotNull(customWrapper);

        Mockito.when(someWrappedInterface.toString()).thenReturn("12345");

        assertSame(someWrappedInterface, someContainer.wrapped());

        assertEquals("12345", someWrappedInterface.toString());
        assertEquals("12345", someContainer.wrapped().toString());
    }
}
