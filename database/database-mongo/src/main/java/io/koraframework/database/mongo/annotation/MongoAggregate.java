package io.koraframework.database.mongo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что метод репозитория выполняет конвейер агрегации.
 * <hr>
 * <b>English</b>: The annotation indicates that the repository method runs an aggregation pipeline.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @MongoAggregate("[{\"$match\": {\"active\": true}}]")
 * List<CityStat> statsByCity();
 * }
 * </pre>
 *
 * @see MongoCollection
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MongoAggregate {

    /**
     * @return <b>Русский</b>: Конвейер агрегации в виде BSON массива этапов.
     * <hr>
     * <b>English</b>: Aggregation pipeline as a BSON array of stages.
     */
    String value();

    /**
     * @return <b>Русский</b>: Имя коллекции, пустая строка означает вывод из {@link MongoCollection}.
     * <hr>
     * <b>English</b>: Collection name, an empty string means it is taken from {@link MongoCollection}.
     */
    String collection() default "";
}
