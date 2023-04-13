package ru.tinkoff.kora.validation.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import java.util.stream.Collectors

val VALID_TYPE = ClassName("ru.tinkoff.kora.validation.common.annotation", "Valid")
val VALIDATE_TYPE = ClassName("ru.tinkoff.kora.validation.common.annotation", "Validate")
val VALIDATED_BY_TYPE = ClassName("ru.tinkoff.kora.validation.common.annotation", "ValidatedBy")
val CONTEXT_TYPE = ClassName("ru.tinkoff.kora.validation.common", "ValidationContext")
val VALIDATOR_TYPE = ClassName("ru.tinkoff.kora.validation.common", "Validator")
val VIOLATION_TYPE = ClassName("ru.tinkoff.kora.validation.common", "Violation")
val EXCEPTION_TYPE = ClassName("ru.tinkoff.kora.validation.common", "ViolationException")

data class ValidatorMeta(
    val source: TypeName,
    val sourceDeclaration: KSClassDeclaration,
    val validator: ValidatorType,
    val fields: List<Field>
)

data class Validated(val target: Type) {
    fun validator(): Type = VALIDATOR_TYPE.canonicalName.asType(listOf(target))
}

data class ValidatorType(val contract: TypeName)

data class Field(
    val type: Type,
    val name: String,
    val isDataClass: Boolean,
    val isNullable: Boolean,
    val constraint: List<Constraint>,
    val validates: List<Validated>
) {

    fun accessor(): String = name

    fun isNotNull(): Boolean = !isNullable
}

data class Constraint(val annotation: Type, val factory: Factory) {

    data class Factory(val type: Type, val parameters: Map<String, Any>) {

        fun validator(): Type = VALIDATOR_TYPE.canonicalName.asType(type.generic)
    }
}

data class Type(private val reference: KSTypeReference?, private val isNullable: Boolean, val packageName: String, val simpleName: String, val generic: List<Type>) {

    fun canonicalName(): String = "$packageName.$simpleName"

    fun asKSType(resolver: Resolver): KSType {
        if (reference != null) {
            return reference.resolve()
        }

        val rootType = resolver.getClassDeclarationByName(canonicalName())
        val genericTypes = generic.asSequence()
            .map { it.asKSTypeArgument(resolver) }
            .toList()

        return if (isNullable) {
            rootType?.asType(genericTypes)?.makeNullable() ?: throw IllegalStateException("Can't extract declaration for: $this")
        } else {
            rootType?.asType(genericTypes)?.makeNotNullable() ?: throw IllegalStateException("Can't extract declaration for: $this")
        }
    }

    fun asKSTypeArgument(resolver: Resolver): KSTypeArgument {
        return if (reference != null) {
            resolver.getTypeArgument(reference, Variance.INVARIANT)
        } else if (generic.isEmpty()) {
            val type = if (isNullable)
                resolver.getClassDeclarationByName(canonicalName())!!.asStarProjectedType().makeNullable()
            else
                resolver.getClassDeclarationByName(canonicalName())!!.asStarProjectedType().makeNotNullable()

            resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(type), Variance.INVARIANT)
        } else {
            resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(asKSType(resolver)), Variance.INVARIANT)
        }
    }

    fun asPoetType(): TypeName = asPoetType(isNullable)

    fun asPoetType(nullable: Boolean): TypeName {
        return if (generic.isEmpty()) {
            ClassName(packageName, simpleName).copy(nullable)
        } else {
            val genericPoetTypes = generic.asSequence()
                .map { t -> t.asPoetType() }
                .toList()
            ClassName(packageName, simpleName).parameterizedBy(genericPoetTypes).copy(nullable)
        }
    }

    override fun toString(): String {
        return if (generic.isEmpty()) {
            canonicalName()
        } else generic.stream()
            .map { it.toString() }
            .collect(Collectors.joining(", ", canonicalName() + "<", ">"))
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
            .filter { it.type != null }
            .map { it.type!!.asType() }
            .toList()
    else
        emptyList()

    val asType = this.resolve().declaration.qualifiedName!!.asString().asType()
    return Type(this, this.resolve().isMarkedNullable, asType.packageName, asType.simpleName, generic)
}

fun KSType.asType(): Type {
    val generic = if (this.arguments.isNotEmpty())
        this.arguments.asSequence()
            .filter { it.type != null }
            .map { it.type!!.asType() }
            .toList()
    else
        emptyList()

    val asType = this.declaration.qualifiedName!!.asString().asType()
    return Type(null, this.isMarkedNullable, this.declaration.packageName.asString(), asType.simpleName, generic)
}

fun String.asType(nullable: Boolean = false): Type = this.asType(emptyList(), nullable)

fun String.asType(generic: List<Type>, nullable: Boolean = false): Type {
    return Type(
        null,
        nullable,
        this.substring(0, this.lastIndexOf('.')),
        this.substring(this.lastIndexOf('.') + 1),
        generic
    )
}
