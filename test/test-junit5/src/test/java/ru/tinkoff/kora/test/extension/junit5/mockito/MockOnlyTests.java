package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@KoraAppTest(TestApplication.class)
public class MockOnlyTests {

    @Mock
    @TestComponent
    private TestComponent12 mock;

    @BeforeEach
    void setupMocks() {
        Mockito.when(mock.get()).thenReturn("?");
    }

    @Test
    void fieldMocked() {
        assertEquals("?", mock.get());
    }

    @Test
    void fieldMockedAndInBeanDependency() {
        assertEquals("?", mock.get());
    }
}
