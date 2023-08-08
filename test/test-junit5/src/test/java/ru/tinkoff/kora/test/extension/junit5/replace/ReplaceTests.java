package ru.tinkoff.kora.test.extension.junit5.replace;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.LifecycleComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceTests implements KoraAppTestGraphModifier {

    @TestComponent
    private LifecycleComponent replaced;

    @Override
    public @Nonnull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(LifecycleComponent.class, () -> (LifecycleComponent) () -> "?");
    }

    @Test
    void replaced() {
        assertEquals("?", replaced.get());
    }
}
