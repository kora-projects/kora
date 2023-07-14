package ru.tinkoff.kora.test.extension.junit5.replace;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.LifecycleComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent2;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent23;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceWithTagTests implements KoraAppTestGraphModifier {

    @TestComponent
    private TestComponent23 lifecycleComponent23;

    @Override
    public @NotNull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestComponent2.class, List.of(LifecycleComponent.class), () -> new TestComponent2() {
                @Override
                public String get() {
                    return "?";
                }
            });
    }

    @Test
    void originalBeanWithReplacedTaggedBean() {
        assertEquals("?3", lifecycleComponent23.get());
    }
}
