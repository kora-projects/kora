package io.koraframework.test.extension.junit5.replace;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestGraphModifier;
import io.koraframework.test.extension.junit5.KoraGraphModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceWrappedTests implements KoraAppTestGraphModifier {

    @TestComponent
    private TestApplication.SomeWrapped someWrapped;
    @TestComponent
    private TestApplication.SomeContainer someContainer;

    @NotNull
    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestApplication.SomeWrapped.class, () -> new TestApplication.SomeWrapped() {
                @Override
                public String toString() {
                    return "12345";
                }
            });
    }

    @Test
    void wrappedReplaced() {
        assertEquals("12345", someWrapped.toString());
        assertEquals("12345", someContainer.wrapped().toString());
    }
}
