package io.koraframework.s3.client.symbol.processor

import io.koraframework.common.util.Size
import io.koraframework.s3.client.kora.S3ClientConfig
import io.koraframework.s3.client.kora.exception.S3ClientErrorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.AdditionalMatchers.aryEq
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import io.koraframework.s3.client.kora.`$S3ClientConfig_UploadConfig_ConfigValueMapper`
import io.koraframework.s3.client.kora.S3Client
import io.koraframework.s3.client.kora.model.response.UploadedPart
import io.koraframework.s3.client.kora.symbol.processor.AbstractS3ClientTest
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

        val writer = mock(S3Client.ContentWriter::class.java)
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
        `when`(config.upload()).thenReturn(`$S3ClientConfig_UploadConfig_ConfigValueMapper`.UploadConfig_Defaults())

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

    @Test
    fun testPutByteBufferWithPosition() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, data: ByteBuffer): String
            }
            """.trimIndent()
        )

        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val buffer = ByteBuffer.wrap(bytes, 5, 3)

        doReturn("etag").`when`(s3Client).putObject(any(), any(), any(), any(), any(ByteArray::class.java), anyInt(), anyInt())

        assertThat(client.invoke<String?>("put", "bucket", "key", buffer)).isEqualTo("etag")

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), same(bytes), eq(5), eq(3))
        assertThat(buffer.position()).isEqualTo(5)
        reset(s3Client)
    }

    @Test
    fun testPutDirectByteBufferKeepsPosition() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, data: ByteBuffer): String
            }
            """.trimIndent()
        )

        val buffer = ByteBuffer.allocateDirect(10).put(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)).position(5).limit(8)

        doReturn("etag").`when`(s3Client).putObject(any(), any(), any(), any(), any(ByteArray::class.java), anyInt(), anyInt())

        assertThat(client.invoke<String?>("put", "bucket", "key", buffer)).isEqualTo("etag")

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), aryEq(byteArrayOf(5, 6, 7)), eq(0), eq(3))
        assertThat(buffer.position()).isEqualTo(5)
        reset(s3Client)
    }

    @Test
    fun testPutInputStreamUnitSinglePart() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, body: InputStream)
            }
            """.trimIndent()
        )

        val upload = uploadConfig(16)
        `when`(config.upload()).thenReturn(upload)
        doReturn("etag").`when`(s3Client).putObject(any(), any(), any(), any(), any(ByteArray::class.java), anyInt(), anyInt())

        client.invoke<Any?>("put", "bucket", "key", ByteArrayInputStream(ByteArray(10)))

        verify(s3Client).putObject(any(), eq("bucket"), eq("key"), any(), any(ByteArray::class.java), eq(0), eq(10))
        verifyNoMoreInteractions(s3Client)
        reset(s3Client)
    }

    @Test
    fun testPutInputStreamUnitMultipart() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, body: InputStream)
            }
            """.trimIndent()
        )

        val part1 = UploadedPart(null, null, null, null, null, "etag1", 1, 16)
        val part2 = UploadedPart(null, null, null, null, null, "etag2", 2, 10)
        val upload = uploadConfig(16)
        `when`(config.upload()).thenReturn(upload)
        doReturn("multipartid").`when`(s3Client).createMultipartUpload(any(), eq("bucket"), eq("key"), any())
        doReturn(part1).`when`(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(1), any(), eq(0), eq(16))
        doReturn(part2).`when`(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(2), any(), eq(0), eq(10))
        doReturn("etag-final").`when`(s3Client).completeMultipartUpload(any(), any(), any(), any(), any(), any())

        client.invoke<Any?>("put", "bucket", "key", ByteArrayInputStream(ByteArray(16 + 10)))

        verify(s3Client).createMultipartUpload(any(), eq("bucket"), eq("key"), any())
        verify(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(1), any(), eq(0), eq(16))
        verify(s3Client).uploadPart(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(2), any(), eq(0), eq(10))
        verify(s3Client).completeMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), eq(listOf(part1, part2)), any())
        verifyNoMoreInteractions(s3Client)
        reset(s3Client)
    }

    @Test
    fun testPutInputStreamAbortsMultipartUploadOnPartFailure() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, body: InputStream): String
            }
            """.trimIndent()
        )

        val error = S3ClientErrorException(500, "InternalError", "boom", "rq")
        val upload = uploadConfig(16)
        `when`(config.upload()).thenReturn(upload)
        doReturn("multipartid").`when`(s3Client).createMultipartUpload(any(), eq("bucket"), eq("key"), any())
        doReturn(UploadedPart(null, null, null, null, null, "etag1", 1, 16))
            .`when`(s3Client).uploadPart(any(), any(), any(), any(), eq(1), any(), anyInt(), anyInt())
        doThrow(error).`when`(s3Client).uploadPart(any(), any(), any(), any(), eq(2), any(), anyInt(), anyInt())
        doNothing().`when`(s3Client).abortMultipartUpload(any(), any(), any(), any(), any())

        assertThatThrownBy { client.invoke<String?>("put", "bucket", "key", ByteArrayInputStream(ByteArray(16 * 2 + 10))) }
            .isSameAs(error)

        verify(s3Client).abortMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), any())
        verify(s3Client, never()).completeMultipartUpload(any(), any(), any(), any(), any(), any())
        reset(s3Client)
    }

    @Test
    fun testPutInputStreamAbortsMultipartUploadOnCompleteFailure() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Put
                fun put(@S3.Bucket bucket: String, key: String, body: InputStream): String
            }
            """.trimIndent()
        )

        val error = S3ClientErrorException(500, "InternalError", "boom", "rq")
        val abortError = S3ClientErrorException(500, "InternalError", "abort failed", "rq")
        val upload = uploadConfig(16)
        `when`(config.upload()).thenReturn(upload)
        doReturn("multipartid").`when`(s3Client).createMultipartUpload(any(), eq("bucket"), eq("key"), any())
        doReturn(UploadedPart(null, null, null, null, null, "etag", 1, 16))
            .`when`(s3Client).uploadPart(any(), any(), any(), any(), anyInt(), any(), anyInt(), anyInt())
        doThrow(error).`when`(s3Client).completeMultipartUpload(any(), any(), any(), any(), any(), any())
        doThrow(abortError).`when`(s3Client).abortMultipartUpload(any(), any(), any(), any(), any())

        assertThatThrownBy { client.invoke<String?>("put", "bucket", "key", ByteArrayInputStream(ByteArray(16 + 10))) }
            .isSameAs(error)
            .hasSuppressedException(abortError)

        verify(s3Client).abortMultipartUpload(any(), eq("bucket"), eq("key"), eq("multipartid"), any())
        reset(s3Client)
    }

    private fun uploadConfig(partSize: Long): S3ClientConfig.UploadConfig {
        val upload = mock(S3ClientConfig.UploadConfig::class.java)
        `when`(upload.partSize()).thenReturn(Size.of(partSize, Size.Type.BYTES))
        return upload
    }
}
