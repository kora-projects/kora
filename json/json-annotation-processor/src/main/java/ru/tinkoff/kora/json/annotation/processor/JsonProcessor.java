package ru.tinkoff.kora.json.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.SealedTypeUtils;
import ru.tinkoff.kora.json.annotation.processor.reader.EnumReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderTypeMetaParser;
import ru.tinkoff.kora.json.annotation.processor.reader.SealedInterfaceReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.EnumWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.JsonWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.SealedInterfaceWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.WriterTypeMetaParser;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Objects;

public class JsonProcessor {
    private final ProcessingEnvironment processingEnv;
    private final Elements elements;
    private final Types types;
    private final ReaderTypeMetaParser readerTypeMetaParser;
    private final WriterTypeMetaParser writerTypeMetaParser;
    private final JsonWriterGenerator writerGenerator;
    private final JsonReaderGenerator readerGenerator;
    private final SealedInterfaceReaderGenerator sealedReaderGenerator;
    private final SealedInterfaceWriterGenerator sealedWriterGenerator;
    private final EnumReaderGenerator enumReaderGenerator;
    private final EnumWriterGenerator enumWriterGenerator;

    public JsonProcessor(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        var knownTypes = new KnownType();
        this.readerTypeMetaParser = new ReaderTypeMetaParser(this.processingEnv, knownTypes);
        this.writerTypeMetaParser = new WriterTypeMetaParser(processingEnv, knownTypes);
        this.writerGenerator = new JsonWriterGenerator(this.processingEnv);
        this.readerGenerator = new JsonReaderGenerator(this.processingEnv);
        this.sealedReaderGenerator = new SealedInterfaceReaderGenerator(this.processingEnv);
        this.sealedWriterGenerator = new SealedInterfaceWriterGenerator(this.processingEnv);
        this.enumReaderGenerator = new EnumReaderGenerator();
        this.enumWriterGenerator = new EnumWriterGenerator();
    }

    public void generateReader(TypeElement jsonElement) {
        var packageName = elements.getPackageOf(jsonElement).getQualifiedName().toString();
        var className = ClassName.get(packageName, JsonUtils.jsonReaderName(jsonElement));
        var reader = generateReader(className, jsonElement);
        var javaFile = JavaFile.builder(packageName, reader).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    public TypeSpec generateReader(ClassName target, TypeElement jsonElement) {
        if (jsonElement.getKind() == ElementKind.ENUM) {
            return this.enumReaderGenerator.generateForEnum(target, jsonElement);
        }
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            return this.sealedReaderGenerator.generateSealedReader(target, jsonElement);
        }
        var jsonElementType = jsonElement.asType();
        var meta = Objects.requireNonNull(this.readerTypeMetaParser.parse(jsonElement, jsonElementType));
        return Objects.requireNonNull(this.readerGenerator.generate(target, meta));
    }

    public void generateWriter(TypeElement jsonElement) {
        var packageName = elements.getPackageOf(jsonElement).getQualifiedName().toString();
        var className = ClassName.get(packageName, JsonUtils.jsonWriterName(jsonElement));
        var writer = generateWriter(className, jsonElement);
        var javaFile = JavaFile.builder(packageName, writer).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    public TypeSpec generateWriter(ClassName targetName, TypeElement jsonElement) {
        if (jsonElement.getKind() == ElementKind.ENUM) {
            return this.enumWriterGenerator.generateEnumWriter(targetName, jsonElement);
        }
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            return this.sealedWriterGenerator.generateSealedWriter(targetName, jsonElement, SealedTypeUtils.collectFinalPermittedSubtypes(types, elements, jsonElement));
        }
        var meta = Objects.requireNonNull(this.writerTypeMetaParser.parse(jsonElement, jsonElement.asType()));
        return Objects.requireNonNull(this.writerGenerator.generate(targetName, meta));
    }
}
