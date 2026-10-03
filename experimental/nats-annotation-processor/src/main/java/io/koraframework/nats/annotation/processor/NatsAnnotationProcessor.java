package io.koraframework.nats.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

/**
 * Generates publisher implementations and listener modules without loading runtime client classes.
 */
public final class NatsAnnotationProcessor extends AbstractKoraProcessor {
    private static final String ROOT = "io.koraframework.nats.common";
    private static final ClassName PUBLISHER = type("annotation.NatsPublisher");
    private static final ClassName SUBJECT = PUBLISHER.nestedClass("Subject");
    private static final ClassName REQUEST = PUBLISHER.nestedClass("Request");
    private static final ClassName LISTENER = type("annotation.NatsListener");
    private static final ClassName CLIENT = type("NatsClient");
    private static final ClassName PUB_CONFIG = type("producer.NatsPublisherConfig");
    private static final ClassName ATOMIC_PUBLISHER = type("producer.AtomicBatchPublisher");
    private static final ClassName ATOMIC_CONFIG = type("producer.NatsAtomicBatchConfig");
    private static final ClassName BATCH_SINK = type("producer.NatsAtomicBatchSink");
    private static final ClassName FUNCTION = ClassName.get("java.util.function", "Function");
    private static final ClassName SUBJECT_CONFIG = PUB_CONFIG.nestedClass("SubjectConfig");
    private static final ClassName LISTENER_CONFIG = type("consumer.NatsListenerConfig");
    private static final ClassName SERIALIZER = type("producer.serializer.NatsSerializer");
    private static final ClassName DESERIALIZER = type("consumer.deserializer.NatsDeserializer");
    private static final ClassName CONSUMER_MESSAGE = type("consumer.NatsMessage");
    private static final ClassName CONSUMER_MESSAGES = type("consumer.NatsMessages");
    private static final ClassName PRODUCER_MESSAGE = type("producer.NatsProducerMessage");
    private static final ClassName PUB_TELEMETRY = type("producer.telemetry.NatsPublisherTelemetry");
    private static final ClassName PUB_TELEMETRY_FACTORY = type("producer.telemetry.NatsPublisherTelemetryFactory");
    private static final ClassName CON_TELEMETRY = type("consumer.telemetry.NatsConsumerTelemetry");
    private static final ClassName CON_TELEMETRY_FACTORY = type("consumer.telemetry.NatsConsumerTelemetryFactory");
    private static final ClassName POLL_OBSERVATION = type("consumer.telemetry.NatsConsumerPollObservation");
    private static final ClassName SERIALIZATION_EXCEPTION = type("exceptions.NatsSerializationException");
    private static final ClassName EXCEPTION = ClassName.get(Exception.class);
    private static final ClassName CONFIGURER = ClassName.get("io.koraframework.common", "Configurer");
    private static final ClassName OPTIONS_BUILDER = ClassName.get("io.nats.client", "Options").nestedClass("Builder");
    private static final ClassName CONSUMER_BUILDER = ClassName.get("io.nats.client.api", "ConsumerConfiguration").nestedClass("Builder");
    private static final ClassName CONTAINER = type("consumer.NatsConsumerContainer");
    private static final ClassName MESSAGE = ClassName.get("io.nats.client", "Message");
    private static final ClassName CONNECTION = ClassName.get("io.nats.client", "Connection");
    private static final ClassName HEADERS = ClassName.get("io.nats.client.impl", "Headers");
    private static final ClassName OPTIONS = ClassName.get("io.nats.client", "PublishOptions");
    private static final ClassName CALLBACK = type("producer.NatsPublishCallback");
    private static final ClassName ACK = ClassName.get("io.nats.client.api", "PublishAck");
    private static final ClassName FUTURE = ClassName.get("java.util.concurrent", "CompletableFuture");
    private static final ClassName STAGE = ClassName.get("java.util.concurrent", "CompletionStage");
    private static final ClassName DURATION = ClassName.get("java.time", "Duration");
    private static final ClassName VOID = ClassName.get(Void.class);
    private final Set<String> generated = new HashSet<>();

    private static ClassName type(String name) {
        return ClassName.bestGuess(ROOT + "." + name);
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(PUBLISHER, LISTENER, CommonClassNames.aopProxy);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv,
                        Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotated : annotatedElements.getOrDefault(PUBLISHER, List.of())) {
            try {
                generatePublisher((TypeElement) annotated.element(), null);
            } catch (InvalidContract e) {
                messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.element);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to generate NATS publisher", e);
            }
        }
        for (var annotated : annotatedElements.getOrDefault(CommonClassNames.aopProxy, List.of())) {
            var proxy = (TypeElement) annotated.element();
            if (!(proxy.getSuperclass() instanceof DeclaredType parent)) {
                continue;
            }
            var implementation = (TypeElement) parent.asElement();
            for (var contract : implementation.getInterfaces()) {
                var publisher = (TypeElement) types.asElement(contract);
                if (!AnnotationUtils.isAnnotationPresent(publisher, PUBLISHER)) {
                    continue;
                }
                try {
                    generatePublisher(publisher, proxy);
                } catch (InvalidContract e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.element);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to generate NATS publisher AOP module", e);
                }
            }
        }
        var controllers = new LinkedHashSet<TypeElement>();
        for (var annotated : annotatedElements.getOrDefault(LISTENER, List.of())) {
            controllers.add((TypeElement) annotated.element().getEnclosingElement());
        }
        for (var controller : controllers) {
            try {
                generateListeners(controller);
            } catch (InvalidContract e) {
                messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.element);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to generate NATS listener", e);
            }
        }
    }

    private static final class InvalidContract extends RuntimeException {
        final Element element;

        InvalidContract(Element element, String message) {
            super(message);
            this.element = element;
        }
    }

    private static void require(boolean condition, Element element, String message) {
        if (!condition) {
            throw new InvalidContract(element, message);
        }
    }

    private String annotationValue(Element element, ClassName annotation) {
        return AnnotationUtils.parseAnnotationValue(elements, AnnotationUtils.findAnnotation(element, annotation), "value");
    }

    private static boolean is(TypeMirror mirror, ClassName name) {
        return mirror instanceof DeclaredType declared && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals(name.canonicalName());
    }

    private static TypeMirror argument(TypeMirror mirror, Element element) {
        require(mirror instanceof DeclaredType && ((DeclaredType) mirror).getTypeArguments().size() == 1,
            element, "NATS record/future must have one concrete type argument");
        var type = ((DeclaredType) mirror).getTypeArguments().getFirst();
        require(type.getKind() != TypeKind.WILDCARD && type.getKind() != TypeKind.TYPEVAR,
            element, "NATS payload type must be concrete");
        return type;
    }

    private static TypeName boxed(TypeMirror type) {
        return TypeName.get(type).box();
    }

    private static String tag(Element parameter, TypeMirror payload) {
        var tag = TagUtils.parseTagValue(parameter);
        return tag == null ? TagUtils.parseTagValue(payload) : tag;
    }

    private static ParameterSpec dependency(TypeName type, String name, String tag) {
        var result = ParameterSpec.builder(type, name);
        if (tag != null) {
            result.addAnnotation(TagUtils.makeAnnotationSpec(tag));
        }
        return result.build();
    }

    private static ParameterSpec mapperDependency(Element source, ClassName mapperKind, TypeName defaultType, String name, String tag) {
        var mapping = CommonUtils.parseMapping(source).getMapping(mapperKind);
        return dependency(mapping != null && mapping.mapperClass() != null ? TypeName.get(mapping.mapperClass()) : defaultType, name, tag);
    }

    private static TypeSpec.Builder module(String name, TypeElement origin) {
        return TypeSpec.interfaceBuilder(name).addModifiers(Modifier.PUBLIC).addOriginatingElement(origin)
            .addAnnotation(CommonClassNames.module).addAnnotation(AnnotationUtils.generated(NatsAnnotationProcessor.class));
    }

    private MethodSpec configMethod(String name, ClassName configType, ClassName tag, String path) {
        return MethodSpec.methodBuilder(name).addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(TagUtils.makeAnnotationSpec(tag)).returns(configType)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueMapper, configType), "mapper")
            .addStatement("return mapper.mapOrThrow(config.get($S))", path).build();
    }

    private void write(String packageName, TypeSpec spec) throws IOException {
        JavaFile.builder(packageName, spec).build().writeTo(processingEnv.getFiler());
    }

    private static String providerPrefix(TypeElement owner) {
        var prefix = NameUtils.getOuterClassesAsPrefix(owner);
        return CommonUtils.decapitalize((prefix.startsWith("$") ? prefix.substring(1) : prefix) + owner.getSimpleName());
    }

    private static String moduleName(TypeElement owner, String suffix) {
        var name = NameUtils.generatedType(owner, suffix);
        return name.startsWith("$") ? name.substring(1) : name;
    }

    private static ParameterSpec configurer(ClassName builder, String name, ClassName tag) {
        return ParameterSpec.builder(ParameterizedTypeName.get(CONFIGURER, builder), name)
            .addAnnotation(ClassName.get("org.jspecify.annotations", "Nullable"))
            .addAnnotation(TagUtils.makeAnnotationSpec(tag)).build();
    }

    private static void connectionProviders(TypeSpec.Builder module, String prefix, ClassName config, ClassName tag,
                                            String path, String canonicalName, ClassName telemetry, ClassName factory) {
        module.addMethod(MethodSpec.methodBuilder(prefix + "Telemetry").addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(TagUtils.makeAnnotationSpec(tag)).returns(telemetry)
            .addParameter(dependency(config, "_config", tag.canonicalName())).addParameter(factory, "_factory")
            .addStatement("return _factory.get($S, $S, _config.telemetry(), _config.driverProperties())", path, canonicalName).build());
        module.addMethod(MethodSpec.methodBuilder(prefix + "Client").addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(TagUtils.makeAnnotationSpec(tag)).returns(CLIENT)
            .addParameter(dependency(config, "_config", tag.canonicalName()))
            .addParameter(configurer(OPTIONS_BUILDER, "_configurer", tag))
            .addStatement(config.equals(LISTENER_CONFIG)
                ? "return new $T(_config, _configurer, _config.threads() > 0, true)"
                : "return new $T(_config, _configurer)", CLIENT).build());
    }

    private void generatePublisher(TypeElement publisher, TypeElement proxy) throws IOException {
        require(publisher.getKind() == ElementKind.INTERFACE, publisher, "@NatsPublisher requires an interface");
        if (publisher.getInterfaces().size() == 1 && is(publisher.getInterfaces().getFirst(), ATOMIC_PUBLISHER)) {
            generateAtomicPublisher(publisher, proxy);
            return;
        }
        require(publisher.getTypeParameters().isEmpty() && publisher.getInterfaces().isEmpty(), publisher,
            "@NatsPublisher requires a non-generic interface without parent interfaces");
        var first = generated.add("publisher:" + publisher.getQualifiedName());
        if (!first && proxy == null) {
            return;
        }
        var pkg = elements.getPackageOf(publisher).getQualifiedName().toString();
        var implName = ClassName.get(pkg, NameUtils.generatedType(publisher, "Impl"));
        var publisherType = ClassName.get(publisher);
        var impl = CommonUtils.extendsKeepAop(publisher, implName.simpleName())
            .superclass(type("producer.AbstractNatsPublisher"))
            .addOriginatingElement(publisher).addAnnotation(AnnotationUtils.generated(NatsAnnotationProcessor.class));
        var module = module(moduleName(publisher, "PublisherModule"), publisher);
        var prefix = providerPrefix(publisher);
        var path = annotationValue(publisher, PUBLISHER);
        module.addMethod(configMethod(prefix + "_PublisherConfig", PUB_CONFIG, publisherType, path));
        connectionProviders(module, prefix + "_Publisher", PUB_CONFIG, publisherType, path,
            publisher.getQualifiedName().toString(), PUB_TELEMETRY, PUB_TELEMETRY_FACTORY);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
            .addParameter(CLIENT, "_client").addParameter(PUB_CONFIG, "_config").addParameter(PUB_TELEMETRY, "_telemetry")
            .addStatement("super($S, _client, _config, _telemetry, _batch)", path);
        var factory = MethodSpec.methodBuilder(prefix + "_PublisherImpl").addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(publisherType).addParameter(dependency(CLIENT, "_client", publisherType.canonicalName()))
            .addParameter(dependency(PUB_CONFIG, "_config", publisherType.canonicalName()))
            .addParameter(dependency(PUB_TELEMETRY, "_telemetry", publisherType.canonicalName()));
        var factoryArgs = CodeBlock.builder().add("_client, _config, _telemetry");
        int index = 0;
        for (var element : publisher.getEnclosedElements()) {
            if (!(element instanceof ExecutableElement method) || method.getKind() != ElementKind.METHOD
                || method.getModifiers().contains(Modifier.DEFAULT) || method.getModifiers().contains(Modifier.STATIC)
                || method.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            require(method.getTypeParameters().isEmpty(), method, "NATS publisher methods must not be generic");
            var stem = "_method" + index++;
            var request = AnnotationUtils.isAnnotationPresent(method, REQUEST);
            VariableElement data = null, headers = null, options = null, callback = null, timeout = null;
            for (var parameter : method.getParameters()) {
                if (is(parameter.asType(), HEADERS)) {
                    require(headers == null, parameter, "Duplicate NATS Headers parameter");
                    headers = parameter;
                } else if (is(parameter.asType(), OPTIONS)) {
                    require(options == null, parameter, "Duplicate NATS PublishOptions parameter");
                    options = parameter;
                } else if (is(parameter.asType(), CALLBACK)) {
                    require(callback == null, parameter, "Duplicate NATS callback parameter");
                    callback = parameter;
                } else if (is(parameter.asType(), DURATION) && request) {
                    require(timeout == null, parameter, "Duplicate request timeout");
                    timeout = parameter;
                } else {
                    require(data == null, parameter, "NATS publisher expects one payload or record parameter");
                    data = parameter;
                }
            }
            require(data != null, method, "NATS publisher requires a payload, NatsProducerMessage<T> or Message");
            var rawMessage = is(data.asType(), MESSAGE);
            var producerMessage = is(data.asType(), PRODUCER_MESSAGE);
            var subjectAnnotation = AnnotationUtils.findAnnotation(method, SUBJECT);
            require(rawMessage || producerMessage || subjectAnnotation != null, method, "Typed NATS payload requires @NatsPublisher.Subject");
            require(!(rawMessage || producerMessage) || (subjectAnnotation == null && headers == null), method,
                "NATS records carry their own subject and headers; remove @Subject/Headers parameter");
            require(!request || (callback == null && options == null), method, "NATS request cannot use PublishOptions or publish callback");
            var generatedMethod = CommonUtils.overridingKeepAop(method);
            CodeBlock message;
            if (rawMessage) {
                generatedMethod.addStatement("var _observation = $L($L.getSubject())", request ? "telemetry().observeRequest" : "observeSend", data);
                generatedMethod.beginControlFlow("try");
                message = CodeBlock.of("$L", data);
            } else {
                var payload = producerMessage ? argument(data.asType(), data) : data.asType();
                var serializerType = ParameterizedTypeName.get(SERIALIZER, boxed(payload));
                var serializer = stem + "Serializer";
                impl.addField(serializerType, serializer, Modifier.PRIVATE, Modifier.FINAL);
                constructor.addParameter(serializerType, serializer).addStatement("this.$N = $N", serializer, serializer);
                factory.addParameter(mapperDependency(data, SERIALIZER, serializerType, serializer, tag(data, payload)));
                factoryArgs.add(", $N", serializer);
                CodeBlock subject;
                CodeBlock body;
                CodeBlock reply;
                if (producerMessage) {
                    subject = CodeBlock.of("$L.subject()", data);
                    body = CodeBlock.of("$L.value()", data);
                    reply = CodeBlock.of("$L.replyTo()", data);
                    generatedMethod.addStatement("var _headers = $L.headers() == null ? new $T() : new $T($L.headers())", data, HEADERS, HEADERS, data);
                } else {
                    var subjectType = ClassName.get(pkg, moduleName(publisher, "PublisherModule")).nestedClass("Subject" + (index - 1));
                    module.addType(TypeSpec.classBuilder(subjectType.simpleName()).addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).build());
                    module.addMethod(configMethod(prefix + stem + "SubjectConfig", SUBJECT_CONFIG, subjectType,
                        annotationValue(method, SUBJECT).startsWith(".") ? path + annotationValue(method, SUBJECT) : annotationValue(method, SUBJECT)));
                    var subjectField = stem + "Subject";
                    impl.addField(SUBJECT_CONFIG, subjectField, Modifier.PRIVATE, Modifier.FINAL);
                    constructor.addParameter(SUBJECT_CONFIG, subjectField).addStatement("this.$N = $N", subjectField, subjectField);
                    factory.addParameter(dependency(SUBJECT_CONFIG, subjectField, subjectType.canonicalName()));
                    factoryArgs.add(", $N", subjectField);
                    subject = CodeBlock.of("this.$N.subject()", subjectField);
                    body = CodeBlock.of("$L", data);
                    reply = CodeBlock.of("null");
                    if (headers == null) {
                        generatedMethod.addStatement("var _headers = new $T()", HEADERS);
                    } else {
                        generatedMethod.addStatement("var _headers = $L == null ? new $T() : new $T($L)", headers, HEADERS, HEADERS, headers);
                    }
                }
                generatedMethod.addStatement("var _observation = $L($L)", request ? "telemetry().observeRequest" : "observeSend", subject);
                generatedMethod.beginControlFlow("try");
                generatedMethod.addStatement("_observation.observeData($L)", body);
                generatedMethod.addStatement("var _message = serializeMessage($L, $L, _headers, $L, this.$N, _observation)", subject, reply, body, serializer);
                message = CodeBlock.of("_message");
            }
            var returnType = method.getReturnType();
            var async = is(returnType, FUTURE) || is(returnType, STAGE);
            var resultType = async ? argument(returnType, method) : returnType;
            if (request) {
                require(resultType.getKind() != TypeKind.VOID && !is(resultType, VOID), method, "NATS request requires a response return type");
                CodeBlock decoder;
                if (is(resultType, MESSAGE)) {
                    decoder = CodeBlock.of("_reply -> _reply");
                } else {
                    var decoderType = ParameterizedTypeName.get(DESERIALIZER, boxed(resultType));
                    var decoderName = stem + "Deserializer";
                    impl.addField(decoderType, decoderName, Modifier.PRIVATE, Modifier.FINAL);
                    constructor.addParameter(decoderType, decoderName).addStatement("this.$N = $N", decoderName, decoderName);
                    var returnTag = TagUtils.parseTagValue(method);
                    if (returnTag == null) {
                        returnTag = TagUtils.parseTagValue(resultType);
                    }
                    factory.addParameter(mapperDependency(method, DESERIALIZER, decoderType, decoderName, returnTag));
                    factoryArgs.add(", $N", decoderName);
                    decoder = CodeBlock.of("this.$N", decoderName);
                }
                generatedMethod.addStatement("return $L($L, $L, $L, _observation)", async ? "requestAsync" : "request", message, decoder,
                    timeout == null ? CodeBlock.of("null") : CodeBlock.of("$L", timeout));
            } else {
                var publishOptions = options == null ? CodeBlock.of("null") : CodeBlock.of("$L", options);
                if (callback != null) {
                    require(returnType.getKind() == TypeKind.VOID, method, "NATS callback publisher must return void");
                    generatedMethod.addStatement("publishCallback($L, $L, $L, _observation)", message, publishOptions, callback);
                } else if (async) {
                    require(is(resultType, ACK) || is(resultType, VOID), method, "Async NATS publish returns CompletableFuture<PublishAck/Void> or CompletionStage<PublishAck/Void>");
                    generatedMethod.addStatement("return $L($L, $L, _observation)", is(resultType, ACK) ? "publishAsync" : "publishCompletionAsync", message, publishOptions);
                } else if (is(returnType, ACK)) {
                    generatedMethod.addStatement("return publishAcknowledged($L, $L, _observation)", message, publishOptions);
                } else {
                    require(returnType.getKind() == TypeKind.VOID, method, "NATS publish returns void, PublishAck or a future; annotate request/reply with @Request");
                    generatedMethod.addStatement("publish($L, $L, _observation)", message, publishOptions);
                }
            }
            generatedMethod.nextControlFlow("catch (RuntimeException | Error _error)")
                .addStatement("_observation.end(_error)").addStatement("throw _error").endControlFlow();
            impl.addMethod(generatedMethod.build());
        }
        constructor.addParameter(ParameterSpec.builder(BATCH_SINK, "_batch")
            .addAnnotation(ClassName.get("org.jspecify.annotations", "Nullable")).build());
        impl.addMethod(constructor.build());
        var batchArgs = CodeBlock.builder().add("$L, _batch", factoryArgs.build());
        factoryArgs.add(", null");
        if (proxy != null) {
            var constructors = CommonUtils.findConstructors(proxy, modifiers -> modifiers.contains(Modifier.PUBLIC));
            require(constructors.size() == 1, proxy, "NATS publisher AOP proxy requires one public constructor");
            var parameters = constructors.getFirst().getParameters();
            for (int i = constructor.build().parameters().size(); i < parameters.size(); i++) {
                var parameter = parameters.get(i);
                var parameterSpec = ParameterSpec.builder(TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
                parameter.getAnnotationMirrors().forEach(annotation -> parameterSpec.addAnnotation(AnnotationSpec.get(annotation)));
                factory.addParameter(parameterSpec.build());
                factoryArgs.add(", $L", parameter);
                batchArgs.add(", $L", parameter);
            }
        }
        factory.addStatement("return new $T($L)", proxy == null ? implName : ClassName.get(proxy), factoryArgs.build());
        module.addMethod(factory.build());
        module.addMethod(MethodSpec.methodBuilder(prefix + "_AtomicPublisherFactory").addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(ParameterizedTypeName.get(FUNCTION, BATCH_SINK, publisherType)).addParameters(factory.build().parameters())
            .addStatement("return _batch -> new $T($L)", proxy == null ? implName : ClassName.get(proxy), batchArgs.build()).build());
        if (first) {
            write(pkg, impl.build());
        }
        if (proxy != null || !CommonUtils.hasAopAnnotations(publisher)) {
            if (generated.add("publisher-module:" + publisher.getQualifiedName())) {
                write(pkg, module.build());
            }
        }
    }

    private void generateAtomicPublisher(TypeElement owner, TypeElement proxy) throws IOException {
        require(owner.getTypeParameters().isEmpty(), owner, "NATS atomic publisher must not be generic");
        var payload = argument(owner.getInterfaces().getFirst(), owner);
        require(payload instanceof DeclaredType, owner, "AtomicBatchPublisher requires a concrete @NatsPublisher interface");
        var publisher = (TypeElement) ((DeclaredType) payload).asElement();
        require(AnnotationUtils.isAnnotationPresent(publisher, PUBLISHER) && publisher.getInterfaces().isEmpty(), owner,
            "AtomicBatchPublisher requires a regular @NatsPublisher interface");
        for (var element : owner.getEnclosedElements()) {
            if (element instanceof ExecutableElement method && method.getKind() == ElementKind.METHOD) {
                require(!method.getModifiers().contains(Modifier.ABSTRACT), method,
                    "NATS atomic publisher must inherit the AtomicBatchPublisher contract without additional abstract methods");
            }
        }
        for (var element : publisher.getEnclosedElements()) {
            if (!(element instanceof ExecutableElement method) || method.getKind() != ElementKind.METHOD
                || !method.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }
            require(method.getReturnType().getKind() == TypeKind.VOID && !AnnotationUtils.isAnnotationPresent(method, REQUEST)
                    && method.getParameters().stream().noneMatch(param -> is(param.asType(), CALLBACK) || is(param.asType(), OPTIONS)), method,
                "Atomic batch publisher methods must return void without @Request, callbacks or PublishOptions; use Headers for constraints");
        }
        var first = generated.add("publisher:" + owner.getQualifiedName());
        if (!first && proxy == null) {
            return;
        }
        var pkg = elements.getPackageOf(owner).getQualifiedName().toString();
        var ownerType = ClassName.get(owner);
        var publisherType = ClassName.get(publisher);
        var implType = ClassName.get(pkg, NameUtils.generatedType(owner, "Impl"));
        var factoryType = ParameterizedTypeName.get(FUNCTION, BATCH_SINK, publisherType);
        var impl = CommonUtils.extendsKeepAop(owner, implType.simpleName())
            .superclass(ParameterizedTypeName.get(type("producer.AtomicBatchPublisherImpl"), publisherType))
            .addOriginatingElement(owner).addAnnotation(AnnotationUtils.generated(NatsAnnotationProcessor.class));
        var ctor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
            .addParameter(CLIENT, "_client").addParameter(PUB_CONFIG, "_publisherConfig").addParameter(ATOMIC_CONFIG, "_config")
            .addParameter(PUB_TELEMETRY, "_telemetry").addParameter(factoryType, "_factory")
            .addStatement("super(_client, _publisherConfig, _config, _telemetry, _factory)");
        impl.addMethod(ctor.build());
        var prefix = providerPrefix(owner);
        var module = module(moduleName(owner, "PublisherModule"), owner)
            .addMethod(configMethod(prefix + "_AtomicBatchConfig", ATOMIC_CONFIG, ownerType, annotationValue(owner, PUBLISHER)));
        var factory = MethodSpec.methodBuilder(prefix + "_AtomicPublisher").addModifiers(Modifier.PUBLIC, Modifier.DEFAULT).returns(ownerType)
            .addParameter(dependency(CLIENT, "_client", publisherType.canonicalName()))
            .addParameter(dependency(PUB_CONFIG, "_publisherConfig", publisherType.canonicalName()))
            .addParameter(dependency(ATOMIC_CONFIG, "_config", ownerType.canonicalName()))
            .addParameter(dependency(PUB_TELEMETRY, "_telemetry", publisherType.canonicalName()))
            .addParameter(factoryType, "_factory");
        var args = CodeBlock.builder().add("_client, _publisherConfig, _config, _telemetry, _factory");
        if (proxy != null) {
            var constructors = CommonUtils.findConstructors(proxy, modifiers -> modifiers.contains(Modifier.PUBLIC));
            require(constructors.size() == 1, proxy, "NATS atomic publisher AOP proxy requires one public constructor");
            var parameters = constructors.getFirst().getParameters();
            for (int i = ctor.build().parameters().size(); i < parameters.size(); i++) {
                var parameter = parameters.get(i);
                var spec = ParameterSpec.builder(TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
                parameter.getAnnotationMirrors().forEach(annotation -> spec.addAnnotation(AnnotationSpec.get(annotation)));
                factory.addParameter(spec.build());
                args.add(", $L", parameter);
            }
        }
        module.addMethod(factory.addStatement("return new $T($L)", proxy == null ? implType : ClassName.get(proxy), args.build()).build());
        if (first) {
            write(pkg, impl.build());
        }
        if (proxy != null || !CommonUtils.hasAopAnnotations(owner)) {
            if (generated.add("publisher-module:" + owner.getQualifiedName())) {
                write(pkg, module.build());
            }
        }
    }

    private void generateListeners(TypeElement controller) throws IOException {
        require(controller.getTypeParameters().isEmpty(), controller, "NATS listener controller must not be generic");
        if (!generated.add("listener:" + controller.getQualifiedName())) {
            return;
        }
        var pkg = elements.getPackageOf(controller).getQualifiedName().toString();
        var moduleName = ClassName.get(pkg, moduleName(controller, "NatsListenerModule"));
        var module = module(moduleName.simpleName(), controller);
        var prefix = providerPrefix(controller) + "_";
        var names = new HashSet<String>();
        for (var element : controller.getEnclosedElements()) {
            if (!(element instanceof ExecutableElement method) || !AnnotationUtils.isAnnotationPresent(method, LISTENER)) {
                continue;
            }
            var methodName = method.getSimpleName().toString();
            require(names.add(methodName), method, "Overloaded @NatsListener methods require distinct names");
            require(!method.getModifiers().contains(Modifier.PRIVATE) && !method.getModifiers().contains(Modifier.STATIC)
                && method.getTypeParameters().isEmpty(), method, "NATS listeners must be accessible, non-static and non-generic");
            var annotation = AnnotationUtils.findAnnotation(method, LISTENER);
            TypeMirror userTag = AnnotationUtils.parseAnnotationValue(elements, annotation, "tag");
            ClassName listenerTag;
            if (userTag == null || userTag.toString().equals(CommonClassNames.tag.canonicalName())) {
                var tagName = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
                listenerTag = moduleName.nestedClass(tagName);
                module.addType(TypeSpec.classBuilder(tagName).addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).build());
            } else {
                listenerTag = ClassName.get((TypeElement) types.asElement(userTag));
            }
            module.addMethod(configMethod(prefix + methodName + "Config", LISTENER_CONFIG, listenerTag, annotationValue(method, LISTENER)));
            connectionProviders(module, prefix + methodName, LISTENER_CONFIG, listenerTag, annotationValue(method, LISTENER),
                controller.getQualifiedName() + "." + methodName, CON_TELEMETRY, CON_TELEMETRY_FACTORY);
            VariableElement data = null;
            boolean batch = false, record = false;
            boolean catchesDeserialization = false;
            for (var parameter : method.getParameters()) {
                if (is(parameter.asType(), SERIALIZATION_EXCEPTION) || is(parameter.asType(), EXCEPTION)) {
                    catchesDeserialization = true;
                    continue;
                }
                if (is(parameter.asType(), MESSAGE) || is(parameter.asType(), HEADERS) || is(parameter.asType(), CONNECTION)
                    || is(parameter.asType(), POLL_OBSERVATION)) {
                    continue;
                }
                require(data == null, parameter, "NATS listener accepts one typed payload, NatsMessage<T> or NatsMessages<T>");
                data = parameter;
                record = is(parameter.asType(), CONSUMER_MESSAGE);
                batch = is(parameter.asType(), CONSUMER_MESSAGES);
            }
            if (batch) {
                for (var parameter : method.getParameters()) {
                    require(parameter == data || is(parameter.asType(), CONNECTION)
                            || is(parameter.asType(), POLL_OBSERVATION), parameter,
                        "Batch NATS listener accepts NatsMessages<T>, optional Connection and NatsConsumerPollObservation");
                }
            }
            require(!catchesDeserialization || data == null || record || !data.asType().getKind().isPrimitive(), method,
                "NATS listener payload must be nullable/boxed when binding a deserialization exception");
            TypeName payload = data == null ? TypeName.get(byte[].class)
                : boxed(record || batch ? argument(data.asType(), data) : data.asType());
            var decoderType = ParameterizedTypeName.get(DESERIALIZER, payload);
            var factory = MethodSpec.methodBuilder(prefix + methodName + "Container").addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(CommonClassNames.root)
                .returns(ParameterizedTypeName.get(CONTAINER, payload)).addAnnotation(TagUtils.makeAnnotationSpec(listenerTag))
                .addParameter(ClassName.get(controller), "_controller").addParameter(dependency(CLIENT, "_client", listenerTag.canonicalName()))
                .addParameter(dependency(LISTENER_CONFIG, "_config", listenerTag.canonicalName()))
                .addParameter(dependency(CON_TELEMETRY, "_telemetry", listenerTag.canonicalName()))
                .addParameter(configurer(CONSUMER_BUILDER, "_customizer", listenerTag));
            if (data != null) {
                factory.addParameter(mapperDependency(data, DESERIALIZER, decoderType, "_deserializer",
                    tag(data, record || batch ? argument(data.asType(), data) : data.asType())));
            }
            var arguments = CodeBlock.builder();
            for (int i = 0; i < method.getParameters().size(); i++) {
                if (i > 0) {
                    arguments.add(", ");
                }
                var parameter = method.getParameters().get(i);
                if (parameter == data) {
                    arguments.add("$L", record || batch ? "_record" : catchesDeserialization ? "_value" : "_record.value()");
                } else if (is(parameter.asType(), MESSAGE)) {
                    arguments.add("_record.message()");
                } else if (is(parameter.asType(), HEADERS)) {
                    arguments.add("_record.headers()");
                } else if (is(parameter.asType(), POLL_OBSERVATION)) {
                    arguments.add("_poll");
                } else if (is(parameter.asType(), SERIALIZATION_EXCEPTION) || is(parameter.asType(), EXCEPTION)) {
                    arguments.add("_error");
                } else {
                    arguments.add("_client.connection()");
                }
            }
            var handlerType = ParameterizedTypeName.get(type(batch ? "consumer.NatsMessagesHandler" : "consumer.NatsMessageHandler"), payload);
            var handler = CodeBlock.builder().add("($T) (_poll, _record) -> {\n", handlerType);
            if (catchesDeserialization) {
                handler.add("  $T _error = null;\n", SERIALIZATION_EXCEPTION);
                if (data != null && !record) {
                    handler.add("  $T _value = null;\n", payload);
                }
                handler.add("  try {\n");
                if (data != null && !record) {
                    handler.add("    _value = _record.value();\n");
                } else {
                    handler.add("    _record.value();\n");
                }
                handler.add("  } catch ($T _failure) {\n    _error = _failure;\n  }\n", SERIALIZATION_EXCEPTION);
            }
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                handler.add("  _controller.$N($L);\n", methodName, arguments.build());
            } else {
                require(!batch && !is(method.getReturnType(), FUTURE) && !is(method.getReturnType(), STAGE), method,
                    "NATS listener replies require synchronous per-record return type");
                var serializerType = ParameterizedTypeName.get(SERIALIZER, boxed(method.getReturnType()));
                var responseTag = TagUtils.parseTagValue(method);
                if (responseTag == null) {
                    responseTag = TagUtils.parseTagValue(method.getReturnType());
                }
                factory.addParameter(mapperDependency(method, SERIALIZER, serializerType, "_serializer", responseTag));
                handler.add("  var _response = _controller.$N($L);\n", methodName, arguments.build())
                    .add("  $T.reply(_record, _client, _response, _serializer);\n", type("consumer.NatsReplies"));
            }
            handler.add("}");
            factory.addStatement("return new $T<>($S, _client, _config, $L, $L, _customizer, _telemetry)", CONTAINER,
                annotationValue(method, LISTENER), data == null ? CodeBlock.of("_message -> _message.getData()") : CodeBlock.of("_deserializer"),
                handler.build());
            module.addMethod(factory.build());
        }
        write(pkg, module.build());
    }
}
