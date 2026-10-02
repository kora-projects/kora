package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.database.symbol.processor.DbUtils
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.FieldFactory
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.parseMappingData

/**
 * Method parameters of a repository operation, and the expressions that turn them into BSON values inside a template.
 */
class MongoParameters(
    private val method: KSFunctionDeclaration,
    private val codecs: FieldFactory,
    private val resolver: Resolver
) {

    class Parameter(val declaration: KSValueParameter, val type: KSType) {
        val name: String get() = this.declaration.name!!.asString()
    }

    val all: List<Parameter> = method.parameters.map { Parameter(it, it.type.resolve().expandTypeAliases(resolver)) }
    private val used = LinkedHashSet<String>()

    /**
     * While a batch operation is generated, the batch parameter stands for the element the loop is on, not for the
     * whole collection.
     */
    private data class BatchBinding(val parameterName: String, val variableName: String, val elementType: KSType)

    private var binding: BatchBinding? = null

    /**
     * @return the parameter annotated with `@Batch`, if the method has one
     */
    fun batchParameter(): Parameter? = this.all.firstOrNull { it.declaration.findAnnotation(DbUtils.batchAnnotation) != null }

    fun bindBatchElement(parameter: Parameter, variableName: String, elementType: KSType) {
        this.binding = BatchBinding(parameter.name, variableName, elementType)
        this.used.add(parameter.name)
    }

    private fun isBound(parameter: Parameter) = this.binding?.parameterName == parameter.name

    private fun baseExpression(parameter: Parameter): CodeBlock =
        if (this.isBound(parameter)) CodeBlock.of("%N", this.binding!!.variableName) else CodeBlock.of("%N", parameter.name)

    private fun baseType(parameter: Parameter): KSType =
        if (this.isBound(parameter)) this.binding!!.elementType else parameter.type

    private fun isBaseNullable(parameter: Parameter) = !this.isBound(parameter) && parameter.type.isMarkedNullable

    fun resolver(): (String) -> CodeBlock = { reference ->
        val dot = reference.indexOf('.')
        val parameterName = if (dot < 0) reference else reference.substring(0, dot)
        val parameter = this.all.firstOrNull { it.name == parameterName }
            ?: throw ProcessingErrorException(
                """
                Mongo query template is invalid:
                  ${this.method.parentDeclaration?.simpleName?.asString()}#${this.method.simpleName.asString()}

                Problem:
                  Template references ':$parameterName', but the method has no such parameter.

                Hint:
                  A placeholder is matched against method parameter names, so ':login' needs a parameter named 'login'.

                Fix:
                  Rename the placeholder or the parameter so that they match.
                """.trimIndent(), this.method
            )
        this.used.add(parameterName)
        if (dot < 0) {
            this.bsonValue(parameter)
        } else {
            this.fieldValue(parameter, reference.substring(dot + 1), reference)
        }
    }

    /**
     * Reads a field of an entity parameter, as in `:user.login`. Only the last segment of the path may be nullable:
     * a nullable link in the middle would have to be null-checked before the next access, which the generated
     * expression can not express.
     */
    private fun fieldValue(parameter: Parameter, path: String, reference: String): CodeBlock {
        var accessor = this.baseExpression(parameter)
        var currentType = this.baseType(parameter)
        var annotated: KSAnnotated = parameter.declaration
        var nullable = this.isBaseNullable(parameter)

        for (segment in path.split('.')) {
            if (nullable) {
                throw ProcessingErrorException(
                    """
                    Mongo query template is invalid:
                      ${this.method.parentDeclaration?.simpleName?.asString()}#${this.method.simpleName.asString()} references ':$reference'

                    Problem:
                      The path goes through a nullable value, so reading the next field could throw.

                    Hint:
                      Only the last segment of a path may be nullable.

                    Fix:
                      Pass the nested value as its own method parameter.
                    """.trimIndent(), this.method
                )
            }

            val declaration = currentType.declaration as? KSClassDeclaration
            val field = declaration?.let { MongoEntity.parse(it, this.resolver).fields.firstOrNull { f -> f.name == segment } }
                ?: throw ProcessingErrorException(
                    """
                    Mongo query template is invalid:
                      ${this.method.parentDeclaration?.simpleName?.asString()}#${this.method.simpleName.asString()} references ':$reference'

                    Problem:
                      Type ${currentType.declaration.qualifiedName?.asString()} has no field '$segment'.

                    Hint:
                      A path reads primary constructor parameters of the type.

                    Fix:
                      Correct the field name, or pass the value as its own method parameter.
                    """.trimIndent(), this.method
                )

            accessor = CodeBlock.of("%L.%N", accessor, field.name)
            currentType = field.type
            annotated = field.annotated
            nullable = field.nullable
        }

        val value = this.bsonValueExpression(currentType, accessor, annotated, 0)
        return if (nullable) {
            CodeBlock.of("if (%L == null) %T.VALUE else %L", accessor, MongoTypes.bsonNull, value)
        } else {
            value
        }
    }

    /**
     * Resolves a parameter referenced by a numeric annotation attribute such as `limit = ":size"`.
     */
    fun requireInt(name: String, attribute: String): Parameter {
        val parameter = this.all.firstOrNull { it.name == name }
        if (parameter == null || parameter.type.declaration.qualifiedName?.asString() != "kotlin.Int") {
            throw ProcessingErrorException(
                """
                Mongo repository method is invalid:
                  ${this.method.parentDeclaration?.simpleName?.asString()}#${this.method.simpleName.asString()}

                Problem:
                  Attribute '$attribute' references ':$name', but the method has no Int parameter with that name.

                Hint:
                  '$attribute' takes an integer literal or a ':name' of an Int method parameter.

                Fix:
                  Add an Int parameter named '$name', or use a literal value.
                """.trimIndent(), this.method
            )
        }
        this.used.add(name)
        return parameter
    }

    fun validateAllUsed() {
        val unused = this.all.filter { it.name !in this.used }.map { it.name }
        if (unused.isNotEmpty()) {
            throw ProcessingErrorException(
                """
                Mongo repository method has unused parameters:
                  ${this.method.parentDeclaration?.simpleName?.asString()}#${this.method.simpleName.asString()}

                Problem:
                  Parameters are never referenced by the operation: ${unused.joinToString(", ")}

                Hint:
                  Every parameter must appear in a template as ':name', or be the entity a write operation stores.

                Fix:
                  Reference the parameter in the template, or remove it from the method.
                """.trimIndent(), this.method
            )
        }
    }

    /**
     * @return the only parameter that no template referenced, which a write operation stores as a document
     */
    fun entityParameter(operation: String): Parameter {
        val candidates = this.all.filter { it.name !in this.used }
        if (candidates.size != 1) {
            throw ProcessingErrorException(
                """
                Mongo repository method is invalid:
                  ${this.method.parentDeclaration?.simpleName?.asString()}#${this.method.simpleName.asString()}

                Problem:
                  $operation needs exactly one parameter holding the document to write, but found ${candidates.size}: ${if (candidates.isEmpty()) "none" else candidates.joinToString(", ") { it.name }}

                Hint:
                  Parameters referenced by a filter template are query arguments, the remaining one is the document.

                Fix:
                  Keep a single entity parameter, and reference every other parameter from the template.
                """.trimIndent(), this.method
            )
        }
        val parameter = candidates.first()
        this.used.add(parameter.name)
        return parameter
    }

    fun bsonValue(parameter: Parameter): CodeBlock {
        val base = this.baseExpression(parameter)
        val value = this.bsonValueExpression(this.baseType(parameter), base, parameter.declaration, 0)
        return if (this.isBaseNullable(parameter)) {
            CodeBlock.of("if (%L == null) %T.VALUE else %L", base, MongoTypes.bsonNull, value)
        } else {
            value
        }
    }

    private fun bsonValueExpression(type: KSType, valueExpr: CodeBlock, annotated: KSAnnotated, depth: Int): CodeBlock {
        val mapping = annotated.parseMappingData().getMapping(MongoTypes.codec)
        if (mapping == null) {
            val nativeType = MongoNativeTypes.find(type.declaration.qualifiedName?.asString())
            if (nativeType != null) {
                return nativeType.bsonValue(valueExpr)
            }
            if ((type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS) {
                return CodeBlock.of("%T(%L.name)", MongoTypes.bsonString, valueExpr)
            }
            val elementType = collectionElementType(type)
            if (elementType != null) {
                val element = "_e$depth"
                return CodeBlock.of(
                    "%T(%L.map { %N -> %L })", MongoTypes.bsonArray, valueExpr, element,
                    this.bsonValueExpression(elementType, CodeBlock.of("%N", element), annotated, depth + 1)
                )
            }
        }

        val codecField = this.codecs.add(mapping, MongoTypes.codec.parameterizedBy(type.toTypeName().copy(false)))
        return CodeBlock.of("%T.encode(this.%N, %L)", MongoTypes.values, codecField, valueExpr)
    }
}
