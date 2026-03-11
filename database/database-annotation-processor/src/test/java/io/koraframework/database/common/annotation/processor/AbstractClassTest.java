package io.koraframework.database.common.annotation.processor;

import io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor;
import io.koraframework.database.common.annotation.processor.jdbc.AbstractJdbcRepositoryTest;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractClassTest extends AbstractJdbcRepositoryTest {
    @Test
    public void testAbstractClassRepository() throws SQLException {
        var repository = compile("test", List.of(executor), """
            @Repository
            public abstract class TestRepository implements JdbcRepository {
                private final String field;

                public TestRepository(@Nullable String field) {
                    this.field = field;
                }

                @Query("INSERT INTO table(value) VALUES (:value)")
                public abstract void abstractMethod(String value);

                public void nonAbstractMethod() {
                }
            }
            """);

        repository.invoke("abstractMethod", "some-value");

        Mockito.verify(executor.preparedStatement).setString(1, "some-value");
    }

    @Test
    public void testAbstractClassRepositoryExtension() throws SQLException {
        compile(List.of(new RepositoryAnnotationProcessor(), new KoraAppProcessor()), """
            @Repository
            public abstract class TestRepository implements JdbcRepository {
                @Query("INSERT INTO table(value) VALUES (:value)")
                public abstract void abstractMethod(String value);

                public void nonAbstractMethod() {
                }
            }
            """, """
            @KoraApp
            public interface TestApp {
                default io.koraframework.database.jdbc.JdbcConnectionFactory factory() { return null; }

                @Root
                default Integer someRoot(TestRepository repository) { return 1; }
            }
            """);
        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$TestRepository_Impl")).isFinal();

    }
}
