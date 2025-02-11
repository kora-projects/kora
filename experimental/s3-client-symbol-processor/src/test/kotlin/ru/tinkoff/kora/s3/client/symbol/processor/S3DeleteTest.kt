package ru.tinkoff.kora.s3.client.symbol.processor

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.*

class S3DeleteTest : AbstractS3Test() {
    @Test
    fun testDeleteKey() {
        val client = this.compile("""
            @S3.Client(clientFactoryTag = [String::class])
            interface Client {
                @S3.Delete
                fun deleteKey(@S3.Bucket bucket: String, key: String)
            }
            """.trimIndent())

        val key1 = UUID.randomUUID().toString()
        client.invoke<Any>("deleteKey", "bucket", key1)
        Mockito.verify(s3Client).delete("bucket", key1)
        Mockito.reset(s3Client)
    }

    @Test
    fun testDeleteKeyWithTemplate() {
        val client = this.compile("""
            @S3.Client(clientFactoryTag = [String::class])
            interface Client {
                @S3.Delete("some/prefix/{key1}/{key2}")
                fun deleteKey(@S3.Bucket bucket: String, key1: String, key2: String)
            }
            """.trimIndent())

        val key1 = UUID.randomUUID().toString()
        val key2 = UUID.randomUUID().toString()
        client.invoke<Any>("deleteKey", "bucket", key1, key2)
        Mockito.verify(s3Client).delete("bucket", "some/prefix/${key1}/${key2}")
        Mockito.reset(s3Client)
    }

    @Test
    fun testDeleteKeys() {
        val client = this.compile("""
            @S3.Client(clientFactoryTag = [String::class])
            interface Client {
                @S3.Delete("some/prefix/{key1}/{key2}")
                fun deleteKeys(@S3.Bucket bucket: String, key: List<String>)
            
                @S3.Delete("some/prefix/{key1}/{key2}")
                fun deleteKeysNonString(@S3.Bucket bucket: String, key: List<Long>)
            }
            """.trimIndent())

        val key1 = UUID.randomUUID().toString()
        val key2 = UUID.randomUUID().toString()
        client.invoke<Any>("deleteKeys", "bucket", listOf(key1, key2))
        Mockito.verify(s3Client).delete("bucket", listOf(key1, key2))
        Mockito.reset(s3Client)
    }
}
