package ru.tinkoff.kora.database.common.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;
import ru.tinkoff.kora.database.common.annotation.processor.repository.error.InvalidParameterUsage;

public class RepositoryErrorsTest {
    @Test
    void testParameterUsage() throws Exception {
        Assertions.assertThatThrownBy(() -> process(InvalidParameterUsage.class))
            .isInstanceOf(TestUtils.CompilationErrorException.class)
            .hasMessageContaining("Parameter usage was not found in query: param2");
    }

    public <T> void process(Class<T> repository) throws Exception {
        TestUtils.annotationProcess(repository, new RepositoryAnnotationProcessor());
    }
}
