package io.koraframework.s3.client.kora.symbol.processor.gen

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.CommonAopUtils.extendsKeepAop
import io.koraframework.ksp.common.CommonAopUtils.overridingKeepAop
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.CommonClassNames.isCollection
import io.koraframework.ksp.common.CommonClassNames.isMap
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.resolveToUnderlying
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.generatedClassName
import io.koraframework.s3.client.kora.symbol.processor.S3ClientUtils
import io.koraframework.s3.client.kora.symbol.processor.S3ClassNames
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer


object ClientGenerator {
    fun generate(resolver: Resolver, s3client: KSClassDeclaration): TypeSpec {
        val packageName = s3client.packageName.asString()
        val bucketsType = ClassName(packageName, s3client.generatedClassName("BucketsConfig"))
        val bucketsPath = S3ClientUtils.parseConfigBuckets(s3client)
        val credsRequired = s3client.getAllFunctions()
            .filter { it.isAbstract }
            .any { S3ClientUtils.credentialsParameter(it) == null }

        val configType = if (credsRequired)
            S3ClassNames.configWithCreds
        else
            S3ClassNames.config
        val b = s3client.extendsKeepAop(s3client.generatedClassName("S3ClientImpl"), resolver)
            .generated(ClientGenerator::class)
            .addProperty(
                PropertySpec.Companion.builder("client", S3ClassNames.client, KModifier.PRIVATE)
                    .initializer("clientFactory.create(configPath, %T::class.java, clientConfig)", s3client.toClassName())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("config", configType, KModifier.PRIVATE)
                    .initializer("clientConfig")
                    .build()
            )
        val constructor = FunSpec.constructorBuilder()
            .addParameter("configPath", String::class)
            .addParameter("clientFactory", S3ClassNames.clientFactory)
            .addParameter("clientConfig", configType)
        if (!bucketsPath.isEmpty()) {
            constructor.addParameter("bucketsConfig", bucketsType)
            b.addProperty(
                PropertySpec.builder("bucketsConfig", bucketsType, KModifier.PRIVATE)
                    .initializer("bucketsConfig")
                    .build()
            )
        }
        b.primaryConstructor(constructor.build())
        for (function in s3client.getAllFunctions()) {
            if (!function.isAbstract) {
                continue
            }
            b.addFunction(generateFunction(resolver, function))
        }
        return b.build()
    }

    private fun generateFunction(resolver: Resolver, function: KSFunctionDeclaration): FunSpec {
        val operations = S3ClassNames.Annotation.operations.asSequence()
            .mapNotNull { function.findAnnotation(it) }
            .toList()
        if (operations.isEmpty()) {
            throw ProcessingErrorException(missingOperationError(function), function)
        }
        if (operations.size > 1) {
            throw ProcessingErrorException(multipleOperationsError(function), function)
        }
        val operation = operations.first()
        return when (operation.annotationType.resolve().toString()) {
            "Get" -> generateGet(resolver, function, operation)
            "List" -> generateList(resolver, function, operation)
            "Head" -> generateHead(resolver, function, operation)
            "Put" -> generatePut(resolver, function, operation)
            "Delete" -> generateDelete(resolver, function, operation)
            else -> throw IllegalStateException(unsupportedOperationInternalError(function, operation))
        }

    }

    private fun generateDelete(resolver: Resolver, function: KSFunctionDeclaration, operation: KSAnnotation): FunSpec {
        val b = function.overridingKeepAop(resolver)
        generateCreds(function, b)
        generateBucket(function, b)
        b.addStatement("val _key = %L", generateKey(function, operation))
        val args = function.parameters.firstOrNull { it.type.resolve().toTypeName() == S3ClassNames.deleteObjectArgs }
        if (args == null) {
            b.addStatement("val _args = null as %T?", S3ClassNames.deleteObjectArgs)
        } else {
            b.addStatement("val _args = %N", args.name!!.asString())
        }
        b.addStatement("this.client.deleteObject(_creds, _bucket, _key, _args)")
        if (function.returnType?.resolve()?.toTypeName() != UNIT) {
            throw ProcessingErrorException(unexpectedReturnTypeError(function, "@S3.Delete", "Unit", function.returnType?.resolve()?.toTypeName()), function)
        }
        return b.build()
    }

    private fun generatePut(resolver: Resolver, function: KSFunctionDeclaration, operation: KSAnnotation): FunSpec {
        val b = function.overridingKeepAop(resolver)
        generateCreds(function, b)
        generateBucket(function, b)
        b.addStatement("val _key = %L", generateKey(function, operation))
        val args = function.parameters.firstOrNull { it.type.resolve().toTypeName() == S3ClassNames.putObjectArgs }
        if (args == null) {
            b.addStatement("val _args = null as %T?", S3ClassNames.putObjectArgs)
        } else {
            b.addStatement("val _args = %N", args.name!!.asString())
        }
        val contents = function.parameters.filter { S3ClassNames.bodyTypes.contains(it.type.resolve().toTypeName()) }
        if (contents.isEmpty()) {
            throw ProcessingErrorException(
                """
                S3 PUT operation '${functionName(function)}' has no upload body parameter.

                Fix: add exactly one body parameter with one of the supported types: ${supportedBodyTypes()}.
                Example: fun put(@S3.Bucket bucket: String, key: String, body: ByteArray): String
                """.trimIndent(),
                function
            )
        }
        if (contents.size != 1) {
            throw ProcessingErrorException(
                """
                S3 PUT operation '${functionName(function)}' has ${contents.size} upload body parameters, but only one body parameter is supported.

                Fix: keep exactly one body parameter with one of the supported types: ${supportedBodyTypes()}.
                Example: fun put(@S3.Bucket bucket: String, key: String, body: ByteArray): String
                """.trimIndent(),
                function
            )
        }
        val returnType = function.returnType!!.resolveToUnderlying().toTypeName()
        val hasReturn = when (returnType) {
            String::class.asTypeName() -> true
            UNIT -> false
            else -> throw ProcessingErrorException(unexpectedReturnTypeError(function, "@S3.Put", "String or Unit", returnType), function)
        }
        val content = contents.first()
        val contentType = content.type.resolveToUnderlying().toTypeName()

        return when (contentType) {
            S3ClassNames.contentWriter -> {
                if (hasReturn) {
                    b.addCode("return ")
                }
                b.addStatement("this.client.putObject(_creds, _bucket, _key, _args, %N)", content.name!!.asString())
                b.build()
            }

            BYTE_ARRAY -> {
                if (hasReturn) {
                    b.addCode("return ");
                }
                b.addStatement("this.client.putObject(_creds, _bucket, _key, _args, %N, 0, %N.size)", content.name!!.asString(), content.name!!.asString())
                b.build()
            }

            ByteBuffer::class.asTypeName() -> {
                val contentName = content.name!!.asString()
                b.addStatement("val _len = %N.remaining()", contentName)
                b.addStatement("val _buf: ByteArray")
                b.addStatement("val _off: Int")
                b.controlFlow("if (%N.hasArray())", contentName) {
                    addStatement("_buf = %N.array()", contentName)
                    addStatement("_off = %N.arrayOffset()", contentName)
                    nextControlFlow("else")
                    addStatement("_buf = %T(_len)", BYTE_ARRAY)
                    addStatement("_off = 0")
                    addStatement("%N.get(_buf)", contentName)
                }
                if (hasReturn) {
                    b.addCode("return ");
                }
                b.addStatement("this.client.putObject(_creds, _bucket, _key, _args, _buf, _off, _len)")
                b.build();
            }

            InputStream::class.asTypeName() -> {
                val contentName = content.name!!.asString()
                b.addStatement("val _buf = %T(this.config.upload().partSize().toBytes().toInt())", BYTE_ARRAY);
                b.controlFlow("try") {
                    controlFlow("%N.use", contentName) {
                        addStatement("val _read = %N.readNBytes(_buf, 0, _buf.size)", contentName)
                        addStatement("val _parts = mutableListOf<%T>()", S3ClassNames.uploadedPart)
                        addStatement("val _uploadId: String")
                        controlFlow("if (_read == _buf.size)") {
                            addStatement("val _createMultipartUploadArgs = %T.from(_args)", S3ClassNames.createMultipartUploadArgs)
                            addStatement("_uploadId = this.client.createMultipartUpload(_creds, _bucket, _key, _createMultipartUploadArgs)")
                            addStatement("val _part = this.client.uploadPart(_creds, _bucket, _key, _uploadId, 1, _buf, 0, _read)")
                            addStatement("_parts.add(_part)")
                            nextControlFlow("else")
                            addComment("end of stream reached on first part, just upload it")
                            if (hasReturn) {
                                addCode("return ")
                            }
                            b.addStatement("this.client.putObject(_creds, _bucket, _key, _args, _buf, 0, _read)")
                        }
                        addStatement("var _partNumber = 2")
                        controlFlow("while (true)") {
                            addStatement("val _read = %N.readNBytes(_buf, 0, _buf.size)", contentName)
                            controlFlow("if (_read > 0)") {
                                addStatement("val _part = this.client.uploadPart(_creds, _bucket, _key, _uploadId, _partNumber, _buf, 0, _read)")
                                addStatement("_parts.add(_part)")
                            }
                            controlFlow("if (_read < _buf.size)") {
                                addStatement("val _completeMultipartUploadArgs = %T.from(_args)", S3ClassNames.completeMultipartUploadArgs)
                                if (hasReturn) {
                                    addCode("return ")
                                }
                                addStatement("this.client.completeMultipartUpload(_creds, _bucket, _key, _uploadId, _parts, _completeMultipartUploadArgs)")
                            }
                            addStatement("_partNumber++")
                        }
                    }
                    nextControlFlow("catch (_e: %T)", IOException::class.asClassName())
                    addStatement("throw %T(_e)", S3ClassNames.unknownException)
                }
                b.build()
            }

            else -> throw ProcessingErrorException(
                """
                S3 PUT operation '${functionName(function)}' has unsupported body type '$contentType'.

                Fix: use one supported body type: ${supportedBodyTypes()}.
                Example: fun put(@S3.Bucket bucket: String, key: String, body: ByteArray): String
                """.trimIndent(),
                function
            )
        }
    }


    private fun generateHead(resolver: Resolver, function: KSFunctionDeclaration, operation: KSAnnotation): FunSpec {
        val b = function.overridingKeepAop(resolver)
        generateCreds(function, b)
        generateBucket(function, b)
        b.addStatement("val _key = %L", generateKey(function, operation))
        val args = function.parameters.firstOrNull { it.type.resolveToUnderlying() == S3ClassNames.headObjectArgs }
        if (args == null) {
            b.addStatement("val _args = null as %T?", S3ClassNames.headObjectArgs)
        } else {
            b.addStatement("val _args = %N", args.name!!.asString())
        }
        val returnType = function.returnType!!.resolveToUnderlying().toTypeName()
        if (returnType.isNullable) {
            b.addStatement("val _rs = this.client.headObject(_creds, _bucket, _key, _args, false)")
            b.addStatement("if (_rs == null) return null")
        } else {
            b.addStatement("val _rs = this.client.headObject(_creds, _bucket, _key, _args)")
        }
        if (returnType == S3ClassNames.headObjectResult) {
            b.addStatement("return _rs")
        } else {
            throw ProcessingErrorException(unexpectedReturnTypeError(function, "@S3.Head", "HeadObjectResult or HeadObjectResult?", returnType), function)
        }
        return b.build()
    }

    private fun generateList(resolver: Resolver, function: KSFunctionDeclaration, operation: KSAnnotation): FunSpec {
        val b = function.overridingKeepAop(resolver)
        generateCreds(function, b)
        generateBucket(function, b)
        val args = function.parameters.firstOrNull { it.type.resolve().toTypeName() == S3ClassNames.listObjectsArgs }
        if (args == null) {
            b.addStatement("val _args = %T()", S3ClassNames.listObjectsArgs)
            b.addStatement("_args.prefix = %L", generateKey(function, operation))
        } else {
            b.addStatement("val _args = %N", args.name!!.asString())
        }
        return when (val returnType = function.returnType!!.resolveToUnderlying().toTypeName().copy(false)) {
            S3ClassNames.listBucketResult -> {
                b.addStatement("return this.client.listObjectsV2(_creds, _bucket, _args)")
                b.build()
            }

            List::class.asClassName().parameterizedBy(S3ClassNames.listBucketResultItem) -> {
                b.addStatement("return this.client.listObjectsV2(_creds, _bucket, _args).items()")
                b.build()
            }

            List::class.asClassName().parameterizedBy(String::class.asClassName()) -> {
                b.addStatement("return this.client.listObjectsV2(_creds, _bucket, _args).items().map { it.key() }")
                b.build()
            }

            Iterator::class.asClassName().parameterizedBy(S3ClassNames.listBucketResultItem) -> {
                b.addStatement("return this.client.listObjectsV2Iterator(_creds, _bucket, _args)")
                b.build()
            }

            Iterator::class.asClassName().parameterizedBy(String::class.asClassName()) -> {
                b.addStatement("val _it = this.client.listObjectsV2Iterator(_creds, _bucket, _args)")
                b.controlFlow("@%T return object : %T<String>", CommonClassNames.generated, Iterator::class.asClassName()) {
                    addCode(
                        """
                        override fun hasNext() = _it.hasNext()
                        override fun next() = _it.next().key()
                    """.trimIndent()
                    )
                }
                b.build()
            }

            else -> {
                throw ProcessingErrorException(
                    unexpectedReturnTypeError(
                        function,
                        "@S3.List",
                        "ListBucketResult, List<ListBucketResult.ListBucketItem>, List<String>, Iterator<ListBucketResult.ListBucketItem>, or Iterator<String>",
                        returnType
                    ),
                    function
                )
            }
        }
    }

    private fun generateGet(resolver: Resolver, function: KSFunctionDeclaration, operation: KSAnnotation): FunSpec {
        val b = function.overridingKeepAop(resolver)
        generateCreds(function, b)
        generateBucket(function, b)
        b.addStatement("val _key = %L", generateKey(function, operation))
        val args = function.parameters.firstOrNull { it.type.toTypeName() == S3ClassNames.getObjectArgs }
        if (args == null) {
            b.addStatement("val _args = null as %T?", S3ClassNames.getObjectArgs)
        } else {
            b.addStatement("val _args = %N", args.name!!.asString())
        }
        val returnType = function.returnType!!.resolve().toTypeName()

        if (returnType.isNullable) {
            b.addStatement("val _rs = this.client.getObject(_creds, _bucket, _key, _args, false)")
            b.addStatement("if (_rs == null) return null")
        } else {
            b.addStatement("val _rs = this.client.getObject(_creds, _bucket, _key, _args)")
        }
        when (returnType.copy(false)) {
            S3ClassNames.getObjectResult -> b.addStatement("return _rs!!")
            BYTE_ARRAY ->
                b.controlFlow("try") {
                    controlFlow("_rs.use") {
                        controlFlow("_rs.body().use { _body ->") {
                            controlFlow("_body.asInputStream().use { _is ->") {
                                addStatement("return _is.readAllBytes()")
                            }
                        }
                    }
                    nextControlFlow("catch (_e: %T)", IOException::class.asClassName())
                    addStatement("throw %T(_e)", S3ClassNames.unknownException)
                }

            else -> throw ProcessingErrorException(unexpectedReturnTypeError(function, "@S3.Get", "GetObjectResult, GetObjectResult?, ByteArray, or ByteArray?", returnType), function)
        }

        return b.build()
    }

    private fun generateCreds(function: KSFunctionDeclaration, b: FunSpec.Builder) {
        val credentials = S3ClientUtils.credentialsParameter(function)
        if (credentials != null) {
            b.addStatement("val _creds = %N", credentials.name?.asString()!!)
        } else {
            b.addStatement("val _creds = this.config.credentials()")
        }
    }

    private fun generateBucket(function: KSFunctionDeclaration, b: FunSpec.Builder) {
        val bucketParam = S3ClientUtils.bucketParameter(function)
        val bucketOnMethod = function.findAnnotation(S3ClassNames.Annotation.bucket)
        val bucketOnClass = function.parentDeclaration?.findAnnotation(S3ClassNames.Annotation.bucket)
        if (bucketParam != null) {
            b.addStatement("val _bucket = %N", bucketParam.name!!.asString())
        } else if (bucketOnMethod != null) {
            val index = S3ClientUtils.parseConfigBuckets(function.parentDeclaration as KSClassDeclaration)
                .indexOf(bucketOnMethod.findValueNoDefault<String>("value"))
            if (index < 0) {
                throw IllegalStateException(bucketIndexInternalError(function))
            }
            b.addStatement("val _bucket = this.bucketsConfig.bucket_%L", index)
        } else if (bucketOnClass != null) {
            val index = S3ClientUtils.parseConfigBuckets(function.parentDeclaration as KSClassDeclaration)
                .indexOf(bucketOnClass.findValueNoDefault<String>("value"))
            if (index < 0) {
                throw IllegalStateException(bucketIndexInternalError(function))
            }
            b.addStatement("val _bucket = this.bucketsConfig.bucket_%L", index)
        } else {
            throw ProcessingErrorException(
                """
                S3 operation '${functionName(function)}' has no bucket source.

                Fix: provide the bucket in one of these ways:
                1. Add a runtime bucket parameter: fun get(@S3.Bucket bucket: String, key: String): GetObjectResult
                2. Configure it on the method: @S3.Bucket("s3.clients.default.bucket")
                3. Configure it on the @S3.Client interface: @S3.Bucket("s3.clients.default.bucket")
                """.trimIndent(),
                function
            )
        }
    }

    private fun generateKey(function: KSFunctionDeclaration, annotation: KSAnnotation): CodeBlock {
        val keyMapping = annotation.findValueNoDefault<String>("value")
        val parameters = function.parameters.filter {
            val parameterTypeName = it.type.resolve().toTypeName()
            !it.isAnnotationPresent(S3ClassNames.Annotation.bucket) && parameterTypeName != S3ClassNames.s3Credentials && !S3ClassNames.args.contains(parameterTypeName) && !S3ClassNames.bodyTypes.contains(
                parameterTypeName
            )
        }
        if (keyMapping != null && !keyMapping.isBlank()) {
            val key = parseKey(function, parameters, keyMapping)
            if (key.params.isEmpty() && !parameters.isEmpty()) {
                throw ProcessingErrorException(
                    """
                    S3 operation '${functionName(function)}' has key template '$keyMapping', but the template does not use any key parameters.

                    Fix: either reference method parameters in the template, or remove unused key parameters from the method.
                    Example: @S3.Get("users/{userId}/files/{fileId}")
                    """.trimIndent(),
                    function
                )
            }
            return key.code
        }
        if (parameters.size > 1) {
            throw ProcessingErrorException(
                """
                S3 operation '${functionName(function)}' has ${parameters.size} key parameters, but no key template.

                Fix: add a template that names every key part, or leave exactly one key parameter.
                Example: @S3.Get("users/{userId}/files/{fileId}")
                """.trimIndent(),
                function
            )
        }
        if (parameters.isEmpty()) {
            throw ProcessingErrorException(
                """
                S3 operation '${functionName(function)}' has no object key.

                Fix: add a key parameter, or specify a constant key in the operation annotation.
                Example: fun get(@S3.Bucket bucket: String, key: String): GetObjectResult
                Example: @S3.Get("constant-key")
                """.trimIndent(),
                function
            )
        }

        val firstParameter = parameters[0]
        if (firstParameter.type.resolve().isCollection()) {
            throw ProcessingErrorException(
                """
                S3 operation '${functionName(function)}' expects one object key, but parameter '${firstParameter.name?.asString()}' is a collection.

                Fix: pass a single key value, or change the method to an operation that is intended to process multiple keys.
                Example: fun get(@S3.Bucket bucket: String, key: String): GetObjectResult
                """.trimIndent(),
                function
            )
        } else {
            return CodeBlock.of("%N.toString()", firstParameter.toString())
        }
    }

    private fun parseKey(function: KSFunctionDeclaration, parameters: List<KSValueParameter>, keyTemplate: String): Key {
        var indexStart = keyTemplate.indexOf("{")
        if (indexStart == -1) {
            return Key(CodeBlock.of("%S", keyTemplate), listOf())
        }

        val params = mutableListOf<KSValueParameter>()
        val builder = CodeBlock.builder()
        var indexEnd = 0
        while (indexStart != -1) {
            if (indexStart != 0) {
                if (indexEnd == 0) {
                    builder.add("%S + ", keyTemplate.substring(0, indexStart))
                } else if (indexStart != (indexEnd + 1)) {
                    builder.add("%S + ", keyTemplate.substring(indexEnd + 1, indexStart))
                }
            }
            indexEnd = keyTemplate.indexOf("}", indexStart)
            if (indexEnd == -1) {
                throw ProcessingErrorException(
                    """
                    S3 operation '${functionName(function)}' has malformed key template '$keyTemplate': missing closing '}'.

                    Fix: close every template parameter.
                    Example: @S3.Get("users/{userId}/files/{fileId}")
                    """.trimIndent(),
                    function
                )
            }

            val paramName = keyTemplate.substring(indexStart + 1, indexEnd)
            val parameter = parameters
                .firstOrNull { it.name?.asString().contentEquals(paramName) }
                ?: throw ProcessingErrorException(
                    """
                    S3 operation '${functionName(function)}' references key template parameter '{$paramName}', but the method has no matching key parameter.

                    Fix: rename the template parameter or add a method parameter named '$paramName'.
                    Example: @S3.Get("users/{$paramName}") fun get(@S3.Bucket bucket: String, $paramName: String): GetObjectResult
                    """.trimIndent(),
                    function
                )

            val parameterType = parameter.type.resolve()
            if (parameterType.isCollection() || parameterType.isMap()) {
                throw ProcessingErrorException(
                    """
                    S3 operation '${functionName(function)}' uses '{$paramName}' in the key template, but parameter '$paramName' is a collection or map.

                    Fix: template parameters must be scalar values. Convert the collection to a single string before calling the S3 client method, or pass a scalar parameter.
                    Example: @S3.Get("users/{$paramName}") fun get(@S3.Bucket bucket: String, $paramName: String): GetObjectResult
                    """.trimIndent(),
                    function
                )
            }

            params.add(parameter)
            builder.add("%L", paramName)
            indexStart = keyTemplate.indexOf("{", indexEnd)
            if (indexStart != -1) {
                builder.add(" + ")
            }
        }

        if (indexEnd + 1 != keyTemplate.length) {
            builder.add(" + %S", keyTemplate.substring(indexEnd + 1))
        }

        return Key(builder.build(), params)
    }


    data class Key(val code: CodeBlock, val params: List<KSValueParameter>)

    private fun missingOperationError(function: KSFunctionDeclaration): String {
        return """
            S3 client method '${functionName(function)}' is not mapped to an S3 operation.

            Fix: annotate the method with exactly one S3 operation annotation: ${supportedOperationAnnotations()}.
            Example: @S3.Get fun get(@S3.Bucket bucket: String, key: String): GetObjectResult
        """.trimIndent()
    }

    private fun multipleOperationsError(function: KSFunctionDeclaration): String {
        return """
            S3 client method '${functionName(function)}' has more than one S3 operation annotation.

            Fix: keep exactly one operation annotation: ${supportedOperationAnnotations()}.
            Example: use @S3.Get or @S3.Put, not both on the same method.
        """.trimIndent()
    }

    private fun unexpectedReturnTypeError(function: KSFunctionDeclaration, operation: String, expected: String, actual: TypeName?): String {
        return """
            S3 operation '$operation' on method '${functionName(function)}' has unsupported return type '${actual ?: "<missing>"}'.

            Expected: $expected.
            Fix: change the method return type to one of the supported types for $operation.
        """.trimIndent()
    }

    private fun unsupportedOperationInternalError(function: KSFunctionDeclaration, operation: KSAnnotation): String {
        return """
            Kora internal error: unsupported S3 operation annotation '${operation.annotationType.resolve()}' on method '${functionName(function)}'.

            This annotation passed the initial S3 operation filter but is not handled by the S3 client generator.
        """.trimIndent()
    }

    private fun functionName(function: KSFunctionDeclaration): String {
        return function.qualifiedName?.asString() ?: function.simpleName.asString()
    }

    private fun supportedOperationAnnotations(): String {
        return S3ClassNames.Annotation.operations.joinToString { "@S3.${it.simpleNames.last()}" }
    }

    private fun supportedBodyTypes(): String {
        return "${S3ClassNames.contentWriter}, ByteArray, ByteBuffer, or InputStream"
    }

    private fun bucketIndexInternalError(function: KSFunctionDeclaration): String {
        return """
            Kora internal error: S3 bucket config path for method '${functionName(function)}' was found on the declaration, but was not registered in the generated buckets config.

            This should not happen for a valid S3 client declaration. Please report this with the S3 client interface source.
        """.trimIndent()
    }

}
