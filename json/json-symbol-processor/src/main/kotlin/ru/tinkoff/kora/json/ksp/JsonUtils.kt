package ru.tinkoff.kora.json.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorField
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

fun isSealed(classDeclaration: KSClassDeclaration): Boolean {
    return classDeclaration.modifiers.contains(Modifier.SEALED)
}

@KspExperimental
fun discriminator(classDeclaration: KSClassDeclaration): String? {
    val discriminatorElement: KSClassDeclaration? = if (classDeclaration.getSealedSubclasses().toList().isNotEmpty()) {
        classDeclaration
    } else {
        findSealedSupertype(classDeclaration)?.declaration as KSClassDeclaration?
    }
    if (discriminatorElement == null) return null
    val discriminatorFieldNameAnnotation = discriminatorElement.getAnnotationsByType(JsonDiscriminatorField::class).firstOrNull()
    return discriminatorFieldNameAnnotation?.value
}

fun findSealedSupertype(classDeclaration: KSClassDeclaration): KSType? {
    return classDeclaration.getAllSuperTypes().firstOrNull { it.declaration is KSClassDeclaration && (it.declaration as KSClassDeclaration).getSealedSubclasses().toList().isNotEmpty() }
}

fun jsonClassPackage(classDeclaration: KSClassDeclaration): String {
    return classDeclaration.packageName.asString()
}

fun jsonWriterName(classDeclaration: KSClassDeclaration): String {
    return classDeclaration.getOuterClassesAsPrefix() + classDeclaration.simpleName.asString() + "JsonWriter"
}

fun jsonWriterName(type: KSType): String {
    val classDeclaration = type.declaration as KSClassDeclaration
    val nullablePrefix = if (type.isMarkedNullable) "Nullable" else ""
    return classDeclaration.getOuterClassesAsPrefix() + nullablePrefix + classDeclaration.simpleName.asString() + "JsonWriter"
}

fun jsonReaderName(type: KSType): String {
    val classDeclaration = type.declaration as KSClassDeclaration
    val nullablePrefix = if (type.isMarkedNullable) "Nullable" else ""
    return classDeclaration.getOuterClassesAsPrefix() + nullablePrefix + classDeclaration.simpleName.asString() + "JsonReader"
}


fun jsonReaderName(classDeclaration: KSClassDeclaration): String {
    return classDeclaration.getOuterClassesAsPrefix() + classDeclaration.simpleName.asString() + "JsonReader"
}
