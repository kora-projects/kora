package ru.tinkoff.kora.s3.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class S3SimpleAsyncClientTests : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            import java.nio.ByteBuffer;
            import java.io.InputStream;
            import ru.tinkoff.kora.s3.client.annotation.*;
            import ru.tinkoff.kora.s3.client.annotation.S3.*;
            import ru.tinkoff.kora.s3.client.model.*;
            import ru.tinkoff.kora.s3.client.*;
            import ru.tinkoff.kora.s3.client.model.S3Object;
            import software.amazon.awssdk.services.s3.model.*;
            """.trimIndent()
    }

    @Test
    fun clientConfig() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                suspend fun get(key: String): S3ObjectMeta
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()

        val config = compileResult.loadClass("\$Client_ClientConfigModule")
        assertThat(config).isNotNull()
    }

    // Get
    @Test
    fun clientGetMeta() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                suspend fun get(key: String): S3ObjectMeta
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientGetObject() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                suspend fun get(key: String): S3Object
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientGetManyMetas() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                suspend fun get(keys: Collection<String>): List<S3ObjectMeta>
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientGetManyMetasFuture() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                suspend fun get(keys: Collection<String>): List<S3ObjectMeta>
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientGetManyObjects() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                suspend fun get(key: List<String>): List<S3Object>
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientGetManyObjectsFuture() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                suspend fun get(key: List<String>): List<S3Object>
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientGetKeyConcat() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get("{key1}-{key2}")
                suspend fun get(key1: String, key2: Long): S3ObjectMeta
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientGetKeyMissing() {
        val result = this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get("{key1}-{key12345}")
                suspend fun get(key1: String): S3ObjectMeta
            }
            """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }

    @Test
    fun clientGetKeyConst() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get("const-key")
                suspend fun get(): S3ObjectMeta
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientGetKeyUnused() {
        val result = this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get("const-key")
                suspend fun get(key: String): S3ObjectMeta
            }
            """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }

    // List
    @Test
    fun clientListMeta() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List
                suspend fun list(): S3ObjectMetaList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListMetaFuture() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List
                suspend fun list(): S3ObjectMetaList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListMetaWithPrefix() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List
                suspend fun list(prefix: String): S3ObjectMetaList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListMetaFutureWithPrefix() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List
                suspend fun list(prefix: String): S3ObjectMetaList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListObjectsWithPrefix() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List
                suspend fun list(prefix: String): S3ObjectList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListLimit() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List(limit = 100)
                suspend fun list(prefix: String): S3ObjectList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListKeyConcat() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List("{key1}-{key2}")
                suspend fun list(key1: String, key2: Long): S3ObjectList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListKeyMissing() {
        val result = this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List("{key1}-{key12345}")
                suspend fun list(key1: String): S3ObjectList
            }
            """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }

    @Test
    fun clientListKeyConst() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List("const-key")
                suspend fun list(): S3ObjectList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListKeyUnused() {
        val result = this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List("const-key")
                suspend fun list(key: String): S3ObjectList
            }
            """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }

    // Delete
    @Test
    fun clientDelete() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete
                suspend fun delete(key: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientDeleteFuture() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete
                suspend fun delete(key: String)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientDeleteKeyConcat() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete("{key1}-{key2}")
                suspend fun delete(key1: String, key2: Long)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientDeleteKeyMissing() {
        val result = this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete("{key1}-{key12345}")
                suspend fun delete(key1: String)
            }
            """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }

    @Test
    fun clientDeleteKeyConst() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete("const-key")
                suspend fun delete()
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientDeleteKeyUnused() {
        val result = this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete("const-key")
                suspend fun delete(key: String)
            }
            """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }

    // Deletes
    @Test
    fun clientDeletes() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete
                suspend fun delete(key: List<String>)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    // Put
    @Test
    fun clientPutBody() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put
                suspend fun put(key: String, body: S3Body)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutBodyReturnVersionId() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put
                suspend fun put(key: String, body: S3Body): String
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutBytes() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put
                suspend fun put(key: String, body: ByteArray)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutBuffer() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put
                suspend fun put(key: String, body: ByteBuffer)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutInputStream() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put
                suspend fun put(key: String, body: InputStream)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutBodyAndType() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put(type = "type")
                suspend fun put(key: String, body: S3Body)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutBodyAndEncoding() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put(encoding = "encoding")
                suspend fun put(key: String, body: S3Body)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutBodyAndTypeAndEncoding() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put(type = "type", encoding = "encoding")
                suspend fun put(key: String, body: S3Body)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutKeyConcat() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put("{key1}-{key2}")
                suspend fun put(key1: String, key2: Long, body: S3Body)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutKeyMissing() {
        val result = this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put("{key1}-{key12345}")
                suspend fun put(key1: String, body: S3Body)
            }
            """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }

    @Test
    fun clientPutKeyConst() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put("const-key")
                suspend fun put(body: S3Body)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutKeyUnused() {
        val result = this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put("const-key")
                suspend fun put(key: String, body: S3Body)
            }
            """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }
}
