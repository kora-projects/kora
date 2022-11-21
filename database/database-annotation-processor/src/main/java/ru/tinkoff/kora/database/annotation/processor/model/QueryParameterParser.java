package ru.tinkoff.kora.database.annotation.processor.model;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcTypes;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

public final class QueryParameterParser {

    public static List<QueryParameter> parse(Types types, ClassName connectionType, ExecutableElement method, ExecutableType methodType) {
        var result = new ArrayList<QueryParameter>(method.getParameters().size());
        for (int i = 0; i < method.getParameters().size(); i++) {
            var parameter = QueryParameterParser.parse(types, method.getParameters().get(i), methodType.getParameterTypes().get(i), connectionType);
            result.add(parameter);
        }
        return result;
    }

    public static QueryParameter parse(Types types, VariableElement parameter, TypeMirror type, ClassName connectionType) {
        var name = parameter.getSimpleName().toString();
        var typeName = TypeName.get(type);
        if (connectionType.equals(typeName)) {
            return new QueryParameter.ConnectionParameter(name, type, parameter);
        }
        var batch = CommonUtils.findDirectAnnotation(parameter, DbUtils.BATCH_ANNOTATION);
        if (batch != null) {
            if (!(typeName instanceof ParameterizedTypeName ptn && (ptn.rawType.canonicalName().equals("java.util.List")))) {
                throw new ProcessingErrorException("@Batch parameter must be a list", parameter);
            }
            var batchType = ((DeclaredType) type).getTypeArguments().get(0);
            var entity = DbEntity.parseEntity(types, batchType);
            if (entity != null) {
                var param = new QueryParameter.EntityParameter(name, entity, parameter);
                return new QueryParameter.BatchParameter(name, type, parameter, param);
            } else {
                var param = new QueryParameter.SimpleParameter(name, batchType, parameter);
                return new QueryParameter.BatchParameter(name, type, parameter, param);
            }
        }
        var entity = DbEntity.parseEntity(types, type);
        if (entity != null) {
            return new QueryParameter.EntityParameter(name, entity, parameter);
        } else {
            return new QueryParameter.SimpleParameter(name, type, parameter);
        }
    }
}
