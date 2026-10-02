package io.koraframework.database.symbol.processor.mongo

import com.mongodb.client.result.InsertManyResult
import io.koraframework.database.common.UpdateCount
import io.koraframework.database.symbol.processor.AbstractRepositoryTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.Codec
import org.bson.types.ObjectId
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
    fun testTypesGeneratedInLaterRoundAreResolved() {
        compile0(
            listOf(
                io.koraframework.database.symbol.processor.RepositorySymbolProcessorProvider(),
                LaterRoundProcessorProvider(testPackage(), "LaterKind", "enum class LaterKind { A, B }")
            ), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoCount(filter = "{\"kind\": :kind}")
                fun countByKind(kind: LaterKind): Long
            }
            """.trimIndent()
        ).assertSuccess()

        val repositoryClass = loadClass("\$TestRepository_Impl")
        val repository = TestObject(repositoryClass.kotlin, repositoryClass.constructors[0].newInstance(executor))
        repository.invoke<Any>("countByKind", loadClass("LaterKind").enumConstants[1])

        Mockito.verify(executor.collection).countDocuments(BsonDocument("kind", BsonString("B")))
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

        val id = ObjectId()
        assertThat(repository.invoke<UpdateCount>("rename", id, "user")).isEqualTo(UpdateCount(3))
        Mockito.verify(executor.collection).updateOne(
            Mockito.eq(BsonDocument("_id", BsonObjectId(id))),
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

        val users = listOf(new("TestUser", ObjectId(), "a"), new("TestUser", ObjectId(), "b"))
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

    @Test
    fun testInsertMany() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoInsert
                fun insertAll(entities: List<TestEntity>)
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestEntity(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )

        val entities = listOf(new("TestEntity", null, "a"), new("TestEntity", null, "b"))
        repository.invoke<Any>("insertAll", entities)

        Mockito.verify(executor.collection).insertMany(entities)
    }

    @Test
    fun testInsertReturnsGeneratedId() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoInsert
                fun insert(entity: TestEntity): ObjectId
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestEntity(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )

        val result = repository.invoke<Any>("insert", new("TestEntity", null, "user"))

        assertThat(result).isEqualTo(executor.insertOneResult.insertedId!!.asObjectId().value)
    }

    @Test
    fun testInsertReturnsEntityCarryingTheId() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoInsert
                fun insert(entity: TestEntity): TestEntity
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestEntity(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )

        val argument = new("TestEntity", null, "user")
        val result = repository.invoke<Any>("insert", argument)
        val id = executor.insertOneResult.insertedId!!.asObjectId().value

        assertThat(result).isEqualTo(new("TestEntity", id, "user"))
        assertThat(result).isNotSameAs(argument)
    }

    @Test
    fun testInsertManyReturnsIdsInArgumentOrder() {
        val first = ObjectId()
        val second = ObjectId()
        executor.insertManyResult = InsertManyResult.acknowledged(mapOf(1 to BsonObjectId(second), 0 to BsonObjectId(first)))

        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoInsert
                fun insertAll(entities: List<TestEntity>): List<ObjectId>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestEntity(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )

        val result = repository.invoke<List<*>>("insertAll", listOf(new("TestEntity", null, "a"), new("TestEntity", null, "b")))

        assertThat(result).containsExactly(first, second)
    }

    @Test
    fun testInsertManyReturnsEntities() {
        val first = ObjectId()
        val second = ObjectId()
        executor.insertManyResult = InsertManyResult.acknowledged(mapOf(1 to BsonObjectId(second), 0 to BsonObjectId(first)))

        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoInsert
                fun insertAll(entities: List<TestEntity>): List<TestEntity>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestEntity(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )

        val firstArgument = new("TestEntity", null, "a")
        val secondArgument = new("TestEntity", null, "b")
        val result = repository.invoke<List<*>>("insertAll", listOf(firstArgument, secondArgument))!!

        assertThat(result).containsExactly(new("TestEntity", first, "a"), new("TestEntity", second, "b"))
        assertThat(result[0]).isNotSameAs(firstArgument)
        assertThat(result[1]).isNotSameAs(secondArgument)
    }

    @Test
    fun testInsertOfAnEmptyCollectionNeverReachesTheDriver() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoInsert
                fun insertAll(entities: List<TestEntity>): List<ObjectId>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestEntity(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )

        val result = repository.invoke<List<*>>("insertAll", listOf<Any>())

        assertThat(result).isEmpty()
        Mockito.verify(executor.collection, Mockito.never()).insertMany(Mockito.anyList())
    }

    @Test
    fun testInsertOfAnEmptyCollectionAsVoidNeverReachesTheDriver() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {
            
                @MongoInsert
                fun insertAll(entities: List<TestEntity>)
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestEntity(@Id val id: ObjectId?, val login: String)
            """.trimIndent()
        )

        repository.invoke<Any>("insertAll", listOf<Any>())

        Mockito.verify(executor.collection, Mockito.never()).insertMany(Mockito.anyList())
    }

    @Test
    fun testUnsupportedInsertReturnTypeIsRejected() {
        assertThatThrownBy {
            compile(
                executor, listOf(codec), """
                @Repository
                @MongoCollection("users")
                interface TestRepository : MongoRepository {
                
                    @MongoInsert
                    fun insert(entity: TestEntity): String
                }
                """.trimIndent(), """
                @EntityMongo
                data class TestEntity(@Id val id: ObjectId?, val login: String)
                """.trimIndent()
            )
        }.hasMessageContaining("Supported return types are Unit, ObjectId and the entity type")
    }

    @Test
    fun testEntityResultWithoutAnIdFieldIsRejected() {
        assertThatThrownBy {
            compile(
                executor, listOf(codec), """
                @Repository
                @MongoCollection("users")
                interface TestRepository : MongoRepository {
                
                    @MongoInsert
                    fun insert(entity: TestEntity): TestEntity
                }
                """.trimIndent(), """
                @EntityMongo
                data class TestEntity(val login: String)
                """.trimIndent()
            )
        }.hasMessageContaining("has no field mapped to '_id'")
    }

    @Test
    fun testIdResultWithNonObjectIdFieldIsRejected() {
        assertThatThrownBy {
            compile(
                executor, listOf(codec), """
                @Repository
                @MongoCollection("users")
                interface TestRepository : MongoRepository {
                
                    @MongoInsert
                    fun insert(entity: TestEntity): ObjectId
                }
                """.trimIndent(), """
                @EntityMongo
                data class TestEntity(@Id val id: String, val login: String)
                """.trimIndent()
            )
        }.hasMessageContaining("so the inserted identifier is not an ObjectId")
    }

    @Test
    fun testProjectionIsDerivedFromTheResultType() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String, val age: Int)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(
            BsonDocument()
                .append("login", BsonInt32(1))
                .append("age", BsonInt32(1))
                .append("_id", BsonInt32(0))
        )
    }

    @Test
    fun testDerivedProjectionKeepsIdWhenTheTypeHasOne() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(@Id val id: ObjectId, val login: String)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(
            BsonDocument()
                .append("_id", BsonInt32(1))
                .append("login", BsonInt32(1))
        )
    }

    @Test
    fun testExplicitProjectionWins() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"login\": 1}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String, val age: Int?)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(BsonDocument("login", BsonInt32(1)))
    }

    @Test
    fun testNoProjectionForAnUnknownResultType() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}")
                fun raw(): List<org.bson.Document>
            }
            """.trimIndent()
        )

        repository.invoke<List<*>>("raw")

        Mockito.verify(executor.findIterable, Mockito.never()).projection(Mockito.any(org.bson.conversions.Bson::class.java))
    }

    @Test
    fun testNoProjectionWhenAFieldNameHoldsADot() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(@Column("addr.city") val city: String)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable, Mockito.never()).projection(Mockito.any(org.bson.conversions.Bson::class.java))
    }

    @Test
    fun testProjectionMayOmitANullableField() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"login\": 1, \"_id\": 0}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String, val age: Int?)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(
            BsonDocument()
                .append("login", BsonInt32(1))
                .append("_id", BsonInt32(0))
        )
    }

    @Test
    fun testProjectionWithAPathCoversItsRoot() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"address.city\": 1, \"_id\": 0}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val address: TestAddress)
            """.trimIndent(), """
            @EntityMongo
            data class TestAddress(val city: String?)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(
            BsonDocument()
                .append("address.city", BsonInt32(1))
                .append("_id", BsonInt32(0))
        )
    }

    @Test
    fun testProjectionWithAnOperatorExpressionIsNotChecked() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"login\": 1, \"tags\": {\"\${'$'}slice\": 3}}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String, val tags: List<String>)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(Mockito.any(org.bson.conversions.Bson::class.java))
    }

    @Test
    fun testProjectionWithAnAggregationExpressionIsNotChecked() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"age\": 1, \"login\": \"\${'$'}profile.login\"}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String, val age: Int)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(Mockito.any(org.bson.conversions.Bson::class.java))
    }

    @Test
    fun testProjectionWithAFractionalInclusionValueIsAccepted() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"login\": 0.5, \"age\": 1}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String, val age: Int)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(
            BsonDocument()
                .append("login", BsonDouble(0.5))
                .append("age", BsonInt32(1))
        )
    }

    @Test
    fun testProjectionWithAPlaceholderIsNotChecked() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"login\": :flag}")
                fun summaries(flag: Int): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String, val age: Int)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries", 1)

        Mockito.verify(executor.findIterable).projection(Mockito.any(org.bson.conversions.Bson::class.java))
    }

    @Test
    fun testProjectionOverAPlainResultTypeIsNotChecked() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"login\": 1}")
                fun summaries(): List<TestUser>
            }
            """.trimIndent(), """
            data class TestUser(val login: String, val age: Int)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(BsonDocument("login", BsonInt32(1)))
    }

    @Test
    fun testProjectionIsNotCheckedWhenAFieldNameHoldsADot() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"login\": 1}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(@Column("addr.city") val city: String, val login: String)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(BsonDocument("login", BsonInt32(1)))
    }

    @Test
    fun testExclusionProjectionOfOnlyIdIsAccepted() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"_id\": 0}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String, val age: Int)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(BsonDocument("_id", BsonInt32(0)))
    }

    @Test
    fun testIdInclusionAlongsideAnExclusionIsNotMixed() {
        val repository = compile(
            executor, listOf(codec), """
            @Repository
            @MongoCollection("users")
            interface TestRepository : MongoRepository {

                @MongoFind(filter = "{}", projection = "{\"_id\": 1, \"login\": 0}")
                fun summaries(): List<TestSummary>
            }
            """.trimIndent(), """
            @EntityMongo
            data class TestSummary(val login: String?, val age: Int)
            """.trimIndent()
        )

        repository.invoke<List<*>>("summaries")

        Mockito.verify(executor.findIterable).projection(
            BsonDocument()
                .append("_id", BsonInt32(1))
                .append("login", BsonInt32(0))
        )
    }

    @Test
    fun testProjectionMissingARequiredFieldIsRejected() {
        assertThatThrownBy {
            compile(
                executor, listOf(codec), """
                @Repository
                @MongoCollection("users")
                interface TestRepository : MongoRepository {

                    @MongoFind(filter = "{}", projection = "{\"login\": 1}")
                    fun summaries(): List<TestSummary>
                }
                """.trimIndent(), """
                @EntityMongo
                data class TestSummary(val login: String, val age: Int)
                """.trimIndent()
            )
        }.hasMessageContaining("Mongo projection does not cover the result type")
            .hasMessageContaining("TestSummary.age is not nullable, but field 'age' is not included in the projection")
            .hasMessageContaining("An inclusion projection returns only the listed fields")
            .hasMessageContaining("Add 'age' to the projection")
    }

    @Test
    fun testProjectionExcludingARequiredFieldIsRejected() {
        assertThatThrownBy {
            compile(
                executor, listOf(codec), """
                @Repository
                @MongoCollection("users")
                interface TestRepository : MongoRepository {

                    @MongoFind(filter = "{}", projection = "{\"age\": 0}")
                    fun summaries(): List<TestSummary>
                }
                """.trimIndent(), """
                @EntityMongo
                data class TestSummary(val login: String, val age: Int)
                """.trimIndent()
            )
        }.hasMessageContaining("Mongo projection does not cover the result type")
            .hasMessageContaining("An exclusion projection returns every field except the listed ones")
            .hasMessageContaining("Remove 'age' from the projection")
    }

    @Test
    fun testMixedProjectionIsRejected() {
        assertThatThrownBy {
            compile(
                executor, listOf(codec), """
                @Repository
                @MongoCollection("users")
                interface TestRepository : MongoRepository {

                    @MongoFind(filter = "{}", projection = "{\"login\": 1, \"age\": 0}")
                    fun summaries(): List<TestSummary>
                }
                """.trimIndent(), """
                @EntityMongo
                data class TestSummary(val login: String, val age: Int)
                """.trimIndent()
            )
        }.hasMessageContaining("mixes included and excluded fields")
    }

    @Test
    fun testInclusionProjectionOfOnlyIdIsRejected() {
        assertThatThrownBy {
            compile(
                executor, listOf(codec), """
                @Repository
                @MongoCollection("users")
                interface TestRepository : MongoRepository {

                    @MongoFind(filter = "{}", projection = "{\"_id\": 1}")
                    fun summaries(): List<TestSummary>
                }
                """.trimIndent(), """
                @EntityMongo
                data class TestSummary(val login: String, val age: Int)
                """.trimIndent()
            )
        }.hasMessageContaining("Mongo projection does not cover the result type")
            .hasMessageContaining("Add 'login' to the projection")
    }
}
