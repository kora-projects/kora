package ru.tinkoff.kora.test.extension.junit5.replace;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceWithGraphTests implements KoraAppTestGraphModifier {

    @Override
    public @NotNull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestComponent12.class, graph -> {
                var component1 = graph.getFirst(TestComponent1.class);
                return new TestComponent12(component1) {
                    @Override
                    public String get() {
                        return "?" + component1.get();
                    }
                };
            });
    }

    @Test
    void originalWithReplacedBean(@TestComponent TestComponent1 component1,
                                  @TestComponent TestComponent12 replace12) {
        assertEquals("1", component1.get());
        assertEquals("?1", replace12.get());
    }
}
