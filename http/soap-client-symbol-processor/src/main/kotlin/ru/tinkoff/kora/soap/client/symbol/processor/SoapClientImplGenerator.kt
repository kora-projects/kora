package ru.tinkoff.kora.soap.client.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.jvm.throws
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import org.w3c.dom.Node
import reactor.core.publisher.SynchronousSink
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import ru.tinkoff.kora.soap.client.common.*
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetryFactory
import java.util.function.Function

class SoapClientImplGenerator(private val resolver: Resolver) {

    private val awaitSingleOrNull = MemberName("kotlinx.coroutines.reactor", "awaitSingleOrNull")
    private val awaitSingle = MemberName("kotlinx.coroutines.reactor", "awaitSingle")

    @KspExperimental
    fun generate(service: KSClassDeclaration, soapClasses: SoapClasses): TypeSpec {
        val jaxbClasses = mutableListOf<TypeName>()
        soapClasses.soapEnvelopeObjectFactory()?.let { jaxbClasses.add(it) }
        val xmlSeeAlso = findAnnotation(service, soapClasses.xmlSeeAlsoType()!!)
        xmlSeeAlso?.arguments?.forEach { arg ->
            if (arg.name!!.asString() == "value") {
                val types = (arg.value as List<KSType>)
                jaxbClasses.addAll(types.map { it.toTypeName() })
            }
        }
        val webService = findAnnotation(service, soapClasses.webServiceType()!!)!!
        var serviceName = findAnnotationValue(webService, "name").toString()
        if (serviceName.isEmpty()) {
            serviceName = findAnnotationValue(webService, "serviceName").toString()
        }
        if (serviceName.isEmpty()) {
            serviceName = findAnnotationValue(webService, "portName").toString()
        }
        if (serviceName.isEmpty()) {
            serviceName = service.simpleName.asString()
        }
        val targetNamespace = findAnnotationValue(webService, "targetNamespace").toString()
        val builder = TypeSpec.classBuilder(service.getOuterClassesAsPrefix() + service.simpleName.asString() + "_SoapClientImpl")
            .generated(WebServiceClientSymbolProcessor::class)
            .addProperty("envelopeProcessor", Function::class.parameterizedBy(SoapEnvelope::class, SoapEnvelope::class), KModifier.PRIVATE)
            .addProperty("jaxb", soapClasses.jaxbContextTypeName()!!, KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("httpClient", soapClasses.httpClientTypeName()!!)
                    .addParameter("telemetry", SoapClientTelemetryFactory::class)
                    .addParameter("config", SoapServiceConfig::class)
                    .throws(soapClasses.jaxbExceptionTypeName()!!)
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
            .filter { method -> findAnnotation(method, soapClasses.webMethodType()!!) != null }
            .toList()
        addRequestClasses(soapClasses, builder, jaxbClassesCode, targetNamespace, webMethods)
        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter("httpClient", soapClasses.httpClientTypeName()!!)
            .addParameter("telemetry", SoapClientTelemetryFactory::class)
            .addParameter("config", SoapServiceConfig::class.asClassName())
            .addParameter("envelopeProcessor", Function::class.parameterizedBy(SoapEnvelope::class, SoapEnvelope::class))
            .addCode("this.jaxb = %T.newInstance(%L)\n", soapClasses.jaxbContextTypeName(), jaxbClassesCode.build())
            .addCode("this.envelopeProcessor = envelopeProcessor\n")
            .throws(soapClasses.jaxbExceptionTypeName()!!)
        for (method in webMethods) {
            val webMethod = findAnnotation(method, soapClasses.webMethodType()!!)!!
            var soapAction: String? = findAnnotationValue(webMethod, "action").toString()
            soapAction = if (soapAction!!.isEmpty()) {
                null
            } else {
                "\"" + soapAction + "\""
            }
            var operationName = findAnnotationValue(webMethod, "operationName").toString()
            if (operationName.isEmpty()) {
                operationName = method.simpleName.asString()
            }
            val executorFieldName = operationName + "RequestExecutor"
            constructorBuilder.addCode(
                "this.%L = %T(httpClient, telemetry, %T(jaxb), %S, config.url(), %S, %S)\n",
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
            addMapResponse(m, method, soapClasses, false)
            builder.addFunction(m.build())
            val returnType = method.returnType!!.resolve()

            val reactiveM = FunSpec.builder(method.simpleName.asString() + "Reactive")
                .addModifiers(KModifier.SUSPEND)
                .returns(returnType.toTypeName())
            for (parameter in method.parameters) {
                reactiveM.addParameter(parameter.name!!.asString(), parameter.type.toTypeName())
            }
            addMapRequest(reactiveM, method, soapClasses)
            reactiveM.addCode("return this.%L.callReactive(__requestEnvelope)\n", executorFieldName)
            reactiveM.addCode(
                "  .handle{ __response:%T,  __sink:%T -> \n",
                SoapResult::class.java,
                SynchronousSink::class.asClassName().parameterizedBy(returnType.toTypeName())
            )
            addMapResponse(reactiveM, method, soapClasses, true)
            val awaitCode = if (returnType.isMarkedNullable) awaitSingleOrNull else awaitSingle
            reactiveM.addCode("\n}.%M()\n", awaitCode)
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
            val webMethod = findAnnotation(method, soapClasses.webMethodType()!!)
            var operationName = findAnnotationValue(webMethod!!, "operationName").toString()
            if (operationName.isEmpty()) {
                operationName = method.simpleName.asString()
            }

            val requestClassName = operationName.toString() + "Request"
            jaxbClassesCode.add(", %L::class.java", requestClassName)
            val b = TypeSpec.objectBuilder(requestClassName)
                .addAnnotation(
                    AnnotationSpec.builder(soapClasses.xmlAccessorTypeClassName()!!)
                        .addMember("value", "%T.NONE", soapClasses.xmlAccessTypeClassName()!!)
                        .build()
                )
                .addAnnotation(
                    AnnotationSpec.builder(soapClasses.xmlRootElementClassName()!!)
                        .addMember("namespace", "%S", targetNamespace)
                        .addMember("name", "%S", operationName!!)
                        .build()
                )
            for (parameter in method.parameters) {
                val webParam = findAnnotation(parameter, soapClasses.webParamType()!!)!!
                if ("OUT" == findAnnotationValue(webParam, "mode").toString()) {
                    continue
                }
                var type = parameter.type.resolve()
                if (soapClasses.holderTypeErasure()!!.isAssignableFrom(type)) {
                    type = type.arguments.first().type!!.resolve()
                }
                b.addProperty(
                    PropertySpec.builder(parameter.name!!.asString(), type.toTypeName())
                        .addAnnotation(
                            AnnotationSpec.builder(soapClasses.xmlElementClassName()!!)
                                .addMember("name", "%S", findAnnotationValue(webParam, "partName").toString())
                                .build()
                        )
                        .build()
                )
            }
            builder.addType(b.build())
        }
    }

    private fun addMapRequest(m: FunSpec.Builder, method: KSFunctionDeclaration, soapClasses: SoapClasses) {
        val requestWrapper = findAnnotation(method, soapClasses.requestWrapperType()!!)
        if (requestWrapper != null) {
            val wrapperClass = findAnnotationValue(requestWrapper, "className")
            m.addCode("val __requestWrapper = %L()\n", wrapperClass)
            for (parameter in method.parameters) {
                val webParam = findAnnotation(parameter, soapClasses.webParamType()!!)!!
                val webParamName = findAnnotationValue(webParam, "name") as String
                if (soapClasses.holderTypeErasure()!!.isAssignableFrom(parameter.type.resolve())) {
                    m.addCode("__requestWrapper.set%L(%L.value)\n", webParamName.replaceFirstChar { it.uppercaseChar() }, parameter)
                } else {
                    m.addCode("__requestWrapper.set%L(%L)\n", webParamName.replaceFirstChar { it.uppercaseChar() }, parameter)
                }
            }
            m.addCode("val __requestEnvelope = this.envelopeProcessor.apply(%L(__requestWrapper))\n", soapClasses.soapEnvelopeTypeName())
        } else if (isRpcBuilding(method, soapClasses)) {
            val webMethod = findAnnotation(method, soapClasses.webMethodType()!!)!!
            var operationName = findAnnotationValue(webMethod, "operationName").toString()
            if (operationName.isEmpty()) {
                operationName = method.simpleName.asString()
            }
            val requestClassName = operationName + "Request"
            m.addCode("val __requestWrapper = %L()\n", requestClassName)
            for (parameter in method.parameters) {
                val webParam = findAnnotation(parameter, soapClasses.webParamType()!!)!!
                if ("OUT" == findAnnotationValue(webParam, "mode").toString()) {
                    continue
                }
                m.addCode("__requestWrapper.%L = %L\n", parameter, parameter)
            }
            m.addCode("val __requestEnvelope = this.envelopeProcessor.apply(%L(__requestWrapper))\n", soapClasses.soapEnvelopeTypeName())
        } else {
            assert(method.parameters.size == 1)
            m.addCode("val __requestEnvelope = this.envelopeProcessor.apply(%L(%L))\n", soapClasses.soapEnvelopeTypeName(), method.parameters[0])
        }
    }

    private fun isRpcBuilding(method: KSFunctionDeclaration, soapClasses: SoapClasses): Boolean {
        val soapBinding = findAnnotation(method.parentDeclaration!!, soapClasses.soapBindingType()!!)
        return soapBinding != null && findAnnotationValue(soapBinding, "style").toString() == "RPC"
    }

    @KspExperimental
    @kotlin.jvm.Throws(Exception::class)
    private fun addMapResponse(m: FunSpec.Builder, method: KSFunctionDeclaration, soapClasses: SoapClasses, isReactive: Boolean) {
        m.addCode("if (__response is %T ) {\n", SoapResult.Failure::class.java)
        m.addCode("val __fault = __response.fault()\n")
        val throws = method.getAnnotationsByType(kotlin.jvm.Throws::class).toList()
        if (throws.isNotEmpty()) {
            m.addCode("val __detail = __fault.getDetail().getAny().get(0)\n")
            m.addCode("val __faultCode = __fault.getFaultcode()\n")
            val thrownTypes = throws.map { it.exceptionClasses.toList() }.flatten()
            for (thrownType in thrownTypes) {
                val thrownTypeDeclaration = resolver.getClassDeclarationByName(thrownType.qualifiedName!!) ?: continue
                val webFault = findAnnotation(thrownTypeDeclaration, soapClasses.webFaultType()!!) ?: continue
                val detailType = thrownTypeDeclaration.getDeclaredFunctions()
                    .filter { getFaultInfo -> getFaultInfo.simpleName.asString() == "getFaultInfo" }
                    .mapNotNull { obj -> obj.returnType?.resolve() }
                    .first()
                val namespace = findAnnotationValue(webFault, "targetNamespace").toString()
                val localPart = findAnnotationValue(webFault, "name").toString()
                m.addCode("if (%S.equals(__faultCode.getNamespaceURI()) && %S.equals(__faultCode.getLocalPart()) && __detail is %T)\n", namespace, localPart, detailType)
                if (isReactive) {
                    m.addCode("  __sink.error(%T(__response.faultMessage(), __detail))\n", thrownType)
                } else {
                    m.addCode("  throw %T(__response.faultMessage(), __detail)\n", thrownType)
                }
                m.addCode("else ")
            }
        }
        if (isReactive) {
            m.addCode("__sink.error(%T(__response.faultMessage(), __fault))\n}\n", SoapFaultException::class)
        } else {
            m.addCode("throw %T(__response.faultMessage(), __fault)\n}\n", SoapFaultException::class)
        }
        m.addCode("val __success =  __response as %T\n", SoapResult.Success::class)
        val responseWrapper = findAnnotation(method, soapClasses.responseWrapperType()!!)
        if (responseWrapper != null) {
            val wrapperClass = findAnnotationValue(responseWrapper, "className") as String?
            val webResult = findAnnotation(method, soapClasses.webResultType()!!)
            m.addCode("val __responseBodyWrapper =  __success.body() as (%L)\n", wrapperClass)
            if (webResult != null) {
                val webResultName = findAnnotationValue(webResult, "name") as String
                if (isReactive) {
                    m.addCode("__sink.next(__responseBodyWrapper.get%L())\n", webResultName.replaceFirstChar { it.uppercaseChar() })
                } else {
                    m.addCode("return __responseBodyWrapper.get%L()\n", webResultName.replaceFirstChar { it.uppercaseChar() })
                }
            } else {
                for (parameter in method.parameters) {
                    val webParam = findAnnotation(parameter, soapClasses.webParamType()!!)!!
                    val mode = findAnnotationValue(webParam, "mode").toString()
                    if (mode.endsWith("IN", false)) {
                        continue
                    }
                    val webParamName = findAnnotationValue(webParam, "name") as String
                    m.addCode("%L.value = __responseBodyWrapper.get%L()\n", parameter, webParamName.replaceFirstChar { it.uppercaseChar() })
                    if (isReactive) {
                        m.addCode("__sink.complete()\n")
                    }
                }
            }
        } else {
            if (method.returnType!!.resolve() == resolver.builtIns.unitType) {
                if (isReactive) {
                    m.addCode("__sink.complete()\n")
                }
                if (isRpcBuilding(method, soapClasses)) {
                    m.addCode("val __document = __success.body() as %T\n", Node::class)
                    m.addCode("for (__i in 0..__document.getChildNodes().getLength()) {\n")
                    m.addCode("val __child = __document.getChildNodes().item(__i)\n")
                    m.addCode("val __childName = __child.getLocalName()\n")
                    m.addCode("try {\n")
                    m.addCode("when (__childName) {\n")
                    for (parameter in method.parameters) {
                        val webParam = findAnnotation(parameter, soapClasses.webParamType()!!)!!
                        if ("IN" == findAnnotationValue(webParam, "mode").toString()) {
                            continue
                        }
                        val parameterType = parameter.type.resolve()
                        if (!soapClasses.holderTypeErasure()!!.isAssignableFrom(parameterType)) {
                            continue
                        }
                        val partType = parameterType.arguments[0]
                        val partName = findAnnotationValue(webParam, "partName").toString()
                        m.addCode("%S ->", partName)
                        m.addCode("  %L.value = this.jaxb.createUnmarshaller().unmarshal(__child, %T::class.java)\n    .getValue()\n", parameter, partType.toTypeName())
                    }
                    m.addCode("\n}\n")
                    m.addCode("\n} catch ( __jaxbException:%T) {\n", soapClasses.jaxbExceptionTypeName())
                    m.addCode("throw %T(__jaxbException)\n", SoapException::class.java)
                    m.addCode("\n}\n")
                    m.addCode("\n}\n")
                }
            } else {
                if (isReactive) {
                    m.addCode("__sink.next( __success.body() as %T)\n", method.returnType!!.toTypeName())
                } else {
                    m.addCode("return __success.body() as %T\n", method.returnType!!.toTypeName())
                }
            }
        }
    }

    private fun findAnnotation(declaration: KSAnnotated, annotationType: KSType): KSAnnotation? {
        return declaration.annotations.firstOrNull { it.annotationType.resolve() == annotationType }
    }

    private fun findAnnotationValue(annotation: KSAnnotation, name: String): Any? {
        return annotation.arguments.firstOrNull { it.name!!.asString() == name }?.value
    }
}
