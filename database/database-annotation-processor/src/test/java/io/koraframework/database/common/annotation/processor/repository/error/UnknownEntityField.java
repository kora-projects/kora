package io.koraframework.database.common.annotation.processor.repository.error;

import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcRepository;

@Repository
public interface UnknownEntityField extends JdbcRepository {

    @Query("SELECT * FROM table WHERE name = :dto.name")
    String wrongEntityField(Dto dto);

    record Dto(long id) {}
}
