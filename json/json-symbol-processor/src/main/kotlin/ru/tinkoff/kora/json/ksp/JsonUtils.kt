package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.generatedClassName
import ru.tinkoff.kora.ksp.common.isJavaRecord
import java.util.*

fun isSealed(classDeclaration: KSClassDeclaration): Boolean {
    return classDeclaration.modifiers.contains(Modifier.SEALED)
}

fun jsonClassPackage(classDeclaration: KSClassDeclaration): String {
    return classDeclaration.packageName.asString()
}

fun KSClassDeclaration.jsonReaderName() = this.generatedClassName("JsonReader")
fun KSClassDeclaration.jsonWriterName() = this.generatedClassName("JsonWriter")

fun findJsonField(param: KSValueParameter, jsonClass: KSClassDeclaration): KSAnnotation? {
    if (jsonClass.isJavaRecord()) {
        return jsonClass.getAllFunctions()
            .firstOrNull { it.simpleName.asString() == param.name!!.asString() && it.parameters.isEmpty() }
            ?.findAnnotation(JsonTypes.jsonFieldAnnotation)
    }
    return findJsonField(param)
}

fun findJsonField(param: KSAnnotated): KSAnnotation? {
    return param.findAnnotation(JsonTypes.jsonFieldAnnotation)
}


fun KSClassDeclaration.discriminatorField(resolver: Resolver): String? {
    if (this.modifiers.contains(Modifier.SEALED)) {
        val annotation = this.findAnnotation(JsonTypes.jsonDiscriminatorField)
        if (annotation != null) {
            return annotation.findValue<String>("value")
        }
    }
    this.superTypes
        .map { it.resolve() }
        .filter { it != resolver.builtIns.anyType }
        .map { it.declaration as KSClassDeclaration }
        .forEach { supertype ->
            val discriminator = supertype.discriminatorField(resolver)
            if (discriminator != null) {
                return discriminator
            }
        }
    return null
}

fun KSClassDeclaration.discriminatorValue(): String {
    return findAnnotation(JsonTypes.jsonDiscriminatorValue)
        ?.findValue<String>("value")
        ?: this.simpleName.asString()
}


fun detectSealedHierarchyTypeVariables(jsonClassDeclaration: KSClassDeclaration, subclasses: List<KSClassDeclaration>): Pair<IdentityHashMap<KSTypeParameter, TypeName>, ArrayList<TypeName>> {
    val subclassesSupertypes = subclasses.map { it.getAllSuperTypes().filter { it.declaration == jsonClassDeclaration }.first() }
    val typeNames = ArrayList<TypeName>(jsonClassDeclaration.typeParameters.size)
    val map = IdentityHashMap<KSTypeParameter, TypeName>()

    for ((index, ksTypeParameter) in jsonClassDeclaration.typeParameters.withIndex()) {
        var typeName = null as TypeName?
        val subtypeArgs = ArrayList<KSTypeParameter>()
        for ((subclassIndex, subclass) in subclasses.withIndex()) {
            val supertype = subclassesSupertypes[subclassIndex]
            val supertypeArgument = supertype.arguments[index].type!!.resolve()
            val argDeclaration = supertypeArgument.declaration
            if (argDeclaration !is KSTypeParameter) {
                typeName = STAR
                continue
            }
            val subtypeArgument = subclass.typeParameters.filter { it.name == argDeclaration.name }.firstOrNull()
            if (subtypeArgument == null) {
                typeName = STAR
                continue
            }
            if (typeName == null || typeName != STAR) {
                subtypeArgs.add(subtypeArgument)
                typeName = if (subtypeArgument.bounds.toList() != ksTypeParameter.bounds.toList()) {
                    STAR
                } else {
                    ksTypeParameter.toTypeVariableName()
                }
            }
        }
        typeNames.add(typeName!!)
        subtypeArgs.forEach { map[it] = typeName }
    }
    return map to typeNames
}

