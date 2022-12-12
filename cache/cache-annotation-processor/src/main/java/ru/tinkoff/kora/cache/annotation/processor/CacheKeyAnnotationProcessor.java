package ru.tinkoff.kora.cache.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.cache.annotation.*;
import ru.tinkoff.kora.common.annotation.Generated;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.cache.annotation.processor.CacheOperationManager.getCacheOperation;

public class CacheKeyAnnotationProcessor extends AbstractKoraProcessor {

    private static final Set<String> CACHE_KEY_GENERATED = new HashSet<>();

    private static Set<Class<? extends Annotation>> getSupportedAnnotations() {
        return Set.of(Cacheable.class, Cacheables.class,
            CachePut.class, CachePuts.class,
            CacheInvalidate.class, CacheInvalidates.class);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        CACHE_KEY_GENERATED.clear();
        CacheOperationManager.reset();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return getSupportedAnnotations().stream()
            .map(Class::getCanonicalName)
            .collect(Collectors.toSet());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final List<ExecutableElement> annotatedElements = getAnnotatedElements(roundEnv);
        for (ExecutableElement method : annotatedElements) {
            try {
                final String packageName = getPackage(method);
                final CacheOperation operation = getCacheOperation(method, processingEnv);

                final List<? extends Annotation> cacheAnnotations = getSupportedAnnotations().stream()
                    .map(method::getAnnotation)
                    .filter(Objects::nonNull)
                    .toList();

                final List<String> annotationNames = cacheAnnotations.stream().map(a -> a.annotationType().getName()).toList();
                if (cacheAnnotations.size() > 1) {
                    throw new IllegalStateException("Multiple annotations found " + annotationNames
                        + ", when expected only one of them for " + operation.meta().origin());
                }

                if (operation.meta().type().equals(CacheMeta.Type.GET) || operation.meta().type().equals(CacheMeta.Type.PUT)) {
                    if (MethodUtils.isVoid(method)) {
                        throw new IllegalStateException(annotationNames + " annotation can't return Void type, but was for " + operation.meta().origin());
                    }

                    if (MethodUtils.isMono(method, processingEnv)) {
                        final DeclaredType returnType = (DeclaredType) method.getReturnType();
                        if (returnType.getTypeArguments().stream().anyMatch(MethodUtils::isVoid)) {
                            throw new IllegalStateException(annotationNames + " annotation can't return " + returnType + " with Void type erasure , but was for " + operation.meta().origin());
                        }
                    } else if (MethodUtils.isFuture(method, processingEnv)) {
                        throw new IllegalArgumentException(annotationNames + " annotation doesn't support return type " + method.getReturnType() + " in " + operation.meta().origin());
                    } else if (MethodUtils.isFlux(method, processingEnv)) {
                        throw new IllegalArgumentException(annotationNames + " annotation doesn't support return type " + method.getReturnType() + " in " + operation.meta().origin());
                    }
                }

                if (CACHE_KEY_GENERATED.contains(operation.key().canonicalName())) {
                    continue;
                }

                final String methodParameters = String.join(", ", operation.meta().getParametersNames(method));
                final List<VariableElement> parameters = operation.meta().getParameters(method);
                final String recordArguments = parameters.stream()
                    .map(p -> p.asType().toString() + " " + p.getSimpleName().toString())
                    .collect(Collectors.joining(", "));

                final String toStringParameters;
                if (parameters.stream().anyMatch(p -> p.asType().toString().equals(String.class.getCanonicalName()))) {
                    toStringParameters = parameters.stream()
                        .map(a -> a.getSimpleName().toString())
                        .collect(Collectors.joining(" + \"-\" + "));
                } else {
                    toStringParameters = parameters.stream()
                        .map(a -> "String.valueOf(" + a.getSimpleName().toString() + ")")
                        .collect(Collectors.joining(" + \"-\" + "));
                }

                final String template = """
                    package %s;
                                        
                    import java.util.List;
                    import java.util.Arrays;
                    import java.lang.Object;
                    import ru.tinkoff.kora.cache.CacheKey;
                    import ru.tinkoff.kora.common.annotation.Generated;
                                        
                    @%s("%s")
                    public record %s(%s) implements CacheKey {
                                        
                      @Override
                      public List<Object> values() {
                        return Arrays.asList(%s);
                      }
                      
                      @Override
                      public String toString() {
                          return %s;
                      }
                    }""";

                final String record = String.format(template, packageName, Generated.class.getSimpleName(), this.getClass().getCanonicalName(),
                    operation.key().simpleName(), recordArguments, methodParameters, toStringParameters);
                writeTo(packageName, operation.key().simpleName(), record, processingEnv.getFiler());
                CACHE_KEY_GENERATED.add(operation.key().canonicalName());
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), method);
                e.printStackTrace();
                return true;
            } catch (IllegalArgumentException | IllegalStateException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), method);
                return true;
            }
        }

        return true;
    }

    public void writeTo(String packageName, String recordName, String recordClass, Filer filer) throws IOException {
        final String fileName = packageName.isEmpty()
            ? recordName
            : packageName + "." + recordName;

        final JavaFileObject filerSourceFile = filer.createSourceFile(fileName, new Element[1]);
        try (Writer writer = filerSourceFile.openWriter()) {
            writer.write(recordClass);
        } catch (Exception e) {
            try {
                filerSourceFile.delete();
            } catch (Exception ignored) {
                // do nothing
            }
            throw e;
        }
    }

    private static List<ExecutableElement> getAnnotatedElements(RoundEnvironment roundEnv) {
        return getSupportedAnnotations().stream()
            .flatMap(a -> roundEnv.getElementsAnnotatedWith(a).stream())
            .filter(a -> a instanceof ExecutableElement)
            .map(a -> ((ExecutableElement) a))
            .toList();
    }

    private String getPackage(Element element) {
        return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    }
}
