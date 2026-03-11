package io.koraframework.database.annotation.processor.model;

import io.koraframework.database.annotation.processor.entity.DbEntity;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public sealed interface QueryParameter {
    String name();

    TypeMirror type();

    VariableElement variable();

    record ConnectionParameter(String name, TypeMirror type, VariableElement variable) implements QueryParameter {}

    record SimpleParameter(String name, TypeMirror type, VariableElement variable) implements QueryParameter {}

    record EntityParameter(String name, DbEntity entity, VariableElement variable) implements QueryParameter {
        @Override
        public TypeMirror type() {
            return entity.typeMirror();
        }
    }

    record BatchParameter(String name, TypeMirror type, VariableElement variable, QueryParameter parameter) implements QueryParameter {
        public BatchParameter {
            if (!(parameter instanceof SimpleParameter || parameter instanceof EntityParameter)) {
                throw new IllegalStateException();
            }
        }
    }
}
