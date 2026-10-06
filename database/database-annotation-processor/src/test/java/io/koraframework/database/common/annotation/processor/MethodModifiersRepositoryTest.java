package io.koraframework.database.common.annotation.processor;

import io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor;
import io.koraframework.database.common.annotation.processor.jdbc.AbstractJdbcRepositoryTest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodModifiersRepositoryTest extends AbstractJdbcRepositoryTest {
    @Test
    public void testInterfacePublicMethod() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                void test();
            }
            """);
    }

    @Test
    public void testAbstractClassPublicMethod() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public abstract class TestRepository implements JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                public abstract void test();
            }
            """);
    }

    @Test
    public void testAbstractClassProtectedMethod() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public abstract class TestRepository implements JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                protected abstract void test();
            }
            """);
    }

    @Test
    public void testAbstractClassPackagePrivateMethod() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public abstract class TestRepository implements JdbcRepository {
                @Query("INSERT INTO test(value) VALUES ('value')")
                protected abstract void test();
            }
            """);
    }

    @Test
    public void testDeprecatedMethodHasNoLintWarnings() {
        compileWithLint(List.of(new RepositoryAnnotationProcessor()), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                /** @deprecated use other */
                @Deprecated
                @Query("DELETE FROM test")
                void deleteAll();
            }
            """);
        compileResult.assertSuccess();
        assertThat(compileResult.lintWarnings()).isEmpty();
    }
}
