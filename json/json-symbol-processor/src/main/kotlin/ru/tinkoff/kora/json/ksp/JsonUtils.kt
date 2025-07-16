package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
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

private val RESTRICTED_PACKAGES = listOf("java.", "javax.", "sun.", "com.sun.", "jdk.", "kotlin.")

fun KSDeclaration.isNativePackage(): Boolean {
    val packageOf = this.packageName.asString()
    return RESTRICTED_PACKAGES.stream().anyMatch { s -> packageOf.startsWith(s) }
}

fun KSValueParameter.findJsonField(): KSAnnotation? {
    val parameterAnnotation = findJsonField(this)
    if (parameterAnnotation != null) {
        return parameterAnnotation
    }
    val constructor = this.parent as KSFunctionDeclaration
    val jsonClass = constructor.parentDeclaration as KSClassDeclaration

    val primaryConstructorParameterAnnotation = jsonClass.primaryConstructor?.parameters?.find { it.name?.asString() == this.name?.asString() }?.let {
        findJsonField(it)
    }
    if (primaryConstructorParameterAnnotation != null) {
        return primaryConstructorParameterAnnotation
    }

    val fieldAnnotation = jsonClass.getAllProperties().find { it.simpleName.asString() == this.name?.asString() }?.let {
        findJsonField(it)
    }

    if (jsonClass.isJavaRecord()) {
        return jsonClass.getAllFunctions()
            .filter { it.simpleName.asString() == this.name?.asString() }
            .filter { it.parameters.isEmpty() }
            .filter { it.modifiers.contains(Modifier.PUBLIC) }
            .map { findJsonField(it) }
            .filterNotNull()
            .firstOrNull()
    }

    return fieldAnnotation
}

fun KSFunctionDeclaration.findJsonField(): KSAnnotation? {
    val functionAnnotation = findJsonField(this)
    if (functionAnnotation != null) {
        return functionAnnotation
    }
    val name = this.simpleName.asString()
    val jsonClass = this.parentDeclaration as KSClassDeclaration
    return jsonClass.getAllFunctions()
        .filter { it.isConstructor() }
        .flatMap { it.parameters }
        .filter { it.name?.asString() == name }
        .map { findJsonField(it) }
        .filterNotNull()
        .firstOrNull()
}

fun KSPropertyDeclaration.findJsonField(): KSAnnotation? {
    val fieldAnnotation = findJsonField(this)
    if (fieldAnnotation != null) {
        return fieldAnnotation
    }
    val jsonClass = when (this.parentDeclaration) {
        is KSFunctionDeclaration -> this.parentDeclaration?.parentDeclaration as KSClassDeclaration
        is KSClassDeclaration -> this.parentDeclaration as KSClassDeclaration
        else -> throw IllegalStateException("Unknown parent type for property ${this.parentDeclaration?.qualifiedName?.asString()}: ${this.javaClass}")
    }

    val primaryConstructorParameterAnnotation = jsonClass.primaryConstructor?.parameters?.find { it.name?.asString() == this.simpleName.asString() }?.let {
        findJsonField(it)
    }
    if (primaryConstructorParameterAnnotation != null) {
        return primaryConstructorParameterAnnotation
    }
    return null
}

fun findJsonField(param: KSAnnotated): KSAnnotation? {
    return param.findAnnotation(JsonTypes.jsonFieldAnnotation)
}


fun KSClassDeclaration.discriminatorField(): String? {
    if (this.packageName.asString() == "kotlin") {
        return null
    }
    if (this.modifiers.contains(Modifier.SEALED)) {
        val annotation = this.findAnnotation(JsonTypes.jsonDiscriminatorField)
        if (annotation != null) {
            return annotation.findValue<String>("value")
        }
    }
    for (type in this.superTypes) {
        val supertype = type.resolve().declaration as KSClassDeclaration
        val discriminator = supertype.discriminatorField()
        if (discriminator != null) {
            return discriminator
        }
    }
    return null
}

fun KSClassDeclaration.discriminatorValues(): List<String> {
    return findAnnotation(JsonTypes.jsonDiscriminatorValue)
        ?.findValue<List<String>>("value")
        ?: listOf(this.simpleName.asString())
}


fun detectSealedHierarchyTypeVariables(jsonClassDeclaration: KSClassDeclaration, subclasses: List<KSClassDeclaration>): IdentityHashMap<KSTypeParameter, TypeName> {
    val subclassesSupertypes = subclasses.map { it.getAllSuperTypes().filter { it.declaration == jsonClassDeclaration }.first() }
    val map = IdentityHashMap<KSTypeParameter, TypeName>()
    val jsonClassTypeName = jsonClassDeclaration.toTypeName()

    for ((index, ksTypeParameter) in jsonClassDeclaration.typeParameters.withIndex()) {
        var typeName = (jsonClassTypeName as ParameterizedTypeName).typeArguments[index]
        val subtypeArgs = ArrayList<KSTypeParameter>()
        val rootTypeBounds = ksTypeParameter.bounds.map { it.toTypeName() }.toSet()
        for ((subclassIndex, subclass) in subclasses.withIndex()) {
            val supertype = subclassesSupertypes[subclassIndex]
            val supertypeArgument = supertype.arguments[index].type!!.resolve()
            val argDeclaration = supertypeArgument.declaration
            if (argDeclaration.qualifiedName?.asString() == "kotlin.Nothing") {
                continue
            }
            if (argDeclaration !is KSTypeParameter) {
                typeName = STAR
                continue
            }
            val subtypeArgument = subclass.typeParameters.filter { it.name == argDeclaration.name }.firstOrNull()
            if (subtypeArgument == null) {
                typeName = STAR
                continue
            }
            if (typeName != STAR) {
                subtypeArgs.add(subtypeArgument)
                val subtypeBounds = subtypeArgument.bounds.map { it.toTypeName() }.toSet()
                if (subtypeBounds != rootTypeBounds) {
                    typeName = STAR
                }
            }
        }
        subtypeArgs.forEach { map[it] = typeName }
    }
    return map
}

