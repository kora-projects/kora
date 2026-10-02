package io.koraframework.database.symbol.processor.mongo

import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.CodeBlock
import io.koraframework.ksp.common.exception.ProcessingErrorException
import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.BsonType
import org.bson.BsonValue
import org.bson.json.JsonReader

/**
 * A BSON document or pipeline written in an operation annotation, with `:name` placeholders for method parameters.
 * The template is parsed at compile time and emitted as literal document construction, so nothing is parsed at
 * runtime and a malformed template fails the build.
 */
class BsonTemplate private constructor(
    private val root: BsonValue,
    val parameters: List<String>
) {

    companion object {
        private const val MARKER_PREFIX = "__kora_placeholder_"
        private const val MARKER_SUFFIX = "__"

        fun parseDocument(template: String, node: KSAnnotated, attribute: String): BsonTemplate {
            val replaced = replacePlaceholders(template)
            return try {
                checkDuplicateKeys(replaced.first)
                BsonTemplate(checkValues(BsonDocument.parse(replaced.first)), replaced.second)
            } catch (e: RuntimeException) {
                throw ProcessingErrorException(parseError(template, attribute, "document", e), node)
            }
        }

        fun parseArray(template: String, node: KSAnnotated, attribute: String): BsonTemplate {
            val replaced = replacePlaceholders(template)
            return try {
                checkDuplicateKeys(replaced.first)
                BsonTemplate(checkValues(BsonArray.parse(replaced.first)), replaced.second)
            } catch (e: RuntimeException) {
                throw ProcessingErrorException(parseError(template, attribute, "array", e), node)
            }
        }

        /**
         * The driver parser keeps the last of duplicate keys silently, so the template is walked once more to reject them.
         */
        private fun checkDuplicateKeys(json: String) {
            JsonReader(json).use { checkDuplicateKeys(it, it.readBsonType()) }
        }

        private fun checkDuplicateKeys(reader: JsonReader, type: BsonType) {
            when (type) {
                BsonType.DOCUMENT -> {
                    reader.readStartDocument()
                    val names = HashSet<String>()
                    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                        val name = reader.readName()
                        require(names.add(name)) { "duplicate key '$name'" }
                        checkDuplicateKeys(reader, reader.currentBsonType)
                    }
                    reader.readEndDocument()
                }

                BsonType.ARRAY -> {
                    reader.readStartArray()
                    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                        checkDuplicateKeys(reader, reader.currentBsonType)
                    }
                    reader.readEndArray()
                }

                else -> reader.skipValue()
            }
        }

        /**
         * Rejects values a template can not be emitted as code for, so they fail as a template error instead of broken generated code.
         */
        private fun <T : BsonValue> checkValues(value: T): T {
            when (value.bsonType) {
                BsonType.DOCUMENT -> value.asDocument().values.forEach { checkValues(it) }
                BsonType.ARRAY -> value.asArray().forEach { checkValues(it) }
                BsonType.DOUBLE -> require(value.asDouble().value.isFinite()) { "${value.asDouble().value} is not supported, a template number must be finite" }
                BsonType.STRING, BsonType.INT32, BsonType.INT64, BsonType.BOOLEAN, BsonType.NULL, BsonType.DATE_TIME,
                BsonType.OBJECT_ID, BsonType.DECIMAL128, BsonType.REGULAR_EXPRESSION -> {}

                else -> throw IllegalArgumentException("BSON type ${value.bsonType} is not supported in a template, pass the value as a method parameter instead")
            }
            return value
        }

        /**
         * Swaps every `:name` placeholder for a marker string, so that what is left is valid JSON the BSON parser can
         * read. Placeholders are only recognised in a value position, which keeps the `key:value` separator and
         * anything inside a string literal untouched.
         */
        private fun replacePlaceholders(template: String): Pair<String, List<String>> {
            val json = StringBuilder(template.length)
            val parameters = ArrayList<String>()
            var inString = false
            var i = 0

            while (i < template.length) {
                val c = template[i]
                if (inString) {
                    json.append(c)
                    if (c == '\\' && i + 1 < template.length) {
                        json.append(template[++i])
                    } else if (c == '"') {
                        inString = false
                    }
                    i++
                    continue
                }
                if (c == '"') {
                    inString = true
                    json.append(c)
                    i++
                    continue
                }
                if (c == ':' && i + 1 < template.length && isIdentifierStart(template[i + 1]) && isValuePosition(json)) {
                    var end = i + 1
                    while (end < template.length && isPathPart(template, end)) {
                        end++
                    }
                    json.append('"').append(MARKER_PREFIX).append(parameters.size).append(MARKER_SUFFIX).append('"')
                    parameters.add(template.substring(i + 1, end))
                    i = end
                    continue
                }
                json.append(c)
                i++
            }
            return json.toString() to parameters
        }

        /**
         * A value can only start right after a `key:` separator, a `,` separator or an opening `[`.
         * Anything else before a colon means the colon is itself the separator, as in an unquoted `{key:true}`.
         */
        private fun isValuePosition(json: StringBuilder): Boolean {
            for (i in json.length - 1 downTo 0) {
                val c = json[i]
                if (c.isWhitespace()) {
                    continue
                }
                return c == ':' || c == ',' || c == '['
            }
            return true
        }

        private fun isIdentifierStart(c: Char) = c.isLetter() || c == '_'

        private fun isIdentifierPart(c: Char) = c.isLetterOrDigit() || c == '_'

        /**
         * A placeholder may walk into an entity parameter, as in `:user.address.city`. A dot only continues the path
         * when a field name follows it, so a placeholder that ends a value keeps the surrounding JSON intact.
         */
        private fun isPathPart(template: String, index: Int): Boolean {
            val c = template[index]
            if (isIdentifierPart(c)) {
                return true
            }
            return c == '.' && index + 1 < template.length && isIdentifierStart(template[index + 1])
        }

        private fun markerIndex(value: String): Int {
            if (!value.startsWith(MARKER_PREFIX) || !value.endsWith(MARKER_SUFFIX)) {
                return -1
            }
            return value.substring(MARKER_PREFIX.length, value.length - MARKER_SUFFIX.length).toIntOrNull() ?: -1
        }

        private fun parseError(template: String, attribute: String, expected: String, cause: RuntimeException) =
            """
            Mongo query template is invalid:
              $template

            Problem:
              The '$attribute' attribute is not a valid BSON $expected: ${cause.message}

            Hint:
              A template is JSON where a method parameter is referenced as ':name' in place of a whole value,
              for example {"login": :login}. A placeholder can not be used inside a string or as a field name.

            Fix:
              Correct the JSON syntax of the template.
            """.trimIndent()
    }

    /**
     * @return the parsed template, for compile-time inspection of a document the generator also emits
     * @throws org.bson.BsonInvalidOperationException if this template was built by [parseArray], whose root is not a document
     */
    fun document(): BsonDocument = this.root.asDocument()

    fun toCodeBlock(parameterResolver: (String) -> CodeBlock): CodeBlock = this.build(this.root, parameterResolver)

    /**
     * Emits an aggregation pipeline as `List<Bson>`, which is what the driver's `aggregate` expects.
     */
    fun toPipelineCodeBlock(parameterResolver: (String) -> CodeBlock, node: KSAnnotated): CodeBlock {
        val stages = this.root.asArray()
        if (stages.isEmpty()) {
            return CodeBlock.of("emptyList<%T>()", MongoTypes.bson)
        }
        val elements = CodeBlock.builder()
        stages.forEachIndexed { index, stage ->
            if (!stage.isDocument) {
                throw ProcessingErrorException(
                    """
                    Mongo aggregation pipeline is invalid:
                      stage $index is a ${stage.bsonType}

                    Problem:
                      Every aggregation stage must be a document.

                    Hint:
                      A pipeline looks like [{"${'$'}match": {...}}, {"${'$'}group": {...}}].

                    Fix:
                      Wrap the stage into a document, or remove it from the pipeline.
                    """.trimIndent(), node
                )
            }
            if (index > 0) {
                elements.add(",\n")
            }
            elements.add(this.build(stage, parameterResolver))
        }
        return CodeBlock.of("listOf<%T>(⇥\n%L⇤\n)", MongoTypes.bson, elements.build())
    }

    private fun build(value: BsonValue, resolver: (String) -> CodeBlock): CodeBlock = when (value.bsonType) {
        org.bson.BsonType.DOCUMENT -> this.buildDocument(value.asDocument(), resolver)
        org.bson.BsonType.ARRAY -> this.buildArray(value.asArray(), resolver)
        org.bson.BsonType.STRING -> {
            val string = value.asString().value
            val index = markerIndex(string)
            if (index < 0) CodeBlock.of("%T(%S)", MongoTypes.bsonString, string) else resolver(this.parameters[index])
        }
        org.bson.BsonType.INT32 -> CodeBlock.of("%T(%L)", MongoTypes.bsonInt32, value.asInt32().value)
        org.bson.BsonType.INT64 -> CodeBlock.of("%T(%LL)", MongoTypes.bsonInt64, value.asInt64().value)
        org.bson.BsonType.DOUBLE -> CodeBlock.of("%T(%L)", MongoTypes.bsonDouble, value.asDouble().value)
        org.bson.BsonType.BOOLEAN -> CodeBlock.of("%T.valueOf(%L)", MongoTypes.bsonBoolean, value.asBoolean().value)
        org.bson.BsonType.NULL -> CodeBlock.of("%T.VALUE", MongoTypes.bsonNull)
        org.bson.BsonType.DATE_TIME -> CodeBlock.of("%T(%LL)", MongoTypes.bsonDateTime, value.asDateTime().value)
        org.bson.BsonType.OBJECT_ID -> CodeBlock.of("%T(%T(%S))", MongoTypes.bsonObjectId, MongoTypes.objectId, value.asObjectId().value.toHexString())
        org.bson.BsonType.DECIMAL128 -> CodeBlock.of("%T(%T.parse(%S))", MongoTypes.bsonDecimal128, MongoTypes.decimal128, value.asDecimal128().value.toString())
        org.bson.BsonType.REGULAR_EXPRESSION -> CodeBlock.of(
            "%T(%S, %S)", MongoTypes.bsonRegularExpression,
            value.asRegularExpression().pattern, value.asRegularExpression().options
        )
        else -> throw IllegalStateException("Kora internal error: unsupported BSON type in a template: " + value.bsonType)
    }

    private fun buildDocument(document: BsonDocument, resolver: (String) -> CodeBlock): CodeBlock {
        if (document.isEmpty()) {
            return CodeBlock.of("%T()", MongoTypes.bsonDocument)
        }
        val b = CodeBlock.builder().add("%T()", MongoTypes.bsonDocument).indent().indent()
        for (entry in document.entries) {
            b.add("\n.append(%S, %L)", entry.key, this.build(entry.value, resolver))
        }
        return b.unindent().unindent().build()
    }

    private fun buildArray(array: BsonArray, resolver: (String) -> CodeBlock): CodeBlock {
        if (array.isEmpty()) {
            return CodeBlock.of("%T()", MongoTypes.bsonArray)
        }
        val elements = CodeBlock.builder()
        array.forEachIndexed { index, element ->
            if (index > 0) {
                elements.add(", ")
            }
            elements.add(this.build(element, resolver))
        }
        return CodeBlock.of("%T(listOf<%T>(%L))", MongoTypes.bsonArray, MongoTypes.bsonValue, elements.build())
    }
}
