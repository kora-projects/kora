package ru.tinkoff.kora.database.common.annotation;

import ru.tinkoff.kora.common.naming.SnakeCaseNameConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;


/**
 * <b>Русский</b>: Указывает поле, которое не должно использоваться в качестве столбца в результатах и параметрах запроса,
 * но которое должно использоваться в качестве набора столбцов родительской сущности.
 * <hr>
 * <b>English</b>: Specifies a field that should not be used as a column in query results and parameters,
 * but that should be used as column set of parent entity.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Table("users")
 * public record User(long id, @Embedded("user_") Name name) {
 *
 *     public record Name(String name, String surname) { }
 * }
 *
 * @Repository
 * public interface UserRepository extends JdbcRepository {
 *
 *     @Query("SELECT u.id, u.name as user_name, u.surname as user_name FROM users u")
 *     List<User> findAll();
 * }
 * }
 * </pre>
 *
 * @see Repository
 * @see Query
 * @see Table
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.CLASS)
public @interface Embedded {

    /**
     * @return <b>Русский</b>: Префикс для колонок встроенных полей,
     * по умолчанию - имя поля сущности, преобразованное с помощью {@link SnakeCaseNameConverter}.
     * <hr>
     * <b>English</b>: Prefix for embedded field columns,
     * default to entity field name converted with {@link SnakeCaseNameConverter}
     */
    String value() default "";
}
