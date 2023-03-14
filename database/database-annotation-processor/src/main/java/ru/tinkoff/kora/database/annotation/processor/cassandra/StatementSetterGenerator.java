package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.FieldFactory;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Objects;

public class StatementSetterGenerator {
    public static void generate(MethodSpec.Builder b, ExecutableElement method, QueryWithParameters sqlWithParameters, List<QueryParameter> parameters, @Nullable QueryParameter batchParam, FieldFactory parameterMappers) {
        if (batchParam != null) {
            b.addCode("var _batch = $T.builder($T.UNLOGGED);\n", CassandraTypes.BATCH_STATEMENT, CassandraTypes.DEFAULT_BATCH_TYPE);
            b.addCode("for (var _param_$L : $L) {$>\n", batchParam.name(), batchParam.name());
        }
        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            if (parameter instanceof QueryParameter.ConnectionParameter) {
                continue;
            }
            var parameterName = parameter.name();
            if (parameter instanceof QueryParameter.BatchParameter batchParameter) {
                parameter = batchParameter.parameter();
                parameterName = "_param_" + parameter.name();
            }
            if (parameter instanceof QueryParameter.SimpleParameter nativeParameter) {
                var isNullable = CommonUtils.isNullable(parameter.variable());
                var sqlParameter = Objects.requireNonNull(sqlWithParameters.find(i));
                if (isNullable) {
                    b.addCode("if ($L == null) {\n", parameter.variable());
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.addCode("  _stmt.setToNull($L);\n", idx);
                    }
                    b.addCode("} else {$>\n");
                }
                var nativeType = CassandraNativeTypes.findNativeType(ClassName.get(parameter.type()));
                var mapping = CommonUtils.parseMapping(parameter.variable()).getMapping(CassandraTypes.PARAMETER_COLUMN_MAPPER);
                if (nativeType != null && mapping == null) {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.addCode(nativeType.bind("_stmt", parameterName, idx)).addCode(";\n");
                    }
                } else if (mapping != null && mapping.mapperClass() != null) {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        var mapper = parameterMappers.get(mapping.mapperClass(), mapping.mapperTags());
                        b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, idx, parameter.variable());
                    }
                } else {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        var mapper = parameterMappers.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, parameter.type(), parameter.variable());
                        b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, idx, parameter.variable());
                    }
                }
                if (isNullable) {
                    b.addCode("$<\n}\n");
                }
            }
            if (parameter instanceof QueryParameter.EntityParameter dtoParameter) {
                for (var field : dtoParameter.entity().columns()) {
                    var isNullable = field.isNullable();
                    var sqlParameter = sqlWithParameters.find(field.queryParameterName(dtoParameter.name()));
                    if (sqlParameter == null || sqlParameter.sqlIndexes().isEmpty()) {
                        continue;
                    }
                    var fieldAccessor = CodeBlock.of("$N.$N()", parameterName, field.accessor()).toString();
                    if (isNullable) {
                        b.addCode("if ($L == null) {\n", fieldAccessor);
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.addCode("  _stmt.setToNull($L);\n", idx);
                        }
                        b.addCode("} else {$>\n");
                    }
                    var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(field.type()));
                    var mapping = CommonUtils.parseMapping(field.element()).getMapping(CassandraTypes.PARAMETER_COLUMN_MAPPER);
                    if (nativeType != null && mapping == null) {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.addCode(nativeType.bind("_stmt", fieldAccessor, idx)).addCode(";\n");
                        }
                    } else if (mapping != null && mapping.mapperClass() != null) {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            var mapper = parameterMappers.get(mapping.mapperClass(), mapping.mapperTags());
                            b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, idx, fieldAccessor);
                        }
                    } else {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            var mapper = parameterMappers.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, field.type(), field.element());
                            b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, idx, fieldAccessor);
                        }
                    }
                    if (isNullable) {
                        b.addCode("$<\n}\n");
                    }
                }
            }
        }
        if (batchParam != null) {
            b.addStatement("var _builtStatement = _stmt.build()");
            b.addStatement("_batch.addStatement(_builtStatement)");
            b.addCode("_stmt = new $T(_builtStatement);$<\n}\n", ClassName.get("com.datastax.oss.driver.api.core.cql", "BoundStatementBuilder"));
            b.addCode("var _s = _batch.build();\n");
        } else {
            b.addCode("var _s = _stmt.build();\n");
        }
    }

}
