package ru.tinkoff.kora.database.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.TypeElement;

public interface RepositoryGenerator {
    TypeSpec generate(TypeElement typeElement, TypeSpec.Builder type, MethodSpec.Builder constructor);

    ClassName repositoryInterface();
}
