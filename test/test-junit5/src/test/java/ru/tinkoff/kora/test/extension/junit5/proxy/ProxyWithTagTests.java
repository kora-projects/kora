package ru.tinkoff.kora.test.extension.junit5.proxy;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication.SomeFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ProxyWithTagTests implements KoraAppTestGraphModifier {

    @Override
    public @Nonnull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .proxyComponent(SomeFactory.class, List.of(String.class), (original) -> {
                return () -> original.getValue() + "2";
            });
    }

    @Test
    void originalWithReplacedBean(@Tag(String.class) @TestComponent SomeFactory someFactory) {
        assertEquals("12", someFactory.getValue());
    }
}
