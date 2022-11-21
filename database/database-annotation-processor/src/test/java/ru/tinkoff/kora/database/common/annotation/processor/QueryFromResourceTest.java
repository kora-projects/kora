package ru.tinkoff.kora.database.common.annotation.processor;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.MockJdbcExecutor;
import ru.tinkoff.kora.database.common.annotation.processor.repository.QueryFromResourceRepository;

import java.sql.SQLException;

public class QueryFromResourceTest {

    private final MockJdbcExecutor executor = new MockJdbcExecutor();
    private final QueryFromResourceRepository repository = DbTestUtils.compile(QueryFromResourceRepository.class, executor);


    @Test
    void testNativeParameter() throws SQLException {
        repository.test();

        Mockito.verify(executor.mockConnection).prepareStatement("SELECT 1;\n");
    }
}
