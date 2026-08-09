package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.ksp.common.FieldFactory
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.parseMappingData

/**
 * Method parameters of a repository operation, and the expressions that turn them into BSON values inside a template.
 */
class MongoParameters(
    private val method: KSFunctionDeclaration,
    private val codecs: FieldFactory
) {

    class Parameter(val declaration: KSValueParameter, val type: KSType) {
        val name: String get() = this.declaration.name!!.asString()
    }

    val all: List<Parameter> = method.parameters.map { Parameter(it, it.type.resolve()) }
    private val used = LinkedHashSet<String>()

    fun resolver(): (String) -> CodeBlock = { name ->
        val parameter = this.all.firstOrNull { it.name == name }
            ?: throw ProcessingErrorException(
                """
                Mongo query template is invalid:
                  ${this.method.parentDeclaration?.simpleName?.asString()}#${this.method.simpleName.asString()}

                Problem:
                  Template references ':$name', but the method has no such parameter.

                Hint:
                  A placeholder is matched against method parameter names, so ':login' needs a parameter named 'login'.

                Fix:
                  Rename the placeholder or the parameter so that they match.
                """.trimIndent(), this.method
            )
        this.used.add(name)
        this.bsonValue(parameter)
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
        val value = this.bsonValueExpression(parameter.type, CodeBlock.of("%N", parameter.name), parameter, 0)
        return if (parameter.type.isMarkedNullable) {
            CodeBlock.of("if (%N == null) %T.VALUE else %L", parameter.name, MongoTypes.bsonNull, value)
        } else {
            value
        }
    }

    private fun bsonValueExpression(type: KSType, valueExpr: CodeBlock, parameter: Parameter, depth: Int): CodeBlock {
        val mapping = parameter.declaration.parseMappingData().getMapping(MongoTypes.codec)
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
                    this.bsonValueExpression(elementType, CodeBlock.of("%N", element), parameter, depth + 1)
                )
            }
        }

        val codecField = this.codecs.add(mapping, MongoTypes.codec.parameterizedBy(type.toTypeName().copy(false)))
        return CodeBlock.of("%T.encode(this.%N, %L)", MongoTypes.values, codecField, valueExpr)
    }
}
