package ru.tinkoff.kora.test.extension.junit5.replace;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceTests implements KoraAppTestGraphModifier {

    @TestComponent
    private Function<String, Integer> replaced;

    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TypeRef.of(Function.class, String.class, Integer.class), () -> (Function<String, Integer>) (s) -> 25);
    }

    @Test
    void replaced() {
        assertEquals(25, replaced.apply("1"));
    }
}
