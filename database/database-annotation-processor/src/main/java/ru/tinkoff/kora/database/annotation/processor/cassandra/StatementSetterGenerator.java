package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.FieldFactory;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;

import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Objects;

public class StatementSetterGenerator {
    public static CodeBlock generate(ExecutableElement method, QueryWithParameters sqlWithParameters, List<QueryParameter> parameters, @Nullable QueryParameter batchParam, FieldFactory parameterMappers) {
        var b = CodeBlock.builder();
        if (batchParam != null) {
            b.add("var _batch = $T.builder($T.UNLOGGED);\n", CassandraTypes.BATCH_STATEMENT, CassandraTypes.DEFAULT_BATCH_TYPE);
            b.add("for (var _param_$L : $L) {$>\n", batchParam.name(), batchParam.name());
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
                    b.add("if ($L == null) {\n", parameter.variable());
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.add("  _stmt.setToNull($L);\n", idx);
                    }
                    b.add("} else {$>\n");
                }
                var nativeType = CassandraNativeTypes.findNativeType(ClassName.get(parameter.type()));
                var mapping = CommonUtils.parseMapping(parameter.variable()).getMapping(CassandraTypes.PARAMETER_COLUMN_MAPPER);
                if (nativeType != null && mapping == null) {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.add(nativeType.bind("_stmt", parameterName, idx)).add(";\n");
                    }
                } else if (mapping != null && mapping.mapperClass() != null) {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        var mapper = parameterMappers.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, mapping, parameter.type());
                        b.add("$L.apply(_stmt, $L, $L);\n", mapper, idx, parameter.variable());
                    }
                } else {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        var mapper = parameterMappers.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, parameter.type(), parameter.variable());
                        b.add("$L.apply(_stmt, $L, $L);\n", mapper, idx, parameter.variable());
                    }
                }
                if (isNullable) {
                    b.add("$<\n}\n");
                }
            }
            if (parameter instanceof QueryParameter.EntityParameter ep) {
                for (var field : ep.entity().columns()) {
                    var isNullable = field.isNullable();
                    var sqlParameter = sqlWithParameters.find(field.queryParameterName(ep.name()));
                    if (sqlParameter == null || sqlParameter.sqlIndexes().isEmpty()) {
                        continue;
                    }
                    var fieldAccessor = CodeBlock.of("$N.$N()", parameterName, field.accessor()).toString();
                    if (isNullable) {
                        b.add("if ($L == null) {\n", fieldAccessor);
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.add("  _stmt.setToNull($L);\n", idx);
                        }
                        b.add("} else {$>\n");
                    }
                    var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(field.type()));
                    var mapping = CommonUtils.parseMapping(field.element()).getMapping(CassandraTypes.PARAMETER_COLUMN_MAPPER);
                    if (nativeType != null && mapping == null) {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.add(nativeType.bind("_stmt", fieldAccessor, idx)).add(";\n");
                        }
                    } else if (mapping != null && mapping.mapperClass() != null) {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            var mapper = parameterMappers.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, mapping, field.type());
                            b.add("$L.apply(_stmt, $L, $L);\n", mapper, idx, fieldAccessor);
                        }
                    } else {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            var mapper = parameterMappers.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, field.type(), field.element());
                            b.add("$L.apply(_stmt, $L, $L);\n", mapper, idx, fieldAccessor);
                        }
                    }
                    if (isNullable) {
                        b.add("$<\n}\n");
                    }
                }

                var sqlParameter = sqlWithParameters.find(ep.name());
                if (sqlParameter == null || sqlParameter.sqlIndexes().isEmpty()) {
                    continue;
                }

                var isNullable = CommonUtils.isNullable(ep.variable());
                var fieldAccessor = CodeBlock.of("$N", parameterName).toString();
                if (isNullable) {
                    b.add("if ($L == null) {\n", fieldAccessor);
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.add("  _stmt.setToNull($L);\n", idx);
                    }
                    b.add("} else {$>\n");
                }

                var mapping = CommonUtils.parseMapping(ep.entity().typeElement()).getMapping(CassandraTypes.PARAMETER_COLUMN_MAPPER);
                if (mapping != null && mapping.mapperClass() != null) {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        var mapper = parameterMappers.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, mapping, ep.type());
                        b.add("$L.apply(_stmt, $L, $L);\n", mapper, idx, fieldAccessor);
                    }
                } else {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        var mapper = parameterMappers.get(CassandraTypes.PARAMETER_COLUMN_MAPPER, ep.type(), ep.entity().typeElement());
                        b.add("$L.apply(_stmt, $L, $L);\n", mapper, idx, fieldAccessor);
                    }
                }
                if (isNullable) {
                    b.add("$<\n}\n");
                }
            }
        }
        if (batchParam != null) {
            b.addStatement("var _builtStatement = _stmt.build()");
            b.addStatement("_batch.addStatement(_builtStatement)");
            b.add("_stmt = new $T(_builtStatement);$<\n}\n", ClassName.get("com.datastax.oss.driver.api.core.cql", "BoundStatementBuilder"));
            b.add("var _s = _batch.build();\n");
        } else {
            b.add("var _s = _stmt.build();\n");
        }
        return b.build();
    }

}
