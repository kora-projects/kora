package ru.tinkoff.kora.s3.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import ru.tinkoff.kora.config.common.factory.MapConfigFactory
import ru.tinkoff.kora.http.common.body.HttpBodyInput
import ru.tinkoff.kora.s3.client.AwsCredentials
import ru.tinkoff.kora.s3.client.model.request.GetObjectArgs
import ru.tinkoff.kora.s3.client.model.response.GetObjectResult
import java.io.ByteArrayInputStream


internal class S3GetTest : AbstractS3ClientTest() {
    @Test
    fun testGetByteArray() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Get
                fun get(@S3.Bucket bucket: String , key: String): ByteArray
            }
            
            """.trimIndent()
        )

        val getObjectResult = mock(GetObjectResult::class.java)
        val getObjectBody = mock(HttpBodyInput::class.java)
        `when`(getObjectResult.body()).thenReturn(getObjectBody)
        `when`(getObjectBody.asInputStream()).thenReturn(ByteArrayInputStream(ByteArray(0)))
        `when`(
            s3Client.getObject(
                any(),
                eq("bucket"),
                eq("key"),
                any(),
                eq(true)
            )
        ).thenReturn(getObjectResult)

        val result = client.invoke<ByteArray?>("get", "bucket", "key")
        assertThat(result).isEqualTo(ByteArray(0))

        verify(s3Client).getObject(
            any(),
            eq("bucket"),
            eq("key"),
            isNull(),
            eq(true)
        )
        verify(getObjectResult).body()
        verify(getObjectResult).close()
        verify(getObjectBody).asInputStream()
        verify(getObjectBody).close()
        reset(s3Client)
    }

    @Test
    fun testGetWithTemplateKeyAndArgs() {
        val bucketConfig = MapConfigFactory.fromMap(
            mapOf(
                "bucket" to "bucket_value"
            )
        )
        val client = this.compile(
            """
            @S3.Client
            @S3.Bucket("bucket")
            interface Client {
                @S3.Get("prefix-{key}")
                fun getByTemplate(key: String, args: GetObjectArgs): GetObjectResult
            }
            
            """.trimIndent(), newGenerated("\$Client_BucketsConfig", bucketConfig)
        )

        val getObjectResult = mock(GetObjectResult::class.java)
        val args = GetObjectArgs()
        `when`(
            s3Client.getObject(
                any(),
                eq("bucket_value"),
                eq("prefix-key1"),
                same(args),
                eq(true)
            )
        ).thenReturn(getObjectResult)

        val result = client.invoke<GetObjectResult?>("getByTemplate", "key1", args)
        assertThat<GetObjectResult?>(result).isSameAs(getObjectResult)

        verify(s3Client).getObject(
            any(),
            eq("bucket_value"),
            eq("prefix-key1"),
            same(args),
            eq(true)
        )
        verify(getObjectResult, never()).body()
        verify(getObjectResult, never()).close()
        reset(s3Client)
    }

    @Test
    fun testGetWithAwsCredentials() {
        val bucketConfig = MapConfigFactory.fromMap(
            mapOf(
                "Client" to mapOf(
                    "bucket" to "bucket_value"
                )
            )
        )
        val client = this.compile(
            """
            @S3.Client
            @S3.Bucket(".bucket")
            interface Client {
                @S3.Get
                fun getWithCreds(creds: AwsCredentials, key: String): GetObjectResult?
            }
            
            """.trimIndent(), newGenerated("\$Client_BucketsConfig", bucketConfig)
        )

        val getObjectResult = mock(GetObjectResult::class.java)
        val creds = AwsCredentials.of("test", "test")
        `when`(
            s3Client.getObject(
                same(creds),
                eq("bucket_value"),
                eq("key"),
                isNull(),
                eq(false)
            )
        ).thenReturn(getObjectResult)

        val result = client.invoke<GetObjectResult?>("getWithCreds", creds, "key")
        assertThat<GetObjectResult?>(result).isSameAs(getObjectResult)

        verify(s3Client).getObject(
            same(creds),
            eq("bucket_value"),
            eq("key"),
            isNull(),
            eq(false)
        )
        reset(s3Client)
    }

    @Test
    fun testGetWithGetObjectArgsParam() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Get
                fun getWithArgs(@S3.Bucket bucket: String, key: String, args: GetObjectArgs): GetObjectResult
            }
            """.trimIndent()
        )

        val getObjectResult = mock(GetObjectResult::class.java)
        val args = GetObjectArgs()
        `when`(
            s3Client.getObject(
                any(),
                eq("bucket"),
                eq("key"),
                same(args),
                eq(true)
            )
        ).thenReturn(getObjectResult)

        assertThat<GetObjectResult?>(client.invoke<GetObjectResult?>("getWithArgs", "bucket", "key", args)).isSameAs(getObjectResult)

        verify(s3Client).getObject(
            any(),
            eq("bucket"),
            eq("key"),
            same(args),
            eq(true)
        )
        reset(s3Client)
    }

    @Test
    fun testGetConstantKey() {
        val bucketConfig = MapConfigFactory.fromMap(
            mapOf(
                "bucket" to "bucket_value"
            )
        )
        val client = this.compile(
            """
            @S3.Client
            @S3.Bucket("bucket")
            interface Client {
                @S3.Get("constant-key")
                fun getConstant(): GetObjectResult
            }
            
            """.trimIndent(), newGenerated("\$Client_BucketsConfig", bucketConfig)
        )

        val getObjectResult = mock(GetObjectResult::class.java)
        `when`(
            s3Client.getObject(
                any(),
                eq("bucket_value"),
                eq("constant-key"),
                isNull(),
                eq(true)
            )
        ).thenReturn(getObjectResult)

        assertThat<GetObjectResult?>(client.invoke<GetObjectResult?>("getConstant")).isSameAs(getObjectResult)

        verify(s3Client).getObject(
            any(),
            eq("bucket_value"),
            eq("constant-key"),
            isNull(),
            eq(true)
        )
        reset(s3Client)
    }
}


