package io.koraframework.test.extension.junit5.initializemode;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent1;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Nested classes here never touch the outer instance, so javac leaves out the synthetic outer instance field.
 */
@KoraAppTest(TestApplication.class)
class NestedWithoutOuterReferenceTests {

    @Nested
    class NestedField {

        @TestComponent
        private TestComponent1 component1;

        @Test
        void fieldInjected() {
            assertNotNull(component1);
        }
    }

    @Nested
    class NestedMethod {

        @Test
        void parameterInjected(@TestComponent TestComponent12 component12) {
            assertEquals("12", component12.get());
        }
    }
}
