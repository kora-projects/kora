package ru.tinkoff.kora.test.extension.junit5.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.MockComponent;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.*;

@KoraAppTest(TestApplication.class)
public class MockBeforeEachAfterEachParameterTests {

    @TestComponent
    private TestComponent12 bean;

    @BeforeEach
    void setupMocks(@MockComponent TestComponent1 mock) {
        assertNull(mock.get());
        Mockito.when(mock.get()).thenReturn("?");
    }

    @AfterEach
    void checkMocks(@MockComponent TestComponent1 mock) {
        assertNotNull(mock.get());
    }

    @Test
    void fieldMocked(@MockComponent TestComponent1 mock) {
        assertEquals("?", mock.get());
    }

    @Test
    void fieldMockedAndInBeanDependency(@MockComponent TestComponent1 mock) {
        assertEquals("?", mock.get());
        assertEquals("?2", bean.get());
    }
}
