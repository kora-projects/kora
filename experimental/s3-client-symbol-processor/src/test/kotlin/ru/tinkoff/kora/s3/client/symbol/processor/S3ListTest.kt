package ru.tinkoff.kora.s3.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta

class S3ListTest : AbstractS3Test() {
    @Test
    fun testListBucket() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.List
                fun list(@S3.Bucket bucket: String): List<S3ObjectMeta>
            
                @S3.List
                fun iterator(@S3.Bucket bucket: String): Iterator<S3ObjectMeta>
            }
            """.trimIndent())

        val list = listOf<S3ObjectMeta>()
        whenever(s3Client.list("bucket", null, null, 1000)).thenReturn(list)
        assertThat(client.invoke<S3ObjectMeta>("list", "bucket")).isSameAs(list)
        verify(s3Client).list("bucket", null, null, 1000)
        reset(s3Client)

        val iterator = arrayListOf<S3ObjectMeta>().iterator()
        whenever(s3Client.listIterator("bucket", null, null, 1000)).thenReturn(iterator)
        assertThat(client.invoke<S3ObjectMeta>("iterator", "bucket")).isSameAs(iterator)
        verify(s3Client).listIterator("bucket", null, null, 1000)
        reset(s3Client)
    }

    @Test
    fun testListBucketPrefix() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.List
                fun prefix(@S3.Bucket bucket: String, prefix: String): List<S3ObjectMeta>
            
                @S3.List("/prefix/{prefix}")
                fun template(@S3.Bucket bucket: String, prefix: String): List<S3ObjectMeta>
            }
            """.trimIndent())

        val list = listOf<S3ObjectMeta>()
        whenever(s3Client.list("bucket", "test1", null, 1000)).thenReturn(list)
        assertThat(client.invoke<S3ObjectMeta>("prefix", "bucket", "test1")).isSameAs(list)
        verify(s3Client).list("bucket", "test1", null, 1000)
        reset(s3Client)

        whenever(s3Client.list("bucket", "/prefix/test1", null, 1000)).thenReturn(list)
        assertThat(client.invoke<S3ObjectMeta>("template", "bucket", "test1")).isSameAs(list)
        verify(s3Client).list("bucket", "/prefix/test1", null, 1000)
        reset(s3Client)
    }

    @Test
    fun testLimit() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.List
                fun onParameter(@S3.Bucket bucket: String, @S3.List.Limit limit: Int): List<S3ObjectMeta>
            
                @S3.List
                @S3.List.Limit(43)
                fun onMethod(@S3.Bucket bucket: String): List<S3ObjectMeta>
            }
            """.trimIndent())

        val list = listOf<S3ObjectMeta>()
        whenever(s3Client.list("bucket", null, null, 42)).thenReturn(list)
        assertThat(client.invoke<S3ObjectMeta>("onParameter", "bucket", 42)).isSameAs(list)
        verify(s3Client).list("bucket", null, null, 42)
        reset(s3Client)

        whenever(s3Client.list("bucket", null, null, 43)).thenReturn(list)
        assertThat(client.invoke<S3ObjectMeta>("onMethod", "bucket")).isSameAs(list)
        verify(s3Client).list("bucket", null, null, 43)
        reset(s3Client)
    }

    @Test
    fun testListDelimiter() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.List
                fun onParameter(@S3.Bucket bucket: String, @S3.List.Delimiter delimiter: String): List<S3ObjectMeta>
            
                @S3.List
                @S3.List.Delimiter("/")
                fun onMethod(@S3.Bucket bucket: String): List<S3ObjectMeta>
            }
            """.trimIndent())

        val list = listOf<S3ObjectMeta>()
        whenever(s3Client.list("bucket", null, "test", 1000)).thenReturn(list)
        assertThat(client.invoke<S3ObjectMeta>("onParameter", "bucket", "test")).isSameAs(list)
        verify(s3Client).list("bucket", null, "test", 1000)
        reset(s3Client)

        whenever(s3Client.list("bucket", null, "/", 1000)).thenReturn(list)
        assertThat(client.invoke<S3ObjectMeta>("onMethod", "bucket")).isSameAs(list)
        verify(s3Client).list("bucket", null, "/", 1000)
        reset(s3Client)
    }

}
