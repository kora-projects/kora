package io.koraframework.avro.annotation.processor.extension;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.avro.annotation.processor.AvroTypes;
import io.koraframework.avro.annotation.processor.AvroUtils;
import io.koraframework.avro.annotation.processor.reader.AvroReaderGenerator;
import io.koraframework.avro.annotation.processor.writer.AvroWriterGenerator;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionResult;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AvroExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;
    private final TypeMirror writerErasure;
    private final TypeMirror readerErasure;
    private final AvroReaderGenerator readerGenerator;
    private final AvroWriterGenerator writerGenerator;
    private final Set<String> generatedMappers = new HashSet<>();

    public AvroExtension(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.writerErasure = this.types.erasure(this.elements.getTypeElement(AvroTypes.writer.canonicalName()).asType());
        this.readerErasure = this.types.erasure(this.elements.getTypeElement(AvroTypes.reader.canonicalName()).asType());
        this.readerGenerator = new AvroReaderGenerator(processingEnv);
        this.writerGenerator = new AvroWriterGenerator(processingEnv);
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, @Nullable String tag) {
        if (!isBinary(tag)) {
            return null;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return null;
        }

        var erasure = this.types.erasure(typeMirror);
        if (this.types.isSameType(erasure, this.writerErasure)) {
            var targetElement = dependencyTarget(typeMirror);
            if (targetElement == null) {
                throw new ProcessingErrorException("AvroWriter<T> can only be created for concrete org.apache.avro.specific.SpecificRecord types", this.types.asElement(typeMirror));
            }
            return generateMapper(targetElement, AvroUtils.writerName(targetElement), typeMirror, tag, () -> this.writerGenerator.generate(targetElement));
        }

        if (this.types.isSameType(erasure, this.readerErasure)) {
            var targetElement = dependencyTarget(typeMirror);
            if (targetElement == null) {
                throw new ProcessingErrorException("AvroReader<T> can only be created for concrete org.apache.avro.specific.SpecificRecord types", this.types.asElement(typeMirror));
            }
            return generateMapper(targetElement, AvroUtils.readerName(targetElement), typeMirror, tag, () -> this.readerGenerator.generate(targetElement));
        }

        return null;
    }

    @Nullable
    private TypeElement dependencyTarget(TypeMirror typeMirror) {
        var declaredType = (DeclaredType) typeMirror;
        var targetType = declaredType.getTypeArguments().get(0);
        if (targetType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var targetElement = this.types.asElement(targetType);
        if (!(targetElement instanceof TypeElement typeElement)
            || targetElement.getKind() == ElementKind.ENUM
            || targetElement.getKind().isInterface()
            || targetElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return null;
        }
        return typeElement;
    }

    private KoraExtensionDependencyGenerator generateMapper(
        TypeElement targetElement,
        String mapperName,
        TypeMirror requestedType,
        @Nullable String tag,
        Runnable generate
    ) {
        return () -> {
            var packageName = AvroUtils.classPackage(this.elements, targetElement);
            var mapperCanonicalName = packageName + "." + mapperName;
            if (this.elements.getTypeElement(mapperCanonicalName) == null && this.generatedMappers.add(mapperCanonicalName)) {
                generate.run();
            }

            return new ExtensionResult.CodeBlockResult(
                targetElement,
                params -> CodeBlock.of("new $T()", ClassName.get(packageName, mapperName)),
                requestedType,
                tag,
                List.of(),
                List.of()
            );
        };
    }

    private boolean isBinary(@Nullable String tag) {
        return tag == null || tag.equals(AvroTypes.avro.canonicalName());
    }
}
