package ru.tinkoff.kora.database.common.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.MockJdbcExecutor;
import ru.tinkoff.kora.database.common.annotation.processor.repository.NoQueryMethodsRepository;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class NoQueryMethodsRepositoryTest {

    private final MockJdbcExecutor executor = new MockJdbcExecutor();
    private final NoQueryMethodsRepository repository = DbTestUtils.compile(NoQueryMethodsRepository.class, executor);


    @Test
    void testCompiles() throws SQLException {
        assertThat(repository).isNotNull();
    }
}
