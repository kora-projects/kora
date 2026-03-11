package io.koraframework.s3.client.symbol.processor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import java.io.InputStream
import java.nio.ByteBuffer

object S3ClassNames {
    object Annotation {
        val client = ClassName("io.koraframework.s3.client.annotation", "S3", "Client")
        val bucket = ClassName("io.koraframework.s3.client.annotation", "S3", "Bucket")
        val get = ClassName("io.koraframework.s3.client.annotation", "S3", "Get")
        val list = ClassName("io.koraframework.s3.client.annotation", "S3", "List")
        val put = ClassName("io.koraframework.s3.client.annotation", "S3", "Put")
        val delete = ClassName("io.koraframework.s3.client.annotation", "S3", "Delete")
        val head = ClassName("io.koraframework.s3.client.annotation", "S3", "Head")
        val operations = setOf(get, list, put, delete, head)
    }

    val client = ClassName("io.koraframework.s3.client", "S3Client")
    val awsCredentials = ClassName("io.koraframework.s3.client", "AwsCredentials")
    val clientFactory = ClassName("io.koraframework.s3.client", "S3ClientFactory")
    val config = ClassName("io.koraframework.s3.client", "S3ClientConfig")
    val configWithCreds = ClassName("io.koraframework.s3.client", "S3ClientConfigWithCredentials")

    val contentWriter: ClassName = client.nestedClass("ContentWriter")

    val putObjectArgs = ClassName("io.koraframework.s3.client.model.request", "PutObjectArgs")
    val getObjectArgs = ClassName("io.koraframework.s3.client.model.request", "GetObjectArgs")
    val deleteObjectArgs = ClassName("io.koraframework.s3.client.model.request", "DeleteObjectArgs")
    val headObjectArgs = ClassName("io.koraframework.s3.client.model.request", "HeadObjectArgs")
    val listObjectsArgs = ClassName("io.koraframework.s3.client.model.request", "ListObjectsArgs")
    val createMultipartUploadArgs = ClassName("io.koraframework.s3.client.model.request", "CreateMultipartUploadArgs")
    val completeMultipartUploadArgs = ClassName("io.koraframework.s3.client.model.request", "CompleteMultipartUploadArgs")
    val args = setOf(putObjectArgs, getObjectArgs, deleteObjectArgs, headObjectArgs, listObjectsArgs)
    val getObjectResult = ClassName("io.koraframework.s3.client.model.response", "GetObjectResult")
    val headObjectResult = ClassName("io.koraframework.s3.client.model.response", "HeadObjectResult")
    val listBucketResult = ClassName("io.koraframework.s3.client.model.response", "ListBucketResult")
    val uploadedPart = ClassName("io.koraframework.s3.client.model.response", "UploadedPart")
    val listBucketResultItem: ClassName = listBucketResult.nestedClass("ListBucketItem")

    val unknownException = ClassName("io.koraframework.s3.client.exception", "S3ClientUnknownException")

    val bodyTypes = setOf(
        ByteArray::class.asTypeName(),
        ByteBuffer::class.asTypeName(),
        InputStream::class.asClassName(),
        contentWriter
    )
}
