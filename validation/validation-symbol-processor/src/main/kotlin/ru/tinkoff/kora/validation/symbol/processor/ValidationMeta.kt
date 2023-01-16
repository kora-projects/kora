package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import kotlin.reflect.KClass

data class ValidatorMeta(
    val source: Type,
    val sourceDeclaration: KSClassDeclaration,
    val validator: ValidatorType,
    val fields: List<Field>
)

data class ValidatedTarget(val target: Type) {
    fun validator(): Type = "ru.tinkoff.kora.validation.common.Validator".asType(listOf(target))
}

data class ValidatorType(val contract: Type, val implementation: Type)

data class Field(
    val type: Type,
    val name: String,
    val isDataClass: Boolean,
    val isNullable: Boolean,
    val constraint: List<Constraint>,
    val validates: List<ValidatedTarget>
) {

    fun accessor(): String = name

    fun isNotNull(): Boolean = !isNullable
}

data class Constraint(val annotation: Type, val factory: Factory) {

    data class Factory(val type: Type, val parameters: Map<String, Any>)
}

data class Type(private val reference: KSTypeReference?, val packageName: String, val simpleName: String, val generic: List<Type>) {

    fun canonicalName(): String {
        return "$packageName.$simpleName"
    }

    @KspExperimental
    fun asPoetType(nullable: Boolean = false): TypeName {
        return if (generic.isEmpty()) {
            ClassName.bestGuess(canonicalName()).copy(nullable)
        } else {
            val genericPoetTypes = generic.asSequence()
                .map { t -> t.asPoetType() }
                .toList()
            ClassName.bestGuess(canonicalName()).parameterizedBy(genericPoetTypes).copy(nullable)
        }
    }

    override fun toString(): String {
        return canonicalName()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Type) return false

        if (packageName != other.packageName) return false
        if (simpleName != other.simpleName) return false
        if (generic != other.generic) return false
        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + simpleName.hashCode()
        result = 31 * result + generic.hashCode()
        return result
    }
}

fun KSTypeReference.asType(): Type {
    val generic = if (this.element != null)
        this.element!!.typeArguments.asSequence()
            .filter { e -> e.type != null }
            .map { e -> e.type!!.asType() }
            .toList()
    else
        emptyList()

    val asType = this.resolve().declaration.qualifiedName!!.asString().asType()
    return Type(this, asType.packageName, asType.simpleName, generic)
}

fun KSType.asType(): Type {
    return this.declaration.qualifiedName.toString().asType()
}

fun KClass<*>.asType(): Type {
    return this.asType(emptyList())
}

fun KClass<*>.asType(generic: List<Type>): Type {
    return Type(null, this.java.packageName, this.java.simpleName, generic)
}

fun String.asType(): Type {
    return this.asType(emptyList())
}

fun String.asType(generic: List<Type>): Type {
    return Type(
        null,
        this.substring(0, this.lastIndexOf('.')),
        this.substring(this.lastIndexOf('.') + 1),
        generic
    )
}
