package io.koraframework.database.common.annotation.processor;

import io.koraframework.annotation.processor.common.TestUtils;
import io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor;
import io.koraframework.database.common.annotation.processor.repository.error.InvalidParameterUsage;
import io.koraframework.database.common.annotation.processor.repository.error.UnknownEntityField;
import io.koraframework.database.common.annotation.processor.repository.error.UnknownQueryParameter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class RepositoryErrorsTest {
    @Test
    void testParameterUsage() throws Exception {
        Assertions.assertThatThrownBy(() -> process(InvalidParameterUsage.class))
            .isInstanceOf(TestUtils.CompilationErrorException.class)
            .hasMessageContaining("Query parameter is unused")
            .hasMessageContaining("param2")
            .hasMessageContaining("Problem:")
            .hasMessageContaining("Fix:");
    }

    @Test
    void testUnknownQueryParameter() {
        Assertions.assertThatThrownBy(() -> process(UnknownQueryParameter.class))
            .isInstanceOf(TestUtils.CompilationErrorException.class)
            .hasMessageContaining("SQL query placeholder has no matching method parameter")
            .hasMessageContaining(":userId")
            .hasMessageContaining("Available parameters:")
            .hasMessageContaining(":id")
            .hasMessageContaining("Problem:")
            .hasMessageContaining("Fix:");
    }

    @Test
    void testUnknownEntityField() {
        Assertions.assertThatThrownBy(() -> process(UnknownEntityField.class))
            .isInstanceOf(TestUtils.CompilationErrorException.class)
            .hasMessageContaining("SQL query placeholder has no matching entity field")
            .hasMessageContaining(":dto.name")
            .hasMessageContaining("Available fields for parameter 'dto':")
            .hasMessageContaining(":dto.id")
            .hasMessageContaining("Problem:")
            .hasMessageContaining("Fix:");
    }

    public <T> void process(Class<T> repository) throws Exception {
        TestUtils.annotationProcess(repository, new RepositoryAnnotationProcessor());
    }
}
