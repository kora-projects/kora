package io.koraframework.database.common.annotation.processor.repository.error;

import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcRepository;

@Repository
public interface InvalidParameterUsage extends JdbcRepository {

    @Query("SELECT * FROM table WHERE field3 = :param1.someField")
    String wrongFieldUsedInTemplate(Dto param1, String param2);

    record Dto(String someField, String otherField) {}

}
