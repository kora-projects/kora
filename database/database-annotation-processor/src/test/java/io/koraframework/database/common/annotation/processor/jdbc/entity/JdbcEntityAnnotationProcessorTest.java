package io.koraframework.database.common.annotation.processor.jdbc.entity;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.junit.jupiter.api.Test;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.database.annotation.processor.jdbc.JdbcEntityAnnotationProcessor;
import io.koraframework.database.common.RowMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcEntityAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    @Test
    public void testRecordMappersGenerated() {
        compile(List.of(new JdbcEntityAnnotationProcessor()), """
            import io.koraframework.database.jdbc.EntityJdbc;
            
            @EntityJdbc
            public record TestRecord(int id){}
            """);

        assertThat(compileResult.loadClass("$TestRecord_JdbcRowMapper"))
            .isNotNull()
            .isAssignableTo(RowMapper.class);
        assertThat(compileResult.loadClass("$TestRecord_JdbcResultSetMapper"))
            .isNotNull()
            .isAssignableTo(JdbcResultSetMapper.class);
        assertThat(compileResult.loadClass("$TestRecord_ListJdbcResultSetMapper"))
            .isNotNull()
            .isAssignableTo(JdbcResultSetMapper.class);
    }

    @Test
    public void testUnknownFieldTypeRequiresMapper() {
        compile(List.of(new JdbcEntityAnnotationProcessor()), """
            import io.koraframework.database.jdbc.EntityJdbc;
            
            @EntityJdbc
            public record TestRecord(TestRecord id){}
            """);

        var expectedColumnMapper = ParameterizedTypeName.get(ClassName.get(JdbcResultColumnMapper.class), ClassName.get(this.compileResult.loadClass("TestRecord")));

        var rowMapper = compileResult.loadClass("$TestRecord_JdbcRowMapper");
        assertThat(rowMapper.getConstructors()[0].getParameters()).hasSize(1);
        assertThat(TypeName.get(rowMapper.getConstructors()[0].getGenericParameterTypes()[0])).isEqualTo(expectedColumnMapper);

        var resultSetMapper = compileResult.loadClass("$TestRecord_JdbcResultSetMapper");
        assertThat(resultSetMapper.getConstructors()[0].getParameters()).hasSize(1);
        assertThat(TypeName.get(resultSetMapper.getConstructors()[0].getGenericParameterTypes()[0])).isEqualTo(expectedColumnMapper);

        var listResultSetMapper = compileResult.loadClass("$TestRecord_ListJdbcResultSetMapper");
        assertThat(listResultSetMapper.getConstructors()[0].getParameters()).hasSize(1);
        assertThat(TypeName.get(listResultSetMapper.getConstructors()[0].getGenericParameterTypes()[0])).isEqualTo(expectedColumnMapper);
    }

    @Test
    public void testJavaBeanMappersGenerated() {
        compile(List.of(new JdbcEntityAnnotationProcessor()), """
            import io.koraframework.database.jdbc.EntityJdbc;
            
            @EntityJdbc
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

        assertThat(compileResult.loadClass("$TestClass_JdbcRowMapper"))
            .isNotNull()
            .isAssignableTo(RowMapper.class);
        assertThat(compileResult.loadClass("$TestClass_JdbcResultSetMapper"))
            .isNotNull()
            .isAssignableTo(JdbcResultSetMapper.class);
        assertThat(compileResult.loadClass("$TestClass_ListJdbcResultSetMapper"))
            .isNotNull()
            .isAssignableTo(JdbcResultSetMapper.class);
    }
}
