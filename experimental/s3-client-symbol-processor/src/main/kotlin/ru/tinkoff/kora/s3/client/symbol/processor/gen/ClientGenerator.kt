package ru.tinkoff.kora.s3.client.symbol.processor.gen

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonAopUtils.extendsKeepAop
import ru.tinkoff.kora.ksp.common.CommonAopUtils.overridingKeepAop
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCollection
import ru.tinkoff.kora.ksp.common.CommonClassNames.isMap
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.resolveToUnderlying
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.generatedClassName
import ru.tinkoff.kora.s3.client.symbol.processor.S3ClassNames
import ru.tinkoff.kora.s3.client.symbol.processor.S3ClientUtils
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
        val b = s3client.extendsKeepAop(s3client.generatedClassName("ClientImpl"), resolver)
            .generated(ClientGenerator::class)
            .addProperty(
                PropertySpec.Companion.builder("client", S3ClassNames.client, KModifier.PRIVATE)
                    .initializer("clientFactory.create(clientConfig)")
                    .build()
            )
            .addProperty(
                PropertySpec.builder("config", configType, KModifier.PRIVATE)
                    .initializer("clientConfig")
                    .build()
            )
        val constructor = FunSpec.constructorBuilder()
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
            throw ProcessingErrorException("No S3 operation annotation found", function)
        }
        if (operations.size > 1) {
            throw ProcessingErrorException("More than one S3 operation annotation found", function)
        }
        val operation = operations.first()
        return when (operation.annotationType.resolve().toString()) {
            "Get" -> generateGet(resolver, function, operation)
            "List" -> generateList(resolver, function, operation)
            "Head" -> generateHead(resolver, function, operation)
            "Put" -> generatePut(resolver, function, operation)
            "Delete" -> generateDelete(resolver, function, operation)
            else -> throw IllegalStateException("Unexpected value: " + operation.annotationType.resolve().toString())
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
            throw ProcessingErrorException("Unexpected return type ${function.returnType}", function)
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
            throw ProcessingErrorException("Unexpected empty content for PUT operation", function)
        }
        if (contents.size != 1) {
            throw ProcessingErrorException("More than one PUT operation annotation found", function)
        }
        val returnType = function.returnType!!.resolveToUnderlying().toTypeName()
        val hasReturn = when (returnType) {
            String::class.asTypeName() -> true
            UNIT -> false
            else -> throw ProcessingErrorException("Unexpected return type $returnType", function)
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

            else -> throw ProcessingErrorException("Unexpected content type $contentType", function)
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
            throw ProcessingErrorException("Unexpected return type ${function.returnType}", function)
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
                throw ProcessingErrorException("Unexpected return type: " + returnType, function)
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

            else -> throw ProcessingErrorException("Unexpected return type $returnType", function)
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
            check(index >= 0)
            b.addStatement("val _bucket = this.bucketsConfig.bucket_%L", index)
        } else if (bucketOnClass != null) {
            val index = S3ClientUtils.parseConfigBuckets(function.parentDeclaration as KSClassDeclaration)
                .indexOf(bucketOnClass.findValueNoDefault<String>("value"))
            check(index >= 0)
            b.addStatement("val _bucket = this.bucketsConfig.bucket_%L", index)
        }
    }

    private fun generateKey(function: KSFunctionDeclaration, annotation: KSAnnotation): CodeBlock {
        val keyMapping = annotation.findValueNoDefault<String>("value")
        val parameters = function.parameters.filter {
            val parameterTypeName = it.type.resolve().toTypeName()
            !it.isAnnotationPresent(S3ClassNames.Annotation.bucket) && parameterTypeName != S3ClassNames.awsCredentials && !S3ClassNames.args.contains(parameterTypeName) && !S3ClassNames.bodyTypes.contains(
                parameterTypeName
            )
        }
        if (keyMapping != null && !keyMapping.isBlank()) {
            val key = parseKey(function, parameters, keyMapping)
            if (key.params.isEmpty() && !parameters.isEmpty()) {
                throw ProcessingErrorException("@S3 operation prefix template must use method arguments or they should be removed", function)
            }
            return key.code
        }
        if (parameters.size > 1) {
            throw ProcessingErrorException("@S3 operation can't have multiple method parameters for keys without key template", function)
        }
        if (parameters.isEmpty()) {
            throw ProcessingErrorException("@S3 operation must have at least one method parameter for keys", function)
        }

        val firstParameter = parameters[0]
        if (firstParameter.type.resolve().isCollection()) {
            throw ProcessingErrorException("@$annotation operation expected single result, but parameter is collection of keys", function)
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

            val paramName = keyTemplate.substring(indexStart + 1, indexEnd)
            val parameter = parameters
                .firstOrNull { it.name?.asString().contentEquals(paramName) }
                ?: throw ProcessingErrorException("@S3 operation key part named '$paramName' expected, but wasn't found", function)

            val parameterType = parameter.type.resolve()
            if (parameterType.isCollection() || parameterType.isMap()) {
                throw ProcessingErrorException("@S3 operation key part '$paramName' can't be Collection or Map", function)
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

}
