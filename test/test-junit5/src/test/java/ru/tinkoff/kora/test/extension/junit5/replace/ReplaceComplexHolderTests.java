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
public class ReplaceComplexHolderTests implements KoraAppTestGraphModifier {

    @TestComponent
    private TestApplication.ComplexHolder holder;

    @NotNull
    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestApplication.ComplexHolder.class, () -> {
                var mock = Mockito.mock(TestApplication.ComplexHolder.class);
                Mockito.when(mock.other()).thenReturn("other");
                return mock;
            });
    }

    @Test
    void holderReplaced() {
        assertEquals("other", holder.other());
    }
}
