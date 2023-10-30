package ru.tinkoff.kora.database.jdbc.mapper.parameter;

import ru.tinkoff.kora.common.Mapping;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * <b>Русский</b>: Контракт для создания конвертер входящих параметров SQL запроса.
 * Предоставляется над аргументом метода через {@link Mapping}.
 * <hr>
 * <b>English</b>: Contract to create a converter of incoming SQL query parameters.
 * Provided over the method argument via {@link Mapping}.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends JdbcRepository {
 *
 *     @Query("INSERT INTO users(fullname) VALUES (:fullName)")
 *     void addUser(@Mapping(MyJdbcParameterColumnMapper.class) String fullName);
 * }
 * }
 * </pre>
 *
 * @see JdbcRepository
 * @see Mapping
 */
public interface JdbcParameterColumnMapper<T> extends Mapping.MappingFunction {
    void set(PreparedStatement stmt, int index, @Nullable T value) throws SQLException;
}
