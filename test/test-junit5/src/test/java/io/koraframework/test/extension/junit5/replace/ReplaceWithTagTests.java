package io.koraframework.test.extension.junit5.replace;

import org.junit.jupiter.api.Test;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestGraphModifier;
import io.koraframework.test.extension.junit5.KoraGraphModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.LifecycleComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent2;
import io.koraframework.test.extension.junit5.testdata.TestComponent23;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class ReplaceWithTagTests implements KoraAppTestGraphModifier {

    @TestComponent
    private TestComponent23 lifecycleComponent23;

    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .replaceComponent(TestComponent2.class, LifecycleComponent.class, () -> new TestComponent2() {
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
