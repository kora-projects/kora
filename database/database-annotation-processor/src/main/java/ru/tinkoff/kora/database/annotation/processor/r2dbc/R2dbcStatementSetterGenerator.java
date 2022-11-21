package ru.tinkoff.kora.database.annotation.processor.r2dbc;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Objects;

public class R2dbcStatementSetterGenerator {

    public static void generate(MethodSpec.Builder b, ExecutableElement method, QueryWithParameters sqlWithParameters, List<QueryParameter> parameters, @Nullable QueryParameter batchParam) {
        if (batchParam != null) {
            b.addCode("for (var _batch_$L : $L) {$>\n", batchParam.name(), batchParam.name());
        }
        for (int i = 0, sqlIndex = 1; i < parameters.size(); i++, sqlIndex++) {
            var parameter = parameters.get(i);
            if (parameter instanceof QueryParameter.ConnectionParameter) {
                continue;
            }
            var parameterName = parameter.name();
            if (parameter instanceof QueryParameter.BatchParameter batchParameter) {
                parameter = batchParameter.parameter();
                parameterName = "_batch_" + parameter.name();
            }
            if (parameter instanceof QueryParameter.SimpleParameter simpleParamter) {
                var sqlParameter = Objects.requireNonNull(sqlWithParameters.find(i));
                var nativeType = R2dbcNativeTypes.findAndBox(TypeName.get(simpleParamter.type()));
                if (nativeType != null) {
                    for (var index : sqlParameter.sqlIndexes()) {
                        b.addCode("_stmt.bind($L, $L);\n", index, parameterName);
                    }
                } else {
                    var mapper = DbUtils.parameterMapperName(method, simpleParamter.variable());
                    for (var index : sqlParameter.sqlIndexes()) {
                        b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, index, parameterName);
                    }
                }
            }
            if (parameter instanceof QueryParameter.EntityParameter entityParam) {
                for (var field : entityParam.entity().entityFields()) {
                    var fieldAccessor = entityParam.entity().entityType() == DbEntity.EntityType.RECORD
                        ? parameterName + "." + field.element().getSimpleName() + "()"
                        : parameterName + ".get" + CommonUtils.capitalize(field.element().getSimpleName().toString()) + "()";
                    var sqlParameter = sqlWithParameters.find(entityParam.name() + "." + field.element().getSimpleName());
                    var nativeType = R2dbcNativeTypes.findAndBox(TypeName.get(field.typeMirror()));
                    if (nativeType != null) {
                        for (var index : sqlParameter.sqlIndexes()) {
                            b.addCode("_stmt.bind($L, $L);\n", index, fieldAccessor);
                        }
                    } else {
                        var mapper = DbUtils.parameterMapperName(method, parameter.variable(), field.element().getSimpleName().toString());
                        for (var index : sqlParameter.sqlIndexes()) {
                            b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, index, fieldAccessor);
                        }
                    }
                }
            }
        }
        if (batchParam != null) {
            b.addCode("_stmt.add();$<\n");
            b.addCode("}\n");
        }
    }

}
