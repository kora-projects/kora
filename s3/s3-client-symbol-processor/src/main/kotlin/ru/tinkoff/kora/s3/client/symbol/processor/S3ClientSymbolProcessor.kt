package ru.tinkoff.kora.s3.client.symbol.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.Module
import ru.tinkoff.kora.ksp.common.*
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.CommonAopUtils.overridingKeepAop
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCollection
import ru.tinkoff.kora.ksp.common.CommonClassNames.isMap
import ru.tinkoff.kora.ksp.common.CommonClassNames.isVoid
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.TagUtils.addTag
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService

class S3ClientSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    companion object {
        private val ANNOTATION_CLIENT: ClassName = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Client")
        private val MEMBER_AWAIT_FUTURE: MemberName = MemberName("kotlinx.coroutines.future", "await")

        private val ANNOTATION_OP_GET: ClassName = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Get")
        private val ANNOTATION_OP_LIST: ClassName = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "List")
        private val ANNOTATION_OP_PUT: ClassName = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Put")
        private val ANNOTATION_OP_DELETE: ClassName = ClassName("ru.tinkoff.kora.s3.client.annotation", "S3", "Delete")

        private val CLASS_CONFIG = ClassName("ru.tinkoff.kora.s3.client", "S3Config")
        private val CLASS_AWS_CONFIG = ClassName("ru.tinkoff.kora.s3.client.aws", "AwsS3ClientConfig")
        private val CLASS_CLIENT_CONFIG: ClassName = ClassName("ru.tinkoff.kora.s3.client", "S3ClientConfig")
        private val CLASS_CLIENT_SIMPLE_SYNC: ClassName = ClassName("ru.tinkoff.kora.s3.client", "S3SimpleClient")
        private val CLASS_CLIENT_SIMPLE_ASYNC: ClassName = ClassName("ru.tinkoff.kora.s3.client", "S3SimpleAsyncClient")
        private val CLASS_CLIENT_AWS_SYNC: ClassName = ClassName("software.amazon.awssdk.services.s3", "S3Client")
        private val CLASS_CLIENT_AWS_ASYNC: ClassName = ClassName("software.amazon.awssdk.services.s3", "S3AsyncClient")
        private val CLASS_CLIENT_AWS_ASYNC_MULTIPART = ClassName("software.amazon.awssdk.services.s3.internal.multipart", "MultipartS3AsyncClient")
        private val CLASS_INTERCEPTOR_AWS_CONTEXT_KEY = ClassName("ru.tinkoff.kora.s3.client.aws", "AwsS3ClientTelemetryInterceptor")
        private val CLASS_CLIENT_AWS_TAG = ClassName("software.amazon.awssdk.awscore", "AwsClient")
        private val CLASS_CLIENT_AWS_MULTIPART_TAG = ClassName("software.amazon.awssdk.services.s3.model", "MultipartUpload")

        private val CLASS_S3_UPLOAD: ClassName = ClassName("ru.tinkoff.kora.s3.client.model", "S3ObjectUpload")
        private val CLASS_S3_BODY: ClassName = ClassName("ru.tinkoff.kora.s3.client.model", "S3Body")
        private val CLASS_S3_BODY_BYTES: ClassName = ClassName("ru.tinkoff.kora.s3.client.model", "ByteS3Body")
        private val CLASS_S3_OBJECT: ClassName = ClassName("ru.tinkoff.kora.s3.client.model", "S3Object")
        private val CLASS_S3_OBJECT_META: ClassName = ClassName("ru.tinkoff.kora.s3.client.model", "S3ObjectMeta")
        private val CLASS_S3_OBJECT_MANY: TypeName = List::class.asTypeName().parameterizedBy(CLASS_S3_OBJECT)
        private val CLASS_S3_OBJECT_META_MANY: TypeName = List::class.asTypeName().parameterizedBy(CLASS_S3_OBJECT_META)
        private val CLASS_S3_OBJECT_LIST: ClassName = ClassName("ru.tinkoff.kora.s3.client.model", "S3ObjectList")
        private val CLASS_S3_OBJECT_META_LIST: ClassName = ClassName("ru.tinkoff.kora.s3.client.model", "S3ObjectMetaList")

        private val CLASS_JDK_FLOW_ADAPTER = ClassName("reactor.adapter", "JdkFlowAdapter")

        private val CLASS_AWS_EXCEPTION_NO_KEY = ClassName("software.amazon.awssdk.services.s3.model", "NoSuchKeyException")
        private val CLASS_AWS_EXCEPTION_NO_BUCKET = ClassName("software.amazon.awssdk.services.s3.model", "NoSuchBucketException")
        private val CLASS_AWS_IS_SYNC_BODY: ClassName = ClassName("software.amazon.awssdk.core.sync", "RequestBody")
        private val CLASS_AWS_IS_ASYNC_BODY: ClassName = ClassName("software.amazon.awssdk.core.async", "AsyncRequestBody")
        private val CLASS_AWS_IS_ASYNC_TRANSFORMER: ClassName = ClassName("software.amazon.awssdk.core.async", "AsyncResponseTransformer")
        private val CLASS_AWS_GET_REQUEST: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "GetObjectRequest")
        private val CLASS_AWS_GET_RESPONSE: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "GetObjectResponse")
        private val CLASS_AWS_GET_IS_RESPONSE: TypeName = ClassName("software.amazon.awssdk.core", "ResponseInputStream").parameterizedBy(CLASS_AWS_GET_RESPONSE)
        private val CLASS_AWS_DELETE_REQUEST: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "DeleteObjectRequest")
        private val CLASS_AWS_DELETE_RESPONSE: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "DeleteObjectResponse")
        private val CLASS_AWS_DELETES_REQUEST: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "DeleteObjectsRequest")
        private val CLASS_AWS_DELETES_RESPONSE: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "DeleteObjectsResponse")
        private val CLASS_AWS_LIST_REQUEST: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "ListObjectsV2Request")
        private val CLASS_AWS_LIST_RESPONSE: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "ListObjectsV2Response")
        private val CLASS_AWS_PUT_REQUEST: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "PutObjectRequest")
        private val CLASS_AWS_PUT_RESPONSE: ClassName = ClassName("software.amazon.awssdk.services.s3.model", "PutObjectResponse")
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_CLIENT.canonicalName).toList()
        val symbolsToProcess = symbols.filter { it.validate() }.filterIsInstance<KSClassDeclaration>()
        for (s3client in symbolsToProcess) {
            if (s3client.classKind != ClassKind.INTERFACE) {
                throw ProcessingErrorException(
                    "@S3.Client annotation is intended to be used on interfaces, but was: ${s3client.classKind}",
                    s3client
                )
            }

            val packageName = s3client.packageName.asString()
            try {
                val typeSpec = generateClient(s3client, resolver)
                val fileImplSpec = FileSpec.builder(packageName, typeSpec.name.toString())
                    .addType(typeSpec)
                    .build()
                fileImplSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)

                val configSpec = generateClientConfig(s3client)
                val configImplSpec = FileSpec.builder(packageName, configSpec.name.toString())
                    .addType(configSpec)
                    .build()
                configImplSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }

        return symbols.filterNot { it.validate() }.toList()
    }

    private fun generateClient(s3client: KSClassDeclaration, resolver: Resolver): TypeSpec {
        val implSpecBuilder: TypeSpec.Builder = TypeSpec.classBuilder(s3client.generatedClassName("Impl"))
            .generated(S3ClientSymbolProcessor::class)
            .addAnnotation(Component::class)
            .addSuperinterface(s3client.toTypeName())

        val constructed = HashSet<Signature>()
        val constructorBuilder = FunSpec.constructorBuilder()
        val constructorCode = CodeBlock.builder()
        implSpecBuilder.addProperty("_clientConfig", CLASS_CLIENT_CONFIG, KModifier.PRIVATE, KModifier.FINAL)
        constructorCode.addStatement("this._clientConfig = clientConfig")
        constructorBuilder.addParameter(
            ParameterSpec.builder("clientConfig", CLASS_CLIENT_CONFIG)
                .addAnnotation(s3client.toTypeName().makeTagAnnotationSpec())
                .build()
        )

        for (func in s3client.getDeclaredFunctions()) {
            val operationType = getOperationType(func)
            if (operationType == null) {
                throw ProcessingErrorException("@S3.Client method without operation annotation can't be non default", func)
            } else {
                val operation = getOperation(func, operationType)
                val methodSpec = func.overridingKeepAop(resolver)
                    .addCode(operation.code)
                    .build()

                implSpecBuilder.addFunction(methodSpec)

                val signatures = mutableListOf<Signature>()
                if (operation.impl == S3Operation.ImplType.SIMPLE) {
                    if (operation.mode == S3Operation.Mode.SYNC) {
                        signatures.add(Signature(CLASS_CLIENT_SIMPLE_SYNC, "simpleSyncClient"))
                    } else {
                        signatures.add(Signature(CLASS_CLIENT_SIMPLE_ASYNC, "simpleAsyncClient"))
                    }
                } else if (operation.impl == S3Operation.ImplType.AWS) {
                    if (operation.mode == S3Operation.Mode.SYNC) {
                        signatures.add(Signature(CLASS_CLIENT_AWS_SYNC, "awsSyncClient"))
                    } else {
                        signatures.add(Signature(CLASS_CLIENT_AWS_ASYNC, "awsAsyncClient"))
                    }
                    if (operation.type == S3Operation.OperationType.PUT) {
                        signatures.add(Signature(CLASS_CLIENT_AWS_ASYNC_MULTIPART, "awsAsyncMultipartClient", listOf(CLASS_CLIENT_AWS_MULTIPART_TAG)))
                        signatures.add(Signature(CLASS_CLIENT_AWS_ASYNC, "awsAsyncClient"))
                        signatures.add(Signature(CLASS_AWS_CONFIG, "awsClientConfig"))
                        signatures.add(Signature(ExecutorService::class.asTypeName(), "awsAsyncExecutor", listOf(CLASS_CLIENT_AWS_TAG)))
                    }
                }

                for (signature in signatures) {
                    if (!constructed.contains(signature)) {
                        if (signature.tags.isEmpty()) {
                            constructorBuilder.addParameter(signature.name, signature.type)
                        } else {
                            constructorBuilder.addParameter(
                                ParameterSpec.builder(signature.name, signature.type)
                                    .addTag(signature.tags)
                                    .build()
                            )
                        }
                        implSpecBuilder.addProperty("_" + signature.name, signature.type, KModifier.PRIVATE, KModifier.FINAL)
                        constructorCode.addStatement("this._" + signature.name + " = " + signature.name)
                        constructed.add(signature)
                    }
                }
            }
        }

        constructorBuilder.addCode(constructorCode.build())
        implSpecBuilder.addFunction(constructorBuilder.build())

        return implSpecBuilder.build()
    }

    private fun generateClientConfig(s3client: KSClassDeclaration): TypeSpec {
        val clientAnnotation = s3client.findAnnotation(ANNOTATION_CLIENT)
        val clientConfigPath = clientAnnotation!!.findValueNoDefault<String>("value")!!

        val extractorClass = CommonClassNames.configValueExtractor.parameterizedBy(CLASS_CLIENT_CONFIG)
        return TypeSpec.interfaceBuilder(s3client.generatedClass("ClientConfigModule"))
            .generated(S3ClientSymbolProcessor::class)
            .addAnnotation(AnnotationSpec.builder(Module::class).build())
            .addOriginatingKSFile(s3client)
            .addFunction(
                FunSpec.builder("clientConfig")
                    .addModifiers(KModifier.PUBLIC)
                    .addAnnotation(s3client.toTypeName().makeTagAnnotationSpec())
                    .addParameter(ParameterSpec.builder("config", CommonClassNames.config).build())
                    .addParameter(ParameterSpec.builder("extractor", extractorClass).build())
                    .addStatement("val value = config.get(%S)", clientConfigPath)
                    .addStatement(
                        "return extractor.extract(value) ?: throw %T.missingValueAfterParse(value)",
                        CommonClassNames.configValueExtractionException
                    )
                    .returns(CLASS_CLIENT_CONFIG)
                    .build()
            )
            .build()
    }

    data class Signature(val type: TypeName, val name: String, val tags: List<TypeName>) {

        constructor(type: TypeName, name: String) : this(type, name, emptyList<TypeName>())
    }

    data class OperationMeta(val type: S3Operation.OperationType, val annotation: KSAnnotation)

    private fun getOperationType(method: KSFunctionDeclaration): OperationMeta? {
        var value: OperationMeta? = null

        for (ksAnnotation in method.annotations) {
            var type: S3Operation.OperationType? = null
            if (ksAnnotation.annotationType.toTypeName() == ANNOTATION_OP_GET) {
                type = S3Operation.OperationType.GET
            } else if (ksAnnotation.annotationType.toTypeName() == ANNOTATION_OP_LIST) {
                type = S3Operation.OperationType.LIST
            } else if (ksAnnotation.annotationType.toTypeName() == ANNOTATION_OP_PUT) {
                type = S3Operation.OperationType.PUT
            } else if (ksAnnotation.annotationType.toTypeName() == ANNOTATION_OP_DELETE) {
                type = S3Operation.OperationType.DELETE
            }

            if (value == null && type != null) {
                value = OperationMeta(type, ksAnnotation)
            } else {
                throw ProcessingErrorException("@S3.Client method must be annotated with single operation annotation", method)
            }
        }

        return value
    }

    private fun getOperation(method: KSFunctionDeclaration, operationMeta: OperationMeta): S3Operation {
        val mode = if (method.isSuspend()) S3Operation.Mode.ASYNC else S3Operation.Mode.SYNC

        return if (S3Operation.OperationType.GET == operationMeta.type) {
            operationGET(method, operationMeta, mode)
        } else if (S3Operation.OperationType.LIST == operationMeta.type) {
            operationLIST(method, operationMeta, mode)
        } else if (S3Operation.OperationType.PUT == operationMeta.type) {
            operationPUT(method, operationMeta, mode)
        } else if (S3Operation.OperationType.DELETE == operationMeta.type) {
            operationDELETE(method, operationMeta, mode)
        } else {
            throw UnsupportedOperationException("Unsupported S3 operation type")
        }
    }

    private fun operationGET(method: KSFunctionDeclaration, operationMeta: OperationMeta, mode: S3Operation.Mode): S3Operation {
        val keyMapping: String? = operationMeta.annotation.findValueNoDefault("value")
        val key: Key
        val firstParameter = method.parameters.firstOrNull()
        if (!keyMapping.isNullOrBlank()) {
            key = parseKey(method, keyMapping)
            if (key.params.isEmpty() && method.parameters.isNotEmpty()) {
                throw ProcessingErrorException("@S3.Get operation key template must use method arguments or they should be removed", method)
            }
        } else if (method.parameters.size > 1) {
            throw ProcessingErrorException("@S3.Get operation can't have multiple method parameters for keys without key template", method)
        } else if (method.parameters.isEmpty()) {
            throw ProcessingErrorException("@S3.Get operation must have key parameter", method)
        } else {
            key = Key(CodeBlock.of("val _key = %L.toString()\n", firstParameter!!.name!!.asString()), listOf(firstParameter!!))
        }

        val returnType = method.returnType!!.toTypeName()

        if (CLASS_S3_OBJECT == returnType || CLASS_S3_OBJECT_META == returnType) {
            if (firstParameter != null && firstParameter.type.resolve().isCollection()) {
                throw ProcessingErrorException("@S3.Get operation expected single result, but parameter is collection of keys", method)
            }

            val bodyBuilder: CodeBlock.Builder = CodeBlock.builder()
            if (mode == S3Operation.Mode.SYNC) {
                bodyBuilder.add("return _simpleSyncClient")
            } else {
                bodyBuilder.add("return _simpleAsyncClient")
            }

            if (CLASS_S3_OBJECT == returnType) {
                bodyBuilder.add(".get(_clientConfig.bucket(), _key)")
            } else {
                bodyBuilder.add(".getMeta(_clientConfig.bucket(), _key)")
            }

            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
            }

            bodyBuilder.add("\n")

            val code: CodeBlock = CodeBlock.builder()
                .add(key.code)
                .add(bodyBuilder.build())
                .build()

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.GET, S3Operation.ImplType.SIMPLE, mode, code)
        } else if (CLASS_S3_OBJECT_MANY == returnType || CLASS_S3_OBJECT_META_MANY == returnType) {
            if (firstParameter != null && !firstParameter.type.resolve().isCollection()) {
                throw ProcessingErrorException("@S3.Get operation expected many results, but parameter isn't collection of keys", method)
            } else if (!keyMapping.isNullOrBlank()) {
                throw ProcessingErrorException("@S3.Get operation expected many results, key template can't be specified for collection of keys", method)
            }

            val clientField = if (mode == S3Operation.Mode.SYNC) "_simpleSyncClient" else "_simpleAsyncClient"

            val bodyBuilder: CodeBlock.Builder = CodeBlock.builder()
            if (CLASS_S3_OBJECT_MANY == returnType) {
                bodyBuilder.add(
                    "return %L.get(_clientConfig.bucket(), %L)",
                    clientField, firstParameter!!.name!!.asString()
                )
            } else {
                bodyBuilder.add(
                    "return %L.getMeta(_clientConfig.bucket(), %L)",
                    clientField, firstParameter!!.name!!.asString()
                )
            }

            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
            }

            bodyBuilder.add("\n")

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.GET, S3Operation.ImplType.SIMPLE, mode, bodyBuilder.build())
        } else if (CLASS_AWS_GET_RESPONSE == returnType || CLASS_AWS_GET_IS_RESPONSE == returnType) {
            if (firstParameter != null && firstParameter.type.resolve().isCollection()) {
                throw ProcessingErrorException("@S3.Get operation expected single result, but parameter is collection of keys", method)
            }

            val clientField = if (mode == S3Operation.Mode.SYNC) "_awsSyncClient" else "_awsAsyncClient"

            val codeBuilder: CodeBlock.Builder = CodeBlock.builder()
                .add(key.code)
                .add("\n")
                .addStatement(
                    """
                    var _request = %T.builder()
                        .bucket(_clientConfig.bucket())
                        .key(_key)
                        .build()
                        """.trimIndent(), CLASS_AWS_GET_REQUEST
                )
                .add("\n")

            if (mode == S3Operation.Mode.SYNC) {
                if (CLASS_AWS_GET_RESPONSE == returnType) {
                    codeBuilder.addStatement("return %L.getObject(_request).response()", clientField).build()
                } else {
                    codeBuilder.addStatement("return %L.getObject(_request)", clientField).build()
                }
            } else {
                if (CLASS_AWS_GET_RESPONSE == returnType) {
                    codeBuilder
                        .add(
                            "return %L.getObject(_request, %T.toBlockingInputStream()).thenApply { it.response() }",
                            clientField, CLASS_AWS_IS_ASYNC_TRANSFORMER
                        )
                        .build()
                } else {
                    codeBuilder
                        .add(
                            "return %L.getObject(_request, %T.toBlockingInputStream())",
                            clientField, CLASS_AWS_IS_ASYNC_TRANSFORMER
                        )
                        .build()
                }

                codeBuilder.add(".%M()\n", MEMBER_AWAIT_FUTURE)
            }

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.GET, S3Operation.ImplType.AWS, mode, codeBuilder.build())
        } else {
            if (firstParameter != null && firstParameter.type.resolve().isCollection()) {
                throw ProcessingErrorException(
                    "@S3.Get operation unsupported method return signature, expected any of List<${CLASS_S3_OBJECT.simpleName}>/List<${CLASS_S3_OBJECT_META.simpleName}>",
                    method
                )
            } else {
                throw ProcessingErrorException(
                    "@S3.Get operation unsupported method return signature, expected any of ${CLASS_S3_OBJECT.simpleName}/${CLASS_S3_OBJECT_META.simpleName}/${CLASS_AWS_GET_RESPONSE.simpleName}/ResponseInputStream<${CLASS_AWS_GET_RESPONSE.simpleName}>",
                    method
                )
            }
        }
    }

    private fun operationLIST(method: KSFunctionDeclaration, operationMeta: OperationMeta, mode: S3Operation.Mode): S3Operation {
        val keyMapping: String? = operationMeta.annotation.findValueNoDefault("value")
        val key: Key?
        val firstParameter = method.parameters.stream().findFirst().orElse(null)
        if (!keyMapping.isNullOrBlank()) {
            key = parseKey(method, keyMapping)
            if (key.params.isEmpty() && method.parameters.isNotEmpty()) {
                throw ProcessingErrorException("@S3.List operation prefix template must use method arguments or they should be removed", method)
            }
        } else if (method.parameters.size > 1) {
            throw ProcessingErrorException("@S3.List operation can't have multiple method parameters for keys without key template", method)
        } else if (method.parameters.isEmpty()) {
            key = null
        } else if (firstParameter.type.resolve().isCollection()) {
            throw ProcessingErrorException("@S3.List operation expected single result, but parameter is collection of keys", method)
        } else {
            key = Key(CodeBlock.of("val _key = %L.toString()", firstParameter.name!!.asString()), listOf(firstParameter))
        }

        val limit: Int = operationMeta.annotation.findValue("limit") ?: 1000
        val returnType: TypeName = method.returnType!!.toTypeName()

        if (CLASS_S3_OBJECT_LIST == returnType || CLASS_S3_OBJECT_META_LIST == returnType) {
            val bodyBuilder: CodeBlock.Builder = CodeBlock.builder()
            if (key != null) {
                bodyBuilder.add(key.code).add("\n")
            }

            if (mode == S3Operation.Mode.SYNC) {
                bodyBuilder.add("return _simpleSyncClient")
            } else {
                bodyBuilder.add("return _simpleAsyncClient")
            }

            val keyField = if ((key == null)) "null as String?" else "_key"
            if (CLASS_S3_OBJECT_LIST == returnType) {
                bodyBuilder.add(".list(_clientConfig.bucket(), %L, %L)", keyField, limit)
            } else {
                bodyBuilder.add(".listMeta(_clientConfig.bucket(), %L, %L)", keyField, limit)
            }

            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
            }

            bodyBuilder.add("\n")

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.LIST, S3Operation.ImplType.SIMPLE, mode, bodyBuilder.build())
        } else if (CLASS_AWS_LIST_RESPONSE == returnType) {
            val clientField = if (mode == S3Operation.Mode.SYNC) "_awsSyncClient" else "_awsAsyncClient"
            val bodyBuilder: CodeBlock.Builder = CodeBlock.builder()
            if (key != null) {
                bodyBuilder.add(key.code).add("\n\n")
            }

            val keyField = if ((key == null)) "null" else "_key"
            bodyBuilder
                .add(
                    """
                    var _request = %L.builder()
                        .bucket(_clientConfig.bucket())
                        .prefix(%L)
                        .maxKeys(%L)
                        .build()
                        """.trimIndent(), CLASS_AWS_LIST_REQUEST, keyField, limit
                )
                .add("\n")
                .add("return %L.listObjectsV2(_request)", clientField)

            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
            }
            bodyBuilder.add("\n")

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.LIST, S3Operation.ImplType.AWS, mode, bodyBuilder.build())
        } else {
            throw ProcessingErrorException(
                "@S3.List operation unsupported method return signature, expected any of ${CLASS_S3_OBJECT_LIST.simpleName}/${CLASS_S3_OBJECT_META_LIST.simpleName}/${CLASS_AWS_LIST_RESPONSE.simpleName}",
                method
            )
        }
    }

    private fun operationPUT(method: KSFunctionDeclaration, operationMeta: OperationMeta, mode: S3Operation.Mode): S3Operation {
        val keyMapping: String? = operationMeta.annotation.findValueNoDefault("value")
        val key: Key

        val keyParameters = method.parameters.stream()
            .filter { p ->
                val bodyType: TypeName = p.type.toTypeName()
                (CLASS_S3_BODY != bodyType
                    && ByteBuffer::class.asTypeName() != bodyType
                    && ByteArray::class.asTypeName() != bodyType)
            }
            .toList()

        val firstParameter = keyParameters.firstOrNull()
        if (!keyMapping.isNullOrBlank()) {
            key = parseKey(method, keyMapping)
            if (key.params.isEmpty() && keyParameters.isNotEmpty()) {
                throw ProcessingErrorException("@S3.Put operation key template must use method arguments or they should be removed", method)
            }
        } else if (firstParameter == null) {
            throw ProcessingErrorException("@S3.Put operation must have parameters", method)
        } else if (firstParameter.type.resolve().isCollection()) {
            throw ProcessingErrorException("@S3.Put operation expected single result, but parameter is collection of keys", method)
        } else {
            key = Key(CodeBlock.of("val _key = %L.toString()", firstParameter.name!!.asString()), java.util.List.of(firstParameter))
        }

        val returnTypeMirror = method.returnType
        val returnType: TypeName = returnTypeMirror!!.toTypeName()

        val bodyParam = method.parameters.stream()
            .filter { p -> key.params.none { kp -> p === kp } }
            .findFirst()
            .orElseThrow { ProcessingErrorException("@S3.Put operation body parameter not found", method) }

        val bodyType: TypeName = bodyParam.type.toTypeName()

        val isResultUpload = CLASS_S3_UPLOAD == returnType
        if (returnTypeMirror.isVoid() || isResultUpload) {
            val bodyCode: CodeBlock
            if (CLASS_S3_BODY == bodyType) {
                bodyCode = CodeBlock.of("val _body = %L", bodyParam.name!!.asString())
            } else {
                val methodCall = when (bodyType) {
                    ByteBuffer::class.asTypeName() -> "ofBuffer"
                    ByteArray::class.asTypeName() -> "ofBytes"
                    else -> throw ProcessingErrorException("@S3.Put operation body must be S3Body/ByteArray/ByteBuffer", method)
                }

                val type: String? = operationMeta.annotation.findValueNoDefault("type")
                val encoding: String? = operationMeta.annotation.findValueNoDefault("encoding")
                bodyCode = if (type != null && encoding != null) {
                    CodeBlock.of(
                        "val _body = %T.%L(%L, %S, %S)",
                        CLASS_S3_BODY, methodCall, bodyParam.name!!.asString(), methodCall, type, encoding
                    )
                } else if (type != null) {
                    CodeBlock.of(
                        "val _body = %T.%L(%L, %S)",
                        CLASS_S3_BODY, methodCall, bodyParam.name!!.asString(), methodCall, type
                    )
                } else if (encoding != null) {
                    CodeBlock.of(
                        "val _body = %T.%L(%L, null, %S)",
                        CLASS_S3_BODY, methodCall, bodyParam.name!!.asString(), methodCall, encoding
                    )
                } else {
                    CodeBlock.of(
                        "val _body = %T.%L(%L)",
                        CLASS_S3_BODY, methodCall, bodyParam.name!!.asString()
                    )
                }
            }

            val methodBuilder: CodeBlock.Builder = CodeBlock.builder()
            if (mode == S3Operation.Mode.SYNC) {
                if (isResultUpload) {
                    methodBuilder.add("return _simpleSyncClient.put(_clientConfig.bucket(), _key, _body)")
                } else {
                    methodBuilder.add("_simpleSyncClient.put(_clientConfig.bucket(), _key, _body)")
                }
                methodBuilder.add("\n")
            } else {
                methodBuilder.add("return _simpleAsyncClient.put(_clientConfig.bucket(), _key, _body)")
                if (returnTypeMirror.isVoid()) {
                    methodBuilder.add(".thenApply {  }")
                }
                methodBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
                methodBuilder.add("\n")
            }

            val code: CodeBlock = CodeBlock.builder()
                .add(key.code)
                .add("\n")
                .add(bodyCode)
                .add("\n")
                .add(methodBuilder.build())
                .build()

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.PUT, S3Operation.ImplType.SIMPLE, mode, code)
        } else if (CLASS_AWS_PUT_RESPONSE == returnType) {
            val bodyCode: CodeBlock
            val requestBuilder: CodeBlock.Builder = CodeBlock.builder()
            val type: String? = operationMeta.annotation.findValueNoDefault("type")
            val encoding: String? = operationMeta.annotation.findValueNoDefault("encoding")
            val bodyParamName = bodyParam.name!!.asString()

            if (CLASS_S3_BODY == bodyType) {
                bodyCode = if (mode == S3Operation.Mode.SYNC) {
                    CodeBlock.builder()
                        .beginControlFlow("val _requestBody = if (%L is %T)", bodyParamName, CLASS_S3_BODY_BYTES)
                        .add("%T.fromBytes(%L.bytes())\n", CLASS_AWS_IS_SYNC_BODY, bodyParamName)
                        .nextControlFlow("else if (%L.size() > 0)", bodyParamName)
                        .add(
                            "%T.fromContentProvider({ %L.asInputStream() }, %L.size(), %L.type())\n",
                            CLASS_AWS_IS_SYNC_BODY,
                            bodyParamName,
                            bodyParamName,
                            bodyParamName
                        )
                        .nextControlFlow("else")
                        .add("%T.fromContentProvider({ %L.asInputStream() }, %L.type())\n", CLASS_AWS_IS_SYNC_BODY, bodyParamName, bodyParamName)
                        .endControlFlow()
                        .build()
                } else {
                    CodeBlock.of(
                        """
                        val _bodySize = if(%L.size() > 0) %L.size() else null
                        val _requestBody = if(%L is %T) %T.fromBytes(%L.bytes())
                            else %T.fromInputStream(%L.asInputStream(), _bodySize, _awsAsyncExecutor)
                            """.trimIndent(),
                        bodyParamName, bodyParamName,
                        bodyParamName, CLASS_S3_BODY_BYTES, CLASS_AWS_IS_ASYNC_BODY, bodyParamName,
                        CLASS_AWS_IS_ASYNC_BODY, bodyParamName
                    )
                }

                requestBuilder.addStatement("_requestBuilder.contentLength(if(%L.size() > 0) %L.size() else null)", bodyParamName, bodyParamName)
                if (type != null) {
                    requestBuilder.addStatement("_requestBuilder.contentType(%S)", type)
                } else {
                    requestBuilder.addStatement("_requestBuilder.contentType(%L.type())", bodyParamName)
                }
                if (encoding != null) {
                    requestBuilder.addStatement("_requestBuilder.contentEncoding(%S)", encoding)
                } else {
                    requestBuilder.addStatement("_requestBuilder.contentEncoding(%L.encoding())", bodyParamName)
                }
            } else {
                val awsBodyClass: ClassName = if (mode == S3Operation.Mode.SYNC) CLASS_AWS_IS_SYNC_BODY else CLASS_AWS_IS_ASYNC_BODY
                when (bodyType) {
                    ByteBuffer::class.asTypeName() -> {
                        bodyCode = CodeBlock.of(
                            "val _requestBody = %T.fromByteBuffer(%L)",
                            awsBodyClass, bodyParamName
                        )
                        requestBuilder.addStatement("_requestBuilder.contentLength(%L.remaining())", bodyParamName)
                    }

                    ByteArray::class.asTypeName() -> {
                        bodyCode = CodeBlock.of(
                            "val _requestBody = %T.fromBytes(%L)",
                            awsBodyClass, bodyParamName
                        )
                        requestBuilder.addStatement("_requestBuilder.contentLength(%L.length)", bodyParamName)
                    }

                    else -> throw ProcessingErrorException("@S3.Put operation body must be S3Body/ByteArray/ByteBuffer", method)
                }

                if (type != null) {
                    requestBuilder.addStatement("_requestBuilder.contentType(%S)", type)
                }
                if (encoding != null) {
                    requestBuilder.addStatement("_requestBuilder.contentEncoding(%S)", encoding)
                }
            }

            val clientField = if (mode == S3Operation.Mode.SYNC) "_awsSyncClient" else "_awsAsyncClient"
            val bodyBuilder = CodeBlock.builder()
                .add(key.code)
                .add("\n")
                .add(bodyCode)
                .add("\n\n")
                .addStatement(
                    """
                    val _requestBuilder = %T.builder()
                        .bucket(_clientConfig.bucket())
                        .key(_key)
                        """.trimIndent(), CLASS_AWS_PUT_REQUEST
                )
                .add(requestBuilder.build())
                .addStatement("val _request = _requestBuilder.build()")
                .add("\n")
                .add("return %L.putObject(_request, _requestBody)", clientField)

            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
            }
            bodyBuilder.add("\n")

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.PUT, S3Operation.ImplType.AWS, mode, bodyBuilder.build())
        } else {
            throw ProcessingErrorException("@S3.Put operation unsupported method return signature, expected any of Unit/${CLASS_S3_UPLOAD.simpleName}/${CLASS_AWS_PUT_RESPONSE.simpleName}", method)
        }
    }

    private fun operationDELETE(method: KSFunctionDeclaration, operationMeta: OperationMeta, mode: S3Operation.Mode): S3Operation {
        val keyMapping: String? = operationMeta.annotation.findValueNoDefault("value")
        val key: Key
        val firstParameter = method.parameters.stream().findFirst().orElse(null)
        if (!keyMapping.isNullOrBlank()) {
            key = parseKey(method, keyMapping)
            if (key.params.isEmpty() && method.parameters.isNotEmpty()) {
                throw ProcessingErrorException("@S3.Delete operation key template must use method arguments or they should be removed", method)
            }
        } else if (method.parameters.size > 1) {
            throw ProcessingErrorException("@S3.Delete operation can't have multiple method parameters for keys without key template", method)
        } else if (method.parameters.isEmpty()) {
            throw ProcessingErrorException("@S3.Delete operation must have key parameter", method)
        } else {
            key = Key(CodeBlock.of("val _key = %L.toString()", firstParameter.toString()), java.util.List.of(firstParameter))
        }

        val returnTypeMirror = method.returnType
        val returnType: TypeName = returnTypeMirror!!.toTypeName()

        val isFirstParamCollection = firstParameter != null && firstParameter.type.resolve().isCollection()
        if (returnTypeMirror.isVoid()) {
            val clientField = if (mode == S3Operation.Mode.SYNC) "_simpleSyncClient" else "_simpleAsyncClient"
            val bodyBuilder = CodeBlock.builder()

            val keyArgName: String
            if (isFirstParamCollection) {
                keyArgName = firstParameter.name!!.asString()
            } else {
                bodyBuilder.add(key.code).add(";\n")
                keyArgName = "_key"
            }

            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add("return ")
            }
            bodyBuilder.add("%L.delete(_clientConfig.bucket(), %L)", clientField, keyArgName)
            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add(".thenApply {  }")
                bodyBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
            }

            bodyBuilder.add("\n")

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.DELETE, S3Operation.ImplType.SIMPLE, mode, bodyBuilder.build())
        } else if (CLASS_AWS_DELETE_RESPONSE == returnType) {
            if (isFirstParamCollection) {
                throw ProcessingErrorException("@S3.Delete operation expected single result, but parameter is collection of keys", method)
            }

            val clientField = if (mode == S3Operation.Mode.SYNC) "_awsSyncClient" else "_awsAsyncClient"
            val bodyBuilder = CodeBlock.builder()
                .add(key.code)
                .add("\n")
                .add(
                    """
                    var _request = %T.builder()
                        .bucket(_clientConfig.bucket())
                        .key(_key)
                        .build()
                        """.trimIndent(), CLASS_AWS_DELETE_REQUEST
                )
                .add("\n")
                .add("return %L.deleteObject(_request)", clientField)

            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
            }

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.DELETE, S3Operation.ImplType.AWS, mode, bodyBuilder.build())
        } else if (CLASS_AWS_DELETES_RESPONSE == returnType) {
            if (isFirstParamCollection) {
                throw ProcessingErrorException("@S3.Delete operation multiple keys, but parameter is not collection of keys", method)
            }

            val clientField = if (mode == S3Operation.Mode.SYNC) "_awsSyncClient" else "_awsAsyncClient"
            val bodyBuilder = CodeBlock.builder()
                .add(
                    """
                        var _request = %T.builder()
                            .bucket(_clientConfig.bucket())
                            .delete(
                                %T.builder()
                                    .objects(%L.map { %T.builder().key(it).build() }.toList())
                                    .build()
                            )
                            .build()
                            """.trimIndent(), CLASS_AWS_DELETES_REQUEST,
                    ClassName("software.amazon.awssdk.services.s3.model", "Delete"),
                    firstParameter.name!!.asString(),
                    ClassName("software.amazon.awssdk.services.s3.model", "ObjectIdentifier")
                )
                .add("\n")
                .add("return %L.deleteObjects(_request)", clientField)

            if (mode == S3Operation.Mode.ASYNC) {
                bodyBuilder.add(".%M()", MEMBER_AWAIT_FUTURE)
            }

            return S3Operation(method, operationMeta.annotation, S3Operation.OperationType.DELETE, S3Operation.ImplType.AWS, mode, bodyBuilder.build())
        } else {
            throw ProcessingErrorException(
                "@S3.Delete operation unsupported method return signature, expected any of Void/${CLASS_AWS_DELETE_RESPONSE.simpleName}/${CLASS_AWS_DELETES_RESPONSE.simpleName}",
                method
            )
        }
    }

    data class Key(val code: CodeBlock, val params: List<KSValueParameter>)

    private fun parseKey(method: KSFunctionDeclaration, keyTemplate: String): Key {
        var indexStart = keyTemplate.indexOf("{")
        if (indexStart == -1) {
            return Key(CodeBlock.of("val _key = %S\n", keyTemplate), listOf())
        }

        val params: MutableList<KSValueParameter> = ArrayList()
        val builder = CodeBlock.builder()
        builder.add("val _key = ")
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
            val parameter = method.parameters.stream()
                .filter { p ->
                    val bodyType: TypeName = p.type.toTypeName()
                    (ByteBuffer::class.asClassName() != bodyType && ByteArray::class.asClassName() != bodyType)
                }
                .filter { p -> p.name!!.asString() == paramName }
                .findFirst()
                .orElseThrow {
                    ProcessingErrorException(
                        "@S3 operation key part named '%s' expected, but was not found".formatted(paramName),
                        method
                    )
                }

            val paramType = parameter.type.resolve()
            if (paramType.isCollection() || paramType.isMap()) {
                throw ProcessingErrorException("@S3 operation key part '%s' can't be Collection or Map".formatted(paramName), method)
            }

            params.add(parameter)
            builder.add("%L", paramName)
            indexStart = keyTemplate.indexOf("{", indexEnd)
            if (indexStart != -1) {
                builder.add(" + ")
            }
        }

        if (indexEnd + 1 != keyTemplate.length) {
            builder.add(" + \$S", keyTemplate.substring(indexEnd + 1))
        }

        return Key(builder.add("\n").build(), params)
    }
}

