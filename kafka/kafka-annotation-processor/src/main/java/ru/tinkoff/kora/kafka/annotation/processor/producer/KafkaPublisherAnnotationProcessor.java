package ru.tinkoff.kora.kafka.annotation.processor.producer;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class KafkaPublisherAnnotationProcessor extends AbstractKoraProcessor {

    private TypeElement kafkaProducerAnnotationElement;
    private TypeElement aopProxyAnnotationElement;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.kafkaProducerAnnotationElement = this.elements.getTypeElement(KafkaClassNames.kafkaPublisherAnnotation.canonicalName());
        this.aopProxyAnnotationElement = this.elements.getTypeElement(CommonClassNames.aopProxy.canonicalName());
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
            KafkaClassNames.kafkaPublisherAnnotation.canonicalName(),
            CommonClassNames.aopProxy.canonicalName()
        );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (this.kafkaProducerAnnotationElement == null) {
            return false;
        }
        var producers = roundEnv.getElementsAnnotatedWith(this.kafkaProducerAnnotationElement);
        var publisherTransactionalGenerator = new KafkaPublisherTransactionalGenerator(types, elements, processingEnv);
        var publisherGenerator = new KafkaPublisherGenerator(types, elements, processingEnv);
        var aopProxies = getAopProxies(roundEnv);
        for (var aopProxy : aopProxies) {
            var publishMethods = new ArrayList<ExecutableElement>();
            for (var method : aopProxy.publisher().getEnclosedElements()) {
                if (method.getKind() != ElementKind.METHOD) {
                    continue;
                }
                publishMethods.add((ExecutableElement) method);
            }
            var annotation = AnnotationUtils.findAnnotation(aopProxy.publisher, KafkaClassNames.kafkaPublisherAnnotation);
            try {
                publisherGenerator.generatePublisherModule(aopProxy.publisher, publishMethods, annotation, aopProxy.proxy);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        for (var producer : producers) {
            try {
                if (!(producer instanceof TypeElement typeElement) || typeElement.getKind() != ElementKind.INTERFACE) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "@KafkaPublisher can be placed only on interfaces extending only TransactionalPublisher or none", producer);
                    continue;
                }
                var annotation = AnnotationUtils.findAnnotation(producer, KafkaClassNames.kafkaPublisherAnnotation);
                var supertypes = typeElement.getInterfaces();
                if (supertypes.isEmpty()) {
                    var publishMethods = new ArrayList<ExecutableElement>();
                    for (var method : typeElement.getEnclosedElements()) {
                        if (method.getKind() != ElementKind.METHOD) {
                            continue;
                        }
                        if (method.getModifiers().contains(Modifier.DEFAULT)) {
                            continue;
                        }
                        publishMethods.add((ExecutableElement) method);
                    }

                    publisherGenerator.generateConfig(typeElement, publishMethods);
                    // we will generate module after aop proxy generated
                    if (!CommonUtils.hasAopAnnotations(typeElement)) {
                        publisherGenerator.generatePublisherModule(typeElement, publishMethods, annotation, null);
                    }
                    publisherGenerator.generatePublisherImplementation(typeElement, publishMethods, annotation);
                    continue;
                }
                if (supertypes.size() != 1) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "@KafkaPublisher can be placed only on interfaces extending only TransactionalPublisher or none", producer);
                    continue;
                }
                var supertypeMirror = (DeclaredType) supertypes.get(0);
                if (!(TypeName.get(supertypeMirror) instanceof ParameterizedTypeName supertypeName)) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "@KafkaPublisher can be placed only on interfaces extending only TransactionalPublisher or none", producer);
                    continue;
                }
                if (supertypeName.rawType.equals(KafkaClassNames.transactionalPublisher)) {
                    var publisherTypeMirror = (DeclaredType) supertypeMirror.getTypeArguments().get(0);
                    var publisherTypeElement = (TypeElement) publisherTypeMirror.asElement();
                    var publisherAnnotation = AnnotationUtils.findAnnotation(publisherTypeElement, KafkaClassNames.kafkaPublisherAnnotation);
                    if (publisherAnnotation == null) {
                        this.messager.printMessage(Diagnostic.Kind.ERROR, "TransactionalPublisher can only have argument types that annotated with @KafkaPublisher too", producer);
                        continue;
                    }

                    var publisherType = ClassName.get(publisherTypeElement);
                    publisherTransactionalGenerator.generatePublisherTransactionalModule(typeElement, publisherTypeElement, annotation);
                    publisherTransactionalGenerator.generatePublisherTransactionalImpl(typeElement, publisherType, publisherTypeElement);
                } else {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "@KafkaPublisher can be placed only on interfaces extending only TransactionalPublisher or none", producer);
                    continue;
                }
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return false;
    }

    private record AopProxy(TypeElement publisher, TypeElement proxy) {}

    private List<AopProxy> getAopProxies(RoundEnvironment roundEnv) {
        var proxies = roundEnv.getElementsAnnotatedWith(aopProxyAnnotationElement);
        var list = new ArrayList<AopProxy>(proxies.size());
        for (var p : proxies) {
            var proxy = (TypeElement) p;
            var proxySupertype = (DeclaredType) proxy.getSuperclass();
            if (proxySupertype == null) continue;
            var proxySupertypeElement = (TypeElement) proxySupertype.asElement();
            for (var pt : proxySupertypeElement.getInterfaces()) {
                var publisherType = (DeclaredType) pt;
                var publisherTypeElement = (TypeElement) publisherType.asElement();
                if (publisherTypeElement.getInterfaces().isEmpty()) {
                    if (AnnotationUtils.isAnnotationPresent(publisherTypeElement, KafkaClassNames.kafkaPublisherAnnotation)) {
                        list.add(new AopProxy(publisherTypeElement, proxy));
                        break;
                    }
                }
            }
        }
        return list;
    }
}
