package io.koraframework.validation.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.validation.symbol.processor.ValidTypes.VALIDATOR_TYPE
import java.util.stream.Collectors

data class ValidatorMeta(
    val source: TypeName,
    val sourceDeclaration: KSClassDeclaration,
    val validator: ValidatorType,
    val fields: List<Field>
)

enum class Container {
    ITERABLE,
    MAP_KEYS,
    MAP_VALUES
}

/**
 * Validation put on a type and on its type arguments, like `List<@Valid Item>` or `Map<String, @Size(max = 5) String>`
 *
 * @param constraints constraints of the type itself
 * @param validated the type itself when it is marked with `@Valid`
 * @param children validation of the type arguments when the type is a collection or a map
 */
data class TypeUse(val constraints: List<Constraint>, val validated: List<Type>, val children: Map<Container, TypeUse>) {

    fun isEmpty() = constraints.isEmpty() && validated.isEmpty() && children.isEmpty()

    /**
     * @param constraintValidator code that creates a validator from a constraint factory
     * @param validValidator code that refers to a validator of the given type
     * @return validator of the container that checks all its elements in a single pass
     */
    fun containerValidator(constraintValidator: (Constraint.Factory) -> CodeBlock, validValidator: (Type) -> CodeBlock): CodeBlock {
        children[Container.ITERABLE]?.let {
            return CodeBlock.of("%T.iterable(%L)", containerValidators, it.validator(constraintValidator, validValidator))
        }
        return CodeBlock.of(
            "%T.map(%L, %L)",
            containerValidators,
            children[Container.MAP_KEYS]?.validator(constraintValidator, validValidator) ?: CodeBlock.of("null"),
            children[Container.MAP_VALUES]?.validator(constraintValidator, validValidator) ?: CodeBlock.of("null")
        )
    }

    private fun validator(constraintValidator: (Constraint.Factory) -> CodeBlock, validValidator: (Type) -> CodeBlock): CodeBlock {
        val validators = constraints.map { constraintValidator(it.factory) } + validated.map { validValidator(it) } +
            (if (children.isEmpty()) listOf() else listOf(containerValidator(constraintValidator, validValidator)))
        return validators.singleOrNull() ?: CodeBlock.of("%T.all(%L)", containerValidators, validators.joinToCode(", "))
    }

    companion object {
        private val containerValidators = ClassName("io.koraframework.validation.common.constraint", "ContainerValidators")
    }
}

/**
 * @param target validated type
 * @param typeUse validation put on type arguments of the target, `null` when the target itself is marked with `@Valid`
 * @param targetType resolved target that keeps the variance of its type arguments, like `MutableList<out Item>`
 */
data class Validated(val target: Type, val typeUse: TypeUse? = null, val targetType: KSType? = null) {
    fun validator(): Type = validatorOf(target)

    fun validatorTypeName(): TypeName = targetType?.let { VALIDATOR_TYPE.parameterizedBy(it.toTypeName()) } ?: validator().asPoetType()

    fun validatorType(resolver: Resolver): KSType {
        val type = targetType ?: return validator().asKSType(resolver)
        val argument = resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(type), Variance.INVARIANT)
        return resolver.getClassDeclarationByName(VALIDATOR_TYPE.canonicalName)!!.asType(listOf(argument))
    }

    companion object {
        fun validatorOf(type: Type): Type = VALIDATOR_TYPE.canonicalName.asType(listOf(type.copy(isNullable = false)))
    }
}

data class ValidatorType(val contract: TypeName)

data class Field(
    val type: Type,
    val name: String,
    val accessor: String,
    val isDataClass: Boolean,
    val isNullable: Boolean,
    val isNotNull: Boolean,
    val isJsonNullable: Boolean,
    val constraint: List<Constraint>,
    val validates: List<Validated>
) {

    fun accessor(): String = accessor

    fun accessorValue(): String = if(isJsonNullable) "$accessor.value()" else accessor
}

data class Constraint(val annotation: Type, val factory: Factory) {

    data class Factory(val type: Type, val parameters: Map<String, Any>) {

        fun validator(): Type = VALIDATOR_TYPE.canonicalName.asType(type.generic.map { it.copy(isNullable = false) })
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
            rootType?.asType(genericTypes)?.makeNullable() ?: throw IllegalStateException(unresolvedValidationTypeError())
        } else {
            rootType?.asType(genericTypes)?.makeNotNullable() ?: throw IllegalStateException(unresolvedValidationTypeError())
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
            ClassName(packageName, simpleName.split('.')).copy(nullable)
        } else {
            val genericPoetTypes = generic.asSequence()
                .map { t -> t.asPoetType() }
                .toList()
            ClassName(packageName, simpleName.split('.')).parameterizedBy(genericPoetTypes).copy(nullable)
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

    private fun unresolvedValidationTypeError(): String {
        return """
            Kora internal error: validation type declaration cannot be resolved for `$this`.

            The type was collected from validation metadata, but KSP resolver cannot find its declaration.
            Please report this with the validated class and generated validator context.
        """.trimIndent()
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

    val packageName = this.declaration.packageName.asString()
    // a nested class keeps its outer classes (`Outer.Inner`), a type parameter stays a bare name
    val simpleName = if (this.declaration is KSTypeParameter)
        this.declaration.simpleName.asString()
    else
        this.declaration.qualifiedName!!.asString().removePrefix("$packageName.")
    return Type(null, this.isMarkedNullable, packageName, simpleName, generic)
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
