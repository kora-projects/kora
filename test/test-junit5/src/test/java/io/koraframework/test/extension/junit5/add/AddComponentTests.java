package io.koraframework.test.extension.junit5.add;

import io.koraframework.common.Tag;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestGraphModifier;
import io.koraframework.test.extension.junit5.KoraGraphModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.LifecycleComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent2;
import io.koraframework.test.extension.junit5.testdata.TestComponent23;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(value = TestApplication.class, components = TestComponent23.class)
public class AddComponentTests implements KoraAppTestGraphModifier {

    @Tag(LifecycleComponent.class)
    @TestComponent
    private TestComponent2 original;
    @Tag(TestComponent23.class)
    @TestComponent
    private LifecycleComponent added;

    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .addComponent(LifecycleComponent.class, TestComponent23.class, () -> (LifecycleComponent) () -> "?");
    }

    @Test
    void addedBean() {
        assertEquals("2", original.get());
        assertEquals("?", added.get());
    }
}
