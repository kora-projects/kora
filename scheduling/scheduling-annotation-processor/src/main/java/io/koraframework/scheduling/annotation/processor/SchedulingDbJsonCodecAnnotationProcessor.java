package io.koraframework.scheduling.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SchedulingDbJsonCodecAnnotationProcessor extends AbstractKoraProcessor {

    private static final ClassName SCHEDULING_DB_JSON_CODEC = ClassName.get("io.koraframework.scheduling.db.annotation", "SchedulingDbJsonCodec");
    private static final ClassName SCHEDULING_DB_CODEC = ClassName.get("io.koraframework.scheduling.db", "SchedulingDbCodec");
    private static final ClassName JSON = ClassName.get("io.koraframework.json.common.annotation", "Json");
    private static final ClassName JSON_READER = ClassName.get("io.koraframework.json.common", "JsonReader");
    private static final ClassName JSON_WRITER = ClassName.get("io.koraframework.json.common", "JsonWriter");

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(SCHEDULING_DB_JSON_CODEC);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var codecElements = annotatedElements.getOrDefault(SCHEDULING_DB_JSON_CODEC, List.of());
        for (var annotated : codecElements) {
            if (!(annotated.element() instanceof TypeElement type)) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "@SchedulingDbJsonCodec can be applied only to types", annotated.element());
                continue;
            }
            this.generateJsonCodecModule(type);
        }
    }

    private void generateJsonCodecModule(TypeElement type) {
        if (!type.getKind().isClass() && !type.getKind().isInterface()) {
            this.messager.printMessage(Diagnostic.Kind.ERROR, "@SchedulingDbJsonCodec can be applied only to classes and interfaces", type);
            return;
        }
        if (!AnnotationUtils.isAnnotationPresent(type, JSON)) {
            this.messager.printMessage(Diagnostic.Kind.ERROR, "@SchedulingDbJsonCodec requires @Json on the same type", type);
            return;
        }

        var typeName = TypeName.get(type.asType());
        var codecType = ParameterizedTypeName.get(SCHEDULING_DB_CODEC, typeName);
        var typeRefType = ParameterizedTypeName.get(CommonClassNames.typeRef, typeName);
        var readerType = ParameterizedTypeName.get(JSON_READER, typeName);
        var writerType = ParameterizedTypeName.get(JSON_WRITER, typeName);
        var methodName = Character.toLowerCase(type.getSimpleName().charAt(0)) + type.getSimpleName().toString().substring(1) + "SchedulingDbCodec";

        var codec = TypeSpec.anonymousClassBuilder("")
            .addSuperinterface(codecType)
            .addMethod(MethodSpec.methodBuilder("typeRef")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(typeRefType)
                .addStatement("return typeRef")
                .build())
            .addMethod(MethodSpec.methodBuilder("serialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(byte[].class))
                .addParameter(typeName, "value")
                .addStatement("return writer.toByteArray(value)")
                .build())
            .addMethod(MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(typeName)
                .addParameter(TypeName.get(byte[].class), "bytes")
                .addStatement("return reader.read(bytes)")
                .build())
            .build();

        var module = TypeSpec.interfaceBuilder("$" + type.getSimpleName() + "_SchedulingDbJsonCodecModule")
            .addOriginatingElement(type)
            .addAnnotation(AnnotationUtils.generated(SchedulingDbJsonCodecAnnotationProcessor.class))
            .addAnnotation(CommonClassNames.module)
            .addModifiers(Modifier.PUBLIC)
            .addMethod(MethodSpec.methodBuilder(methodName)
                .addAnnotation(CommonClassNames.defaultComponent)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .returns(codecType)
                .addParameter(typeRefType, "typeRef")
                .addParameter(readerType, "reader")
                .addParameter(writerType, "writer")
                .addStatement("return $L", codec)
                .build())
            .build();

        var packageName = elements.getPackageOf(type).getQualifiedName().toString();
        var moduleFile = JavaFile.builder(packageName, module);
        CommonUtils.safeWriteTo(this.processingEnv, moduleFile.build());
    }
}
