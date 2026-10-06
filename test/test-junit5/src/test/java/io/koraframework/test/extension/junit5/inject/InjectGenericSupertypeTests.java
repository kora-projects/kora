package io.koraframework.test.extension.junit5.inject;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.GenericComponent;
import io.koraframework.test.extension.junit5.testdata.GenericRepository;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@KoraAppTest(TestApplication.class)
public class InjectGenericSupertypeTests {

    @TestComponent
    private GenericRepository<String> interfaceOfGenericSuperclass;
    @TestComponent
    private GenericRepository.AbstractGenericRepository<String> genericSuperclass;
    @TestComponent
    private GenericRepository<Integer> interfaceOfSuperclass;

    @Test
    void fieldsInjected() {
        assertEquals("string", interfaceOfGenericSuperclass.find());
        assertEquals("string", genericSuperclass.find());
        assertEquals(1, interfaceOfSuperclass.find());
    }

    @Test
    void parametersInjected(@TestComponent GenericRepository<String> interfaceOfGenericSuperclass,
                            @TestComponent GenericRepository<Integer> interfaceOfSuperclass) {
        assertEquals("string", interfaceOfGenericSuperclass.find());
        assertEquals(1, interfaceOfSuperclass.find());
    }

    @Test
    void wildcardParameterInjected(@TestComponent GenericComponent<? extends Integer> component) {
        assertInstanceOf(GenericComponent.IntGenericComponent.class, component);
    }
}
