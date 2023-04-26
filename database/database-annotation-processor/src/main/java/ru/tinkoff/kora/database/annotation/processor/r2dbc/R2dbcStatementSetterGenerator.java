package ru.tinkoff.kora.database.annotation.processor.r2dbc;

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

public class R2dbcStatementSetterGenerator {

    public static void generate(MethodSpec.Builder b, ExecutableElement method, QueryWithParameters sqlWithParameters, List<QueryParameter> parameters, @Nullable QueryParameter batchParam, FieldFactory parameterMappers) {
        if (batchParam != null) {
            b.addCode("""
                for (int i = 0; i < $L.size(); i++) {
                  var _batch_$L = $L.get(i);$>
                """, batchParam.name(), batchParam.name(), batchParam.name());
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

            if (parameter instanceof QueryParameter.SimpleParameter simpleParameter) {
                var sqlParameter = Objects.requireNonNull(sqlWithParameters.find(i));
                var nativeType = R2dbcNativeTypes.findAndBox(TypeName.get(simpleParameter.type()));
                var mapping = CommonUtils.parseMapping(simpleParameter.variable()).getMapping(R2dbcTypes.PARAMETER_COLUMN_MAPPER);
                if (nativeType != null && mapping == null) {
                    for (var index : sqlParameter.sqlIndexes()) {
                        if (CommonUtils.isNullable(simpleParameter.variable())) {
                            b.addCode("""
                                if($L == null) {
                                  _stmt.bindNull($L, $L.class);
                                } else {
                                  _stmt.bind($L, $L);
                                }
                                """, parameterName, index, nativeType, index, parameterName);
                        } else {
                            b.addCode("_stmt.bind($L, $L);\n", index, parameterName);
                        }
                    }
                } else if (mapping != null && mapping.mapperClass() != null) {
                    var mapper = parameterMappers.get(R2dbcTypes.PARAMETER_COLUMN_MAPPER, mapping, parameter.type());
                    for (var index : sqlParameter.sqlIndexes()) {
                        b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, index, parameterName);
                    }
                } else {
                    var mapper = parameterMappers.get(R2dbcTypes.PARAMETER_COLUMN_MAPPER, simpleParameter.type(), simpleParameter.variable());
                    for (var index : sqlParameter.sqlIndexes()) {
                        b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, index, parameterName);
                    }
                }
            }

            if (parameter instanceof QueryParameter.EntityParameter entityParameter) {
                for (var field : entityParameter.entity().columns()) {
                    var fieldAccessor = CodeBlock.of("$N.$L()", parameterName, field.accessor());

                    var sqlParameter = sqlWithParameters.find(field.queryParameterName(entityParameter.name()));
                    if (sqlParameter == null || sqlParameter.sqlIndexes().isEmpty()) {
                        continue;
                    }
                    var nativeType = R2dbcNativeTypes.findAndBox(TypeName.get(field.type()));
                    var mapping = CommonUtils.parseMapping(field.element()).getMapping(R2dbcTypes.PARAMETER_COLUMN_MAPPER);
                    if (nativeType != null && mapping == null) {
                        for (var index : sqlParameter.sqlIndexes()) {
                            if (CommonUtils.isNullable(field.element())) {
                                b.addCode("""
                                    if($L == null) {
                                      _stmt.bindNull($L, $L.class);
                                    } else {
                                      _stmt.bind($L, $L);
                                    }
                                    """, fieldAccessor, index, nativeType, index, fieldAccessor);
                            } else {
                                b.addCode("_stmt.bind($L, $L);\n", index, fieldAccessor);
                            }
                        }
                    } else if (mapping != null && mapping.mapperClass() != null) {
                        var mapper = parameterMappers.get(R2dbcTypes.PARAMETER_COLUMN_MAPPER, mapping, field.type());
                        for (var index : sqlParameter.sqlIndexes()) {
                            b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, index, fieldAccessor);
                        }
                    } else {
                        var mapper = parameterMappers.get(R2dbcTypes.PARAMETER_COLUMN_MAPPER, field.type(), field.element());
                        for (var index : sqlParameter.sqlIndexes()) {
                            b.addCode("$L.apply(_stmt, $L, $L);\n", mapper, index, fieldAccessor);
                        }
                    }
                }
            }
        }

        if (batchParam != null) {
            b.addCode("""
                if(i != $L.size() - 1) {
                  _stmt.add();
                }""", batchParam.name());
            b.addCode("\n$<}\n");
        }
    }
}
