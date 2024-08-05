package ru.tinkoff.kora.test.extension.junit5.proxy;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication.SomeFactory;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent1;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent12;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(value = TestApplication.class, components = TestComponent12.class)
public class ProxyWithGraphTests implements KoraAppTestGraphModifier {

    @Override
    public @Nonnull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .proxyComponent(SomeFactory.class, (original, graph) -> {
                return () -> original.getValue() + "2" + graph.getFirst(TestComponent12.class).get();
            });
    }

    @Test
    void originalWithReplacedBean(@TestComponent SomeFactory someFactory) {
        assertEquals("1212", someFactory.getValue());
    }
}
