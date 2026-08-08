package io.koraframework.database.jdbc.postgres;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.annotation.Pg;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PgCollectionParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgCollectionResultColumnMapper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Русский</b>: Конвертеры коллекций в массивы PostgreSQL. Параметр принимается как {@link List}, {@link Set}
 * или {@link Collection}, чтобы не заставлять пересобирать коллекцию ради вызова; результат всегда {@link List} —
 * уникальность задаётся запросом через {@code DISTINCT}.
 * <hr>
 * <b>English</b>: Converters of collections into PostgreSQL arrays. A parameter is accepted as a {@link List},
 * {@link Set} or {@link Collection} so that a collection need not be rebuilt just to make the call; a result is always
 * a {@link List} — uniqueness is expressed by the query via {@code DISTINCT}.
 */
public interface PgCollectionJdbcMappersModule {

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Boolean>> booleanListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Boolean>> booleanSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Boolean>> booleanCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Boolean>> booleanListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Short>> shortListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Short>> shortSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Short>> shortCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Short>> shortListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Integer>> integerListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Integer>> integerSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Integer>> integerCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Integer>> integerListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Long>> longListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Long>> longSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Long>> longCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Long>> longListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Float>> floatListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Float>> floatSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Float>> floatCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Float>> floatListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Double>> doubleListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Double>> doubleSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Double>> doubleCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Double>> doubleListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<BigDecimal>> bigDecimalListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("numeric");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<BigDecimal>> bigDecimalSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("numeric");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<BigDecimal>> bigDecimalCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("numeric");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<BigDecimal>> bigDecimalListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<String>> stringListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("varchar");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<String>> stringSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("varchar");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<String>> stringCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("varchar");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<String>> stringListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<UUID>> uuidListJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("uuid");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<UUID>> uuidSetJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("uuid");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<UUID>> uuidCollectionJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("uuid");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<UUID>> uuidListJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }
}
