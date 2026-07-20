package io.koraframework.database.jdbc;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumColumnDataTest {

    @Test
    void withSqlTypeNameAndFromValueFactoryComposeTogether() {
        Function<String, Status> custom = v -> "on".equals(v) ? Status.ACTIVE : Status.INACTIVE;
        var data = EnumColumnData.byName(Status.class)
            .withSqlTypeName("status_enum")
            .withFromValueFactory(custom);

        assertThat(data.sqlTypeName()).isEqualTo("status_enum");
        assertThat(data.valueGetter().apply(Status.ACTIVE)).isEqualTo("ACTIVE");
        assertThat(data.fromValueFactory().apply("on")).isEqualTo(Status.ACTIVE);
        assertThat(data.fromValueFactory().apply("whatever")).isEqualTo(Status.INACTIVE);
    }

    @Test
    void ofWithSqlTypeNameAndFromValueFactoryComposeTogether() {
        Function<String, Coded> custom = v -> "x".equals(v) ? Coded.A : Coded.B;
        var data = EnumColumnData.of(Coded.class, Coded::code)
            .withSqlTypeName("coded_enum")
            .withFromValueFactory(custom);

        assertThat(data.sqlTypeName()).isEqualTo("coded_enum");
        assertThat(data.valueGetter().apply(Coded.A)).isEqualTo("a");
        assertThat(data.valueGetter().apply(Coded.B)).isEqualTo("b");
        assertThat(data.fromValueFactory().apply("x")).isEqualTo(Coded.A);
        assertThat(data.fromValueFactory().apply("y")).isEqualTo(Coded.B);
    }

    enum Status {ACTIVE, INACTIVE}

    enum Coded {
        A("a"), B("b");
        final String code;

        Coded(String code) {this.code = code;}

        String code() {return code;}
    }

    @Test
    void byNameRoundTrips() {
        var data = EnumColumnData.byName(Status.class);

        assertThat(data.valueGetter().apply(Status.ACTIVE)).isEqualTo("ACTIVE");
        assertThat(data.fromValueFactory().apply("INACTIVE")).isEqualTo(Status.INACTIVE);
        assertThat(data.sqlTypeName()).isNull();
    }

    @Test
    void byOrdinalRoundTrips() {
        var data = EnumColumnData.byOrdinal(Status.class);

        assertThat(data.valueGetter().apply(Status.ACTIVE)).isEqualTo(0);
        assertThat(data.valueGetter().apply(Status.INACTIVE)).isEqualTo(1);
        assertThat(data.fromValueFactory().apply(1)).isEqualTo(Status.INACTIVE);
    }

    @Test
    void ofUsesCustomValueGetterAndAutoReverse() {
        var data = EnumColumnData.of(Coded.class, Coded::code);

        assertThat(data.valueGetter().apply(Coded.A)).isEqualTo("a");
        assertThat(data.fromValueFactory().apply("b")).isEqualTo(Coded.B);
    }

    @Test
    void autoReverseThrowsOnUnknownValue() {
        var data = EnumColumnData.of(Coded.class, Coded::code);

        assertThatThrownBy(() -> data.fromValueFactory().apply("zzz"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zzz");
    }

    @Test
    void byStrategyAppliesStrategyToName() {
        var data = EnumColumnData.byStrategy(Status.class, JdbcEnumValueMappingStrategy.lowerCasing());

        assertThat(data.valueGetter().apply(Status.ACTIVE)).isEqualTo("active");
        assertThat(data.fromValueFactory().apply("inactive")).isEqualTo(Status.INACTIVE);
    }

    @Test
    void withSqlTypeNameSetsIt() {
        var data = EnumColumnData.byName(Status.class).withSqlTypeName("status_enum");

        assertThat(data.sqlTypeName()).isEqualTo("status_enum");
        assertThat(data.valueGetter().apply(Status.ACTIVE)).isEqualTo("ACTIVE");
    }
}
