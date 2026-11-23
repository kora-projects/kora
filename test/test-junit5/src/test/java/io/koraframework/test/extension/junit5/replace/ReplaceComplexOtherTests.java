package io.koraframework.test.extension.junit5.replace;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestGraphModifier;
import io.koraframework.test.extension.junit5.KoraGraphModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceComplexOtherTests implements KoraAppTestGraphModifier {

    @TestComponent
    private TestApplication.ComplexOther other;

    @NonNull
    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestApplication.ComplexOther.class, () -> (TestApplication.ComplexOther) () -> "12345");
    }

    @Test
    void otherReplaced() {
        assertEquals("12345", other.other());
    }
}
