package ru.tinkoff.kora.test.extension.junit5.replace;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

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
    void wrappedMocked() {
        assertEquals("12345", someWrapped.toString());
        assertEquals("12345", someContainer.wrapped().toString());
    }
}
