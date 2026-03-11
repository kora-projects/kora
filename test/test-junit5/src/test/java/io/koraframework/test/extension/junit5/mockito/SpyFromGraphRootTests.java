package io.koraframework.test.extension.junit5.mockito;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.internal.util.MockUtil;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KoraAppTest(TestApplication.class)
public class SpyFromGraphRootTests {

    @Spy
    @TestComponent
    private TestComponent12 spy;

    @BeforeEach
    void setupSpy() {
        assertTrue(MockUtil.isSpy(spy));
        Mockito.when(spy.get()).thenReturn("?");
    }

    @Test
    void fieldSpy() {
        assertTrue(MockUtil.isSpy(spy));
        assertEquals("?", spy.get());
    }
}
