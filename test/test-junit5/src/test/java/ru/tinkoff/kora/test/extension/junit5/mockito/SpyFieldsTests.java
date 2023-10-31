package ru.tinkoff.kora.test.extension.junit5.mockito;

import org.junit.jupiter.api.Test;
import org.mockito.Spy;
import org.mockito.internal.util.MockUtil;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KoraAppTest(TestApplication.class)
public class SpyFieldsTests {

    @Spy
    @TestComponent
    private TestComponent1 mock = new TestComponent1();
    @TestComponent
    private TestComponent12 bean;

    @Test
    void fieldMocked() {
        assertTrue(MockUtil.isSpy(mock));
        assertEquals("1", mock.get());
    }

    @Test
    void fieldMockedAndInBeanDependency() {
        assertTrue(MockUtil.isSpy(mock));
        assertEquals("1", mock.get());
        assertEquals("12", bean.get());
    }
}
