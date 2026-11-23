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
public class ReplaceComplexWrappedTests implements KoraAppTestGraphModifier {

    @TestComponent
    private TestApplication.ComplexWrapped wrapped;

    @NonNull
    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestApplication.ComplexWrapped.class, () -> (TestApplication.ComplexWrapped) () -> "12345");
    }

    @Test
    void wrappedReplaced() {
        assertEquals("12345", wrapped.wrapped());
    }
}
