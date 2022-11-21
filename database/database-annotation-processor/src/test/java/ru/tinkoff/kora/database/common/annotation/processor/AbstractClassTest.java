package ru.tinkoff.kora.database.common.annotation.processor;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.MockJdbcExecutor;
import ru.tinkoff.kora.database.common.annotation.processor.repository.AbstractClassRepository;

import java.sql.SQLException;

public class AbstractClassTest {

    private final MockJdbcExecutor executor = new MockJdbcExecutor();
    private final AbstractClassRepository repository = DbTestUtils.compile(AbstractClassRepository.class, "", executor);


    @Test
    void testNativeParameter() throws SQLException {
        repository.abstractMethod("test");

        Mockito.verify(executor.preparedStatement).setString(1, "test");
    }
}
