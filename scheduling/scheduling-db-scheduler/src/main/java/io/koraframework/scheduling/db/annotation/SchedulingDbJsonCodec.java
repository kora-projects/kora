package io.koraframework.scheduling.db.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a db-scheduler task payload type for JSON serialization.
 *
 * <p>The annotated type must also be annotated with {@code @Json}. The scheduling annotation processors generate
 * a Kora module that provides a {@code SchedulingDbCodec<T>} backed by the generated {@code JsonReader<T>} and
 * {@code JsonWriter<T>}. The scheduling DB serializer discovers these codecs and uses them for matching task
 * payload types, falling back to db-scheduler's default Java serializer when no codec is registered.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface SchedulingDbJsonCodec {}
