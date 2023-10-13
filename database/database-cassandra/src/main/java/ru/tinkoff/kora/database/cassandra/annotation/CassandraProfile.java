package ru.tinkoff.kora.database.cassandra.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Задает имя профиля выполнения из конфигурации, который будет использоваться для запроса.
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
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface CassandraProfile {

    /**
     * @return <b>Русский</b>: Имя профиля выполнения.
     * <hr>
     * <b>English</b>: Name of the execution profile.
     */
    String value();
}
