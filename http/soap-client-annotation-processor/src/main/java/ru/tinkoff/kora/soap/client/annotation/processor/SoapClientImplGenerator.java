package ru.tinkoff.kora.soap.client.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import org.w3c.dom.Node;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class SoapClientImplGenerator {

    private static final ClassName SYNCHRONOUS_SINK = ClassName.get("reactor.core.publisher", "SynchronousSink");
    private static final ClassName SOAP_CONFIG = ClassName.get("ru.tinkoff.kora.soap.client.common", "SoapServiceConfig");
    private static final ClassName HTTP_CLIENT = ClassName.get("ru.tinkoff.kora.http.client.common", "HttpClient");
    private static final ClassName SOAP_TELEMETRY = ClassName.get("ru.tinkoff.kora.soap.client.common.telemetry", "SoapClientTelemetryFactory");

    private final ProcessingEnvironment processingEnv;

    public SoapClientImplGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public TypeSpec generateModule(Element element, SoapClasses soapClasses) {
        var webService = findAnnotation(element, soapClasses.webServiceType());
        var serviceName = findAnnotationValue(webService, "name").toString();
        if (serviceName.isEmpty()) {
            serviceName = findAnnotationValue(webService, "serviceName").toString();
        }
        if (serviceName.isEmpty()) {
            serviceName = findAnnotationValue(webService, "portName").toString();
        }
        if (serviceName.isEmpty()) {
            serviceName = element.getSimpleName().toString();
        }

        var configPath = "soapClient." + serviceName;

        var moduleName = NameUtils.generatedType(element, "SoapClientModule");
        var extractorClass = ParameterizedTypeName.get(CommonClassNames.configValueExtractor, SOAP_CONFIG);
        var elementType = ClassName.get(element.asType());

        var methodPrefix = serviceName.substring(0, 1).toLowerCase() + serviceName.substring(1);
        var type = TypeSpec.interfaceBuilder(moduleName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated).addMember("value", "$S", WebServiceClientAnnotationProcessor.class.getCanonicalName()).build())
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.module).build())
            .addOriginatingElement(element)
            .addMethod(MethodSpec.methodBuilder(methodPrefix + "_SoapConfig")
                .addAnnotation(TagUtils.makeAnnotationSpec(Set.of(elementType.toString())))
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT).returns(SOAP_CONFIG)
                .addAnnotation(CommonClassNames.defaultComponent)
                .addParameter(ParameterSpec.builder(CommonClassNames.config, "config").build())
                .addParameter(ParameterSpec.builder(extractorClass, "extractor").build())
                .addStatement("var value = config.get($S)", configPath)
                .addStatement("var parsed = extractor.extract(value)")
                .beginControlFlow("if (parsed == null)")
                .addStatement("throw $T.missingValueAfterParse(value)", CommonClassNames.configValueExtractionException)
                .endControlFlow()
                .addStatement("return parsed")
                .build())
            .addMethod(MethodSpec.methodBuilder(methodPrefix + "_SoapClientImpl")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .returns(elementType)
                .addAnnotation(CommonClassNames.defaultComponent)
                .addParameter(ParameterSpec.builder(HTTP_CLIENT, "httpClient").build())
                .addParameter(ParameterSpec.builder(SOAP_TELEMETRY, "telemetry").build())
                .addParameter(ParameterSpec.builder(SOAP_CONFIG, "config").addAnnotation(TagUtils.makeAnnotationSpec(Set.of(elementType.toString()))).build())
                .beginControlFlow("try")
                .addStatement("return new $L(httpClient, telemetry, config)", NameUtils.generatedType(element, "SoapClientImpl"))
                .nextControlFlow("catch (Exception e)")
                .addStatement("throw new $T(e)", IllegalStateException.class)
                .endControlFlow()
                .build()
            );

        return type.build();
    }

    record ObjectFactory(ClassName className, String fieldName, DeclaredType typeMirror, TypeElement typeElement) {
        public TypeMirror findWrappedResponseType(ProcessingEnvironment processingEnv, String wrapperClass) {
            var objectFactoryMethods = CommonUtils.findMethods(typeElement, modifiers -> modifiers.contains(Modifier.PUBLIC));
            for (var executableElement : objectFactoryMethods) {
                if (executableElement.getParameters().isEmpty() && executableElement.getReturnType().toString().equals(wrapperClass)) {
                    var returnType = executableElement.getReturnType();
                    var wrapperTypeElement = (TypeElement) processingEnv.getTypeUtils().asElement(returnType);
                    var wrapperTypeMethods = CommonUtils.findMethods(wrapperTypeElement, modifiers -> modifiers.contains(Modifier.PUBLIC));
                    for (var wrapperMethod : wrapperTypeMethods) {
                        if (wrapperMethod.getParameters().isEmpty()) {
                            return wrapperMethod.getReturnType();
                        }
                    }
                }
            }
            throw new IllegalArgumentException("No method found for wrapper class " + wrapperClass);
        }

        @Nullable
        public ExecutableElement findWrapperMethod(ExecutableElement method, VariableElement parameter) {
            var paramFactoryName = "create" + CommonUtils.capitalize(method.getSimpleName().toString()) + CommonUtils.capitalize(parameter.getSimpleName().toString());
            for (var method0 : CommonUtils.findMethods(typeElement, modifiers -> modifiers.contains(Modifier.PUBLIC))) {
                if (method0.getParameters().size() == 1 && method0.getSimpleName().contentEquals(paramFactoryName)) {
                    return method0;
                }
            }
            return null;
        }
    }

    public TypeSpec generate(Element service, SoapClasses soapClasses) {
        var jaxbClasses = new ArrayList<TypeName>();
        jaxbClasses.add(soapClasses.soapEnvelopeObjectFactory());
        var objectFactories = new ArrayList<ObjectFactory>();
        var xmlSeeAlso = findAnnotation(service, soapClasses.xmlSeeAlsoType());
        if (xmlSeeAlso != null) {
            for (var valuesEntry : xmlSeeAlso.getElementValues().entrySet()) {
                if (!valuesEntry.getKey().getSimpleName().contentEquals("value")) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                var annotationValues = (List<AnnotationValue>) valuesEntry.getValue().getValue();
                for (int i = 0; i < annotationValues.size(); i++) {
                    var annotationValue = annotationValues.get(i);
                    var value = (TypeMirror) annotationValue.getValue();
                    var typeName = TypeName.get(value);
                    jaxbClasses.add(typeName);
                    if (typeName instanceof ClassName cn && cn.simpleName().equals("ObjectFactory")) {
                        objectFactories.add(new ObjectFactory(
                            cn, "OBJECT_FACTORY_" + i, (DeclaredType) value, (TypeElement) processingEnv.getTypeUtils().asElement(value)
                        ));
                    }
                }
            }
        }

        var webService = findAnnotation(service, soapClasses.webServiceType());
        var serviceName = findAnnotationValue(webService, "name").toString();
        if (serviceName.isEmpty()) {
            serviceName = findAnnotationValue(webService, "serviceName").toString();
        }
        if (serviceName.isEmpty()) {
            serviceName = findAnnotationValue(webService, "portName").toString();
        }
        if (serviceName.isEmpty()) {
            serviceName = service.getSimpleName().toString();
        }
        var targetNamespace = findAnnotationValue(webService, "targetNamespace").toString();
        var builder = TypeSpec.classBuilder(NameUtils.generatedType(service, "SoapClientImpl"))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated).addMember("value", CodeBlock.of("$S", WebServiceClientAnnotationProcessor.class.getCanonicalName())).build())
            .addField(ParameterizedTypeName.get(ClassName.get(Function.class), soapClasses.soapEnvelopeTypeName(), soapClasses.soapEnvelopeTypeName()), "envelopeProcessor", Modifier.PRIVATE, Modifier.FINAL)
            .addField(soapClasses.jaxbContextTypeName(), "jaxb", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(soapClasses.httpClientTypeName(), "httpClient")
                .addParameter(soapClasses.soapClientTelemetryFactory(), "telemetry")
                .addParameter(soapClasses.soapServiceConfig(), "config")
                .addCode("this(httpClient, telemetry, config, $T.identity());\n", Function.class)
                .addException(soapClasses.jaxbExceptionTypeName())
                .build())
            .addSuperinterface(service.asType());

        var jaxbClassesCode = CodeBlock.builder();
        for (int i = 0; i < jaxbClasses.size(); i++) {
            jaxbClassesCode.add("$T.class", jaxbClasses.get(i));
            if (i < jaxbClasses.size() - 1) {
                jaxbClassesCode.add(", ");
            }
        }

        for (var factory : objectFactories) {
            var fieldSpec = FieldSpec.builder(factory.className, factory.fieldName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", factory.className)
                .build();
            builder.addField(fieldSpec);
        }

        var webMethods = service.getEnclosedElements().stream()
            .filter(element -> element instanceof ExecutableElement)
            .map(ExecutableElement.class::cast)
            .filter(method -> findAnnotation(method, soapClasses.webMethodType()) != null)
            .toList();
        this.addRequestClasses(soapClasses, builder, jaxbClassesCode, targetNamespace, webMethods);

        var constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(soapClasses.httpClientTypeName(), "httpClient")
            .addParameter(soapClasses.soapClientTelemetryFactory(), "telemetry")
            .addParameter(soapClasses.soapServiceConfig(), "config")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class), soapClasses.soapEnvelopeTypeName(), soapClasses.soapEnvelopeTypeName()), "envelopeProcessor")
            .addCode("this.jaxb = $T.newInstance($L);\n", soapClasses.jaxbContextTypeName(), jaxbClassesCode.build())
            .addCode("this.envelopeProcessor = envelopeProcessor;\n")
            .addException(soapClasses.jaxbExceptionTypeName());

        for (var method : webMethods) {
            var webMethod = findAnnotation(method, soapClasses.webMethodType());
            var soapAction = findAnnotationValue(webMethod, "action").toString();
            if (soapAction.isEmpty()) {
                soapAction = null;
            } else {
                soapAction = "\"" + soapAction + "\"";
            }
            var operationName = findAnnotationValue(webMethod, "operationName").toString();
            if (operationName.isEmpty()) {
                operationName = method.getSimpleName().toString();
            }
            var executorFieldName = operationName + "RequestExecutor";
            constructorBuilder.addCode(
                "this.$L = new $T(httpClient, telemetry, new $T(jaxb), $S, $S, config, $S, $S);\n",
                executorFieldName, soapClasses.soapRequestExecutor(), soapClasses.xmlToolsType(), service.toString(), serviceName, operationName, soapAction
            );
            builder.addField(soapClasses.soapRequestExecutor(), executorFieldName, Modifier.PRIVATE, Modifier.FINAL);

            var m = MethodSpec.overriding(method);
            this.addMapRequest(m, method, soapClasses, objectFactories);
            m.addCode("var __response = this.$L.call(__requestEnvelope);\n", executorFieldName);
            this.addMapResponse(m, method, soapClasses, false, objectFactories);
            builder.addMethod(m.build());
            var monoParam = method.getReturnType().getKind() == TypeKind.VOID
                ? this.processingEnv.getElementUtils().getTypeElement("java.lang.Void").asType()
                : method.getReturnType();
            var reactiveReturnType = ParameterizedTypeName.get(ClassName.get(CompletionStage.class), ClassName.get(monoParam));


            var reactiveM = MethodSpec.methodBuilder(method.getSimpleName() + "Async")
                .addModifiers(Modifier.PUBLIC)
                .returns(reactiveReturnType);
            for (var parameter : method.getParameters()) {
                reactiveM.addParameter(TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
            }
            this.addMapRequest(reactiveM, method, soapClasses, objectFactories);
            reactiveM.addCode("var __future = new $T<$T>();\n", CompletableFuture.class, monoParam);
            reactiveM.addCode("this.$L.callAsync(__requestEnvelope)\n", executorFieldName);
            reactiveM.addCode("  .whenComplete((__response, __throwable) -> {$>$>\n", soapClasses.soapResult(), ParameterizedTypeName.get(SYNCHRONOUS_SINK, TypeName.get(monoParam)));
            reactiveM.addCode("if (__throwable != null) {\n");
            reactiveM.addCode("  __future.completeExceptionally(__throwable);\n");
            reactiveM.addCode("  return;\n");
            reactiveM.addCode("}\n");
            this.addMapResponse(reactiveM, method, soapClasses, true, objectFactories);
            reactiveM.addCode("$<$<\n});\n");
            reactiveM.addCode("return __future;\n");
            builder.addMethod(reactiveM.build());

        }
        builder.addMethod(constructorBuilder.build());
        return builder.build();
    }

    private void addRequestClasses(SoapClasses soapClasses, TypeSpec.Builder builder, CodeBlock.Builder jaxbClassesCode, String targetNamespace, List<ExecutableElement> webMethods) {
        for (var method : webMethods) {
            if (!isRpcBuilding(method, soapClasses)) {
                continue;
            }
            var webMethod = findAnnotation(method, soapClasses.webMethodType());
            var operationName = findAnnotationValue(webMethod, "operationName").toString();
            if (operationName.isEmpty()) {
                operationName = method.getSimpleName().toString();
            }
            var requestClassName = operationName + "Request";
            jaxbClassesCode.add(", $L.class", requestClassName);
            var b = TypeSpec.classBuilder(requestClassName).addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(AnnotationSpec.builder(soapClasses.xmlAccessorTypeClassName())
                    .addMember("value", "$T.NONE", soapClasses.xmlAccessTypeClassName()).build())
                .addAnnotation(AnnotationSpec.builder(soapClasses.xmlRootElementClassName()).addMember("namespace", "$S", targetNamespace).addMember("name", "$S", operationName).build());
            for (var parameter : method.getParameters()) {
                var webParam = findAnnotation(parameter, soapClasses.webParamType());
                if ("OUT".equals(findAnnotationValue(webParam, "mode").toString())) {
                    continue;
                }
                var type = parameter.asType();
                var typeName = TypeName.get(type);
                if (typeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(soapClasses.holderTypeClassName())) {
                    type = ((DeclaredType) type).getTypeArguments().get(0);
                }

                b.addField(FieldSpec.builder(TypeName.get(type), parameter.getSimpleName().toString(), Modifier.PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(soapClasses.xmlElementClassName())
                        .addMember("name", "$S", findAnnotationValue(webParam, "partName").toString()).build())
                    .build());
            }
            builder.addType(b.build());
        }
    }

    private void addMapRequest(MethodSpec.Builder m, ExecutableElement method, SoapClasses soapClasses, List<ObjectFactory> objectFactories) {
        var requestWrapper = findAnnotation(method, soapClasses.requestWrapperType());
        if (requestWrapper != null) {
            var wrapperClass = this.<String>findAnnotationValue(requestWrapper, "className");
            var objectFactory = this.findObjectFactoryByWrapper(wrapperClass, objectFactories);
            m.addCode("var __requestWrapper = new $L();\n", wrapperClass);
            for (var parameter : method.getParameters()) {
                var webParam = findAnnotation(parameter, soapClasses.webParamType());
                var webParamName = (String) findAnnotationValue(webParam, "name");
                var isHolder = TypeName.get(parameter.asType()) instanceof ParameterizedTypeName ptn && ptn.rawType.equals(soapClasses.holderTypeClassName());
                var wrapperMethod = objectFactory.findWrapperMethod(method, parameter);
                if (wrapperMethod == null) {
                    if (isHolder) {
                        m.addCode("__requestWrapper.set$L($L.value);\n", CommonUtils.capitalize(webParamName), parameter);
                    } else {
                        m.addCode("__requestWrapper.set$L($L);\n", CommonUtils.capitalize(webParamName), parameter);
                    }
                } else {
                    if (isHolder) {
                        var wrapCode = CodeBlock.of("$N.$N($L.value)", objectFactory.fieldName, wrapperMethod.getSimpleName().toString(), parameter);
                        m.addCode("__requestWrapper.set$L($L);\n", CommonUtils.capitalize(webParamName), wrapCode);
                    } else {
                        var wrapCode = CodeBlock.of("$N.$N($L)", objectFactory.fieldName, wrapperMethod.getSimpleName().toString(), parameter);
                        m.addCode("__requestWrapper.set$L($L);\n", CommonUtils.capitalize(webParamName), wrapCode);
                    }
                }
            }
            m.addCode("var __requestEnvelope = this.envelopeProcessor.apply(new $L(__requestWrapper));\n", soapClasses.soapEnvelopeTypeName());
        } else if (isRpcBuilding(method, soapClasses)) {
            var webMethod = findAnnotation(method, soapClasses.webMethodType());
            var operationName = findAnnotationValue(webMethod, "operationName").toString();
            if (operationName.isEmpty()) {
                operationName = method.getSimpleName().toString();
            }
            var requestClassName = operationName + "Request";
            m.addCode("var __requestWrapper = new $L();\n", requestClassName);
            for (var parameter : method.getParameters()) {
                var webParam = findAnnotation(parameter, soapClasses.webParamType());
                if ("OUT".equals(findAnnotationValue(webParam, "mode").toString())) {
                    continue;
                }
                m.addCode("__requestWrapper.$L = $L;\n", parameter, parameter);
            }
            m.addCode("var __requestEnvelope = this.envelopeProcessor.apply(new $L(__requestWrapper));\n", soapClasses.soapEnvelopeTypeName());
        } else {
            if (method.getParameters().size() != 1) throw new AssertionError();
            m.addCode("var __requestEnvelope = this.envelopeProcessor.apply(new $L($L));\n", soapClasses.soapEnvelopeTypeName(), method.getParameters().get(0));
        }
    }

    private boolean isRpcBuilding(ExecutableElement method, SoapClasses soapClasses) {
        var soapBinding = findAnnotation(method.getEnclosingElement(), soapClasses.soapBindingType());
        return soapBinding != null && findAnnotationValue(soapBinding, "style").toString().equals("RPC");
    }

    private void checkNullForResponseWrapper(MethodSpec.Builder m, boolean isReactive, boolean isJaxbElement, String methodName) {
        m.beginControlFlow("if (__responseBodyWrapper == null || __responseBodyWrapper.$N() == null)", methodName);
        if (isReactive) {
            m.addStatement("__future.complete(null)");
        } else {
            m.addStatement("return null");
        }
        m.nextControlFlow("else");
        if (isReactive) {
            if (isJaxbElement) {
                m.addStatement("__future.complete(__responseBodyWrapper.$N().getValue())", methodName);
            } else {
                m.addStatement("__future.complete(__responseBodyWrapper.$N())", methodName);
            }
        } else {
            if (isJaxbElement) {
                m.addStatement("return __responseBodyWrapper.$N().getValue()", methodName);
            } else {
                m.addStatement("return __responseBodyWrapper.$N()", methodName);
            }
        }
        m.endControlFlow();
    }

    private void addMapResponse(MethodSpec.Builder m, ExecutableElement method, SoapClasses soapClasses, boolean isReactive, List<ObjectFactory> objectFactories) {
        m.addCode("if (__response instanceof $T __failure) {$>\n", soapClasses.soapResultFailure());
        m.addCode("var __fault = __failure.fault();\n");
        if (!method.getThrownTypes().isEmpty()) {
            m.beginControlFlow("if (__fault.getDetail() != null && __fault.getDetail().getAny() != null && __fault.getDetail().getAny().size() > 0)");
            m.addCode("var __detail = __fault.getDetail().getAny().get(0);\n");
            for (var thrownType : method.getThrownTypes()) {
                var webFault = this.findAnnotation(processingEnv.getTypeUtils().asElement(thrownType), soapClasses.webFaultType());
                if (webFault == null) {
                    continue;
                }
                var detailType = processingEnv.getTypeUtils().asElement(thrownType).getEnclosedElements().stream()
                    .filter(getFaultInfo -> getFaultInfo.getKind() == ElementKind.METHOD)
                    .filter(getFaultInfo -> getFaultInfo.getSimpleName().contentEquals("getFaultInfo"))
                    .map(ExecutableElement.class::cast)
                    .map(ExecutableElement::getReturnType)
                    .findFirst()
                    .get();
                m.addCode("if (__detail instanceof $T __error) {\n", detailType);
                if (isReactive) {
                    m.addCode("  __future.completeExceptionally(new $T(__failure.faultMessage(), __error));\n", thrownType);
                    m.addCode("  return;\n", thrownType);
                } else {
                    m.addCode("  throw new $T(__failure.faultMessage(), __error);\n", thrownType);
                }
                m.addCode("} else ");
            }
            if (isReactive) {
                m.addCode("{\n");
                m.addCode("  __future.completeExceptionally(new $T(__failure.faultMessage(), __fault));\n", soapClasses.soapFaultException());
                m.addCode("  return;\n}\n");
            } else {
                m.addCode("\n  throw new $T(__failure.faultMessage(), __fault);\n", soapClasses.soapFaultException());
            }
            m.endControlFlow();
        }

        if (isReactive) {
            m.addCode("__future.completeExceptionally(new $T(__failure.faultMessage(), __fault));\n", soapClasses.soapFaultException());
            m.addCode("return;$<\n}\n");
        } else {
            m.addCode("throw new $T(__failure.faultMessage(), __fault);$<\n}\n", soapClasses.soapFaultException());
        }
        m.addCode("var __success = ($T) __response;\n", soapClasses.soapResultSuccess());
        var responseWrapper = findAnnotation(method, soapClasses.responseWrapperType());
        if (responseWrapper != null) {
            var wrapperClass = this.<String>findAnnotationValue(responseWrapper, "className");
            var webResult = findAnnotation(method, soapClasses.webResultType());
            m.addCode("var __responseBodyWrapper = ($L) __success.body();\n", wrapperClass);
            if (webResult != null) {
                var objectFactory = this.findObjectFactoryByWrapper(wrapperClass, objectFactories);
                var wrappedType = objectFactory.findWrappedResponseType(this.processingEnv, wrapperClass);
                var webResultName = (String) findAnnotationValue(webResult, "name");
                var isJaxbElement = !wrappedType.toString().equals(method.getReturnType().toString());
                checkNullForResponseWrapper(m, isReactive, isJaxbElement, "get" + CommonUtils.capitalize(webResultName));
            } else {
                for (var parameter : method.getParameters()) {
                    var webParam = findAnnotation(parameter, soapClasses.webParamType());
                    var mode = this.<String>findAnnotationValue(webParam, "mode");
                    if ("IN".equals(mode)) {
                        continue;
                    }
                    var webParamName = findAnnotationValue(webParam, "name");
                    m.addCode("$L.value = __responseBodyWrapper.get$L();\n", parameter, CommonUtils.capitalize(webParamName.toString()));
                    if (isReactive) {
                        m.addCode("__future.complete(null);\n");
                    }
                }
            }
        } else {
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                if (isReactive) {
                    m.addCode("__future.complete(null);\n");
                }
                if (this.isRpcBuilding(method, soapClasses)) {
                    m.addCode("var __document = ($T) __success.body();\n", Node.class);
                    m.addCode("for (var __i = 0; __i < __document.getChildNodes().getLength(); __i++) {$>\n", Node.class);
                    m.addCode("var __child = __document.getChildNodes().item(__i);\n");
                    m.addCode("var __childName = __child.getLocalName();\n");
                    m.addCode("try {$>\n");
                    m.addCode("switch (__childName) {$>\n");
                    for (var parameter : method.getParameters()) {
                        var webParam = findAnnotation(parameter, soapClasses.webParamType());
                        if ("IN".equals(findAnnotationValue(webParam, "mode").toString())) {
                            continue;
                        }
                        var parameterType = parameter.asType();
                        var parameterTypeName = TypeName.get(parameterType);
                        if (!(parameterTypeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(soapClasses.holderTypeClassName()))) {
                            continue;
                        }
                        var partType = ((DeclaredType) parameterType).getTypeArguments().get(0);
                        var partName = findAnnotationValue(webParam, "partName").toString();
                        m.addCode("case $S:\n", partName);
                        m.addCode("  $L.value = this.jaxb.createUnmarshaller().unmarshal(__child, $T.class)\n    .getValue();\n", parameter, partType);
                        m.addCode("  break;\n");

                    }
                    m.addCode("default: break;\n");
                    m.addCode("$<\n}\n");
                    m.addCode("$<\n} catch ($T __jaxbException) {$>\n", soapClasses.jaxbExceptionTypeName());
                    m.addCode("throw new $T(__jaxbException);\n", soapClasses.soapException());
                    m.addCode("$<\n}\n");
                    m.addCode("$<\n}\n");
                }
            } else {
                if (isReactive) {
                    m.addCode("__future.complete(($T) __success.body());\n", method.getReturnType());
                } else {
                    m.addCode("return ($T) __success.body();\n", method.getReturnType());
                }
            }
        }
    }

    @Nullable
    private AnnotationMirror findAnnotation(Element element, ClassName annotationType) {
        return AnnotationUtils.findAnnotation(element, annotationType);
    }

    @Nullable
    private <T> T findAnnotationValue(AnnotationMirror annotationMirror, String name) {
        return AnnotationUtils.parseAnnotationValue(this.processingEnv.getElementUtils(), annotationMirror, name);
    }

    private ObjectFactory findObjectFactoryByWrapper(String wrapperClass, Iterable<ObjectFactory> objectFactories) {
        for (var objectFactory : objectFactories) {
            var typePackageName = objectFactory.className.packageName();
            if (wrapperClass.startsWith(typePackageName) && wrapperClass.indexOf('.', typePackageName.length() + 1) < 0) {
                return objectFactory;
            }
        }
        throw new IllegalStateException();
    }
}
