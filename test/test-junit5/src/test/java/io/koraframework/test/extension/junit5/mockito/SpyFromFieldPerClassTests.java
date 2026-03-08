package io.koraframework.test.extension.junit5.mockito;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.internal.util.MockUtil;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent1;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KoraAppTest(TestApplication.class)
public class SpyFromFieldPerClassTests {

    @Spy
    @TestComponent
    private TestComponent1 spy = new TestComponent1();
    @TestComponent
    private TestComponent12 bean;

    @BeforeEach
    void setupSpy() {
        assertTrue(MockUtil.isSpy(spy));
        Mockito.when(spy.get()).thenReturn("?");
    }

    @Test
    void fieldSpy() {
        assertTrue(MockUtil.isSpy(spy));
        assertEquals("?", spy.get());
        Mockito.verify(spy, Mockito.times(1)).get();
    }

    @Test
    void fieldSpyAndBeanDependency() {
        assertTrue(MockUtil.isSpy(spy));
        assertEquals("?", spy.get());
        assertEquals("?2", bean.get());
        Mockito.verify(spy, Mockito.times(2)).get();
    }

    @Test
    void fieldSpyAgain() {
        assertTrue(MockUtil.isSpy(spy));
        assertEquals("?", spy.get());
        assertEquals("?2", bean.get());
        Mockito.verify(spy, Mockito.times(2)).get();
    }
}
