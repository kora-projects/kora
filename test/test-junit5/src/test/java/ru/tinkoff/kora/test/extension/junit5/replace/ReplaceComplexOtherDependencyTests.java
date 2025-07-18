package ru.tinkoff.kora.test.extension.junit5.replace;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceComplexOtherDependencyTests implements KoraAppTestGraphModifier {

    @Tag(TestApplication.ComplexOther.class)
    @TestComponent
    private String dep;

    @NotNull
    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestApplication.ComplexOther.class, () -> (TestApplication.ComplexOther) () -> "12345");
    }

    @Test
    void otherDepReplaced() {
        assertEquals("other-12345", dep);
    }
}
