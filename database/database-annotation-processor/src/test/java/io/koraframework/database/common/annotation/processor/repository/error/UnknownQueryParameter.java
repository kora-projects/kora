package io.koraframework.database.common.annotation.processor.repository.error;

import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcRepository;

@Repository
public interface UnknownQueryParameter extends JdbcRepository {

    @Query("SELECT * FROM table WHERE id = :userId")
    String wrongParameterName(long id);
}
