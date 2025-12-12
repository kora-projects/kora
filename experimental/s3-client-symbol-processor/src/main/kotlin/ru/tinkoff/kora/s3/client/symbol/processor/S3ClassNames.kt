package ru.tinkoff.kora.s3.client.symbol.processor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import java.io.InputStream
import java.nio.ByteBuffer

object S3ClassNames {
    object Annotation {
        val client = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Client")
        val bucket = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Bucket")
        val get = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Get")
        val list = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "List")
        val put = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Put")
        val delete = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Delete")
        val head = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Head")
        val operations = setOf(get, list, put, delete, head)
    }

    val client = ClassName("ru.tinkoff.kora.s3.client", "S3Client")
    val awsCredentials = ClassName("ru.tinkoff.kora.s3.client", "AwsCredentials")
    val clientFactory = ClassName("ru.tinkoff.kora.s3.client", "S3ClientFactory")
    val config = ClassName("ru.tinkoff.kora.s3.client", "S3ClientConfig")
    val configWithCreds = ClassName("ru.tinkoff.kora.s3.client", "S3ClientConfigWithCredentials")

    val contentWriter: ClassName = client.nestedClass("ContentWriter")

    val putObjectArgs = ClassName("ru.tinkoff.kora.s3.client.model.request", "PutObjectArgs")
    val getObjectArgs = ClassName("ru.tinkoff.kora.s3.client.model.request", "GetObjectArgs")
    val deleteObjectArgs = ClassName("ru.tinkoff.kora.s3.client.model.request", "DeleteObjectArgs")
    val headObjectArgs = ClassName("ru.tinkoff.kora.s3.client.model.request", "HeadObjectArgs")
    val listObjectsArgs = ClassName("ru.tinkoff.kora.s3.client.model.request", "ListObjectsArgs")
    val createMultipartUploadArgs = ClassName("ru.tinkoff.kora.s3.client.model.request", "CreateMultipartUploadArgs")
    val completeMultipartUploadArgs = ClassName("ru.tinkoff.kora.s3.client.model.request", "CompleteMultipartUploadArgs")
    val args = setOf(putObjectArgs, getObjectArgs, deleteObjectArgs, headObjectArgs, listObjectsArgs)
    val getObjectResult = ClassName("ru.tinkoff.kora.s3.client.model.response", "GetObjectResult")
    val headObjectResult = ClassName("ru.tinkoff.kora.s3.client.model.response", "HeadObjectResult")
    val listBucketResult = ClassName("ru.tinkoff.kora.s3.client.model.response", "ListBucketResult")
    val uploadedPart = ClassName("ru.tinkoff.kora.s3.client.model.response", "UploadedPart")
    val listBucketResultItem: ClassName = listBucketResult.nestedClass("ListBucketItem")

    val unknownException = ClassName("ru.tinkoff.kora.s3.client.exception", "S3ClientUnknownException")

    val bodyTypes = setOf(
        ByteArray::class.asTypeName(),
        ByteBuffer::class.asTypeName(),
        InputStream::class.asClassName(),
        contentWriter
    )
}
