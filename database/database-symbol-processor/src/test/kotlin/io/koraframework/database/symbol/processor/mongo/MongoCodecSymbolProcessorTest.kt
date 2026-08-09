package io.koraframework.database.symbol.processor.mongo

import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test

class MongoCodecSymbolProcessorTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String = super.commonImports() + """
        import io.koraframework.database.common.annotation.Column
        import io.koraframework.database.common.annotation.Id
        import io.koraframework.database.mongo.annotation.EntityMongo
        import org.bson.types.ObjectId
        
        """.trimIndent()

    @Test
    fun testDataClassRoundTrip() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(@Id val id: ObjectId, val login: String, val age: Int, val comment: String?)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val codec = codec("TestUser")
        val id = ObjectId()
        val user = new("TestUser", id, "user", 42, null)

        val document = encode(codec, user)
        assertThat(document.getObjectId("_id").value).isEqualTo(id)
        assertThat(document.getString("login").value).isEqualTo("user")
        assertThat(document.getInt32("age").value).isEqualTo(42)
        assertThat(document.isNull("comment")).isTrue()

        assertThat(decode(codec, document)).isEqualTo(user)
    }

    @Test
    fun testColumnRenamesDocumentField() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(@Column("user_login") val login: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val document = encode(codec("TestUser"), new("TestUser", "user"))

        assertThat(document.containsKey("user_login")).isTrue()
        assertThat(document.containsKey("login")).isFalse()
    }

    @Test
    fun testCollectionsAndEnumRoundTrip() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(val tags: List<String>, val counters: Map<String, Long>, val status: TestStatus)
            """.trimIndent(), """
            enum class TestStatus { ACTIVE, BLOCKED }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val codec = codec("TestUser")
        val status = loadClass("TestStatus").enumConstants.first { (it as Enum<*>).name == "BLOCKED" }
        val user = new("TestUser", listOf("a", "b"), mapOf("x" to 10L), status)

        val document = encode(codec, user)
        assertThat(document.getArray("tags").size).isEqualTo(2)
        assertThat(document.getDocument("counters").getInt64("x").value).isEqualTo(10L)
        assertThat(document.getString("status").value).isEqualTo("BLOCKED")

        assertThat(decode(codec, document)).isEqualTo(user)
    }

    @Test
    fun testNestedEntityUsesItsOwnCodec() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(val login: String, val address: TestAddress)
            """.trimIndent(), """
            @EntityMongo
            data class TestAddress(val city: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val addressCodec = codec("TestAddress")
        val codec = loadClass("\$TestUser_MongoCodec").constructors[0].newInstance(addressCodec) as Codec<Any>
        val user = new("TestUser", "user", new("TestAddress", "Moscow"))

        val document = encode(codec, user)
        assertThat(document.getDocument("address").getString("city").value).isEqualTo("Moscow")
        assertThat(decode(codec, document)).isEqualTo(user)
    }

    @Test
    fun testUnknownFieldIsSkippedAndAbsentRequiredFieldFails() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(val login: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val codec = codec("TestUser")
        val document = BsonDocument()
            .append("unknown", BsonInt32(1))
            .append("login", BsonString("user"))

        assertThat(decode(codec, document)).isEqualTo(new("TestUser", "user"))
        assertThatThrownBy { decode(codec, BsonDocument()) }
            .isInstanceOf(NullPointerException::class.java)
            .hasMessageContaining("login")
    }

    @Test
    fun testNullIdIsNotWrittenSoTheServerGeneratesIt() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val document = encode(codec("TestUser"), new("TestUser", null, "user"))

        assertThat(document.containsKey("_id")).isFalse()
        assertThat(document.getString("login").value).isEqualTo("user")
    }

    @Test
    fun testPresentIdIsStillWritten() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val id = ObjectId()
        val document = encode(codec("TestUser"), new("TestUser", id, "user"))

        assertThat(document.getObjectId("_id").value).isEqualTo(id)
    }

    @Test
    fun testAbsentIdDecodesToNull() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val codec = codec("TestUser")
        val document = BsonDocument().append("login", BsonString("user"))

        assertThat(decode(codec, document)).isEqualTo(new("TestUser", null, "user"))
    }

    @Test
    fun testNullableIdOfAnotherTypeIsRejected() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(@Id val id: String?, val login: String)
            """.trimIndent()
        )

        val failure = compileResult.assertFailure()
        assertThat(failure.messages.joinToString("\n")).contains("Field mapped to '_id' is nullable but is not an ObjectId")
    }

    @Test
    fun testColumnMappedIdFollowsTheSameRule() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(@Column("_id") val key: String?, val login: String)
            """.trimIndent()
        )

        val failure = compileResult.assertFailure()
        assertThat(failure.messages.joinToString("\n")).contains("Field mapped to '_id' is nullable but is not an ObjectId")
    }

    @Test
    fun testEmbeddedIsRejected() {
        compile0(
            listOf(MongoEntitySymbolProcessorProvider()), """
            @EntityMongo
            data class TestUser(@io.koraframework.database.common.annotation.Embedded val address: TestAddress)
            """.trimIndent(), """
            data class TestAddress(val city: String)
            """.trimIndent()
        )

        val failure = compileResult.assertFailure()
        assertThat(failure.messages.joinToString("\n")).contains("@Embedded is not supported")
    }

    @Suppress("UNCHECKED_CAST")
    private fun codec(entity: String): Codec<Any> =
        loadClass("\$${entity}_MongoCodec").getConstructor().newInstance() as Codec<Any>

    private fun encode(codec: Codec<Any>, value: Any): BsonDocument {
        val document = BsonDocument()
        BsonDocumentWriter(document).use { writer ->
            codec.encode(writer, value, EncoderContext.builder().build())
        }
        return document
    }

    private fun decode(codec: Codec<Any>, document: BsonDocument): Any =
        BsonDocumentReader(document).use { reader ->
            codec.decode(reader, DecoderContext.builder().build())
        }
}
