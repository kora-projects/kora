package ru.tinkoff.kora.database.annotation.processor.jdbc;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import java.util.List;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.isNullable;

public class StatementSetterGenerator {

    public static CodeBlock generate(ExecutableElement method, QueryWithParameters sqlWithParameters, List<QueryParameter> parameters, @Nullable QueryParameter batchParam) {
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
                } else {
                    var mapper = DbUtils.parameterMapperName(method, parameter.variable());
                    for (var idx : sqlParameter.sqlIndexes()) {
                        b.add("$L.set(_stmt, $L, $L);\n", mapper, idx + 1, parameterName);
                    }
                }
            }
            if (parameter instanceof QueryParameter.EntityParameter entityParameter) {
                for (var field : entityParameter.entity().entityFields()) {
                    var sqlParameter = sqlWithParameters.find(entityParameter.name() + "." + field.element().getSimpleName());
                    if (sqlParameter == null || sqlParameter.sqlIndexes().isEmpty()) {
                        continue;
                    }
                    var name = entityParameter.entity().entityType() == DbEntity.EntityType.RECORD
                        ? parameterName + "." + field.element().getSimpleName() + "()"
                        : parameterName + ".get" + CommonUtils.capitalize(field.element().getSimpleName().toString()) + "()";
                    var mappingData = CommonUtils.parseMapping(field.element());
                    var mapping = mappingData.getMapping(JdbcTypes.PARAMETER_COLUMN_MAPPER);
                    var nativeType = JdbcNativeTypes.findNativeType(TypeName.get(field.typeMirror()));
                    if (mapping == null && nativeType != null) {
                        if (isNullable(field.element())) {
                            b.add("if ($L != null) {$>", name);
                            for (var idx : sqlParameter.sqlIndexes()) {
                                b.add("\n").add(nativeType.bind("_stmt", name, idx + 1)).add(";");
                            }
                            b.add("$<\n} else {$>");
                            for (var idx : sqlParameter.sqlIndexes()) {
                                b.add("\n").add(nativeType.bindNull("_stmt", idx + 1)).add(";");
                            }
                            b.add("$<\n}\n");
                        } else {
                            for (var idx : sqlParameter.sqlIndexes()) {
                                b.add(nativeType.bind("_stmt", name, idx + 1)).add(";\n");
                            }
                        }
                    } else {
                        var mapper = DbUtils.parameterMapperName(method, parameter.variable(), field.element().getSimpleName().toString());
                        for (var idx : sqlParameter.sqlIndexes()) {
                            b.add("$L.set(_stmt, $L, $L);\n", mapper, idx + 1, name);
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
