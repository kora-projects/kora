package ru.tinkoff.kora.database.common.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.AbstractJdbcRepositoryTest;

import java.sql.SQLException;
import java.util.List;

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

}
