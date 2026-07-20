package io.koraframework.database.jdbc;

import io.koraframework.database.jdbc.mapper.result.ListCollectionFactory;
import io.koraframework.database.jdbc.mapper.result.SetCollectionFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArrayColumnDataTest {

    @Test
    void withNoopMappingKeepsElementsAndTypeName() {
        var data = ArrayColumnData.withNoopMapping("varchar");

        assertThat(data.sqlTypeName()).isEqualTo("varchar");
        assertThat(data.toDbElement().apply("x")).isEqualTo("x");
        assertThat(data.fromDbElement().apply("y")).isEqualTo("y");
    }

    @Test
    void listFactoryCreatesEmptyList() {
        assertThat(new ListCollectionFactory<String>().create(4)).isInstanceOf(List.class).isEmpty();
    }

    @Test
    void setFactoryCreatesEmptySet() {
        assertThat(new SetCollectionFactory<String>().create(4)).isInstanceOf(Set.class).isEmpty();
    }
}
