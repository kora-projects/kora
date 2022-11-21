package ru.tinkoff.kora.database.common.annotation.processor.repository.error;

import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

@Repository
public interface InvalidParameterUsage extends JdbcRepository {

    @Query("SELECT * FROM table WHERE field3 = :param1.someField")
    String wrongFieldUsedInTemplate(Dto param1, String param2);

    record Dto(String someField, String otherField) {}

}
