package io.koraframework.test.extension.junit5.mockito;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestGraphModifier;
import io.koraframework.test.extension.junit5.KoraGraphModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.LifecycleTestApplication;
import io.koraframework.test.extension.junit5.testdata.LifecycleTestApplication.CountingLifecycleComponent;
import io.koraframework.test.extension.junit5.testdata.LifecycleTestApplication.DependentComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@KoraAppTest(LifecycleTestApplication.class)
public class MockGraphModificationDependenciesTests implements KoraAppTestGraphModifier {

    static int initBefore;

    @BeforeAll
    static void snapshot() {
        initBefore = CountingLifecycleComponent.INIT.get();
    }

    @Override
    public KoraGraphModification graph() {
        return KoraGraphModification.create()
            .mockComponent(DependentComponent.class, () -> Mockito.mock(DependentComponent.class));
    }

    @Test
    void mockedComponentDependencyNotInitialized(@TestComponent DependentComponent component) {
        assertNull(component.get());
        assertEquals(0, CountingLifecycleComponent.INIT.get() - initBefore);
    }
}
