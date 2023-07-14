package ru.tinkoff.kora.test.extension.junit5.add;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Tag;
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
public class AddComponentWithGraphTests implements KoraAppTestGraphModifier {

    @Override
    public @NotNull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .addComponent(
                LifecycleComponent.class, List.of(TestComponent23.class),
                g -> {
                    final TestComponent2 simpleComponent2 = g.getFirst(TestComponent2.class, LifecycleComponent.class);
                    return (LifecycleComponent) () -> "?" + simpleComponent2.get();
                });
    }

    @Test
    void originalWithAddedBean(@Tag(TestComponent23.class) @TestComponent LifecycleComponent lifecycleComponent23) {
        assertEquals("?2", lifecycleComponent23.get());
    }
}
