package io.koraframework.test.extension.junit5.replace;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestGraphModifier;
import io.koraframework.test.extension.junit5.KoraGraphModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestExtendModule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
class ReplaceFromOuterClassInNestedTests implements KoraAppTestGraphModifier {

    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestExtendModule.SomeExtendModuleService.class, () -> (TestExtendModule.SomeExtendModuleService) () -> "replaced");
    }

    @Nested
    class NestedReplace {

        @Test
        void outerReplacementApplied(@TestComponent TestExtendModule.SomeExtendModuleService service) {
            assertEquals("replaced", service.getValue());
        }
    }
}
