package ru.tinkoff.kora.database.annotation.processor.jdbc;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.FieldFactory;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import java.util.List;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.isNullable;

public class StatementSetterGenerator {

    public static CodeBlock generate(ExecutableElement method, QueryWithParameters sqlWithParameters, List<QueryParameter> parameters, @Nullable QueryParameter batchParam, FieldFactory parameterMappers) {
        var b = CodeBlock.builder();
        if (batchParam != null) {
            // one of Iterable<T>, Iterator<T>, Stream<T>
            b.add("for (var _i : $L) {$>\n", batchParam.variable());
        }
        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            if (parameter instanceof QueryParameter.ConnectionParameter) {
                continue;
            }
            var _i = i;
            var parameterName = parameter.name();
            if (parameter instanceof QueryParameter.BatchParameter batchParameter) {
                parameterName = "_i";
                parameter = batchParameter.parameter();
            }
            if (parameter instanceof QueryParameter.SimpleParameter nativeParameter) {
                var sqlParameter = sqlWithParameters.parameters()
                    .stream()
                    .filter(p -> p.methodIndex() == _i)
                    .findFirst()
                    .get();
                var mappingData = CommonUtils.parseMapping(parameter.variable());
                var mapping = mappingData.getMapping(JdbcTypes.PARAMETER_COLUMN_MAPPER);
                var nativeType = JdbcNativeTypes.findNativeType(TypeName.get(parameter.type()));
                if (mapping == null && nativeType != null) {
                    if (isNullable(parameter.variable())) {
                        b.add("if ($L != null) {$>", parameterName);
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.add("\n").add(nativeType.bind("_stmt", parameterName, idx + 1)).add(";");
                        }
                        b.add("$<\n} else {$>");
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.add("\n").add(nativeType.bindNull("_stmt", idx + 1)).add(";");
                        }
                        b.add("$<\n}\n");
                    } else {
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.add(nativeType.bind("_stmt", parameterName, idx + 1)).add(";\n");
                        }
                    }
                } else if (mapping != null && mapping.mapperClass() != null) {
                    var mapper = parameterMappers.get(JdbcTypes.PARAMETER_COLUMN_MAPPER, mapping, parameter.type());
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.add("$L.set(_stmt, $L, $L);\n", mapper, idx + 1, parameterName);
                    }
                } else {
                    var mapper = parameterMappers.get(JdbcTypes.PARAMETER_COLUMN_MAPPER, parameter.type(), parameter.variable());
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.add("$L.set(_stmt, $L, $L);\n", mapper, idx + 1, parameterName);
                    }
                }
            }
            if (parameter instanceof QueryParameter.EntityParameter entityParameter) {
                for (var field : entityParameter.entity().columns()) {
                    var sqlParameter = sqlWithParameters.find(field.queryParameterName(entityParameter.name()));
                    if (sqlParameter == null || sqlParameter.sqlIndexes().isEmpty()) {
                        continue;
                    }
                    var accessor = CodeBlock.of("$N.$N()", parameterName, field.accessor()).toString();
                    var mappingData = CommonUtils.parseMapping(field.element());
                    var mapping = mappingData.getMapping(JdbcTypes.PARAMETER_COLUMN_MAPPER);
                    var nativeType = JdbcNativeTypes.findNativeType(TypeName.get(field.type()));
                    if (mapping == null && nativeType != null) {
                        if (isNullable(field.element())) {
                            b.add("if ($L != null) {$>", accessor);
                            for (var idx : sqlParameter.sqlIndexes()) {
                                b.add("\n").add(nativeType.bind("_stmt", accessor, idx + 1)).add(";");
                            }
                            b.add("$<\n} else {$>");
                            for (var idx : sqlParameter.sqlIndexes()) {
                                b.add("\n").add(nativeType.bindNull("_stmt", idx + 1)).add(";");
                            }
                            b.add("$<\n}\n");
                        } else {
                            for (var idx : sqlParameter.sqlIndexes()) {
                                b.add(nativeType.bind("_stmt", accessor, idx + 1)).add(";\n");
                            }
                        }
                    } else if (mapping == null) {
                        var mapper = parameterMappers.get(JdbcTypes.PARAMETER_COLUMN_MAPPER, field.type(), field.element());
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.add("$L.set(_stmt, $L, $L);\n", mapper, idx + 1, accessor);
                        }
                    } else {
                        var mapper = parameterMappers.get(JdbcTypes.PARAMETER_COLUMN_MAPPER, mapping, field.type());
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.add("$L.set(_stmt, $L, $L);\n", mapper, idx + 1, accessor);
                        }
                    }
                }
            }
        }
        if (batchParam != null) {
            b.add("_stmt.addBatch();$<\n};\n");
        }
        return b.build();
    }
}
