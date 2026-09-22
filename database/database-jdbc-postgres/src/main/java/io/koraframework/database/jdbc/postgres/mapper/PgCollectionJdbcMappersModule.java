package io.koraframework.database.jdbc.postgres.mapper;

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
    default JdbcParameterColumnMapper<List<Boolean>> booleanListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Boolean>> booleanSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Boolean>> booleanCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("bool");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Boolean>> booleanListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Short>> shortListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Short>> shortSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Short>> shortCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int2");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Short>> shortListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Integer>> integerListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Integer>> integerSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Integer>> integerCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Integer>> integerListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Long>> longListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Long>> longSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Long>> longCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("int8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Long>> longListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Float>> floatListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Float>> floatSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Float>> floatCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float4");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Float>> floatListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<Double>> doubleListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<Double>> doubleSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<Double>> doubleCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("float8");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<Double>> doubleListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<BigDecimal>> bigDecimalListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("numeric");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<BigDecimal>> bigDecimalSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("numeric");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<BigDecimal>> bigDecimalCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("numeric");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<BigDecimal>> bigDecimalListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<String>> stringListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("varchar");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<String>> stringSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("varchar");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<String>> stringCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("varchar");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<String>> stringListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<List<UUID>> uuidListPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("uuid");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Set<UUID>> uuidSetPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("uuid");
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Collection<UUID>> uuidCollectionPostgresJdbcParameterColumnMapper() {
        return new PgCollectionParameterColumnMapper<>("uuid");
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<List<UUID>> uuidListPostgresJdbcResultColumnMapper() {
        return new PgCollectionResultColumnMapper<>();
    }
}
