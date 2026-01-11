package ru.tinkoff.kora.database.common.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.AbstractJdbcRepositoryTest;

import java.sql.SQLException;
import java.util.List;

public class QueryParametersTest  extends AbstractJdbcRepositoryTest {
    @Test
    public void testDifferentPatterns() throws SQLException {
        var repository = compileJdbc(List.of(), """
            @Repository
            public interface TestRepository extends JdbcRepository {
                @Query("UPDATE TEST set v1 = :param1, v2 = fun(:param2), v3 = idx[:param3], v4 = :param4;")
                void test(String param1, String param2, String param3, String param4);
            }
            """);
    }

}
