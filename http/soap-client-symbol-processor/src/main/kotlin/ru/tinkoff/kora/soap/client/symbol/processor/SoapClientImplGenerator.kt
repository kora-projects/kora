package ru.tinkoff.kora.soap.client.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.jvm.throws
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.w3c.dom.Node
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation
import ru.tinkoff.kora.ksp.common.doesImplement
import ru.tinkoff.kora.ksp.common.generatedClassName
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import ru.tinkoff.kora.soap.client.common.*
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetryFactory
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.function.Function

class SoapClientImplGenerator(private val resolver: Resolver) {

    private val soapConfig = ClassName("ru.tinkoff.kora.soap.client.common", "SoapServiceConfig")
    private val httpClient = ClassName("ru.tinkoff.kora.http.client.common", "HttpClient")
    private val soapTelemetry = ClassName("ru.tinkoff.kora.soap.client.common.telemetry", "SoapClientTelemetryFactory")

    fun generateModule(declaration: KSClassDeclaration, soapClasses: SoapClasses): TypeSpec {
        val webService = declaration.findAnnotation(soapClasses.webServiceType())!!
        var serviceName = webService.findValue<String>("name") ?: ""
        if (serviceName.isEmpty()) {
            serviceName = webService.findValue<String>("serviceName") ?: ""
        }
        if (serviceName.isEmpty()) {
            serviceName = webService.findValue<String>("portName") ?: ""
        }
        if (serviceName.isEmpty()) {
            serviceName = declaration.simpleName.asString()
        }

        val configPath = "soapClient.$serviceName"
        val moduleName = declaration.generatedClassName("SoapClientModule")
        val extractorClass = CommonClassNames.configValueExtractor.parameterizedBy(soapConfig)
        val elementType = declaration.toClassName()

        val methodPrefix = serviceName.substring(0, 1).lowercase(Locale.getDefault()) + serviceName.substring(1)
        val implName = declaration.getOuterClassesAsPrefix() + declaration.simpleName.asString() + "_SoapClientImpl"
        val type = TypeSpec.interfaceBuilder(moduleName)
            .generated(WebServiceClientSymbolProcessor::class)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.module).build())
            .addOriginatingKSFile(declaration.containingFile!!)
            .addFunction(
                FunSpec.builder(methodPrefix + "_SoapConfig")
                    .returns(soapConfig)
                    .addAnnotation(CommonClassNames.defaultComponent)
                    .addAnnotation(listOf(elementType.canonicalName).toTagAnnotation())
                    .addParameter(ParameterSpec.builder("config", CommonClassNames.config).build())
                    .addParameter(ParameterSpec.builder("extractor", extractorClass).build())
                    .addStatement("val value = config.get(%S)", configPath)
                    .addStatement("val parsed = extractor.extract(value)")
                    .controlFlow("if (parsed == null)") {
                        addStatement("throw %T.missingValueAfterParse(value)", CommonClassNames.configValueExtractionException)
                    }
                    .addStatement("return parsed")
                    .build()
            )
            .addFunction(
                FunSpec.builder(methodPrefix + "_SoapClientImpl")
                    .returns(declaration.toClassName())
                    .addAnnotation(CommonClassNames.defaultComponent)
                    .addParameter(ParameterSpec.builder("httpClient", httpClient).build())
                    .addParameter(ParameterSpec.builder("telemetry", soapTelemetry).build())
                    .addParameter(
                        ParameterSpec.builder("config", soapConfig)
                            .addAnnotation(listOf(elementType.canonicalName).toTagAnnotation())
                            .build()
                    )
                    .addStatement(
                        "return %T(httpClient, telemetry, config, %T.identity())",
                        ClassName(declaration.packageName.asString(), implName), Function::class
                    )
                    .build()
            )

        return type.build()
    }

    fun generate(service: KSClassDeclaration, soapClasses: SoapClasses): TypeSpec {
        val jaxbClasses = mutableListOf<TypeName>()
        jaxbClasses.add(soapClasses.soapEnvelopeObjectFactory())
        val xmlSeeAlso = service.findAnnotation(soapClasses.xmlSeeAlsoType())
        xmlSeeAlso?.arguments?.forEach { arg ->
            if (arg.name!!.asString() == "value") {
                val types = (arg.value as List<*>)
                jaxbClasses.addAll(types.map { (it as KSType).toTypeName() })
            }
        }
        val webService = service.findAnnotation(soapClasses.webServiceType())!!
        var serviceName = webService.findValue<String>("name") ?: ""
        if (serviceName.isEmpty()) {
            serviceName = webService.findValue<String>("serviceName") ?: ""
        }
        if (serviceName.isEmpty()) {
            serviceName = webService.findValue<String>("portName") ?: ""
        }
        if (serviceName.isEmpty()) {
            serviceName = service.simpleName.asString()
        }
        val targetNamespace = webService.findValue<String>("targetNamespace")!!
        val builder = TypeSpec.classBuilder(service.getOuterClassesAsPrefix() + service.simpleName.asString() + "_SoapClientImpl")
            .generated(WebServiceClientSymbolProcessor::class)
            .addProperty("envelopeProcessor", Function::class.parameterizedBy(SoapEnvelope::class, SoapEnvelope::class), KModifier.PRIVATE)
            .addProperty("jaxb", soapClasses.jaxbContextTypeName(), KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("httpClient", soapClasses.httpClientTypeName()!!)
                    .addParameter("telemetry", SoapClientTelemetryFactory::class)
                    .addParameter("config", SoapServiceConfig::class)
                    .throws(soapClasses.jaxbExceptionTypeName())
                    .build()
            )
            .addSuperinterface(service.toClassName())
        val jaxbClassesCode = CodeBlock.builder()
        for (i in jaxbClasses.indices) {
            jaxbClassesCode.add("%T::class.java", jaxbClasses[i])
            if (i < jaxbClasses.size - 1) {
                jaxbClassesCode.add(", ")
            }
        }
        val webMethods = service.getDeclaredFunctions()
            .filter { method -> method.isAnnotationPresent(soapClasses.webMethodType()) }
            .toList()
        addRequestClasses(soapClasses, builder, jaxbClassesCode, targetNamespace, webMethods)
        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter("httpClient", soapClasses.httpClientTypeName())
            .addParameter("telemetry", SoapClientTelemetryFactory::class)
            .addParameter("config", SoapServiceConfig::class.asClassName())
            .addParameter("envelopeProcessor", Function::class.parameterizedBy(SoapEnvelope::class, SoapEnvelope::class))
            .addCode("this.jaxb = %T.newInstance(%L)\n", soapClasses.jaxbContextTypeName(), jaxbClassesCode.build())
            .addCode("this.envelopeProcessor = envelopeProcessor\n")
            .throws(soapClasses.jaxbExceptionTypeName())
        for (method in webMethods) {
            val webMethod = method.findAnnotation(soapClasses.webMethodType())!!
            var soapAction = webMethod.findValue<String>("action")
            soapAction = if (soapAction.isNullOrEmpty()) {
                null
            } else {
                "\"" + soapAction + "\""
            }
            var operationName = webMethod.findValue<String>("operationName") ?: ""
            if (operationName.isEmpty()) {
                operationName = method.simpleName.asString()
            }
            val executorFieldName = operationName + "RequestExecutor"
            constructorBuilder.addCode(
                "this.%L = %T(httpClient, telemetry, %T(jaxb), %S, config, %S, %S)\n",
                executorFieldName, SoapRequestExecutor::class.java, soapClasses.xmlToolsType(), serviceName, operationName, soapAction
            )
            builder.addProperty(executorFieldName, SoapRequestExecutor::class, KModifier.PRIVATE)
            val m = FunSpec.builder(method.simpleName.asString()).addModifiers(KModifier.OVERRIDE)
            method.parameters.forEach { param ->
                m.addParameter(param.name!!.asString(), param.type.toTypeName())
            }
            method.returnType?.let { m.returns(it.toTypeName()) }
            addMapRequest(m, method, soapClasses)
            m.addCode("val __response = this.%L.call(__requestEnvelope)\n", executorFieldName)
            addMapResponse(m, method, soapClasses)
            builder.addFunction(m.build())
            val returnType = method.returnType!!.resolve()

            val reactiveM = FunSpec.builder(method.simpleName.asString() + "Reactive")
                .addModifiers(KModifier.SUSPEND)
                .returns(returnType.toTypeName())
            for (parameter in method.parameters) {
                reactiveM.addParameter(parameter.name!!.asString(), parameter.type.toTypeName())
            }
            addMapRequest(reactiveM, method, soapClasses)
            reactiveM.addStatement("val __responseFuture = this.%L.callAsync(__requestEnvelope) as %T", executorFieldName, CompletionStage::class.asClassName().parameterizedBy(SoapResult::class.asTypeName().copy(true)))
            reactiveM.addStatement("val __response = __responseFuture.%M()", CommonClassNames.await)
            addMapResponse(reactiveM, method, soapClasses)
            builder.addFunction(reactiveM.build())
        }
        builder.primaryConstructor(constructorBuilder.build())
        return builder.build()
    }

    private fun addRequestClasses(soapClasses: SoapClasses, builder: TypeSpec.Builder, jaxbClassesCode: CodeBlock.Builder, targetNamespace: String, webMethods: List<KSFunctionDeclaration>) {
        for (method in webMethods) {
            if (!isRpcBuilding(method, soapClasses)) {
                continue
            }
            val webMethod = method.findAnnotation(soapClasses.webMethodType())!!
            var operationName = webMethod.findValue<String>("operationName")
            if (operationName.isNullOrEmpty()) {
                operationName = method.simpleName.asString()
            }

            val requestClassName = operationName.toString() + "Request"
            jaxbClassesCode.add(", %L::class.java", requestClassName)
            val b = TypeSpec.classBuilder(requestClassName)
                .addAnnotation(
                    AnnotationSpec.builder(soapClasses.xmlAccessorTypeClassName())
                        .addMember("value = %T.NONE", soapClasses.xmlAccessTypeClassName())
                        .build()
                )
                .addAnnotation(
                    AnnotationSpec.builder(soapClasses.xmlRootElementClassName())
                        .addMember("namespace = %S", targetNamespace)
                        .addMember("name = %S", operationName)
                        .build()
                )
            for (parameter in method.parameters) {
                val webParam = parameter.findAnnotation(soapClasses.webParamType())!!
                if ("OUT" == webParam.findEnumValue("mode")) {
                    continue
                }
                var type = parameter.type.resolve()
                type.declaration.let {
                    if (it is KSClassDeclaration && it.doesImplement(soapClasses.holderType())) {
                        type = type.arguments.first().type!!.resolve()
                    }
                }
                b.addProperty(
                    PropertySpec.builder(parameter.name!!.asString(), type.toTypeName().copy(true))
                        .initializer("null")
                        .mutable(true)
                        .addAnnotation(
                            AnnotationSpec.builder(soapClasses.xmlElementClassName())
                                .addMember("name = %S", webParam.findValue<String>("partName")!!)
                                .build()
                        )
                        .build()
                )
            }
            builder.addType(b.build())
        }
    }

    private fun addMapRequest(m: FunSpec.Builder, method: KSFunctionDeclaration, soapClasses: SoapClasses) {
        val requestWrapper = method.findAnnotation(soapClasses.requestWrapperType())
        if (requestWrapper != null) {
            val wrapperClass = requestWrapper.findValue<String>("className")!!
            m.addCode("val __requestWrapper = %L()\n", wrapperClass)
            for (parameter in method.parameters) {
                val webParam = parameter.findAnnotation(soapClasses.webParamType())!!
                val webParamName = webParam.findValue<String>("name")!!
                parameter.type.resolve().declaration.let {
                    if (it is KSClassDeclaration && it.doesImplement(soapClasses.holderType())) {
                        m.addCode("__requestWrapper.set%L(%L.value)\n", webParamName.replaceFirstChar { it.uppercaseChar() }, parameter)
                    } else {
                        m.addCode("__requestWrapper.set%L(%L)\n", webParamName.replaceFirstChar { it.uppercaseChar() }, parameter)
                    }
                }
            }
            m.addCode("val __requestEnvelope = this.envelopeProcessor.apply(%T(__requestWrapper))\n", soapClasses.soapEnvelopeTypeName())
        } else if (isRpcBuilding(method, soapClasses)) {
            val webMethod = method.findAnnotation(soapClasses.webMethodType())!!
            var operationName = webMethod.findValue<String>("operationName") ?: ""
            if (operationName.isEmpty()) {
                operationName = method.simpleName.asString()
            }
            val requestClassName = operationName + "Request"
            m.addCode("val __requestWrapper = %L()\n", requestClassName)
            for (parameter in method.parameters) {
                val webParam = parameter.findAnnotation(soapClasses.webParamType())!!
                if ("OUT" == webParam.findEnumValue("mode")) {
                    continue
                }
                m.addCode("__requestWrapper.%L = %L\n", parameter, parameter)
            }
            m.addCode("val __requestEnvelope = this.envelopeProcessor.apply(%T(__requestWrapper))\n", soapClasses.soapEnvelopeTypeName())
        } else {
            assert(method.parameters.size == 1)
            m.addCode("val __requestEnvelope = this.envelopeProcessor.apply(%T(%L))\n", soapClasses.soapEnvelopeTypeName(), method.parameters[0])
        }
    }

    private fun isRpcBuilding(method: KSFunctionDeclaration, soapClasses: SoapClasses): Boolean {
        val soapBinding = method.parentDeclaration?.findAnnotation(soapClasses.soapBindingType())
        return soapBinding.findEnumValue("style") == "RPC"
    }

    @OptIn(KspExperimental::class)
    private fun addMapResponse(m: FunSpec.Builder, method: KSFunctionDeclaration, soapClasses: SoapClasses) {
        m.controlFlow("if (__response is %T )", SoapResult.Failure::class.java) {
            m.addCode("val __fault = __response.fault()\n")
            val throws = resolver.getJvmCheckedException(method).toList()
            if (throws.isNotEmpty()) {
                m.addStatement("val __detail = __fault.detail.any.firstOrNull()")
                for (thrownType in throws) {
                    val thrownTypeDeclaration = thrownType.declaration as KSClassDeclaration
                    if (!thrownTypeDeclaration.isAnnotationPresent(soapClasses.webFaultType())) {
                        continue
                    }
                    val detailType = thrownTypeDeclaration.getDeclaredFunctions()
                        .filter { getFaultInfo -> getFaultInfo.simpleName.asString() == "getFaultInfo" }
                        .mapNotNull { obj -> obj.returnType?.resolve() }
                        .first()
                    m.addCode("if (__detail is %T)\n", detailType.toTypeName())
                    m.addCode("  throw %T(__response.faultMessage(), __detail)\n", thrownType.toTypeName())
                    m.addCode("else ")
                }
            }
            m.addStatement("throw %T(__response.faultMessage(), __fault)", SoapFaultException::class)
        }
        m.addCode("val __success =  __response as %T\n", SoapResult.Success::class)
        val responseWrapper = method.findAnnotation(soapClasses.responseWrapperType())
        if (responseWrapper != null) {
            val wrapperClass = responseWrapper.findValue<String>("className")
            val webResult = method.findAnnotation(soapClasses.webResultType())
            m.addCode("val __responseBodyWrapper =  __success.body() as (%L)\n", wrapperClass)
            if (webResult != null) {
                val webResultName = webResult.findValue<String>("name")!!
                m.addCode("return __responseBodyWrapper.get%L()\n", webResultName.replaceFirstChar { it.uppercaseChar() })
            } else {
                for (parameter in method.parameters) {
                    val webParam = parameter.findAnnotation(soapClasses.webParamType())!!
                    val mode = webParam.findEnumValue("mode") ?: ""
                    if (mode.endsWith("IN", false)) {
                        continue
                    }
                    val webParamName = webParam.findValue<String>("name")!!
                    m.addCode("%L.value = __responseBodyWrapper.get%L()\n", parameter, webParamName.replaceFirstChar { it.uppercaseChar() })
                }
            }
        } else {
            if (method.returnType!!.resolve() == resolver.builtIns.unitType) {
                if (isRpcBuilding(method, soapClasses)) {
                    m.addCode("val __document = __success.body() as %T\n", Node::class)
                    m.controlFlow("for (__i in 0..__document.childNodes.getLength())") {
                        addCode("val __child = __document.childNodes.item(__i)\n")
                        addCode("val __childName = __child.localName\n")
                        controlFlow("try") {
                            addCode("when (__childName) {\n")
                            for (parameter in method.parameters) {
                                val webParam = parameter.findAnnotation(soapClasses.webParamType())!!
                                if ("IN" == webParam.findEnumValue("mode")) {
                                    continue
                                }
                                val parameterType = parameter.type.resolve()
                                val parameterTypeDecl = parameterType.declaration
                                if (parameterTypeDecl !is KSClassDeclaration || !parameterTypeDecl.doesImplement(soapClasses.holderType())) {
                                    continue
                                }
                                val partType = parameterType.arguments[0]
                                val partName = webParam.findValue<String>("partName")!!
                                addCode("%S ->", partName)
                                addStatement(" %L.value = this.jaxb.createUnmarshaller()\n  .unmarshal(__child, %T::class.java)\n  ?.value", parameter, partType.toTypeName())
                            }
                            addCode("\n}\n")
                            nextControlFlow("catch (__jaxbException: %T)", soapClasses.jaxbExceptionTypeName())
                            addStatement("throw %T(__jaxbException)", SoapException::class.java)
                        }

                    }
                }
            } else {
                m.addCode("return __success.body() as %T\n", method.returnType!!.toTypeName())
            }
        }
    }

    private fun KSAnnotation?.findEnumValue(name: String): String? {
        val style = this?.findValue<Any>(name)
        if (style is KSDeclaration) {
            return style.simpleName.asString()
        }
        return style?.toString()
    }
}
