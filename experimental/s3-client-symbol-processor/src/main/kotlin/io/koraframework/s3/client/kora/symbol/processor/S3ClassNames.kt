package io.koraframework.s3.client.kora.symbol.processor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import java.io.InputStream
import java.nio.ByteBuffer

object S3ClassNames {
    object Annotation {
        val client = ClassName("io.koraframework.s3.client.kora.annotation", "S3", "Client")
        val bucket = ClassName("io.koraframework.s3.client.kora.annotation", "S3", "Bucket")
        val get = ClassName("io.koraframework.s3.client.kora.annotation", "S3", "Get")
        val list = ClassName("io.koraframework.s3.client.kora.annotation", "S3", "List")
        val put = ClassName("io.koraframework.s3.client.kora.annotation", "S3", "Put")
        val delete = ClassName("io.koraframework.s3.client.kora.annotation", "S3", "Delete")
        val head = ClassName("io.koraframework.s3.client.kora.annotation", "S3", "Head")
        val operations = setOf(get, list, put, delete, head)
    }

    val client = ClassName("io.koraframework.s3.client.kora", "S3Client")
    val s3Credentials = ClassName("io.koraframework.s3.client.kora", "S3Credentials")
    val clientFactory = ClassName("io.koraframework.s3.client.kora", "S3ClientFactory")
    val config = ClassName("io.koraframework.s3.client.kora", "S3ClientConfig")
    val configWithCreds = ClassName("io.koraframework.s3.client.kora", "S3ClientConfigWithCredentials")

    val contentWriter: ClassName = client.nestedClass("ContentWriter")

    val putObjectArgs = ClassName("io.koraframework.s3.client.kora.model.request", "PutObjectArgs")
    val getObjectArgs = ClassName("io.koraframework.s3.client.kora.model.request", "GetObjectArgs")
    val deleteObjectArgs = ClassName("io.koraframework.s3.client.kora.model.request", "DeleteObjectArgs")
    val headObjectArgs = ClassName("io.koraframework.s3.client.kora.model.request", "HeadObjectArgs")
    val listObjectsArgs = ClassName("io.koraframework.s3.client.kora.model.request", "ListObjectsArgs")
    val createMultipartUploadArgs = ClassName("io.koraframework.s3.client.kora.model.request", "CreateMultipartUploadArgs")
    val completeMultipartUploadArgs = ClassName("io.koraframework.s3.client.kora.model.request", "CompleteMultipartUploadArgs")
    val args = setOf(putObjectArgs, getObjectArgs, deleteObjectArgs, headObjectArgs, listObjectsArgs)
    val getObjectResult = ClassName("io.koraframework.s3.client.kora.model.response", "GetObjectResult")
    val headObjectResult = ClassName("io.koraframework.s3.client.kora.model.response", "HeadObjectResult")
    val listBucketResult = ClassName("io.koraframework.s3.client.kora.model.response", "ListBucketResult")
    val uploadedPart = ClassName("io.koraframework.s3.client.kora.model.response", "UploadedPart")
    val listBucketResultItem: ClassName = listBucketResult.nestedClass("ListBucketItem")

    val unknownException = ClassName("io.koraframework.s3.client.kora.exception", "S3ClientUnknownException")

    val bodyTypes = setOf(
        ByteArray::class.asTypeName(),
        ByteBuffer::class.asTypeName(),
        InputStream::class.asClassName(),
        contentWriter
    )
}
