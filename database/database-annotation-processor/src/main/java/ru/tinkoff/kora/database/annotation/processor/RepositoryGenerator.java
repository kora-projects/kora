package ru.tinkoff.kora.database.annotation.processor;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

public interface RepositoryGenerator {
    TypeSpec generate(TypeElement typeElement, TypeSpec.Builder type, MethodSpec.Builder constructor);

    @Nullable
    // todo TypeName
    TypeMirror repositoryInterface();
}
