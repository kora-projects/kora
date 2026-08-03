package io.koraframework.s3.client.kora.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.exception.ProcessingErrorException


object S3ClientUtils {
    fun parseConfigBuckets(s3client: KSClassDeclaration): List<String> {
        val bucketPaths = LinkedHashSet<String>()

        val onClass = s3client.findAnnotation(S3ClassNames.Annotation.bucket)
        if (onClass != null) {
            val value = onClass.findValueNoDefault<String>("value")
            if (value == null) {
                throw ProcessingErrorException(missingBucketValueError(s3client.qualifiedName?.asString()), s3client)
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
                throw ProcessingErrorException(missingBucketValueError(function.qualifiedName?.asString()), function)
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
                    throw ProcessingErrorException(
                        """
                        S3 bucket parameter is ambiguous for '${method.qualifiedName?.asString() ?: method.simpleName.asString()}': more than one parameter is annotated with @S3.Bucket.

                        Fix: keep @S3.Bucket on exactly one bucket parameter, or remove all bucket parameters and configure the bucket with @S3.Bucket("config.path") on the S3 client class or method.
                        Example: fun get(@S3.Bucket bucket: String, key: String): GetObjectResult
                        """.trimIndent(),
                        method
                    )
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
            if (S3ClassNames.s3Credentials == typeName) {
                if (foundParam != null) {
                    throw ProcessingErrorException(
                        """
                        S3 credentials are ambiguous for '${method.qualifiedName?.asString() ?: method.simpleName.asString()}': more than one parameter has type S3Credentials.

                        Fix: keep at most one S3Credentials parameter. If credentials should come from config, remove the S3Credentials parameter completely.
                        Example: fun get(credentials: S3Credentials, @S3.Bucket bucket: String, key: String): GetObjectResult
                        """.trimIndent(),
                        method
                    )
                }
                foundParam = param
            }
        }
        return foundParam
    }

    private fun missingBucketValueError(targetName: String?): String {
        return """
            S3 bucket config path is missing${targetName?.let { " for '$it'" } ?: ""}: @S3.Bucket was used without a value.

            Fix: pass the config path that contains the bucket name, or move @S3.Bucket to a method parameter when the bucket is passed at runtime.
            Example: @S3.Bucket("s3.clients.default.bucket")
        """.trimIndent()
    }
}
