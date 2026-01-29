package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplicationOps;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent3;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplicationOps.class)
public class MockOnlyTests {

    @Mock
    @TestComponent
    private TestComponent3 mock;

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
