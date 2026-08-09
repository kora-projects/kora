package io.koraframework.database.symbol.processor.mongo

import io.koraframework.database.common.UpdateCount
import io.koraframework.database.symbol.processor.AbstractRepositoryTest
import org.assertj.core.api.Assertions.assertThat
import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.codecs.Codec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MongoRepositorySymbolProcessorTest : AbstractRepositoryTest() {

    private val executor = MockMongoExecutor()
    private val codec: Codec<*> = Mockito.mock(Codec::class.java)

    override fun commonImports(): String = super.commonImports() + """
        import io.koraframework.database.mongo.*
        import io.koraframework.database.mongo.annotation.*
        import org.bson.types.ObjectId
        
        """.trimIndent()

    @BeforeEach
    fun resetExecutor() {
        this.executor.reset()
    }

    @Test
    fun testFindByFilter() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoFind(filter = "{\"login\": :login}")
                fun findByLogin(login: String): TestUser?
            }
            """.trimIndent(), """
            data class TestUser(val login: String)
            """.trimIndent()
        )

        assertThat(repository.invoke<Any>("findByLogin", "user")).isNull()
        Mockito.verify(executor.collection).find(BsonDocument("login", BsonString("user")))
    }

    @Test
    fun testFindListWithSortAndLimit() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoFind(filter = "{}", sort = "{\"login\": -1}", limit = "10", skip = "5")
                fun findAll(): List<TestUser>
            }
            """.trimIndent(), """
            data class TestUser(val login: String)
            """.trimIndent()
        )

        assertThat(repository.invoke<List<*>>("findAll")).isEmpty()
        Mockito.verify(executor.collection).find(BsonDocument())
        Mockito.verify(executor.findIterable).sort(BsonDocument("login", BsonInt32(-1)))
        Mockito.verify(executor.findIterable).skip(5)
        Mockito.verify(executor.findIterable).limit(10)
    }

    @Test
    fun testLimitAndSkipFromParameters() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoFind(filter = "{}", limit = ":size", skip = ":offset")
                fun findPage(size: Int, offset: Int): List<TestUser>
            }
            """.trimIndent(), """
            data class TestUser(val login: String)
            """.trimIndent()
        )

        repository.invoke<List<*>>("findPage", 20, 40)

        Mockito.verify(executor.findIterable).limit(20)
        Mockito.verify(executor.findIterable).skip(40)
    }

    @Test
    fun testFindWithCollectionParameter() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoFind(filter = "{\"login\": {\"\${'$'}in\": :logins}}")
                fun findByLogins(logins: List<String>): List<TestUser>
            }
            """.trimIndent(), """
            data class TestUser(val login: String)
            """.trimIndent()
        )

        repository.invoke<List<*>>("findByLogins", listOf("a", "b"))

        Mockito.verify(executor.collection).find(
            BsonDocument("login", BsonDocument("\$in", BsonArray(listOf(BsonString("a"), BsonString("b")))))
        )
    }

    @Test
    fun testNullableParameterBecomesBsonNull() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoFind(filter = "{\"login\": :login}")
                fun findByLogin(login: String?): List<TestUser>
            }
            """.trimIndent(), """
            data class TestUser(val login: String)
            """.trimIndent()
        )

        repository.invoke<List<*>>("findByLogin", null)

        Mockito.verify(executor.collection).find(BsonDocument("login", org.bson.BsonNull.VALUE))
    }

    @Test
    fun testUpdateReturnsUpdateCount() {
        Mockito.`when`(executor.updateResult.modifiedCount).thenReturn(3L)

        val repository = compile(
            executor, listOf<Any>(), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoUpdate(filter = "{\"_id\": :id}", update = "{\"\${'$'}set\": {\"login\": :login}}")
                fun rename(id: ObjectId, login: String): UpdateCount
            }
            """.trimIndent()
        )

        val id = org.bson.types.ObjectId()
        assertThat(repository.invoke<UpdateCount>("rename", id, "user")).isEqualTo(UpdateCount(3))
        Mockito.verify(executor.collection).updateOne(
            Mockito.eq(BsonDocument("_id", org.bson.BsonObjectId(id))),
            Mockito.eq(BsonDocument("\$set", BsonDocument("login", BsonString("user")))),
            Mockito.any(com.mongodb.client.model.UpdateOptions::class.java)
        )
    }

    @Test
    fun testDeleteManyAndCount() {
        Mockito.`when`(executor.deleteResult.deletedCount).thenReturn(2L)
        Mockito.`when`(executor.collection.countDocuments(Mockito.any(org.bson.conversions.Bson::class.java))).thenReturn(7L)

        val repository = compile(
            executor, listOf<Any>(), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoDelete(filter = "{\"active\": false}", many = true)
                fun deleteInactive(): Long
            
                @MongoCount(filter = "{}")
                fun countAll(): Long
            }
            """.trimIndent()
        )

        assertThat(repository.invoke<Long>("deleteInactive")).isEqualTo(2L)
        assertThat(repository.invoke<Long>("countAll")).isEqualTo(7L)
        Mockito.verify(executor.collection).deleteMany(BsonDocument("active", org.bson.BsonBoolean.FALSE))
    }

    @Test
    fun testBatchUpdateGoesThroughBulkWrite() {
        Mockito.`when`(executor.bulkWriteResult.modifiedCount).thenReturn(2)

        val repository = compile(
            executor, listOf<Any>(), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoUpdate(filter = "{\"_id\": :users.id}", update = "{\"\${'$'}set\": {\"login\": :users.login}}")
                fun renameAll(@Batch users: List<TestUser>): UpdateCount
            }
            """.trimIndent(), """
            data class TestUser(val id: ObjectId, val login: String)
            """.trimIndent()
        )

        val users = listOf(new("TestUser", org.bson.types.ObjectId(), "a"), new("TestUser", org.bson.types.ObjectId(), "b"))
        assertThat(repository.invoke<UpdateCount>("renameAll", users)).isEqualTo(UpdateCount(2))
        Mockito.verify(executor.collection).bulkWrite(Mockito.anyList())
    }

    @Test
    fun testEmptyBatchDoesNotReachTheDriver() {
        val repository = compile(
            executor, listOf<Any>(), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoDelete(filter = "{\"_id\": :users.id}")
                fun deleteAll(@Batch users: List<TestUser>): UpdateCount
            }
            """.trimIndent(), """
            data class TestUser(val id: ObjectId, val login: String)
            """.trimIndent()
        )

        assertThat(repository.invoke<UpdateCount>("deleteAll", emptyList<Any>())).isEqualTo(UpdateCount(0))
        Mockito.verify(executor.collection, Mockito.never()).bulkWrite(Mockito.anyList())
    }

    @Test
    fun testInsertAndAggregate() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoInsert
                fun insert(user: TestUser)
            
                @MongoAggregate("[{\"\${'$'}match\": {\"city\": :city}}]")
                fun statsByCity(city: String): List<TestUser>
            }
            """.trimIndent(), """
            data class TestUser(val login: String)
            """.trimIndent()
        )

        val user = new("TestUser", "user")
        repository.invoke<Any>("insert", user)
        repository.invoke<List<*>>("statsByCity", "Moscow")

        Mockito.verify(executor.collection).insertOne(user)
        Mockito.verify(executor.collection).aggregate(listOf(BsonDocument("\$match", BsonDocument("city", BsonString("Moscow")))))
    }
}
