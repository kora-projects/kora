package io.koraframework.test.extension.junit5.inject;

import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A component can be a {@code Wrapped<X>} and implement a contract of its own at the same time, the way
 * {@code JdbcDataSource}, {@code CassandraSession} and {@code MongoDataSource} do. Asking for that contract must hand
 * out the component itself, not the value it wraps.
 */
@KoraAppTest(TestApplication.class)
public class InjectSelfWrappedComponentTests {

    @TestComponent
    private TestApplication.ComplexOther fieldInjected;

    @Test
    void testFieldInjectionPrefersTheComponentOverItsWrappedValue() {
        assertNotNull(this.fieldInjected);
        assertEquals("1", this.fieldInjected.other());
    }

    @Test
    void testParameterInjectionPrefersTheComponentOverItsWrappedValue(@TestComponent TestApplication.ComplexOther other) {
        assertNotNull(other);
        assertEquals("1", other.other());
    }

    @Test
    void testWrappedValueIsStillAvailableByItsOwnType(@TestComponent TestApplication.ComplexWrapped wrapped) {
        assertNotNull(wrapped);
        assertEquals("1", wrapped.wrapped());
    }
}
