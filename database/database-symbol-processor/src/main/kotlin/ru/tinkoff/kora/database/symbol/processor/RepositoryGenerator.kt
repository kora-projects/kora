package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec

interface RepositoryGenerator {
    fun generate(
        repositoryType: KSClassDeclaration,
        typeBuilder: TypeSpec.Builder,
        constructorBuilder: FunSpec.Builder
    ): TypeSpec

    fun repositoryInterface(): KSType?
}
