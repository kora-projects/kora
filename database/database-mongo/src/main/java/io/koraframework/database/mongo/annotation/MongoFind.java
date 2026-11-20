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
 * <b>Русский</b>: Метод с одним результатом без {@code @Nullable} и без {@code Optional} бросает {@link java.util.NoSuchElementException}, если документ не найден.
 * <hr>
 * <b>English</b>: A single-result method that is neither {@code @Nullable} nor {@code Optional} throws {@link java.util.NoSuchElementException} when no document is found.
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
     * @return <b>Русский</b>: Максимальное число документов: целое число либо {@code :имя} параметра метода типа {@code int}.
     * Пустая строка или 0 означают отсутствие ограничения.
     * <hr>
     * <b>English</b>: Maximum number of documents: an integer, or {@code :name} of an {@code int} method parameter.
     * An empty string or 0 means no limit.
     */
    String limit() default "";

    /**
     * @return <b>Русский</b>: Число пропускаемых документов: целое число либо {@code :имя} параметра метода типа {@code int}.
     * <hr>
     * <b>English</b>: Number of documents to skip: an integer, or {@code :name} of an {@code int} method parameter.
     */
    String skip() default "";

    /**
     * @return <b>Русский</b>: Имя коллекции, пустая строка означает вывод из {@link MongoCollection}.
     * <hr>
     * <b>English</b>: Collection name, an empty string means it is taken from {@link MongoCollection}.
     */
    String collection() default "";
}
