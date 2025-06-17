package ru.tinkoff.kora.test.extension.junit5.replace;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceComplexHolderDependencyTests implements KoraAppTestGraphModifier {

    @Tag(TestApplication.ComplexHolder.class)
    @TestComponent
    private String dep;

    @NotNull
    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestApplication.ComplexHolder.class, () -> {
                var mock = Mockito.mock(TestApplication.ComplexHolder.class);
                Mockito.when(mock.other()).thenReturn("other");
                Mockito.when(mock.value()).thenReturn(() -> "wrapped");
                return mock;
            });
    }

    @Test
    void holderDepReplaced() {
        assertEquals("holder-wrapped-other", dep);
    }
}
