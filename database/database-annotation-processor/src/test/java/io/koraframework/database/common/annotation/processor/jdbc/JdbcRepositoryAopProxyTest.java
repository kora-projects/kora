package io.koraframework.database.common.annotation.processor.jdbc;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcRepositoryAopProxyTest extends AbstractAnnotationProcessorTest {

    @Test
    public void testAopProxyIsGeneratedForRepository() {
        compile(List.of(new RepositoryAnnotationProcessor(), new AopAnnotationProcessor()), """
            @io.koraframework.database.common.annotation.Repository
            public interface TestRepository extends io.koraframework.database.jdbc.JdbcRepository {
                @io.koraframework.logging.common.annotation.Log
                @io.koraframework.database.common.annotation.Query("SELECT 1")
                void test();
            }
            """);
        compileResult.assertSuccess();

        var repository = compileResult.loadClass("$TestRepository_Impl");
        var proxy = compileResult.loadClass("$TestRepository_Impl__AopProxy");
        assertThat(proxy.getSuperclass()).isEqualTo(repository);
        assertThat(proxy.getInterfaces()).isEmpty();
        assertThat(compileResult.loadClass("TestRepository")).isAssignableFrom(proxy);
    }
}
