package ru.tinkoff.kora.s3.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.config.common.factory.MapConfigFactory
import ru.tinkoff.kora.s3.client.S3Client
import ru.tinkoff.kora.s3.client.model.S3Body
import ru.tinkoff.kora.s3.client.model.S3Object
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta
import java.io.InputStream
import java.time.Instant
import java.util.*

class S3GetTest : AbstractS3Test() {
    @Test
    fun testGetMetadata() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.Get
                fun getRequired(@S3.Bucket bucket: String, key: String): S3ObjectMeta
            
                @S3.Get
                fun getOptional(@S3.Bucket bucket: String, key: String): S3ObjectMeta?
            }
            """.trimIndent())

        val meta = S3ObjectMeta("bucket", "key", Instant.now(), -1)

        whenever(s3Client.getMeta("bucket", "key")).thenReturn(meta)
        assertThat(client.invoke<S3ObjectMeta>("getRequired", "bucket", "key")).isSameAs(meta)
        verify(s3Client).getMeta("bucket", "key")
        reset(s3Client)

        whenever(s3Client.getMetaOptional("bucket", "key")).thenReturn(meta)
        assertThat(client.invoke<S3ObjectMeta?>("getOptional", "bucket", "key")).isEqualTo(meta)
        verify(s3Client).getMetaOptional("bucket", "key")
        reset(s3Client)

        whenever(s3Client.getMetaOptional("bucket", "key")).thenReturn(null)
        assertThat(client.invoke<S3ObjectMeta?>("getOptional", "bucket", "key")).isNull()
        verify(s3Client).getMetaOptional("bucket", "key")
        reset(s3Client)
    }

    @Test
    fun testGetBytes() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.Get
                fun get(@S3.Bucket bucket: String, key: String): ByteArray
                @S3.Get
                fun getRange(@S3.Bucket bucket: String, key: String, range: RangeData): ByteArray
                @S3.Get
                fun getNullable(@S3.Bucket bucket: String, key: String): ByteArray?
            }
            """.trimIndent())

        val meta = S3ObjectMeta("bucket", "key", Instant.now(), -1)
        val bytes = UUID.randomUUID().toString().toByteArray()
        val supplier: () -> S3Object = { S3Object(meta, S3Body.ofInputStream(bytes.inputStream(), bytes.size.toLong())) }

        whenever(s3Client.get("bucket", "key", null)).thenReturn(supplier())
        assertThat(client.invoke<ByteArray>("get", "bucket", "key")).isEqualTo(bytes)
        verify(s3Client).get("bucket", "key", null)
        reset(s3Client)

        whenever(s3Client.get("bucket", "key", S3Client.RangeData.LastN(10))).thenReturn(supplier())
        assertThat(client.invoke<ByteArray>("getRange", "bucket", "key", S3Client.RangeData.LastN(10))).isEqualTo(bytes)
        verify(s3Client).get("bucket", "key", S3Client.RangeData.LastN(10))
        reset(s3Client)

        whenever(s3Client.getOptional("bucket", "key", null)).thenReturn(null)
        assertThat(client.invoke<ByteArray?>("getNullable", "bucket", "key")).isNull()
        verify(s3Client).getOptional("bucket", "key", null)
        reset(s3Client)

        whenever(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier())
        assertThat(client.invoke<ByteArray?>("getNullable", "bucket", "key")).isEqualTo(bytes)
        verify(s3Client).getOptional("bucket", "key", null)
        reset(s3Client)
    }

    @Test
    fun testGetS3Object() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.Get
                fun get(@S3.Bucket bucket: String, key: String): S3Object
                @S3.Get
                fun getRange(@S3.Bucket bucket: String, key: String, range: RangeData): S3Object
                @S3.Get
                fun getNullable(@S3.Bucket bucket: String, key: String): S3Object?
            }
            """.trimIndent())

        val meta = S3ObjectMeta("bucket", "key", Instant.now(), -1)
        val bytes = UUID.randomUUID().toString().toByteArray()
        val supplier: () -> S3Object = { S3Object(meta, S3Body.ofInputStream(bytes.inputStream(), bytes.size.toLong())) }

        whenever(s3Client.get("bucket", "key", null)).thenReturn(supplier())
        client.invoke<S3Object>("get", "bucket", "key").use {
            assertThat(it).isNotNull()
            assertThat(it.meta).isSameAs(meta)
            it.body.use {
                assertThat(it.asBytes()).isEqualTo(bytes)
            }
        }
        verify(s3Client).get("bucket", "key", null)
        reset(s3Client)

        whenever(s3Client.get("bucket", "key", S3Client.RangeData.LastN(10))).thenReturn(supplier())
        client.invoke<S3Object>("getRange", "bucket", "key", S3Client.RangeData.LastN(10)).use {
            assertThat(it).isNotNull()
            assertThat(it.meta).isSameAs(meta)
            it.body.use {
                assertThat(it.asBytes()).isEqualTo(bytes)
            }
        }
        verify(s3Client).get("bucket", "key", S3Client.RangeData.LastN(10))
        reset(s3Client)

        whenever(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier())
        client.invoke<S3Object>("getNullable", "bucket", "key").use {
            assertThat(it).isNotNull()
            assertThat(it.meta).isSameAs(meta)
            it.body.use {
                assertThat(it.asBytes()).isEqualTo(bytes)
            }
        }
        verify(s3Client).getOptional("bucket", "key", null)
        reset(s3Client)

        whenever(s3Client.getOptional("bucket", "key", null)).thenReturn(null)
        client.invoke<S3Object>("getNullable", "bucket", "key").use {
            assertThat(it).isNull()
        }
        verify(s3Client).getOptional("bucket", "key", null)
        reset(s3Client)
    }

    @Test
    fun testGetS3Body() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.Get
                fun get(@S3.Bucket bucket: String, key: String): S3Body
                @S3.Get
                fun getRange(@S3.Bucket bucket: String, key: String, range: RangeData): S3Body
                @S3.Get
                fun getNullable(@S3.Bucket bucket: String, key: String): S3Body?
            }
            """.trimIndent())

        val meta = S3ObjectMeta("bucket", "key", Instant.now(), -1)
        val bytes = UUID.randomUUID().toString().toByteArray()
        val supplier: () -> S3Object = { S3Object(meta, S3Body.ofInputStream(bytes.inputStream(), bytes.size.toLong())) }

        whenever(s3Client.get("bucket", "key", null)).thenReturn(supplier())
        client.invoke<S3Body>("get", "bucket", "key").use {
            assertThat(it).isNotNull()
            assertThat(it.asBytes()).isEqualTo(bytes)
        }
        verify(s3Client).get("bucket", "key", null)
        reset(s3Client)

        whenever(s3Client.get("bucket", "key", S3Client.RangeData.LastN(10))).thenReturn(supplier())
        client.invoke<S3Body>("getRange", "bucket", "key", S3Client.RangeData.LastN(10)).use {
            assertThat(it).isNotNull()
            assertThat(it.asBytes()).isEqualTo(bytes)
        }
        verify(s3Client).get("bucket", "key", S3Client.RangeData.LastN(10))
        reset(s3Client)

        whenever(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier())
        client.invoke<S3Body>("getNullable", "bucket", "key").use {
            assertThat(it).isNotNull()
            assertThat(it.asBytes()).isEqualTo(bytes)
        }
        verify(s3Client).getOptional("bucket", "key", null)
        reset(s3Client)

        whenever(s3Client.getOptional("bucket", "key", null)).thenReturn(null)
        client.invoke<S3Body>("getNullable", "bucket", "key").use {
            assertThat(it).isNull()
        }
        verify(s3Client).getOptional("bucket", "key", null)
        reset(s3Client)
    }

    @Test
    fun testGetInputStream() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.Get
                fun get(@S3.Bucket bucket: String, key: String): InputStream
                @S3.Get
                fun getRange(@S3.Bucket bucket: String, key: String, range: RangeData): InputStream
                @S3.Get
                fun getNullable(@S3.Bucket bucket: String, key: String): InputStream?
            }
            """.trimIndent())

        val meta = S3ObjectMeta("bucket", "key", Instant.now(), -1)
        val bytes = UUID.randomUUID().toString().toByteArray()
        val supplier: () -> S3Object = { S3Object(meta, S3Body.ofInputStream(bytes.inputStream(), bytes.size.toLong())) }

        whenever(s3Client.get("bucket", "key", null)).thenReturn(supplier())
        client.invoke<InputStream>("get", "bucket", "key").use {
            assertThat(it).isNotNull()
            assertThat(it.readAllBytes()).isEqualTo(bytes)
        }
        verify(s3Client).get("bucket", "key", null)
        reset(s3Client)

        whenever(s3Client.get("bucket", "key", S3Client.RangeData.LastN(10))).thenReturn(supplier())
        client.invoke<InputStream>("getRange", "bucket", "key", S3Client.RangeData.LastN(10)).use {
            assertThat(it).isNotNull()
            assertThat(it.readAllBytes()).isEqualTo(bytes)
        }
        verify(s3Client).get("bucket", "key", S3Client.RangeData.LastN(10))
        reset(s3Client)

        whenever(s3Client.getOptional("bucket", "key", null)).thenReturn(supplier())
        client.invoke<InputStream>("getNullable", "bucket", "key").use {
            assertThat(it).isNotNull()
            assertThat(it.readAllBytes()).isEqualTo(bytes)
        }
        verify(s3Client).getOptional("bucket", "key", null)
        reset(s3Client)

        whenever(s3Client.getOptional("bucket", "key", null)).thenReturn(null)
        client.invoke<InputStream>("getNullable", "bucket", "key").use {
            assertThat(it).isNull()
        }
        verify(s3Client).getOptional("bucket", "key", null)
        reset(s3Client)
    }

    @Test
    fun testGetWithKeyTemplate() {
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.Get("prefix/{key1}/{key2}/suffix")
                fun get(@S3.Bucket bucket: String, key1: String, key2: String): ByteArray
            }
            """.trimIndent())

        val k1 = UUID.randomUUID().toString()
        val k2 = UUID.randomUUID().toString()
        val key = "prefix/$k1/$k2/suffix"
        val meta = S3ObjectMeta("bucket", key, Instant.now(), -1)
        val bytes = UUID.randomUUID().toString().toByteArray()
        val supplier: () -> S3Object = { S3Object(meta, S3Body.ofInputStream(bytes.inputStream(), bytes.size.toLong())) }

        whenever(s3Client.get("bucket", key, null)).thenReturn(supplier())
        client.invoke<ByteArray>("get", "bucket", k1, k2).let {
            assertThat(it).isNotNull()
            assertThat(it).isEqualTo(bytes)
        }
    }

    @Test
    fun testGetWithBucketOnClient() {
        val config = MapConfigFactory.fromMap(mapOf(
            "s3" to mapOf(
                "client" to mapOf(
                    "bucket" to "on-class"
                )
            )
        ))
        val client = this.compile("""
            @S3.Client
            @Bucket("s3.client.bucket")
            interface Client {
                @S3.Get("prefix/{key}/suffix")
                fun get(key: String): ByteArray
            }
            """.trimIndent(), newGenerated("\$Client_ClientConfig", config))

        val k1 = UUID.randomUUID().toString()
        val key = "prefix/$k1/suffix"
        val meta = S3ObjectMeta("bucket", key, Instant.now(), -1)
        val bytes = UUID.randomUUID().toString().toByteArray()
        val supplier: () -> S3Object = { S3Object(meta, S3Body.ofInputStream(bytes.inputStream(), bytes.size.toLong())) }

        whenever(s3Client.get("on-class", key, null)).thenReturn(supplier())
        client.invoke<ByteArray>("get", k1).let {
            assertThat(it).isNotNull()
            assertThat(it).isEqualTo(bytes)
        }
    }

    @Test
    fun testGetWithBucketOnMethod() {
        val config = MapConfigFactory.fromMap(mapOf(
            "s3" to mapOf(
                "client" to mapOf(
                    "get" to mapOf(
                        "bucket" to "on-method"
                    )
                )
            )
        ))
        val client = this.compile("""
            @S3.Client
            interface Client {
                @S3.Get("prefix/{key}/suffix")
                @Bucket("s3.client.get.bucket")
                fun get(key: String): ByteArray
            }
            """.trimIndent(), newGenerated("\$Client_ClientConfig", config))

        val k1 = UUID.randomUUID().toString()
        val key = "prefix/$k1/suffix"
        val meta = S3ObjectMeta("bucket", key, Instant.now(), -1)
        val bytes = UUID.randomUUID().toString().toByteArray()
        val supplier: () -> S3Object = { S3Object(meta, S3Body.ofInputStream(bytes.inputStream(), bytes.size.toLong())) }

        whenever(s3Client.get("on-method", key, null)).thenReturn(supplier())
        client.invoke<ByteArray>("get", k1).let {
            assertThat(it).isNotNull()
            assertThat(it).isEqualTo(bytes)
        }
    }

    @Test
    fun testGetWithBucketOnMethodAndType() {
        val config = MapConfigFactory.fromMap(mapOf(
            "s3" to mapOf(
                "client" to mapOf(
                    "bucket" to "on-class",
                    "get" to mapOf(
                        "bucket" to "on-method"
                    )
                )
            )
        ))
        val client = this.compile("""
            @S3.Client
            @Bucket("s3.client.bucket")
            interface Client {
                @S3.Get("prefix/{key}/suffix")
                @Bucket("s3.client.get.bucket")
                fun get(key: String): ByteArray
            }
            """.trimIndent(), newGenerated("\$Client_ClientConfig", config))

        val k1 = UUID.randomUUID().toString()
        val key = "prefix/$k1/suffix"
        val meta = S3ObjectMeta("bucket", key, Instant.now(), -1)
        val bytes = UUID.randomUUID().toString().toByteArray()
        val supplier: () -> S3Object = { S3Object(meta, S3Body.ofInputStream(bytes.inputStream(), bytes.size.toLong())) }

        whenever(s3Client.get("on-method", key, null)).thenReturn(supplier())
        client.invoke<ByteArray>("get", k1).let {
            assertThat(it).isNotNull()
            assertThat(it).isEqualTo(bytes)
        }
    }

}
