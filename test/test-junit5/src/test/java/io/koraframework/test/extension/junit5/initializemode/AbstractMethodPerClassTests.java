package io.koraframework.test.extension.junit5.initializemode;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent1;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(value = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractMethodPerClassTests {

    @Test
    void inheritedTest(@TestComponent TestComponent12 component12) {
        assertEquals("12", component12.get());
    }

    final static class MethodPerClassChild extends AbstractMethodPerClassTests {

        @TestComponent
        private TestComponent1 component1;

        @Test
        void childTest() {
            assertNotNull(component1);
        }
    }
}
