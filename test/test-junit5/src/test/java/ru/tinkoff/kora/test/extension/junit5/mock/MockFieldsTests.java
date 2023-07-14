package ru.tinkoff.kora.test.extension.junit5.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.MockComponent;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@KoraAppTest(TestApplication.class)
public class MockFieldsTests {

    @MockComponent
    private TestComponent1 mock;
    @TestComponent
    private TestComponent12 bean;

    @BeforeEach
    void setupMocks() {
        assertNull(mock.get());
        Mockito.when(mock.get()).thenReturn("?");
    }

    @Test
    void fieldMocked() {
        assertEquals("?", mock.get());
    }

    @Test
    void fieldMockedAndInBeanDependency() {
        assertEquals("?", mock.get());
        assertEquals("?2", bean.get());
    }
}
