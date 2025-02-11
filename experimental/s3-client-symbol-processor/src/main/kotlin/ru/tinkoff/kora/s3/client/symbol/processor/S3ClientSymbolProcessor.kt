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
import ru.tinkoff.kora.ksp.common.*
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonAopUtils.overridingKeepAop
import ru.tinkoff.kora.ksp.common.CommonClassNames.isCollection
import ru.tinkoff.kora.ksp.common.CommonClassNames.isList
import ru.tinkoff.kora.ksp.common.CommonClassNames.isMap
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.addOriginatingKSFile
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.io.IOException
import java.io.InputStream

class S3ClientSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(S3ClassNames.Annotation.client.canonicalName).toList()
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
                val generatedConfig = generateClientConfig(s3client)
                generatedConfig.typeSpec?.let {
                    FileSpec.get(packageName, it).writeTo(environment.codeGenerator, false)
                }

                val typeSpec = generateClient(generatedConfig, s3client)
                val fileImplSpec = FileSpec.builder(packageName, typeSpec.name.toString())
                    .addType(typeSpec)
                    .build()
                fileImplSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)

            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
        }

        return symbols.filterNot { it.validate() }.toList()
    }

    private fun generateClient(generatedConfig: GenerateClientConfig, s3client: KSClassDeclaration): TypeSpec {
        val implClassName = ClassName(s3client.packageName.asString(), s3client.generatedClassName("Impl"))
        val implSpecBuilder: TypeSpec.Builder = TypeSpec.classBuilder(implClassName)
            .generated(S3ClientSymbolProcessor::class)
            .addSuperinterface(s3client.toTypeName())
            .addOriginatingKSFile(s3client)

        val clientAnnotation = s3client.findAnnotation(S3ClassNames.Annotation.client)
        val constructorBuilder = FunSpec.constructorBuilder()
        val clientTag = clientAnnotation?.findValueNoDefault<List<KSType>>("clientFactoryTag")
        if (clientTag != null) {
            constructorBuilder.addParameter(ParameterSpec.builder("clientFactory", S3ClassNames.clientFactory)
                .addAnnotation(clientTag.makeTagAnnotationSpec())
                .build())
        } else {
            constructorBuilder.addParameter("clientFactory", S3ClassNames.clientFactory)
        }
        generatedConfig.typeSpec?.let { configSpec ->
            val configTypeName = ClassName(implClassName.packageName, configSpec.name!!)
            constructorBuilder.addParameter("config", configTypeName)
            implSpecBuilder.addProperty(PropertySpec.builder("config", configTypeName)
                .initializer("config")
                .build())
        }
        implSpecBuilder.addProperty(PropertySpec.builder("client", S3ClassNames.client).initializer("clientFactory.create(%T::class.java)", implClassName).build())
        implSpecBuilder.primaryConstructor(constructorBuilder.build())

        for (func in s3client.getDeclaredFunctions()) {
            if (func.isAbstract) {
                val operation = generateMethod(generatedConfig, func)
                implSpecBuilder.addFunction(operation)
            }
        }

        return implSpecBuilder.build()
    }

    private data class GenerateClientConfig(val typeSpec: TypeSpec?, val paths: List<String>)

    private fun generateClientConfig(s3client: KSClassDeclaration): GenerateClientConfig {
        val bucketPaths = LinkedHashSet<String>()
        val onClass = s3client.findAnnotation(S3ClassNames.Annotation.bucket)
        if (onClass != null) {
            val value = onClass.findValueNoDefault<String>("value")
            if (value != null) {
                bucketPaths.add(value)
            }
        }
        for (func in s3client.getDeclaredFunctions()) {
            val onMethod = func.findAnnotation(S3ClassNames.Annotation.bucket)
            if (onMethod != null) {
                val value = onMethod.findValueNoDefault<String>("value")
                if (value != null) {
                    bucketPaths.add(value)
                }
            }
        }
        if (bucketPaths.isEmpty()) {
            return GenerateClientConfig(null, emptyList())
        }
        val paths = ArrayList(bucketPaths)
        val configType = ClassName(s3client.packageName.asString(), s3client.generatedClass("ClientConfig"))
        val b = TypeSpec.classBuilder(configType)
            .generated(S3ClientSymbolProcessor::class)
            .addOriginatingKSFile(s3client)
        val constructor = FunSpec.constructorBuilder()
            .addParameter("config", CommonClassNames.config)
            .build()
        for ((i, path) in paths.withIndex()) {
            b.addProperty(PropertySpec.builder("bucket_$i", String::class).initializer("config.get(%S).asString()", path).build())
        }
        b.primaryConstructor(constructor)
        return GenerateClientConfig(b.build(), paths)
    }

    private fun generateMethod(generatedConfig: GenerateClientConfig, func: KSFunctionDeclaration): FunSpec {
        if (func.isSuspend()) {
            throw ProcessingErrorException("@S3.Client method can't be suspend", func)
        }
        for (parameter in func.parameters) {
            if (parameter.type.resolve().isMarkedNullable) {
                throw ProcessingErrorException("S3 operation can't have nullable method argument", parameter)
            }
        }
        val s3Operations = func.annotations
            .filter { S3ClassNames.Annotation.s3OperationClassNames.contains(it.annotationType.toTypeName()) }
            .toList()
        if (s3Operations.isEmpty()) {
            throw ProcessingErrorException("@S3.Client method must be annotated with single operation annotation", func)
        }
        if (s3Operations.size > 1) {
            throw ProcessingErrorException("@S3.Client method without operation annotation can't be non default", func)
        }
        val operationAnnotation = s3Operations.first()
        return when (s3Operations.first().annotationType.toTypeName()) {
            S3ClassNames.Annotation.get -> operationGET(generatedConfig, func, operationAnnotation)
            S3ClassNames.Annotation.delete -> operationDELETE(generatedConfig, func, operationAnnotation)
            S3ClassNames.Annotation.list -> operationLIST(generatedConfig, func, operationAnnotation)
            S3ClassNames.Annotation.put -> operationPUT(generatedConfig, func, operationAnnotation)
            else -> throw IllegalStateException("Unknown operation annotation")
        }
    }

    private fun operationGET(generatedConfig: GenerateClientConfig, func: KSFunctionDeclaration, annotation: KSAnnotation): FunSpec {
        val returnType = func.returnType!!.resolve().toTypeName()
        val allowedTypeNames = setOf<TypeName>(
            S3ClassNames.objectMeta,
            ByteArray::class.asTypeName(),
            S3ClassNames.body,
            S3ClassNames.s3Object,
            InputStream::class.asTypeName()
        )
        val returnTypeNonNullable = returnType.copy(false)
        if (!allowedTypeNames.contains(returnTypeNonNullable)) {
            throw ProcessingErrorException("Function ${func.simpleName.asString()} has return type $returnType, but should have one of $allowedTypeNames", func)
        }
        val rangeParams = func.parameters.filter { S3ClassNames.rangeClasses.contains(it.type.toTypeName()) }
        if (rangeParams.size > 1) {
            throw ProcessingErrorException("Function ${func.simpleName.asString()} has more than one range parameter", rangeParams[1])
        }
        val range = rangeParams.firstOrNull()
        val bucket = extractBucket(generatedConfig, func)
        val key = extractKey(func, annotation, true)
        val builder = func.overridingKeepAop()
            .addStatement("val _bucket = %L", bucket)
            .addStatement("val _key = %L", key)
        if (returnTypeNonNullable == S3ClassNames.objectMeta) {
            if (range != null) {
                throw ProcessingErrorException("Range parameters are not allowed on metadata requests", range)
            }
            if (returnType.isNullable) {
                builder.addStatement("return this.client.getMetaOptional(_bucket, _key)")
            } else {
                builder.addStatement("return this.client.getMeta(_bucket, _key)!!")
            }
            return builder.build()
        }
        if (range != null) {
            builder.addStatement("val _range = %L", range)
        } else {
            builder.addStatement("val _range = null as %T?", S3ClassNames.rangeData)
        }
        if (returnTypeNonNullable == ByteArray::class.asTypeName()) {
            if (returnType.isNullable) {
                builder.controlFlow("this.client.getOptional(_bucket, _key, _range).use { _object ->") {
                    controlFlow("_object?.body().use { _body ->") {
                        addStatement("return _body?.asBytes()")
                    }
                }
            } else {
                builder.controlFlow("this.client.get(_bucket, _key, _range).use { _object ->") {
                    controlFlow("_object.body().use { _body ->") {
                        addStatement("return _body.asBytes()!!")
                    }
                }
            }
            return builder.build()
        }
        if (returnTypeNonNullable == S3ClassNames.s3Object) {
            if (returnType.isNullable) {
                builder.addStatement("return this.client.getOptional(_bucket, _key, _range)")
            } else {
                builder.addStatement("return this.client.get(_bucket, _key, _range)!!")
            }
            return builder.build()
        }
        if (returnTypeNonNullable == S3ClassNames.body) {
            if (returnType.isNullable) {
                builder.addStatement("return this.client.getOptional(_bucket, _key, _range)?.body()")
            } else {
                builder.addStatement("return this.client.get(_bucket, _key, _range).body()!!")
            }
            return builder.build()
        }
        if (returnTypeNonNullable == InputStream::class.asClassName()) {
            if (returnType.isNullable) {
                builder.addStatement("return this.client.getOptional(_bucket, _key, _range)?.body()?.asInputStream()")
            } else {
                builder.addStatement("return this.client.get(_bucket, _key, _range).body().asInputStream()!!")
            }
            return builder.build()
        }
        throw IllegalStateException("Not gonna happen");
    }

    private fun operationLIST(generatedConfig: GenerateClientConfig, func: KSFunctionDeclaration, annotation: KSAnnotation): FunSpec {
        val returnType = func.returnType!!.resolve().toTypeName()
        val isList = returnType == List::class.asClassName().parameterizedBy(S3ClassNames.objectMeta)
        val isIterator = returnType == Iterator::class.asClassName().parameterizedBy(S3ClassNames.objectMeta)
        if (!isList && !isIterator) {
            throw ProcessingErrorException("Function ${func.simpleName.asString()} has return type $returnType, but should have one of List<S3ObjectMeta> or Iterator<S3ObjectMeta>", func)
        }
        val bucket = extractBucket(generatedConfig, func)
        val key = extractKey(func, annotation, false)
        val limit = extractLimit(func)
        val delimiter = extractDelimiter(func)

        val builder = func.overridingKeepAop()
            .addStatement("val _bucket = %L", bucket)
            .addStatement("val _key = %L", key)
            .addStatement("val _delimiter = %L", delimiter)
            .addStatement("val _limit = %L", limit)
        if (isList) {
            return builder.addStatement("return this.client.list(_bucket, _key, _delimiter, _limit)").build()
        } else {
            require(isIterator)
            return builder.addStatement("return this.client.listIterator(_bucket, _key, _delimiter, _limit)").build()
        }
    }

    private fun extractDelimiter(func: KSFunctionDeclaration): CodeBlock {
        val onParameter = func.parameters.filter { it.isAnnotationPresent(S3ClassNames.Annotation.listDelimiter) }
        if (onParameter.size > 1) {
            throw ProcessingErrorException("@S3.List operation expected at most one @S3.List.Delimiter parameter", onParameter[1])
        }
        if (onParameter.isNotEmpty()) {
            val parameter = onParameter[0]
            val annotation = parameter.findAnnotation(S3ClassNames.Annotation.listDelimiter)!!
            if (annotation.findValueNoDefault<String>("value") != null) {
                throw ProcessingErrorException("@S3.List.Delimiter annotation can't have value when annotating parameter", parameter)
            }
            val parameterType = parameter.type.resolve().toTypeName()
            if (parameterType == STRING) {
                return CodeBlock.of("%N", parameter.name?.asString())
            }
            throw ProcessingErrorException("@S3.List.Delimiter annotation can't have parameter of type $parameterType: only String is allowed", parameter);
        }
        val onMethod = func.findAnnotation(S3ClassNames.Annotation.listDelimiter)
        if (onMethod != null) {
            val value = onMethod.findValueNoDefault<String>("value")
            if (value != null) {
                return CodeBlock.of("%S", value)
            }
            throw ProcessingErrorException("@S3.List.Delimiter annotation must have value when annotating method", func)
        }
        return CodeBlock.of("null as String?")
    }

    private fun extractLimit(func: KSFunctionDeclaration): Any {
        val onParameter = func.parameters.filter { it.isAnnotationPresent(S3ClassNames.Annotation.listLimit) }
        if (onParameter.size > 1) {
            throw ProcessingErrorException("@S3.List operation expected at most one @S3.List.Limit parameter", onParameter[1])
        }
        if (onParameter.isNotEmpty()) {
            val parameter = onParameter[0]
            val annotation = parameter.findAnnotation(S3ClassNames.Annotation.listLimit)!!
            if (annotation.findValueNoDefault<Int>("value") != null) {
                throw ProcessingErrorException("@S3.List.Limit annotation can't have value when annotating parameter", parameter)
            }
            val parameterType = parameter.type.toTypeName()
            return when (parameterType) {
                INT -> CodeBlock.of("%M(1000, %N)", MemberName("kotlin.math", "min"), parameter.name?.asString())
                LONG -> CodeBlock.of("%M(1000, %T.toIntExact(%N))", MemberName("kotlin.math", "min"), Math::class.asClassName(), parameter.name?.asString())
                else -> throw ProcessingErrorException("@S3.List.Limit annotation can only be used on Int or Long parameters", parameter)
            }
        }
        val onFunction = func.findAnnotation(S3ClassNames.Annotation.listLimit)
        if (onFunction != null) {
            val value = onFunction.findValueNoDefault<Int>("value")
            return when {
                value == null -> throw ProcessingErrorException("@S3.List.Limit annotation must have value when annotating method", func)
                value <= 0 -> throw ProcessingErrorException("@S3.List.Limit should be more than zero", func)
                value > 1000 -> throw ProcessingErrorException("@S3.List.Limit should be less than 1000", func)
                else -> CodeBlock.of("%L", value)
            }
        }
        return CodeBlock.of("1000")
    }

    private fun operationPUT(generatedConfig: GenerateClientConfig, func: KSFunctionDeclaration, annotation: KSAnnotation): FunSpec {
        val returnType = func.returnType!!.toTypeName()
        if (returnType != UNIT && returnType != S3ClassNames.uploadResult) {
            throw ProcessingErrorException("@S3.Put operation return type must be Unit or S3ObjectUploadResult", func)
        }
        val bodyParams = func.parameters.filter { p -> S3ClassNames.bodyTypes.contains(p.type.resolve().toTypeName()) }
        if (bodyParams.size != 1) {
            throw ProcessingErrorException("@S3.Put operation must have exactly one parameter of types S3Body, byte[] or InputStream", func)
        }
        val bodyParam = bodyParams[0]
        val bodyParamType = bodyParam.type.resolve().toTypeName()
        val bucket = extractBucket(generatedConfig, func)
        val key = extractKey(func, annotation, true)
        val b = func.overridingKeepAop()
            .addStatement("val _bucket = %L", bucket)
            .addStatement("val _key = %L", key)
        when (bodyParamType) {
            S3ClassNames.body -> b.addStatement("val _body = %N", bodyParam.name?.asString()!!)
            ByteArray::class.asClassName() -> b.addStatement("val _body = %T.ofBytes(%N)", S3ClassNames.body, bodyParam.name!!.asString())
            InputStream::class.asClassName() -> b.addStatement("val _body = %T.ofInputStream(%N)", S3ClassNames.body, bodyParam.name!!.asString())
            else -> throw IllegalStateException("never gonna happen")
        }
        b.addStatement("return this.client.put(_bucket, _key, _body)")
        return b.build()
    }

    private fun operationDELETE(generatedConfig: GenerateClientConfig, func: KSFunctionDeclaration, annotation: KSAnnotation): FunSpec {
        val returnType = func.returnType!!.toTypeName()
        if (returnType != UNIT) {
            throw ProcessingErrorException("@S3.Delete operation unsupported method return signature, expected Unit, got $returnType", func)
        }
        val bucket = extractBucket(generatedConfig, func)
        val nonBucketParams = func.parameters.filter { !it.isAnnotationPresent(S3ClassNames.Annotation.bucket) }
        if (nonBucketParams.isEmpty()) {
            throw ProcessingErrorException("@S3.Delete operation must have key related parameter", func)
        }
        val funSpec = func.overridingKeepAop()
            .addStatement("val _bucket = %L", bucket)
        val firstKeyParam = nonBucketParams.first()
        val firstKeyParamType = firstKeyParam.type.resolve()
        if (nonBucketParams.size == 1 && (firstKeyParamType.isCollection() || firstKeyParamType.isList())) {
            val collectionType = firstKeyParamType.toTypeName() as ParameterizedTypeName
            if (collectionType.typeArguments.first() == STRING) {
                funSpec.addStatement("val _key = %L", firstKeyParam.name!!.asString())
            } else {
                funSpec.addStatement("val _key = %L.map{it.toString()}", firstKeyParam.name!!.asString())
            }
        } else {
            val key = extractKey(func, annotation, true)
            funSpec.addStatement("val _key = %L", key)
        }
        funSpec.addStatement("this.client.delete(_bucket, _key)")
        return funSpec.build()
    }


    private fun extractKey(func: KSFunctionDeclaration, annotation: KSAnnotation, isRequired: Boolean): CodeBlock {
        val keyMapping = annotation.findValueNoDefault<String>("value")
        val parameters = func.parameters.filter {
            val parameterTypeName = it.type.toTypeName()
            return@filter !it.isAnnotationPresent(S3ClassNames.Annotation.bucket)
                && !it.isAnnotationPresent(S3ClassNames.Annotation.listLimit)
                && !it.isAnnotationPresent(S3ClassNames.Annotation.listDelimiter)
                && !S3ClassNames.rangeClasses.contains(parameterTypeName)
                && !S3ClassNames.bodyTypes.contains(parameterTypeName)
        }
        if (!keyMapping.isNullOrBlank()) {
            val key = parseKey(func, parameters, keyMapping)
            if (key.params.isEmpty() && parameters.isNotEmpty()) {
                throw ProcessingErrorException("@S3 operation key template must use method arguments or the should be removed", parameters[0])
            }
            return key.code
        }
        if (parameters.size > 1) {
            throw ProcessingErrorException("@S3 operation can't have multiple function parameters for keys without key template", func)
        }
        if (parameters.isEmpty()) {
            if (isRequired) {
                throw ProcessingErrorException("@S3 operation must have at least one method parameter for key", func)
            }
            return CodeBlock.of("null as String?")
        }
        if (parameters.first().type.resolve().isCollection()) {
            throw ProcessingErrorException("@%${annotation.shortName.asString()} operation expected single result, but parameter is collection of keys", func)
        }
        return CodeBlock.of("%N.toString()", parameters.first().name!!.asString())
    }

    private fun extractBucket(generatedConfig: GenerateClientConfig, func: KSFunctionDeclaration): CodeBlock {
        val onParameter = func.parameters.filter { it.isAnnotationPresent(S3ClassNames.Annotation.bucket) }
        if (onParameter.size > 1) {
            throw ProcessingErrorException("@S3.Delete operation can't have multiple @S3.Bucket annotations", func)
        }
        onParameter.firstOrNull()?.let {
            return CodeBlock.of("%N", it.name?.asString()!!)
        }
        val onMethod = func.findAnnotation(S3ClassNames.Annotation.bucket)
        if (onMethod != null) {
            val value = onMethod.findValueNoDefault<String>("value")!!
            val i = generatedConfig.paths.indexOf(value)
            if (i < 0) {
                throw ProcessingErrorException("@S3 operation bucket annotation value must be one of ${generatedConfig.paths.joinToString()}", func)
            }
            return CodeBlock.of("this.config.bucket_%L", i)
        }
        val onClass = func.parentDeclaration?.findAnnotation(S3ClassNames.Annotation.bucket)
        if (onClass != null) {
            val value = onClass.findValueNoDefault<String>("value")!!
            val i = generatedConfig.paths.indexOf(value)
            if (i < 0) {
                throw ProcessingErrorException("@S3 operation bucket annotation value must be one of ${generatedConfig.paths.joinToString()}", func)
            }
            return CodeBlock.of("this.config.bucket_%L", i)
        }
        throw ProcessingErrorException("S3 operation expected bucket on parameter, function or class but got none", func)
    }

    data class Key(val code: CodeBlock, val params: List<KSValueParameter>)

    private fun parseKey(method: KSFunctionDeclaration, parameters: List<KSValueParameter>, keyTemplate: String): Key {
        var indexStart = keyTemplate.indexOf("{")
        if (indexStart == -1) {
            return Key(CodeBlock.of("%S\n", keyTemplate), listOf())
        }

        val params = ArrayList<KSValueParameter>()
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
            val parameter = parameters.firstOrNull { p -> p.name!!.asString() == paramName }
            if (parameter == null) {
                throw ProcessingErrorException(
                    "@S3 operation key part named '${paramName}' expected, but wasn't found",
                    method
                )
            }

            val paramType = parameter.type.resolve()
            if (paramType.isCollection() || paramType.isMap()) {
                throw ProcessingErrorException("@S3 operation key part '${paramName}' can't be Collection or Map", method)
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
}

