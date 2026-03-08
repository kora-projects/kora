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

@KoraAppTest(TestApplication.class)
public class AddComponentWithGraphTests implements KoraAppTestGraphModifier {

    @Tag(LifecycleComponent.class)
    @TestComponent
    private TestComponent2 component2;

    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .addComponent(
                LifecycleComponent.class, TestComponent23.class,
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
