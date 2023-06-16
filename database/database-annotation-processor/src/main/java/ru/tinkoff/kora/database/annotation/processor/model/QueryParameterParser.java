package ru.tinkoff.kora.database.annotation.processor.model;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

public final class QueryParameterParser {

    public static List<QueryParameter> parse(Types types, ClassName connectionType, ClassName parameterMapper, ExecutableElement method, ExecutableType methodType) {
        return parse(types, List.of(connectionType), parameterMapper, method, methodType);
    }

    public static List<QueryParameter> parse(Types types, List<ClassName> connectionTypes, ClassName parameterMapper, ExecutableElement method, ExecutableType methodType) {
        var result = new ArrayList<QueryParameter>(method.getParameters().size());
        for (int i = 0; i < method.getParameters().size(); i++) {
            var parameter = QueryParameterParser.parse(types, method.getParameters().get(i), methodType.getParameterTypes().get(i), connectionTypes, parameterMapper);
            result.add(parameter);
        }
        return result;
    }

    public static QueryParameter parse(Types types, VariableElement parameter, TypeMirror type, List<ClassName> connectionTypes, ClassName parameterMapper) {
        var name = parameter.getSimpleName().toString();
        var typeName = TypeName.get(type);
        for (var connectionType : connectionTypes) {
            if (connectionType.equals(typeName)) {
                return new QueryParameter.ConnectionParameter(name, type, parameter);
            }
        }
        var mapping = CommonUtils.parseMapping(parameter).getMapping(parameterMapper);
        var batch = CommonUtils.findDirectAnnotation(parameter, DbUtils.BATCH_ANNOTATION);
        if (batch != null) {
            if (!(typeName instanceof ParameterizedTypeName ptn && (ptn.rawType.canonicalName().equals("java.util.List")))) {
                throw new ProcessingErrorException("@Batch parameter must be a list", parameter);
            }
            var batchType = ((DeclaredType) type).getTypeArguments().get(0);
            final QueryParameter param;
            if (mapping != null) {
                param = new QueryParameter.SimpleParameter(name, batchType, parameter);
            } else {
                var entity = DbEntity.parseEntity(types, batchType);
                if (entity != null) {
                    param = new QueryParameter.EntityParameter(name, entity, parameter);
                } else {
                    param = new QueryParameter.SimpleParameter(name, batchType, parameter);
                }
            }
            return new QueryParameter.BatchParameter(name, type, parameter, param);
        }
        if (mapping != null) {
            return new QueryParameter.SimpleParameter(name, type, parameter);
        }
        var entity = DbEntity.parseEntity(types, type);
        if (entity != null) {
            return new QueryParameter.EntityParameter(name, entity, parameter);
        } else {
            return new QueryParameter.SimpleParameter(name, type, parameter);
        }
    }
}
