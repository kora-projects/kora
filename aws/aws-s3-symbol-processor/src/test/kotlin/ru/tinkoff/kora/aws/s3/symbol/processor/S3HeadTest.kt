package ru.tinkoff.kora.aws.s3.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import ru.tinkoff.kora.aws.s3.model.response.HeadObjectResult
import ru.tinkoff.kora.http.common.header.HttpHeaders

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
}
