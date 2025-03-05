package ru.tinkoff.kora.s3.client.symbol.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
import java.io.InputStream

object S3ClassNames {
    object Annotation {
        val client = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Client")
        val get = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Get")
        val list = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "List")
        val put = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Put")
        val delete = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Delete")
        val s3OperationClassNames = setOf(get, list, put, delete)

        val bucket = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Bucket")

        val listLimit = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "List", "Limit")
        val listDelimiter = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "List", "Delimiter")

    }

    val client = ClassName("ru.tinkoff.kora.s3.client", "S3Client")
    val clientFactory = ClassName("ru.tinkoff.kora.s3.client", "S3ClientFactory")
    val body = ClassName("ru.tinkoff.kora.s3.client.model", "S3Body")
    val s3Object = ClassName("ru.tinkoff.kora.s3.client.model", "S3Object")
    val objectMeta = ClassName("ru.tinkoff.kora.s3.client.model", "S3ObjectMeta")
    val uploadResult = ClassName("ru.tinkoff.kora.s3.client.model", "S3ObjectUploadResult")
    val bodyTypes = setOf(body, InputStream::class.asTypeName(), ByteArray::class.asTypeName())


    val rangeData = ClassName("ru.tinkoff.kora.s3.client", "S3Client", "RangeData")
    val rangeDataRange = ClassName("ru.tinkoff.kora.s3.client", "S3Client", "RangeData", "Range")
    val rangeDataStartFrom = ClassName("ru.tinkoff.kora.s3.client", "S3Client", "RangeData", "StartFrom")
    val rangeDataLastN = ClassName("ru.tinkoff.kora.s3.client", "S3Client", "RangeData", "LastN")
    val rangeClasses = setOf(rangeData, rangeDataRange, rangeDataStartFrom, rangeDataLastN)
}
