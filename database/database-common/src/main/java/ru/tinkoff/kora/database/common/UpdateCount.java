package ru.tinkoff.kora.database.common;

import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;

/**
 * <b>Русский</b>: Специальный тип в качестве возвращаемого значения для получения информации
 * о количестве затронутых строк в ходе выполнения запроса в базу данных.
 * <hr>
 * <b>English</b>: A special type as a return value to retrieve information about
 * the number of affected rows during the execution of a database query.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends JdbcRepository {
 *
 *     @Query("INSERT INTO users(fullname) VALUES (:fullName)")
 *     UpdateCount addUser(String fullName);
 * }
 * }
 * </pre>
 *
 * @see Repository
 * @see Query
 */
public record UpdateCount(long value) {}
