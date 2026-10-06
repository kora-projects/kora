package io.koraframework.database.mongo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что для сущности нужно сгенерировать BSON кодек во время компиляции.
 * <hr>
 * <b>English</b>: The annotation indicates that a BSON codec should be generated for the entity at compile time.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @EntityMongo
 * @MongoCollection("users")
 * public record User(@Id ObjectId id, String login) {}
 * }
 * </pre>
 *
 * @see MongoCollection
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface EntityMongo {
}
