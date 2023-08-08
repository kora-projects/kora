package ru.tinkoff.kora.test.extension.junit5.mock;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.*;

import javax.annotation.Nonnull;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@KoraAppTest(TestApplication.class)
public class MockGraphModificationTests implements KoraAppTestGraphModifier {
    @TestComponent
    TestComponent1 component1;
    @Tag(LifecycleComponent.class)
    @TestComponent
    TestComponent2 component;
    @TestComponent
    TestComponent23 component23;

    @Override
    public @Nonnull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .mockComponent(TestComponent1.class)
            .mockComponent(TestComponent2.class, List.of(LifecycleComponent.class));
    }

    @Test
    void mockFromGraph() {
        assertNull(component1.get());
        Mockito.when(component1.get()).thenReturn("?");
        assertEquals("?", component1.get());
    }

    @Test
    void mockFromGraphWithTag() {
        assertNull(component.get());
        Mockito.when(component.get()).thenReturn("?");
        assertEquals("?", component.get());
    }

    @Test
    void mockBeanDependency() {
        assertNull(component.get());
        Mockito.when(component.get()).thenReturn("?");
        assertEquals("?", component.get());

        assertEquals("?3", component23.get());
    }
}
