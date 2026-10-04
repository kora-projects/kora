package io.koraframework.test.extension.junit5.replace;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import io.koraframework.common.annotation.Tag;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestGraphModifier;
import io.koraframework.test.extension.junit5.KoraGraphModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceComplexOtherDependencyTests implements KoraAppTestGraphModifier {

    @Tag(TestApplication.ComplexOther.class)
    @TestComponent
    private String dep;

    @NonNull
    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestApplication.ComplexOther.class, () -> (TestApplication.ComplexOther) () -> "12345");
    }

    @Test
    void otherDepReplaced() {
        assertEquals("other-12345", dep);
    }
}
