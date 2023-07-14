package ru.tinkoff.kora.test.extension.junit5.mock;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.MockComponent;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@KoraAppTest(TestApplication.class)
public class MockGraphAddedTests {

    @Test
    void mock(@MockComponent TestComponent1 component1) {
        assertNull(component1.get());
        Mockito.when(component1.get()).thenReturn("?");
        assertEquals("?", component1.get());
    }

    @Test
    void mockWithTag(@Tag(LifecycleComponent.class) @MockComponent TestComponent2 mock) {
        assertNull(mock.get());
        Mockito.when(mock.get()).thenReturn("?");
        assertEquals("?", mock.get());
    }

    @Test
    void beanWithTaggedMock(@Tag(LifecycleComponent.class) @MockComponent TestComponent2 mock,
                            @TestComponent TestComponent23 component23) {
        assertNull(mock.get());
        Mockito.when(mock.get()).thenReturn("?");
        assertEquals("?", mock.get());

        assertEquals("?3", component23.get());
    }
}
