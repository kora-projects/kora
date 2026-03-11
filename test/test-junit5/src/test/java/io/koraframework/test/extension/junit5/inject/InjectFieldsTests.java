package io.koraframework.test.extension.junit5.inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.koraframework.common.Tag;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.GenericComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import io.koraframework.test.extension.junit5.testdata.TestComponent1;
import io.koraframework.test.extension.junit5.testdata.TestComponent12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class InjectFieldsTests {

    @TestComponent
    private TestComponent1 component1;
    @TestComponent
    private TestComponent12 component12;
    @TestComponent
    private GenericComponent<String> stringGenericComponentByInterface;
    @TestComponent
    private GenericComponent<Integer> integerGenericComponentByInterface;
    @TestComponent
    private GenericComponent<Long> longGenericComponentByInterface;
    @TestComponent
    private GenericComponent.StringGenericComponent stringGenericComponent;
    @TestComponent
    private GenericComponent.LongGenericComponent longGenericComponent;
    @TestComponent
    @Tag(Tag.Any.class)
    private GenericComponent.IntGenericComponent integerGenericComponent;

    @BeforeEach
    void beforeEach() {
        assertEquals("1", component1.get());
    }

    @AfterEach
    void afterEach() {
        assertEquals("1", component1.get());
    }

    @Test
    void testBean() {
        assertEquals("1", component1.get());
        assertEquals("12", component12.get());
    }

    @Test
    void testGenericComponentByType() {
        assertThat(stringGenericComponentByInterface).isInstanceOf(GenericComponent.StringGenericComponent.class);
        assertThat(integerGenericComponentByInterface).isInstanceOf(GenericComponent.IntGenericComponent.class);
        assertThat(longGenericComponentByInterface).isInstanceOf(GenericComponent.LongGenericComponent.class);
    }

    @Test
    void testGenericComponentByGenericInterface() {
        assertThat(stringGenericComponent).isNotNull();
        assertThat(integerGenericComponent).isNotNull();
        assertThat(longGenericComponent).isNotNull();
    }
}
