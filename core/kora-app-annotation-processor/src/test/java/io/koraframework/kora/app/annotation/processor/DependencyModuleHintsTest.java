package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.ClassName;
import io.koraframework.kora.app.annotation.processor.DependencyModuleHintProvider.KoraHint;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.core.json.JsonFactoryBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks hints collected from all kora-module-hints.json files of the repository
 */
class DependencyModuleHintsTest {

    private static final String PG = "io.koraframework.database.jdbc.postgres.annotation.Pg";
    private static final String PG_TIP = "arrays, collections, intervals";
    private static final String PG_JSON_TIP = "json or jsonb column";

    @Test
    void jdbcExecutorIsProvidedByJdbcAndPostgresModules() throws IOException {
        assertThat(modules("io.koraframework.database.jdbc.JdbcExecutor", null)).containsExactlyInAnyOrder(
            "io.koraframework.database.jdbc.JdbcDatabaseModule",
            "io.koraframework.database.jdbc.postgres.PostgresJdbcDatabaseModule"
        );
    }

    @Test
    void configIsProvidedByHoconAndYamlModules() throws IOException {
        assertThat(modules("io.koraframework.config.common.Config", null)).containsExactlyInAnyOrder(
            "io.koraframework.config.hocon.HoconConfigModule",
            "io.koraframework.config.yaml.YamlConfigModule"
        );
    }

    @Test
    void pgTaggedMapperIsProvidedByPostgresModule() throws IOException {
        assertThat(modules("io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper<java.util.List<java.lang.Integer>>", PG))
            .containsExactly("io.koraframework.database.jdbc.postgres.PostgresJdbcDatabaseModule");
    }

    @Test
    void postgresSpecificColumnTypesHavePgTip() throws IOException {
        for (var type : List.of(
            "java.util.List<java.lang.Integer>",
            "kotlin.collections.List<kotlin.Int>",
            "java.lang.Integer[]",
            "int[]",
            "kotlin.IntArray",
            "java.time.Duration",
            "io.koraframework.database.jdbc.postgres.PgRange<java.lang.Long>"
        )) {
            var tips = tips("io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper<" + type + ">", null);
            assertThat(tips).as(type).anyMatch(t -> t.contains(PG_TIP)).noneMatch(t -> t.contains(PG_JSON_TIP));
        }
    }

    @Test
    void otherColumnTypesHavePgJsonTip() throws IOException {
        var tips = tips("io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper<com.example.Payload>", null);
        assertThat(tips).anyMatch(t -> t.contains(PG_JSON_TIP)).noneMatch(t -> t.contains(PG_TIP));
    }

    @Test
    void moduleHintMessage() {
        var hint = new DependencyModuleHintProvider.Hint.ModuleHint(
            ClassName.get("io.koraframework.database.jdbc", "JdbcExecutor"),
            null,
            "io.koraframework:database-jdbc",
            "io.koraframework.database.jdbc.JdbcDatabaseModule"
        );
        assertThat(hint.message().strip()).isEqualTo("""
            io.koraframework.database.jdbc.JdbcExecutor is provided by Kora module io.koraframework.database.jdbc.JdbcDatabaseModule:
            1. Add Gradle dependency: implementation("io.koraframework:database-jdbc")
            2. Extend the @KoraApp interface with io.koraframework.database.jdbc.JdbcDatabaseModule""");
    }

    private static List<String> modules(String type, @Nullable String tag) throws IOException {
        return matching(type, tag).stream()
            .filter(KoraHint.KoraModuleHint.class::isInstance)
            .map(h -> ((KoraHint.KoraModuleHint) h).module())
            .toList();
    }

    private static List<String> tips(String type, @Nullable String tag) throws IOException {
        return matching(type, tag).stream()
            .filter(KoraHint.KoraTipHint.class::isInstance)
            .map(h -> ((KoraHint.KoraTipHint) h).tip())
            .toList();
    }

    private static List<KoraHint> matching(String type, @Nullable String tag) throws IOException {
        try (var r = DependencyModuleHintsTest.class.getResourceAsStream("/kora-hints.json");
             var parser = new JsonFactoryBuilder().build().createParser(r)) {
            return KoraHint.parseList(parser).stream()
                .filter(h -> h.typeRegex().matcher(type).matches())
                .filter(h -> Objects.equals(h.tag(), tag))
                .toList();
        }
    }
}
