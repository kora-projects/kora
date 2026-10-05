package io.koraframework.test.extension.junit5.inject;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class InjectCycleProxyTests {

    @TestComponent
    private TestApplication.CycleSecond cycleSecond;

    @Test
    void cycleProxyTargetIsInitialized() {
        assertEquals("first", cycleSecond.value());
    }
}
