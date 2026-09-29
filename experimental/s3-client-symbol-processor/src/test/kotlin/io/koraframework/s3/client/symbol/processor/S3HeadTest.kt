package io.koraframework.s3.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import io.koraframework.http.common.header.HttpHeaders
import io.koraframework.s3.client.kora.model.request.HeadObjectArgs
import io.koraframework.s3.client.kora.model.response.HeadObjectResult
import io.koraframework.s3.client.kora.symbol.processor.AbstractS3ClientTest

class S3HeadTest : AbstractS3ClientTest() {
    @Test
    fun testHead() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Head
                fun head(@S3.Bucket bucket: String , key: String): HeadObjectResult
            }
            
            """.trimIndent()
        )
        val response = HeadObjectResult("test", "test", 1, HttpHeaders.empty())

        `when`(
            s3Client.headObject(
                any(),
                eq("bucket"),
                eq("key"),
                any(),
                eq(true)
            )
        ).thenReturn(response)

        val result = client.invoke<HeadObjectResult>("head", "bucket", "key")

        assertThat(result).isSameAs(response)

        verify(s3Client).headObject(
            any(),
            eq("bucket"),
            eq("key"),
            isNull(),
            eq(true)
        )
    }

    @Test
    fun testHeadWithArgs() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Head
                fun head(@S3.Bucket bucket: String, key: String, args: HeadObjectArgs): HeadObjectResult
            }
            
            """.trimIndent()
        )
        val response = HeadObjectResult("test", "test", 1, HttpHeaders.empty())
        val args = HeadObjectArgs().setVersionId("v1")

        `when`(
            s3Client.headObject(
                any(),
                eq("bucket"),
                eq("key"),
                any(),
                eq(true)
            )
        ).thenReturn(response)

        val result = client.invoke<HeadObjectResult>("head", "bucket", "key", args)

        assertThat(result).isSameAs(response)

        verify(s3Client).headObject(
            any(),
            eq("bucket"),
            eq("key"),
            same(args),
            eq(true)
        )
    }

    @Test
    fun testHeadOptionalWithArgs() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Head
                fun head(@S3.Bucket bucket: String, key: String, args: HeadObjectArgs): HeadObjectResult?
            }
            
            """.trimIndent()
        )
        val response = HeadObjectResult("test", "test", 1, HttpHeaders.empty())
        val args = HeadObjectArgs().setVersionId("v1")

        `when`(
            s3Client.headObject(
                any(),
                eq("bucket"),
                eq("key"),
                any(),
                eq(false)
            )
        ).thenReturn(response)

        val result = client.invoke<HeadObjectResult>("head", "bucket", "key", args)

        assertThat(result).isSameAs(response)

        verify(s3Client).headObject(
            any(),
            eq("bucket"),
            eq("key"),
            same(args),
            eq(false)
        )
    }
}
