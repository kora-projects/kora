package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException
import org.bson.BsonValue

/**
 * The `projection` of a `@MongoFind`: derived from the result type when the attribute is absent, checked against it
 * when it is written by hand.
 */
internal object MongoProjections {

    /**
     * A projection is derived only for an @EntityMongo type: only then does Kora own the codec and know that the
     * field list is exactly what will be read back.
     */
    fun derive(entityType: KSType): CodeBlock? {
        val entity = entityOrNull(entityType) ?: return null
        // A dot in a field name is a literal character (e.g. @Column("addr.city")), but a dot in a projection
        // means a path into a subdocument. We cannot tell the two apart, so we leave the query alone.
        if (entity.fields.any { it.bsonName.contains('.') }) {
            return null
        }

        val b = CodeBlock.builder().add("%T()", MongoTypes.bsonDocument)
        for (field in entity.fields) {
            b.add(".append(%S, %T(1))", field.bsonName, MongoTypes.bsonInt32)
        }
        if (entity.idField == null) {
            b.add(".append(%S, %T(0))", "_id", MongoTypes.bsonInt32)
        }
        return b.build()
    }

    /**
     * Checks a hand-written projection against the result type, so a field missing from an inclusion projection (or
     * dropped by an exclusion projection) fails the build instead of surfacing as a null deep inside the codec.
     * Every case this can not read with certainty is skipped rather than guessed: a placeholder, an operator
     * expression, a non-`@EntityMongo` result type, or a dotted field name all leave the query alone.
     */
    fun validate(method: KSFunctionDeclaration, repository: KSClassDeclaration, entityType: KSType, template: BsonTemplate) {
        if (template.parameters.isNotEmpty()) {
            return
        }
        val entity = entityOrNull(entityType) ?: return
        if (entity.fields.any { it.bsonName.contains('.') }) {
            return
        }

        val included = LinkedHashSet<String>()
        val excluded = LinkedHashSet<String>()
        for ((key, value) in template.document()) {
            when {
                value.isDocument -> return
                isTruthy(value) -> included.add(key)
                isFalsy(value) -> excluded.add(key)
                else -> return
            }
        }

        val includedWithoutId = included.count { it != "_id" }
        val excludedWithoutId = excluded.count { it != "_id" }
        if (includedWithoutId > 0 && excludedWithoutId > 0) {
            throw ProcessingErrorException(
                """
                Mongo projection is invalid:
                  ${repository.simpleName.asString()}#${method.simpleName.asString()}

                Problem:
                  The projection mixes included and excluded fields.

                Hint:
                  MongoDB accepts either an inclusion or an exclusion projection; only '_id' may be excluded from an
                  inclusion projection.

                Fix:
                  Keep either inclusions or exclusions.
                """.trimIndent(), method
            )
        }

        // A lone '_id' key is still an inclusion projection (it returns only _id), so the discriminator can not
        // fall back to "exclusion" just because every other key happens to be '_id'.
        val isInclusion = excludedWithoutId == 0 && included.isNotEmpty()
        for (field in entity.fields) {
            if (field.nullable) {
                continue
            }
            val name = field.bsonName
            val covered = if (isInclusion) {
                included.any { it == name || it.startsWith("$name.") } || (name == "_id" && "_id" !in excluded)
            } else {
                excluded.none { it == name }
            }
            if (covered) {
                continue
            }
            if (isInclusion) {
                throw ProcessingErrorException(
                    """
                    Mongo projection does not cover the result type:
                      ${repository.simpleName.asString()}#${method.simpleName.asString()}

                    Problem:
                      ${entity.declaration.simpleName.asString()}.${field.name} is not nullable, but field '$name' is not included in the projection.

                    Hint:
                      An inclusion projection returns only the listed fields, so every required field of the result type
                      must be listed.

                    Fix:
                      Add '$name' to the projection, make the field nullable, or drop the projection attribute and let Kora
                      derive it from the result type.
                    """.trimIndent(), method
                )
            } else {
                throw ProcessingErrorException(
                    """
                    Mongo projection does not cover the result type:
                      ${repository.simpleName.asString()}#${method.simpleName.asString()}

                    Problem:
                      ${entity.declaration.simpleName.asString()}.${field.name} is not nullable, but field '$name' is not included in the projection.

                    Hint:
                      An exclusion projection returns every field except the listed ones, so a required field of the result type
                      must not be listed.

                    Fix:
                      Remove '$name' from the projection, make the field nullable, or drop the projection attribute and let Kora
                      derive it from the result type.
                    """.trimIndent(), method
                )
            }
        }
    }

    /**
     * [MongoEntity.parse] reports a declaration it can not read as a compile error, so anything that is not an entity
     * to begin with — a driver `Document`, a plain data class — has to be filtered out before it is called.
     */
    private fun entityOrNull(entityType: KSType): MongoEntity? {
        val declaration = entityType.declaration as? KSClassDeclaration ?: return null
        if (declaration.primaryConstructor == null || declaration.findAnnotation(MongoTypes.mongoEntity) == null) {
            return null
        }
        return MongoEntity.parse(declaration)
    }

    /**
     * A number is truthy/falsy by its actual value, not by truncating it to an int first: `asNumber().intValue()`
     * truncates `0.5` to `0` for a `BsonDouble` (a plain `(int)` cast) and round-trips a `BsonDecimal128` through
     * `doubleValue()` anyway, so comparing the double directly is both correct and at least as safe.
     */
    private fun isTruthy(value: BsonValue): Boolean =
        (value.isNumber && value.asNumber().doubleValue() != 0.0) || (value.isBoolean && value.asBoolean().value)

    private fun isFalsy(value: BsonValue): Boolean =
        (value.isNumber && value.asNumber().doubleValue() == 0.0) || (value.isBoolean && !value.asBoolean().value)
}
