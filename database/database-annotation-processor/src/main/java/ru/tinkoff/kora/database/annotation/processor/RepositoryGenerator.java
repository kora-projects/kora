package ru.tinkoff.kora.database.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.TypeElement;

public interface RepositoryGenerator {
    TypeSpec generate(TypeElement typeElement, TypeSpec.Builder type, MethodSpec.Builder constructor);

    ClassName repositoryInterface();
}
