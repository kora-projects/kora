package ru.tinkoff.kora.json.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class GeneratorModuleAnnotationProcessor extends AbstractKoraProcessor {
    private JsonProcessor processor;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(CommonClassNames.generatorModule.canonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.processor = new JsonProcessor(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            var annotationTypeName = ClassName.get(annotation);
            if (annotationTypeName.equals(CommonClassNames.generatorModule)) {
                var modules = roundEnv.getElementsAnnotatedWith(annotation);
                for (var module : modules) {
                    try {
                        this.generateModule(module);
                    } catch (ProcessingErrorException e) {
                        e.printError(this.processingEnv);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return false;
    }

    private void generateModule(Element module) throws Exception {
        var generatorModuleAnnotation = AnnotationUtils.findAnnotation(module, CommonClassNames.generatorModule);
        var generatorModuleGenerator = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(generatorModuleAnnotation, "generator");
        if (generatorModuleGenerator == null || !TypeName.get(generatorModuleGenerator).equals(JsonTypes.json)) {
            // not a json generator
            return;
        }
        var typesToProcess = AnnotationUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(generatorModuleAnnotation, "types");
        if (typesToProcess == null || typesToProcess.isEmpty()) {
            return;
        }
        var packageElement = elements.getPackageOf(module);
        var packageName = packageElement.getQualifiedName().toString();
        var builder = TypeSpec.interfaceBuilder(NameUtils.generatedType(module, CommonClassNames.generatorModule))
            .addOriginatingElement(module)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addAnnotation(AnnotationUtils.generated(this.getClass()));
        var error = false;
        for (int i = 0; i < typesToProcess.size(); i++) {
            var jsonType = (DeclaredType) typesToProcess.get(i);
            var jsonTypeElement = (TypeElement) jsonType.asElement();
            try {
                var expectedReaderName = ClassName.get(packageName, NameUtils.generatedType(module, CommonClassNames.generatorModule) + "_" + i + "_JsonReader");
                var readerMethod = this.generateMapper(module, "reader" + i, expectedReaderName, jsonTypeElement, this.processor::generateReader);
                builder.addMethod(readerMethod);

                var expectedWriterName = ClassName.get(packageName, NameUtils.generatedType(module, CommonClassNames.generatorModule) + "_" + i + "_JsonWriter");
                var writerMethod = this.generateMapper(module, "writer" + i, expectedWriterName, jsonTypeElement, this.processor::generateWriter);
                builder.addMethod(writerMethod);
            } catch (ProcessingErrorException e) {
                error = true;
                @SuppressWarnings("unchecked")
                var annotationValue = (List<AnnotationValue>) generatorModuleAnnotation.getElementValues().entrySet().stream()
                    .filter(k -> k.getKey().getSimpleName().contentEquals("types"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .get()
                    .getValue();
                e.printError(this.processingEnv);
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), module, generatorModuleAnnotation, annotationValue.get(i));
            }
        }
        if (error) {
            return;
        }
        var moduleType = builder.build();
        var javaFile = JavaFile.builder(packageName, moduleType).build();
        javaFile.writeTo(processingEnv.getFiler());
    }

    private MethodSpec generateMapper(Element module, String methodName, ClassName expectedMapperName, TypeElement jsonTypeElement, BiFunction<ClassName, TypeElement, TypeSpec> generator) {
        var packageElement = elements.getPackageOf(module);
        var packageName = packageElement.getQualifiedName().toString();
        var mapper = generator.apply(expectedMapperName, jsonTypeElement);
        var mapperBuilder = mapper.toBuilder();
        mapperBuilder.originatingElements.clear();
        mapperBuilder.addOriginatingElement(module);

        CommonUtils.safeWriteTo(processingEnv, JavaFile.builder(packageName, mapperBuilder.build()).build());
        return this.mapperMethod(methodName, expectedMapperName, mapper);
    }

    private MethodSpec mapperMethod(String methodName, ClassName mapperName, TypeSpec mapper) {
        var readerConstructor = mapper.methodSpecs.stream().filter(m -> m.name.equals("<init>")).findFirst().get();
        return MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameters(readerConstructor.parameters)
            .returns(mapperName)
            .addStatement("return new $T$L($L)", mapperName, mapper.typeVariables.isEmpty() ? "" : "<>", readerConstructor.parameters.stream().map(p -> CodeBlock.of("$N", p.name)).collect(CodeBlock.joining(",")))
            .build();
    }
}
