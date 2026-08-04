package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.JdbcDatabaseModule;
import io.koraframework.database.jdbc.postgres.annotation.Pg;
import io.koraframework.database.jdbc.postgres.annotation.PgJson;
import io.koraframework.database.jdbc.postgres.annotation.PgJsonb;

/**
 * <b>Русский</b>: Модуль JDBC с PostgreSQL специфичными конвертерами: интервалы, коллекции, массивы,
 * range-типы и JSON. Конвертеры типов JDK поставляются с тегом {@link Pg}, а конвертеры JSON — с тегами
 * {@link PgJson} и {@link PgJsonb}, и применяются только к тем полям сущностей и аргументам методов,
 * где соответствующий тег указан явно.
 * <br>
 * Модули можно наследовать и по отдельности, если нужна только часть конвертеров.
 * <hr>
 * <b>English</b>: JDBC module with PostgreSQL specific converters: intervals, collections, arrays, range types and JSON.
 * Converters of JDK types are supplied with the {@link Pg} tag, and JSON converters with the {@link PgJson} and
 * {@link PgJsonb} tags, and are applied only to those entity fields and method arguments where the corresponding tag
 * is specified explicitly.
 * <br>
 * The modules can also be inherited one by one when only a part of the converters is needed.
 */
public interface PostgresJdbcDatabaseModule extends
        JdbcDatabaseModule,
        PgIntervalJdbcMappersModule,
        PgCollectionJdbcMappersModule,
        PgArrayJdbcMappersModule,
        PgRangeJdbcMappersModule,
        PgJsonJdbcMappersModule {
}
