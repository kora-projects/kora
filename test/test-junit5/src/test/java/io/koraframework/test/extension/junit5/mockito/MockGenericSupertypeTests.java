package io.koraframework.test.extension.junit5.mockito;

import io.koraframework.common.annotation.Tag;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.GenericRepository;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestApplication.class)
public class MockGenericSupertypeTests {

    @Mock
    @TestComponent
    private GenericRepository<String> mock;
    @Tag(GenericRepository.class)
    @TestComponent
    private Supplier<String> dependency;

    @BeforeEach
    void setupMocks() {
        Mockito.when(mock.find()).thenReturn("mock");
    }

    @Test
    void mockReplacesComponentInheritingGenericSupertype() {
        assertEquals("mock", mock.find());
        assertEquals("mock", dependency.get());
    }
}
