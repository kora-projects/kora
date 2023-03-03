package ru.tinkoff.kora.database.annotation.processor.vertx;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.FieldFactory;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public class ParametersToTupleBuilder {

    public static void generate(MethodSpec.Builder b, QueryWithParameters sqlWithParameters, ExecutableElement method, List<QueryParameter> parameters, @Nullable QueryParameter batchParam, FieldFactory parameterMappers) {
        if (batchParam != null) {
            b.addCode("var _batchParams = new $T<$T>($L.size());\n", ArrayList.class, VertxTypes.TUPLE, batchParam.variable());
            b.addCode("for (var _i = 0; _i < $L.size(); _i++) {$>\n", batchParam.variable());
            for (var parameter : parameters) {
                if (parameter instanceof QueryParameter.BatchParameter) {
                    b.addCode("var _batch_$L = $L.get(_i);\n", parameter.name(), parameter.name());
                }
            }
        }
        record Param(List<Integer> index, String name, CodeBlock code) {}
        var sqlParams = parameters.stream()
            .filter(Predicate.not(p -> p instanceof QueryParameter.ConnectionParameter))
            .map(p -> {
                if (p instanceof QueryParameter.BatchParameter bp) {
                    return Map.entry("_batch_" + bp.name(), bp.parameter());
                } else {
                    return Map.entry(p.name(), p);
                }
            })
            .<Param>mapMulti((e, sink) -> {
                if (e.getValue() instanceof QueryParameter.SimpleParameter simpleParameter) {
                    var nativeType = VertxNativeTypes.find(TypeName.get(simpleParameter.type()));
                    var mapping = CommonUtils.parseMapping(simpleParameter.variable()).getMapping(VertxTypes.PARAMETER_COLUMN_MAPPER);
                    var sqlParameter = Objects.requireNonNull(sqlWithParameters.find(simpleParameter.variable().getSimpleName().toString()));
                    if (nativeType == null || mapping != null && mapping.mapperClass() == null) {
                        var mapperName = parameterMappers.get(VertxTypes.PARAMETER_COLUMN_MAPPER, simpleParameter.type(), simpleParameter.variable());
                        sink.accept(new Param(
                            sqlParameter.sqlIndexes(),
                            simpleParameter.variable().getSimpleName().toString(),
                            CodeBlock.of("$N.apply($L)", mapperName, e.getKey())
                        ));
                    } else if (mapping != null) {
                        var mapperName = parameterMappers.get(mapping.mapperClass(), mapping.mapperTags());
                        sink.accept(new Param(
                            sqlParameter.sqlIndexes(),
                            simpleParameter.variable().getSimpleName().toString(),
                            CodeBlock.of("$N.apply($L)", mapperName, e.getKey())
                        ));
                    } else {
                        sink.accept(new Param(
                            sqlParameter.sqlIndexes(),
                            simpleParameter.variable().getSimpleName().toString(),
                            CodeBlock.of("$L", e.getKey())
                        ));
                    }
                } else {
                    var entityParam = (QueryParameter.EntityParameter) e.getValue();
                    for (var field : entityParam.entity().entityFields()) {
                        var sqlParameter = sqlWithParameters.find(entityParam.variable().getSimpleName() + "." + field.element().getSimpleName());
                        if (sqlParameter == null || sqlParameter.sqlIndexes().isEmpty()) {
                            continue;
                        }
                        var variableName = entityParam.variable().getSimpleName() + "$" + field.element().getSimpleName();
                        var fieldAccessor = entityParam.entity().entityType() == DbEntity.EntityType.RECORD
                            ? e.getKey() + "." + field.element().getSimpleName() + "()"
                            : e.getKey() + ".get" + CommonUtils.capitalize(field.element().getSimpleName().toString()) + "()";
                        var nativeType = VertxNativeTypes.find(TypeName.get(field.typeMirror()));
                        var mapping = CommonUtils.parseMapping(field.element()).getMapping(VertxTypes.PARAMETER_COLUMN_MAPPER);
                        if (nativeType != null && mapping == null) {
                            sink.accept(new Param(
                                sqlParameter.sqlIndexes(),
                                variableName,
                                CodeBlock.of("$L", fieldAccessor)
                            ));
                        } else if (mapping != null && mapping.mapperClass() != null) {
                            var mapperName = parameterMappers.get(mapping.mapperClass(), mapping.mapperTags());
                            sink.accept(new Param(
                                sqlParameter.sqlIndexes(),
                                variableName,
                                CodeBlock.of("$L.apply($L)", mapperName, fieldAccessor)
                            ));
                        } else {
                            var mapperName = parameterMappers.get(VertxTypes.PARAMETER_COLUMN_MAPPER, field.typeMirror(), field.element());
                            sink.accept(new Param(
                                sqlParameter.sqlIndexes(),
                                variableName,
                                CodeBlock.of("$L.apply($L)", mapperName, fieldAccessor)
                            ));
                        }
                    }
                }
            })
            .toList();
        for (var sqlParam : sqlParams) {
            b.addCode("var _$L = $L;\n", sqlParam.name, sqlParam.code);
        }
        if (sqlParams.isEmpty()) {
            b.addCode("var _tuple = $T.tuple();\n", VertxTypes.TUPLE);
        } else {
            b.addCode("var _tuple = $T.of($>\n", VertxTypes.TUPLE);
            for (int i = 0; i < sqlParams.size(); i++) {
                if (i > 0) {
                    b.addCode(",\n");
                }
                b.addCode("_$L", sqlParams.get(i).name());
            }
            b.addCode("$<\n);\n");
        }
        if (batchParam != null) {
            b.addCode("_batchParams.add(_tuple);\n");
            b.addCode("$<\n}\n");
        }
    }

}
