package ru.tinkoff.kora.test.extension.junit5.mockito;

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

@KoraAppTest(value = TestApplication.class, components = TestComponent12.class)
public class MockParametersTests {

    @Test
    void mock(@Mock @TestComponent TestComponent1 mock) {
        assertNull(mock.get());
        Mockito.when(mock.get()).thenReturn("?");
        assertEquals("?", mock.get());
    }

    @Test
    void beanWithMock(@Mock @TestComponent TestComponent1 mock,
                      @TestComponent TestComponent12 bean) {
        assertNull(mock.get());
        Mockito.when(mock.get()).thenReturn("?");
        assertEquals("?", mock.get());
        assertEquals("?2", bean.get());
    }
}
