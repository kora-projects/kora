package ru.tinkoff.kora.database.common.annotation;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает SQL/CQL запрос для метода репозитория.
 * <hr>
 * <b>English</b>: The annotation specifies the SQL/CQL query for the repository method.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends JdbcRepository {
 *
 *     @Query("INSERT INTO users(fullname) VALUES (:fullName)")
 *     void addUser(String fullName);
 * }
 * }
 * </pre>
 *
 * @see Repository
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Query {

    /**
     * @return <b>Русский</b>: SQL/CQL запрос.
     * <hr>
     * <b>English</b>: SQL/CQL query.
     */
    @Language("SQL")
    String value();
}
