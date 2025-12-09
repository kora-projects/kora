package ru.tinkoff.kora.aws.s3.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import ru.tinkoff.kora.aws.s3.`$S3ClientConfig_UploadConfig_ConfigValueExtractor`
import ru.tinkoff.kora.aws.s3.S3Client
import ru.tinkoff.kora.aws.s3.S3Client.ContentWriter
import ru.tinkoff.kora.aws.s3.model.response.UploadedPart
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ThreadLocalRandom


internal class S3PutTest : AbstractS3ClientTest() {
    @Test
    fun testPutByteArray() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String,  data: ByteArray): String
            }
            
            """.trimIndent()
        )

        val bytes = UUID.randomUUID().toString().toByteArray(StandardCharsets.UTF_8)

        `when`(
            s3Client.putObject(
                any(),
                eq("bucket"),
                eq("key"),
                any(),
                same(bytes),
                eq(0),
                eq(bytes.size)
            )
        ).thenReturn("etag")

        assertThat(client.invoke<String?>("put", "bucket", "key", bytes)).isEqualTo("etag")

        verify(s3Client).putObject(
            any(),
            eq("bucket"),
            eq("key"),
            any(),
            same(bytes),
            eq(0),
            eq(bytes.size)
        )
        reset(s3Client)
    }

    @Test
    fun testPutByteBuffer() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, data: ByteBuffer): String
            }
            
            """.trimIndent()
        )

        val bytes = UUID.randomUUID().toString().toByteArray(StandardCharsets.UTF_8)

        `when`(
            s3Client.putObject(
                any(),
                eq("bucket"),
                eq("key"),
                any(),
                same(bytes),
                eq(0),
                eq(bytes.size - 1)
            )
        ).thenReturn("etag")

        assertThat(client.invoke<String?>("put", "bucket", "key", ByteBuffer.wrap(bytes).slice(0, bytes.size - 1))).isEqualTo("etag")

        verify(s3Client).putObject(
            any(),
            eq("bucket"),
            eq("key"),
            any(),
            same(bytes),
            eq(0),
            eq(bytes.size - 1)
        )
        reset(s3Client)
    }

    @Test
    @Throws(Exception::class)
    fun testPutWithContentWriter() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, writer: S3Client.ContentWriter): String
            }
            
            """.trimIndent()
        )

        val writer = mock(ContentWriter::class.java)
        `when`(writer.length()).thenReturn(5L)

        `when`(
            s3Client.putObject(
                any(),
                eq("bucket"),
                eq("key"),
                any(),
                same(writer)
            )
        ).thenReturn("etag-content")

        assertThat(client.invoke<String?>("put", "bucket", "key", writer)).isEqualTo("etag-content")

        verify(s3Client).putObject(
            any(),
            eq("bucket"),
            eq("key"),
            any(),
            same(writer)
        )
        reset<S3Client?>(s3Client)
    }

    @Test
    @Throws(Exception::class)
    fun testPutInputStream() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, writer: InputStream): String
            }
            """.trimIndent()
        )

        val bytes = ByteArray(8 * 1024 * 1024)
        ThreadLocalRandom.current().nextBytes(bytes)
        val `is` = ByteArrayInputStream(bytes)
        val part1 = UploadedPart(null, null, null, null, null, "etag1", 1, (5 * 1024 * 1024).toLong())
        val part2 = UploadedPart(null, null, null, null, null, "etag2", 2, (3 * 1024 * 1024).toLong())

        `when`(
            s3Client.createMultipartUpload(
                any(),
                eq("bucket"),
                eq("key"),
                any()
            )
        ).thenReturn("multipartid")
        `when`(
            s3Client.uploadPart(
                any(),
                eq("bucket"),
                eq("key"),
                eq("multipartid"),
                eq(1),
                any(),
                eq(0),
                eq(5 * 1024 * 1024)
            )
        ).thenReturn(part1)
        `when`(
            s3Client.uploadPart(
                any(),
                eq("bucket"),
                eq("key"),
                eq("multipartid"),
                eq(2),
                any(),
                eq(0),
                eq(3 * 1024 * 1024)
            )
        ).thenReturn(part2)
        `when`(
            s3Client.completeMultipartUpload(
                any(),
                eq("bucket"),
                eq("key"),
                eq("multipartid"),
                eq(listOf(part1, part2)),
                any()
            )
        ).thenReturn("etag-final")
        `when`(config.upload()).thenReturn(`$S3ClientConfig_UploadConfig_ConfigValueExtractor`.UploadConfig_Defaults())

        assertThat(client.invoke<String?>("put", "bucket", "key", `is`)).isEqualTo("etag-final")

        verify(s3Client).createMultipartUpload(
            any(),
            eq("bucket"),
            eq("key"),
            any()
        )
        verify(s3Client).uploadPart(
            any(),
            eq("bucket"),
            eq("key"),
            eq("multipartid"),
            eq(1),
            any(),
            eq(0),
            eq(5 * 1024 * 1024)
        )
        verify(s3Client).uploadPart(
            any(),
            eq("bucket"),
            eq("key"),
            eq("multipartid"),
            eq(2),
            any(),
            eq(0),
            eq(3 * 1024 * 1024)
        )
        verify(s3Client).completeMultipartUpload(
            any(),
            eq("bucket"),
            eq("key"),
            eq("multipartid"),
            eq(listOf(part1, part2)),
            any()
        )
        reset(s3Client)
    }
}
