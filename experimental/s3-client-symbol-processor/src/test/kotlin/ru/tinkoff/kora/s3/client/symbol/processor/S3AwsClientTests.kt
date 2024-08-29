package ru.tinkoff.kora.s3.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class S3AwsClientTests : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import java.util.List;
            import java.util.Collection;
            import ru.tinkoff.kora.s3.client.annotation.*;
            import ru.tinkoff.kora.s3.client.annotation.S3.*;
            import ru.tinkoff.kora.s3.client.model.*;
            import ru.tinkoff.kora.s3.client.*;
            import ru.tinkoff.kora.s3.client.model.S3Object;
            import software.amazon.awssdk.services.s3.model.*;
            """.trimIndent()
    }

    @Test
    fun clientGetAws() {
        this.compile0(
             """
            @S3.Client("my")
            interface Client {
                        
                @S3.Get
                fun get(key: String): GetObjectResponse
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListAws() {
        this.compile0(
             """
            @S3.Client("my")
            interface Client {
                        
                @S3.List
                fun list(): ListObjectsV2Response
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListAwsWithPrefix() {
        this.compile0(
             """
            @S3.Client("my")
            interface Client {
                        
                @S3.List
                fun list(prefix: String): ListObjectsV2Response
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListAwsLimit() {
        this.compile0(
             """
            @S3.Client("my")
            interface Client {
                        
                @S3.List(limit = 100)
                fun list(prefix: String): ListObjectsV2Response
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientListKeyAndDelimiter() {
        this.compile0(
            """
            @S3.Client("my")
            interface Client {
                        
                @S3.List(value = "some/path/to/{key1}/object", delimiter = "/")
                fun list(key1: String): ListObjectsV2Response
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientDeleteAws() {
        this.compile0(
             """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete
                fun delete(key: String): DeleteObjectResponse
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientDeletesAws() {
        this.compile0(
             """
            @S3.Client("my")
            interface Client {
                        
                @S3.Delete
                fun delete(key: List<String>): DeleteObjectsResponse
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }

    @Test
    fun clientPutBody() {
        this.compile0(
             """
            @S3.Client("my")
            interface Client {
                        
                @S3.Put
                fun put(key: String, value: S3Body): PutObjectResponse
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val clazz = compileResult.loadClass("\$Client_Impl")
        assertThat(clazz).isNotNull()
    }
}
