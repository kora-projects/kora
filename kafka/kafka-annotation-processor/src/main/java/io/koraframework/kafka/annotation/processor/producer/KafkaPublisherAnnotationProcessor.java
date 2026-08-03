package io.koraframework.kafka.annotation.processor.producer;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.kafka.annotation.processor.KafkaClassNames;

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
import java.util.Map;
import java.util.Set;

public class KafkaPublisherAnnotationProcessor extends AbstractKoraProcessor {

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(
            KafkaClassNames.kafkaPublisherAnnotation,
            CommonClassNames.aopProxy
        );
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var producers = annotatedElements.getOrDefault(KafkaClassNames.kafkaPublisherAnnotation, List.of());
        var publisherTransactionalGenerator = new KafkaPublisherTransactionalGenerator(types, elements, processingEnv);
        var publisherGenerator = new KafkaPublisherGenerator(types, elements, processingEnv);
        var aopProxies = getAopProxies(annotatedElements);
        for (var aopProxy : aopProxies) {
            var publishMethods = new ArrayList<ExecutableElement>();
            for (var method : aopProxy.publisher().getEnclosedElements()) {
                if (method.getKind() != ElementKind.METHOD) {
                    continue;
                }
                publishMethods.add((ExecutableElement) method);
            }
            var annotation = AnnotationUtils.findAnnotation(aopProxy.publisher, KafkaClassNames.kafkaPublisherAnnotation);
            publisherGenerator.generatePublisherModule(aopProxy.publisher, publishMethods, annotation, aopProxy.proxy);
        }
        for (var annotated : producers) {
            var producer = annotated.element();
            try {
                if (!(producer instanceof TypeElement typeElement) || typeElement.getKind() != ElementKind.INTERFACE) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, publisherTargetError(producer.toString()), producer);
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
                    this.messager.printMessage(Diagnostic.Kind.ERROR, publisherTypeError(typeElement), producer);
                    continue;
                }
                var supertypeMirror = (DeclaredType) supertypes.get(0);
                if (!(TypeName.get(supertypeMirror) instanceof ParameterizedTypeName supertypeName)) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, publisherTypeError(typeElement), producer);
                    continue;
                }
                if (supertypeName.rawType().equals(KafkaClassNames.transactionalPublisher)) {
                    var publisherTypeMirror = (DeclaredType) supertypeMirror.getTypeArguments().get(0);
                    var publisherTypeElement = (TypeElement) publisherTypeMirror.asElement();
                    var publisherAnnotation = AnnotationUtils.findAnnotation(publisherTypeElement, KafkaClassNames.kafkaPublisherAnnotation);
                    if (publisherAnnotation == null) {
                        this.messager.printMessage(Diagnostic.Kind.ERROR, transactionalPublisherArgumentError(typeElement, publisherTypeElement), producer);
                        continue;
                    }

                    var publisherType = ClassName.get(publisherTypeElement);
                    publisherTransactionalGenerator.generatePublisherTransactionalModule(typeElement, publisherTypeElement, annotation);
                    publisherTransactionalGenerator.generatePublisherTransactionalImpl(typeElement, publisherType, publisherTypeElement);
                } else {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, publisherTypeError(typeElement), producer);
                    continue;
                }
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            } catch (IOException e) {
                throw new IllegalStateException("Kora internal error: failed to generate Kafka publisher for " + producer, e);
            }
        }
    }

    private static String publisherTypeError(TypeElement publisher) {
        return """
            Kafka publisher type is invalid:
              %s

            Problem:
              @KafkaPublisher interface can either extend no interfaces or extend exactly one TransactionalPublisher<T>.

            Hint:
              Extra parent interfaces make it ambiguous which generated publisher contract should be used.

            Fix:
              Remove extra parent interfaces, or make this interface extend only TransactionalPublisher<YourPublisher>.
            """.formatted(publisher.getQualifiedName());
    }

    private static String transactionalPublisherArgumentError(TypeElement publisher, TypeElement publisherArgument) {
        return """
            Kafka transactional publisher type is invalid:
              %s

            Problem:
              TransactionalPublisher argument is not annotated with @KafkaPublisher: %s

            Hint:
              Kora needs to generate the nested publisher before it can generate the transactional wrapper.

            Fix:
              Add @KafkaPublisher to %s, or change the TransactionalPublisher<T> argument to a publisher interface that already has it.
            """.formatted(publisher.getQualifiedName(), publisherArgument.getQualifiedName(), publisherArgument.getQualifiedName());
    }

    private record AopProxy(TypeElement publisher, TypeElement proxy) {}

    private static String publisherTargetError(String publisher) {
        return """
            Kafka publisher type is invalid:
              %s

            Problem:
              @KafkaPublisher can be placed only on interfaces.

            Hint:
              Kora generates an implementation for the publisher interface.

            Fix:
              Move @KafkaPublisher to an interface, then declare publisher methods on that interface.
            """.formatted(publisher);
    }

    private List<AopProxy> getAopProxies(Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var proxies = annotatedElements.getOrDefault(CommonClassNames.aopProxy, List.of());
        var list = new ArrayList<AopProxy>(proxies.size());
        for (var p : proxies) {
            var proxy = (TypeElement) p.element();
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
