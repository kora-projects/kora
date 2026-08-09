package io.koraframework.database.mongo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что метод репозитория выполняет поиск документов.
 * <hr>
 * <b>English</b>: The annotation indicates that the repository method performs a document search.
 * <br>
 * <br>
 * <b>Русский</b>: Параметры метода подставляются по имени через {@code :имя} и всегда занимают целое значение BSON.
 * <hr>
 * <b>English</b>: Method parameters are bound by name via {@code :name} and always occupy a whole BSON value.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @MongoFind(filter = "{\"login\": :login}")
 * Optional<User> findByLogin(String login);
 * }
 * </pre>
 *
 * @see MongoCollection
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MongoFind {

    /**
     * @return <b>Русский</b>: Фильтр поиска в виде BSON документа.
     * <hr>
     * <b>English</b>: Search filter as a BSON document.
     */
    String filter() default "{}";

    /**
     * @return <b>Русский</b>: Проекция возвращаемых полей, пустая строка означает все поля.
     * <hr>
     * <b>English</b>: Projection of the returned fields, an empty string means all fields.
     */
    String projection() default "";

    /**
     * @return <b>Русский</b>: Сортировка результатов, пустая строка означает порядок по умолчанию.
     * <hr>
     * <b>English</b>: Result sort order, an empty string means the natural order.
     */
    String sort() default "";

    /**
     * @return <b>Русский</b>: Максимальное число документов, где 0 означает отсутствие ограничения.
     * <hr>
     * <b>English</b>: Maximum number of documents, where 0 means no limit.
     */
    int limit() default 0;

    /**
     * @return <b>Русский</b>: Число пропускаемых документов.
     * <hr>
     * <b>English</b>: Number of documents to skip.
     */
    int skip() default 0;

    /**
     * @return <b>Русский</b>: Имя коллекции, пустая строка означает вывод из {@link MongoCollection}.
     * <hr>
     * <b>English</b>: Collection name, an empty string means it is taken from {@link MongoCollection}.
     */
    String collection() default "";
}
