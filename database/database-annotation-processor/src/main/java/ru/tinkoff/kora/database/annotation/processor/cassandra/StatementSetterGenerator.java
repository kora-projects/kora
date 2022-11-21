package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Objects;

public class StatementSetterGenerator {
    public static void generate(MethodSpec.Builder b, ExecutableElement method, QueryWithParameters sqlWithParameters, List<QueryParameter> parameters, @Nullable QueryParameter batchParam) {
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
                if (nativeType != null) {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.addCode(nativeType.bind("_stmt", parameterName, idx)).addCode(";\n");
                    }
                } else {
                    for (var idx : sqlParameter.sqlIndexes()) {
                        var mapper = DbUtils.parameterMapperName(method, parameter.variable());
                        b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, idx, parameter.variable());
                    }
                }
                if (isNullable) {
                    b.addCode("$<\n}\n");
                }
            }
            if (parameter instanceof QueryParameter.EntityParameter dtoParameter) {
                for (var field : dtoParameter.entity().entityFields()) {
                    var isNullable = CommonUtils.isNullable(field.element());
                    var sqlParameter = sqlWithParameters.find(dtoParameter.name() + "." + field.element().getSimpleName());
                    if (sqlParameter == null || sqlParameter.sqlIndexes().isEmpty()) {
                        continue;
                    }
                    var fieldAccessor = dtoParameter.entity().entityType() == DbEntity.EntityType.RECORD
                        ? parameterName + "." + field.element().getSimpleName() + "()"
                        : parameterName + ".get" + CommonUtils.capitalize(field.element().getSimpleName().toString()) + "()";
                    if (isNullable) {
                        b.addCode("if ($L == null) {\n", fieldAccessor);
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.addCode("  _stmt.setToNull($L);\n", idx);
                        }
                        b.addCode("} else {$>\n");
                    }
                    var nativeType = CassandraNativeTypes.findNativeType(ClassName.get(field.typeMirror()));
                    if (nativeType != null) {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.addCode(nativeType.bind("_stmt", fieldAccessor, idx)).addCode(";\n");
                        }
                    } else {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            var mapper = DbUtils.parameterMapperName(method, parameter.variable(), field.element().getSimpleName().toString());
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
