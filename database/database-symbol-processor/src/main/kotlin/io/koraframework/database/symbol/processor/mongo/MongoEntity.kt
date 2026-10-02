package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import io.koraframework.database.symbol.processor.DbUtils
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.exception.ProcessingErrorException

/**
 * Mongo entity model. Unlike `DbEntity` it keeps nested objects nested: a nested document is mapped by its own
 * codec instead of being flattened into prefixed columns the way SQL modules do.
 */
class MongoEntity(
    val declaration: KSClassDeclaration,
    val fields: List<Field>
) {

    val idField: Field? get() = this.fields.firstOrNull { it.bsonName == "_id" }

    /**
     * Rebuilds the entity from its primary constructor, substituting [idExpr] for the identifier field and reading
     * every other field off [sourceExpr]. Kotlin entities are always data classes, so this is the only way to
     * produce a result that carries the generated `_id`.
     */
    fun rebuildWithId(idExpr: CodeBlock, sourceExpr: CodeBlock): CodeBlock {
        val id = this.idField
        val b = CodeBlock.builder().add("%T(", this.declaration.toClassName())
        this.fields.forEachIndexed { index, field ->
            if (index > 0) {
                b.add(", ")
            }
            b.add(if (field === id) idExpr else CodeBlock.of("%L.%N", sourceExpr, field.name))
        }
        return b.add(")").build()
    }

    class Field(
        val parameter: KSValueParameter,
        val annotated: KSAnnotated,
        val type: KSType,
        val bsonName: String,
        val nullable: Boolean
    ) {
        val name: String get() = parameter.name!!.asString()
        val variableName: String get() = "_$name"
    }

    companion object {

        /**
         * @param resolver expands typealiases in field types; without it field types stay as declared, which is enough
         * for callers that only read field names
         */
        fun parse(declaration: KSClassDeclaration, resolver: Resolver? = null): MongoEntity {
            val constructor = declaration.primaryConstructor
                ?: throw ProcessingErrorException(
                    """
                    Mongo entity type is invalid:
                      ${declaration.qualifiedName?.asString()}

                    Problem:
                      @EntityMongo type has no primary constructor.

                    Hint:
                      Kora builds an entity from its primary constructor parameters.

                    Fix:
                      Turn the type into a data class, or supply a custom Codec for it.
                    """.trimIndent(), declaration
                )

            val properties = declaration.getAllProperties().associateBy { it.simpleName.asString() }
            val fields = constructor.parameters.map { parameter ->
                val name = parameter.name!!.asString()
                val property = properties[name]
                val annotated: KSAnnotated = if (property != null && property.findAnnotation(DbUtils.columnAnnotation) != null) property else parameter
                rejectEmbedded(parameter, property, declaration)

                val type = parameter.type.resolve().let { if (resolver != null) it.expandTypeAliases(resolver) else it }
                Field(
                    parameter = parameter,
                    annotated = annotated,
                    type = type,
                    bsonName = parseBsonName(name, parameter, property),
                    nullable = type.isMarkedNullable
                )
            }

            val entity = MongoEntity(declaration, fields)
            validate(entity)
            return entity
        }

        private fun validate(entity: MongoEntity) {
            if (entity.fields.isEmpty()) {
                throw ProcessingErrorException(
                    """
                    Mongo entity has no fields:
                      ${entity.declaration.qualifiedName?.asString()}

                    Problem:
                      @EntityMongo type has no persistable fields, so the generated codec would produce an empty document.

                    Hint:
                      Kora maps primary constructor parameters of the entity.

                    Fix:
                      Add fields to the entity, or remove @EntityMongo from this type.
                    """.trimIndent(), entity.declaration
                )
            }

            val seen = HashMap<String, String>()
            for (field in entity.fields) {
                val previous = seen.put(field.bsonName, field.name)
                if (previous != null) {
                    throw ProcessingErrorException(
                        """
                        Mongo entity has duplicate document field:
                          $previous and ${field.name} both map to '${field.bsonName}'

                        Problem:
                          Two entity fields map to the same BSON document field, so one of them would silently overwrite the other.

                        Hint:
                          A document field name comes from @Column, or from @Id which maps to '_id', or from the field name itself.

                        Fix:
                          Give one of the fields a distinct @Column name.
                        """.trimIndent(), entity.declaration
                    )
                }
            }

            val id = entity.idField
            if (id != null && id.nullable && id.type.makeNotNullable().declaration.qualifiedName?.asString() != "org.bson.types.ObjectId") {
                throw ProcessingErrorException(
                    """
                    Mongo entity field is invalid:
                      ${entity.declaration.qualifiedName?.asString()}.${id.name}

                    Problem:
                      Field mapped to '_id' is nullable but is not an ObjectId.

                    Hint:
                      When '_id' is absent the server generates an ObjectId, which a ${id.type} field can not read back.

                    Fix:
                      Declare the field as ObjectId, or make it non-nullable and assign the identifier yourself.
                    """.trimIndent(), entity.declaration
                )
            }
        }

        private fun rejectEmbedded(parameter: KSValueParameter, property: KSAnnotated?, declaration: KSClassDeclaration) {
            if (parameter.findAnnotation(DbUtils.embeddedAnnotation) != null || property?.findAnnotation(DbUtils.embeddedAnnotation) != null) {
                throw ProcessingErrorException(
                    """
                    Mongo entity field is invalid:
                      ${declaration.qualifiedName?.asString()}#${parameter.name?.asString()}

                    Problem:
                      @Embedded is not supported for Mongo entities.

                    Hint:
                      @Embedded flattens a nested object into prefixed columns, which only makes sense for tabular databases.
                      MongoDB stores a nested object as a nested document instead.

                    Fix:
                      Remove @Embedded and annotate the nested type with @EntityMongo, or supply a Codec for it.
                    """.trimIndent(), declaration
                )
            }
        }

        private fun parseBsonName(name: String, parameter: KSValueParameter, property: KSAnnotated?): String {
            val column = parameter.findAnnotation(DbUtils.columnAnnotation) ?: property?.findAnnotation(DbUtils.columnAnnotation)
            if (column != null) {
                return column.findValueNoDefault<String>("value")!!
            }
            if (parameter.isAnnotationPresent(DbUtils.idAnnotation) || property?.isAnnotationPresent(DbUtils.idAnnotation) == true) {
                return "_id"
            }
            return name
        }
    }
}
