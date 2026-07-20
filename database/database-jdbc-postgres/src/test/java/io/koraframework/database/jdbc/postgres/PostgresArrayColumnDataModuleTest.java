package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.EnumColumnData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresArrayColumnDataModuleTest {

    enum Status { ACTIVE, INACTIVE }

    private final PostgresArrayColumnDataModule module = new PostgresArrayColumnDataModule() {};

    @Test
    void uuidArrayTypeName() {
        assertThat(module.uuidArrayColumnData().sqlTypeName()).isEqualTo("uuid");
    }

    @Test
    void integerArrayTypeName() {
        assertThat(module.integerArrayColumnData().sqlTypeName()).isEqualTo("int4");
    }

    @Test
    void enumArrayFromEnumColumnData() {
        var data = module.enumArrayColumnData(EnumColumnData.byName(Status.class).withSqlTypeName("status_enum"));

        assertThat(data.sqlTypeName()).isEqualTo("status_enum");
        assertThat(data.toDbElement().apply(Status.ACTIVE)).isEqualTo("ACTIVE");
        assertThat(data.fromDbElement().apply("INACTIVE")).isEqualTo(Status.INACTIVE);
    }

    @Test
    void enumArrayDefaultsToVarcharWhenNoSqlTypeName() {
        var data = module.enumArrayColumnData(EnumColumnData.byName(Status.class));

        assertThat(data.sqlTypeName()).isEqualTo("varchar");
    }

    @Test
    void uuidNoopMappingKeepsElement() {
        var id = UUID.randomUUID();
        assertThat(module.uuidArrayColumnData().toDbElement().apply(id)).isEqualTo(id);
    }
}
