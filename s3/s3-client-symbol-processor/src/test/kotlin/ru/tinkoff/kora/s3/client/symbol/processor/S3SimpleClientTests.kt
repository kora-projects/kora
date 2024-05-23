package ru.tinkoff.kora.s3.client.symbol.processor

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class S3SimpleClientTests : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
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

    // Get
    @Test
    fun clientGetMeta() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                fun get(key: String): S3ObjectMeta
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
                fun get(key: String): S3Object
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
                fun get(keys: Collection<String>): List<S3ObjectMeta>
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
                fun get(key: List<String>): List<S3Object>
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
                fun get(key1: String, key2: Long): S3ObjectMeta
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
                fun get(key1: String): S3ObjectMeta
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
                fun get(): S3ObjectMeta
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
                fun get(key: String): S3ObjectMeta
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
                fun list(): S3ObjectMetaList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListObjects() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List
                fun list(): S3ObjectList
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
                fun list(prefix: String): S3ObjectMetaList
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
                fun list(prefix: String): S3ObjectList
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListLimitWithPrefix() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List(limit = 100)
                fun list(prefix: String): S3ObjectList
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
                fun list(key1: String, key2: Long): S3ObjectList
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
                fun list(key1: String): S3ObjectList
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
                fun list(): S3ObjectList
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
                fun list(key: String): S3ObjectList
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
                fun delete(key: String)
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
                fun delete(key1: String, key2: Long)
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
                fun delete(key1: String)
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
                fun delete()
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
                fun delete(key: String)
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
                fun delete(key: List<String>)
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
                fun put(key: String, body: S3Body)
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
                fun put(key: String, body: S3Body): String
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
                fun put(key: String, body: ByteArray)
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
                fun put(key: String, body: ByteBuffer)
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
                fun put(key: String, body: InputStream)
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
                fun put(key: String, body: S3Body)
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
                fun put(key: String, body: S3Body)
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
                fun put(key: String, body: S3Body)
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
                fun put(key1: String, key2: Long, body: S3Body)
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
                fun put(key1: String, body: S3Body)
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
                fun put(body: S3Body)
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
                    fun put(key: String, body: S3Body)
                }
                """.trimIndent()
        )
        assertThat(result.isFailed()).isTrue()
    }
}
