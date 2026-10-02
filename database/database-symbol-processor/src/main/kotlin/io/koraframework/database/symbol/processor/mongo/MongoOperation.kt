package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.exception.ProcessingErrorException

class MongoOperation(val kind: Kind, private val annotation: KSAnnotation, private val method: KSFunctionDeclaration) {

    enum class Kind(val annotationName: ClassName) {
        FIND(MongoTypes.find),
        INSERT(MongoTypes.insert),
        UPDATE(MongoTypes.update),
        REPLACE(MongoTypes.replace),
        DELETE(MongoTypes.delete),
        COUNT(MongoTypes.count),
        AGGREGATE(MongoTypes.aggregate)
    }

    companion object {

        fun parse(method: KSFunctionDeclaration): MongoOperation {
            val found = Kind.entries.mapNotNull { kind ->
                method.findAnnotation(kind.annotationName)?.let { MongoOperation(kind, it, method) }
            }

            val owner = method.parentDeclaration?.simpleName?.asString()
            if (found.isEmpty()) {
                throw ProcessingErrorException(
                    """
                    Mongo repository method is invalid:
                      $owner#${method.simpleName.asString()}

                    Problem:
                      Abstract repository method has no Mongo operation annotation.

                    Hint:
                      Kora generates a method body from one of @MongoFind, @MongoInsert, @MongoUpdate, @MongoReplace,
                      @MongoDelete, @MongoCount or @MongoAggregate.

                    Fix:
                      Annotate the method with an operation annotation, or give it a default implementation.
                    """.trimIndent(), method
                )
            }
            if (found.size > 1) {
                throw ProcessingErrorException(
                    """
                    Mongo repository method is invalid:
                      $owner#${method.simpleName.asString()}

                    Problem:
                      Method has more than one Mongo operation annotation: ${found.joinToString(", ") { "@" + it.kind.annotationName.simpleName }}

                    Hint:
                      A repository method maps to exactly one MongoDB operation.

                    Fix:
                      Keep a single operation annotation on the method.
                    """.trimIndent(), method
                )
            }
            return found.first()
        }
    }

    fun string(attribute: String): String {
        val argument = this.annotation.arguments.firstOrNull { it.name?.asString() == attribute } ?: return ""
        // KSP gives no value for an argument that is not a compile-time constant, for example "{'${'$'}set': 1}" with an unescaped template
        val value = argument.value ?: throw ProcessingErrorException(
            """
            Mongo repository method is invalid:
              ${this.method.parentDeclaration?.simpleName?.asString()}#${this.method.simpleName.asString()}

            Problem:
              @${this.kind.annotationName.simpleName}($attribute) is not a compile-time constant string.

            Hint:
              In Kotlin a '${'$'}' followed by a name starts a string template, so "{'${'$'}set': 1}" refers to a variable named 'set'.

            Fix:
              Escape the dollar sign of MongoDB operators: "{'\${'$'}set': 1}".
            """.trimIndent(), this.method
        )
        return value as String
    }

    fun stringOrNull(attribute: String): String? = this.string(attribute).takeIf { it.isNotBlank() }

    fun flag(attribute: String): Boolean = this.annotation.findValue<Boolean>(attribute) ?: false

    fun number(attribute: String): Int = this.annotation.findValue<Int>(attribute) ?: 0
}
