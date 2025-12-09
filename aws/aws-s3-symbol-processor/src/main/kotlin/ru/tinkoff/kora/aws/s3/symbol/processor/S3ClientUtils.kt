package ru.tinkoff.kora.aws.s3.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException


object S3ClientUtils {
    fun parseConfigBuckets(s3client: KSClassDeclaration): List<String> {
        val bucketPaths = LinkedHashSet<String>()

        val onClass = s3client.findAnnotation(S3ClassNames.Annotation.bucket)
        if (onClass != null) {
            val value = onClass.findValueNoDefault<String>("value")
            if (value == null) {
                throw ProcessingErrorException("@S3.Bucket annotation is missing value", s3client)
            }
            bucketPaths.add(value)
        }
        for (function in s3client.getAllFunctions()) {
            if (!function.isAbstract) {
                continue
            }
            val onMethod = function.findAnnotation(S3ClassNames.Annotation.bucket)
            if (onMethod == null) {
                continue
            }
            val value = onMethod.findValueNoDefault<String>("value")
            if (value == null) {
                throw ProcessingErrorException("@S3.Bucket annotation is missing value", function)
            }
            bucketPaths.add(value)
        }
        return ArrayList<String>(bucketPaths)
    }

    fun bucketParameter(method: KSFunctionDeclaration): KSValueParameter? {
        var foundParam: KSValueParameter? = null
        for (param in method.parameters) {

            val ann = param.findAnnotation(S3ClassNames.Annotation.bucket)
            if (ann != null) {
                if (foundParam != null) {
                    throw ProcessingErrorException("Multiple @S3.Bucket annotations found", method)
                }
                foundParam = param
            }
        }
        return foundParam
    }

    fun credentialsParameter(method: KSFunctionDeclaration): KSValueParameter? {
        var foundParam: KSValueParameter? = null
        for (param in method.parameters) {
            val typeName = param.type.resolve().toTypeName()
            if (S3ClassNames.awsCredentials == typeName) {
                if (foundParam != null) {
                    throw ProcessingErrorException("Multiple AwsCredentials parameters found", method)
                }
                foundParam = param
            }
        }
        return foundParam
    }
}
