package ru.tinkoff.kora.soap.client.annotation.processor;

import com.squareup.javapoet.*;
import org.w3c.dom.Node;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class SoapClientImplGenerator {
    private final ProcessingEnvironment processingEnv;

    public SoapClientImplGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public TypeSpec generate(Element service, SoapClasses soapClasses) {
        var jaxbClasses = new ArrayList<TypeName>();
        jaxbClasses.add(soapClasses.soapEnvelopeObjectFactory());
        var xmlSeeAlso = findAnnotation(service, soapClasses.xmlSeeAlsoType());
        if (xmlSeeAlso != null) {
            for (var valuesEntry : xmlSeeAlso.getElementValues().entrySet()) {
                if (!valuesEntry.getKey().getSimpleName().contentEquals("value")) {
                    continue;
                }
                var annotationValues = (List<AnnotationValue>) valuesEntry.getValue().getValue();
                for (var annotationValue : annotationValues) {
                    var value = (TypeMirror) annotationValue.getValue();
                    jaxbClasses.add(TypeName.get(value));
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
        var builder = TypeSpec.classBuilder(CommonUtils.getOuterClassesAsPrefix(service) + service.getSimpleName() + "_SoapClientImpl")
            .addModifiers(Modifier.PUBLIC)
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
            constructorBuilder.addCode("this.$L = new $T(httpClient, telemetry, new $T(jaxb), $S, config.url(), $S, $S);",
                executorFieldName, soapClasses.soapRequestExecutor(), soapClasses.xmlToolsType(), serviceName, operationName, soapAction
            );
            builder.addField(soapClasses.soapRequestExecutor(), executorFieldName, Modifier.PRIVATE, Modifier.FINAL);

            var m = MethodSpec.overriding(method);
            this.addMapRequest(m, method, soapClasses);
            m.addCode("var __response = this.$L.call(__requestEnvelope);\n", executorFieldName);
            this.addMapResponse(m, method, soapClasses, false);
            builder.addMethod(m.build());
            var monoParam = method.getReturnType().getKind() == TypeKind.VOID
                ? this.processingEnv.getElementUtils().getTypeElement("java.lang.Void").asType()
                : method.getReturnType();
            var reactiveReturnType = ParameterizedTypeName.get(ClassName.get(Mono.class), ClassName.get(monoParam));


            var reactiveM = MethodSpec.methodBuilder(method.getSimpleName() + "Reactive")
                .addModifiers(Modifier.PUBLIC)
                .returns(reactiveReturnType);
            for (var parameter : method.getParameters()) {
                reactiveM.addParameter(TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
            }
            this.addMapRequest(reactiveM, method, soapClasses);
            reactiveM.addCode("return this.$L.callReactive(__requestEnvelope)\n", executorFieldName);
            reactiveM.addCode("  .handle(($T __response, $T __sink) -> {$>$>\n", soapClasses.soapResult(), ParameterizedTypeName.get(ClassName.get(SynchronousSink.class), TypeName.get(monoParam)));
            this.addMapResponse(reactiveM, method, soapClasses, true);
            reactiveM.addCode("$<$<\n});\n");
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
            var b = TypeSpec.classBuilder(requestClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(AnnotationSpec.builder(soapClasses.xmlAccessorTypeClassName())
                    .addMember("value", "$T.NONE", soapClasses.xmlAccessTypeClassName())
                    .build())
                .addAnnotation(AnnotationSpec.builder(soapClasses.xmlRootElementClassName())
                    .addMember("namespace", "$S", targetNamespace)
                    .addMember("name", "$S", operationName)
                    .build());
            for (var parameter : method.getParameters()) {
                var webParam = findAnnotation(parameter, soapClasses.webParamType());
                if ("OUT".equals(findAnnotationValue(webParam, "mode").toString())) {
                    continue;
                }
                var type = parameter.asType();
                if (this.processingEnv.getTypeUtils().isAssignable(type, soapClasses.holderTypeErasure())) {
                    type = ((DeclaredType) type).getTypeArguments().get(0);
                }

                b.addField(FieldSpec.builder(TypeName.get(type), parameter.getSimpleName().toString(), Modifier.PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(soapClasses.xmlElementClassName())
                        .addMember("name", "$S", findAnnotationValue(webParam, "partName").toString())
                        .build())
                    .build());
            }
            builder.addType(b.build());
        }
    }

    private void addMapRequest(MethodSpec.Builder m, ExecutableElement method, SoapClasses soapClasses) {
        var requestWrapper = findAnnotation(method, soapClasses.requestWrapperType());
        if (requestWrapper != null) {
            var wrapperClass = findAnnotationValue(requestWrapper, "className");
            m.addCode("var __requestWrapper = new $L();\n", wrapperClass);
            for (var parameter : method.getParameters()) {
                var webParam = findAnnotation(parameter, soapClasses.webParamType());
                var webParamName = (String) findAnnotationValue(webParam, "name");
                if (processingEnv.getTypeUtils().isAssignable(parameter.asType(), processingEnv.getTypeUtils().erasure(soapClasses.holderTypeErasure()))) {
                    m.addCode("__requestWrapper.set$L($L.value);\n", CommonUtils.capitalize(webParamName), parameter);
                } else {
                    m.addCode("__requestWrapper.set$L($L);\n", CommonUtils.capitalize(webParamName), parameter);
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
            assert method.getParameters().size() == 1;
            m.addCode("var __requestEnvelope = this.envelopeProcessor.apply(new $L($L));\n", soapClasses.soapEnvelopeTypeName(), method.getParameters().get(0));
        }
    }

    private boolean isRpcBuilding(ExecutableElement method, SoapClasses soapClasses) {
        var soapBinding = findAnnotation(method.getEnclosingElement(), soapClasses.soapBindingType());
        return soapBinding != null && findAnnotationValue(soapBinding, "style").toString().equals("RPC");
    }

    private void addMapResponse(MethodSpec.Builder m, ExecutableElement method, SoapClasses soapClasses, boolean isReactive) {
        m.addCode("if (__response instanceof $T __failure) {$>\n", soapClasses.soapResultFailure());
        m.addCode("var __fault = __failure.fault();\n");
        if (!method.getThrownTypes().isEmpty()) {
            m.addCode("var __detail = __fault.getDetail().getAny().get(0);\n");
            m.addCode("var __faultCode = __fault.getFaultcode();\n");
            for (var thrownType : method.getThrownTypes()) {
                var webFault = this.findAnnotation(processingEnv.getTypeUtils().asElement(thrownType), soapClasses.webFaultType());
                if (webFault == null) {
                    continue;
                }
                var detailType = processingEnv.getTypeUtils().asElement(thrownType).getEnclosedElements()
                    .stream()
                    .filter(getFaultInfo -> getFaultInfo.getKind() == ElementKind.METHOD)
                    .filter(getFaultInfo -> getFaultInfo.getSimpleName().contentEquals("getFaultInfo"))
                    .map(ExecutableElement.class::cast)
                    .map(ExecutableElement::getReturnType)
                    .findFirst()
                    .get();
                var namespace = findAnnotationValue(webFault, "targetNamespace").toString();
                var localPart = findAnnotationValue(webFault, "name").toString();
                m.addCode("if ($S.equals(__faultCode.getNamespaceURI()) && $S.equals(__faultCode.getLocalPart()) && __detail instanceof $T __error)\n", namespace, localPart, detailType);
                if (isReactive) {
                    m.addCode("  __sink.error(new $T(__failure.faultMessage(), __error));\n", thrownType);
                } else {
                    m.addCode("  throw new $T(__failure.faultMessage(), __error);\n", thrownType);
                }
                m.addCode("else ");
            }
        }
        if (isReactive) {
            m.addCode("__sink.error(new $T(__failure.faultMessage(), __fault));$<\n}\n", soapClasses.soapFaultException());
        } else {
            m.addCode("throw new $T(__failure.faultMessage(), __fault);$<\n}\n", soapClasses.soapFaultException());
        }
        m.addCode("var __success = ($T) __response;\n", soapClasses.soapResultSuccess());
        var responseWrapper = findAnnotation(method, soapClasses.responseWrapperType());
        if (responseWrapper != null) {
            var wrapperClass = (String) findAnnotationValue(responseWrapper, "className");
            var webResult = findAnnotation(method, soapClasses.webResultType());
            m.addCode("var __responseBodyWrapper = ($L) __success.body();\n", wrapperClass);
            if (webResult != null) {
                var webResultName = (String) findAnnotationValue(webResult, "name");
                if (isReactive) {
                    m.addCode("__sink.next(__responseBodyWrapper.get$L());\n", CommonUtils.capitalize(webResultName));
                } else {
                    m.addCode("return __responseBodyWrapper.get$L();\n", CommonUtils.capitalize(webResultName));
                }
            } else {
                for (var parameter : method.getParameters()) {
                    var webParam = findAnnotation(parameter, soapClasses.webParamType());
                    var mode = (String) findAnnotationValue(webParam, "mode").toString();
                    if ("IN".equals(mode)) {
                        continue;
                    }
                    var webParamName = findAnnotationValue(webParam, "name");
                    m.addCode("$L.value = __responseBodyWrapper.get$L();\n", parameter, CommonUtils.capitalize(webParamName.toString()));
                    if (isReactive) {
                        m.addCode("__sink.complete();\n");
                    }
                }
            }
        } else {
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                if (isReactive) {
                    m.addCode("__sink.complete();\n");
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
                        if (!this.processingEnv.getTypeUtils().isAssignable(parameterType, soapClasses.holderTypeErasure())) {
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
                    m.addCode("__sink.next(($T) __success.body());\n", method.getReturnType());
                } else {
                    m.addCode("return ($T) __success.body();\n", method.getReturnType());
                }
            }
        }
    }

    @Nullable
    private AnnotationMirror findAnnotation(Element element, TypeMirror annotationType) {
        return CommonUtils.findAnnotation(this.processingEnv.getElementUtils(), this.processingEnv.getTypeUtils(), element, annotationType);
    }

    @Nullable
    private Object findAnnotationValue(AnnotationMirror annotationMirror, String name) {
        return CommonUtils.parseAnnotationValue(this.processingEnv.getElementUtils(), annotationMirror, name);
    }

}
