package io.koraframework.test.extension.junit5.inject;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@KoraAppTest(TestApplication.class)
public class InjectPromiseOfTests {

    @TestComponent
    private TestApplication.PromiseHolder promiseHolder;

    @Test
    void promiseOfTargetIsInitialized() {
        assertTrue(promiseHolder.promise().get().isPresent());
    }
}
