package ru.tinkoff.kora.s3.client.symbol.processor

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import ru.tinkoff.kora.config.common.factory.MapConfigFactory
import ru.tinkoff.kora.s3.client.AwsCredentials
import ru.tinkoff.kora.s3.client.model.request.DeleteObjectArgs


internal class S3DeleteTest : AbstractS3ClientTest() {
    @Test
    fun testDeleteWithBucketAndKey() {
        val client = this.compile(
            """
            @S3.Client
            interface Client {
                @S3.Delete
                fun delete(@S3.Bucket bucket: String, key: String)
            }
            
            """.trimIndent()
        )

        client.invoke<Any?>("delete", "bucket", "key")

        verify(s3Client).deleteObject(any(), eq("bucket"), eq("key"), isNull())
        reset(s3Client)
    }

    @Test
    fun testDeleteWithTemplateKeyAndArgs() {
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
                @S3.Delete("prefix-{key}")
                fun deleteByTemplate(key: String, args: DeleteObjectArgs)
            }
            
            """.trimIndent(), newGenerated("\$Client_BucketsConfig", bucketConfig)
        )

        val args = DeleteObjectArgs()
        client.invoke<Any?>("deleteByTemplate", "key1", args)

        verify(s3Client).deleteObject(any(), eq("bucket_value"), eq("prefix-key1"), same(args))
        reset(s3Client)
    }

    @Test
    fun testDeleteWithAwsCredentials() {
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
                @S3.Delete
                fun deleteWithCreds(creds: AwsCredentials, key: String)
            }
            
            """.trimIndent(), newGenerated("\$Client_BucketsConfig", bucketConfig)
        )

        val creds = AwsCredentials.of("test", "test")
        client.invoke<Any?>("deleteWithCreds", creds, "key")

        verify(s3Client).deleteObject(same(creds), eq("bucket_value"), eq("key"), isNull())
        reset(s3Client)
    }

    @Test
    fun testDeleteConstantKey() {
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
                @S3.Delete("constant-key")
                fun deleteConstant()
            }
            
            """.trimIndent(), newGenerated("\$Client_BucketsConfig", bucketConfig)
        )

        client.invoke<Any?>("deleteConstant")

        verify(s3Client).deleteObject(any(), eq("bucket_value"), eq("constant-key"), isNull())
        reset(s3Client)
    }
}
