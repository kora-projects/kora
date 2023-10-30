package ru.tinkoff.kora.database.cassandra.annotation;

import ru.tinkoff.kora.database.cassandra.CassandraConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Обозначает специальный <a href="https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useCreateUDT.html">UDT тип</a> данных
 * <hr>
 * <b>English</b>: Sets the name of the execution profile from configuration that will be used for query.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends CassandraRepository {
 *
 *     @CassandraProfile("myProfile")
 *     @Query("INSERT INTO users(fullname) VALUES (:fullName)")
 *     void addUser(String fullName);
 * }
 * }
 * </pre>
 *
 * @see CassandraConfig#profiles()
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UDT {
}
