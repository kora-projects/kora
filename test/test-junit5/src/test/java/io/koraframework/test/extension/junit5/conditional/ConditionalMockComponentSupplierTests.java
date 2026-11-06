package io.koraframework.test.extension.junit5.conditional;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestGraphModifier;
import io.koraframework.test.extension.junit5.KoraGraphModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.ConditionalComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
class ConditionalMockComponentSupplierTests implements KoraAppTestGraphModifier {

    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .mockComponent(ConditionalComponent.class, () -> new ConditionalComponent("mocked"));
    }

    @Test
    void mockedConditionalComponentKeepsItsCondition(@TestComponent ConditionalComponent component) {
        assertEquals(new ConditionalComponent("mocked"), component);
    }
}
