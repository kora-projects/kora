package io.koraframework.test.extension.junit5.inject;

import org.junit.jupiter.api.Test;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KoraAppTest(TestApplication.class)
public class InjectWrapperTests {

    @TestComponent
    private TestApplication.SomeChild someChild;
    @TestComponent
    private TestApplication.ComplexWrapped someWrapped;
    @TestComponent
    private Integer someInt;

    @Test
    void testWrapped() {
        assertNotNull(someInt);
        assertNotNull(someChild);
        assertNotNull(someWrapped);
    }
}
