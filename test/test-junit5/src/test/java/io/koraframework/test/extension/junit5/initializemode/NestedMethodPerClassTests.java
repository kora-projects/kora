package io.koraframework.test.extension.junit5.initializemode;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent1;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(value = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NestedMethodPerClassTests {

    @TestComponent
    private TestComponent1 component1;

    @Test
    void test1() {
        assertNotNull(component1);
    }

    @Nested
    class Nested1 {

        @Test
        void test2(@TestComponent TestComponent12 component12) {
            assertEquals("12", component12.get());
        }
    }
}
