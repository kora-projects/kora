package ru.tinkoff.kora.test.extension.junit5.replace;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceComplexInterfaceHolderTests implements KoraAppTestGraphModifier {

    @TestComponent
    private TestApplication.ComplexInterfaceHolder<String> holder;

    @NotNull
    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TypeRef.of(TestApplication.ComplexInterfaceHolder.class, String.class), () -> {
                var mock = Mockito.mock(TestApplication.ComplexInterfaceHolder.class);
                Mockito.when(mock.other()).thenReturn("12345");
                return mock;
            });
    }

    @Test
    void holderReplaced() {
        assertEquals("12345", holder.other());
    }
}
