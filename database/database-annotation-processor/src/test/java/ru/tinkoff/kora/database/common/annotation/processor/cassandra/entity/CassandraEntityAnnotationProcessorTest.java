package ru.tinkoff.kora.database.common.annotation.processor.cassandra.entity;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraEntityAnnotationProcessor;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper;
import ru.tinkoff.kora.database.common.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CassandraEntityAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    @Test
    public void testRecordMappersGenerated() {
        compile(List.of(new CassandraEntityAnnotationProcessor()), """
            import ru.tinkoff.kora.database.cassandra.annotation.EntityCassandra;
            
            @EntityCassandra
            public record TestRecord(int id){}
            """);

        assertThat(compileResult.loadClass("$TestRecord_CassandraRowMapper"))
            .isNotNull()
            .isAssignableTo(RowMapper.class);
        assertThat(compileResult.loadClass("$TestRecord_ListCassandraResultSetMapper"))
            .isNotNull()
            .isAssignableTo(CassandraResultSetMapper.class);
        assertThat(compileResult.loadClass("$TestRecord_CassandraResultSetMapper"))
            .isNotNull()
            .isAssignableTo(CassandraResultSetMapper.class);
    }

    @Test
    public void testUnknownFieldTypeRequiresMapper() {
        compile(List.of(new CassandraEntityAnnotationProcessor()), """
            import ru.tinkoff.kora.database.cassandra.annotation.EntityCassandra;
            
            @EntityCassandra
            public record TestRecord(TestRecord id){}
            """);

        var expectedColumnMapper = ParameterizedTypeName.get(ClassName.get(CassandraRowColumnMapper.class), ClassName.get(this.compileResult.loadClass("TestRecord")));

        var rowMapper = compileResult.loadClass("$TestRecord_CassandraRowMapper");
        assertThat(rowMapper.getConstructors()[0].getParameters()).hasSize(1);
        assertThat(TypeName.get(rowMapper.getConstructors()[0].getGenericParameterTypes()[0])).isEqualTo(expectedColumnMapper);

        var resultSetMapper = compileResult.loadClass("$TestRecord_CassandraResultSetMapper");
        assertThat(resultSetMapper.getConstructors()[0].getParameters()).hasSize(1);
        assertThat(TypeName.get(resultSetMapper.getConstructors()[0].getGenericParameterTypes()[0])).isEqualTo(expectedColumnMapper);

        var listResultSetMapper = compileResult.loadClass("$TestRecord_CassandraResultSetMapper");
        assertThat(listResultSetMapper.getConstructors()[0].getParameters()).hasSize(1);
        assertThat(TypeName.get(listResultSetMapper.getConstructors()[0].getGenericParameterTypes()[0])).isEqualTo(expectedColumnMapper);
    }

    @Test
    public void testJavaBeanMappersGenerated() {
        compile(List.of(new CassandraEntityAnnotationProcessor()), """
            import ru.tinkoff.kora.database.cassandra.annotation.EntityCassandra;
            
            @EntityCassandra
            public class TestClass {
                private int id;
            
                public int getId() {
                    return id;
                }
            
                public void setId(int id) {
                    this.id = id;
                }
            }
            """);

        assertThat(compileResult.loadClass("$TestClass_CassandraRowMapper"))
            .isNotNull()
            .isAssignableTo(RowMapper.class);
        assertThat(compileResult.loadClass("$TestClass_CassandraResultSetMapper"))
            .isNotNull()
            .isAssignableTo(CassandraResultSetMapper.class);
        assertThat(compileResult.loadClass("$TestClass_ListCassandraResultSetMapper"))
            .isNotNull()
            .isAssignableTo(CassandraResultSetMapper.class);
    }
}
