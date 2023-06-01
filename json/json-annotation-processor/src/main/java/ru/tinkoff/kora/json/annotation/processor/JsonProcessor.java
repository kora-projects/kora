package ru.tinkoff.kora.json.annotation.processor;

import com.squareup.javapoet.JavaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ComparableTypeMirror;
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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Objects;

public class JsonProcessor {
    private static final Logger log = LoggerFactory.getLogger(JsonProcessor.class);

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
        var jsonElementType = jsonElement.asType();
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var readerClassName = JsonUtils.jsonReaderName(this.types, jsonElementType);
        var readerElement = this.elements.getTypeElement(packageElement + "." + readerClassName);
        if (readerElement != null) {
            return;
        }
        log.info("Generating JsonReader for {}", jsonElementType);
        if (jsonElement.getKind() == ElementKind.ENUM) {
            this.generateEnumReader(jsonElement);
            return;
        }
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            this.generateSealedRootReader(jsonElement);
            return;
        }
        this.generateDtoReader(jsonElement, jsonElementType);
    }

    private void generateSealedRootReader(TypeElement jsonElement) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var sealedReaderType = this.sealedReaderGenerator.generateSealedReader(jsonElement);

        var javaFile = JavaFile.builder(packageElement, sealedReaderType).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    private void generateEnumReader(TypeElement jsonElement) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var sealedReaderType = this.enumReaderGenerator.generateForEnum(jsonElement);

        var javaFile = JavaFile.builder(packageElement, sealedReaderType).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    private void generateDtoReader(TypeElement typeElement, TypeMirror jsonTypeMirror) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, typeElement);
        var meta = Objects.requireNonNull(this.readerTypeMetaParser.parse(typeElement, jsonTypeMirror));
        var readerType = Objects.requireNonNull(this.readerGenerator.generate(meta));

        var javaFile = JavaFile.builder(packageElement, readerType).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    private void generateEnumWriter(TypeElement jsonElement) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var enumWriterType = this.enumWriterGenerator.generateEnumWriter(jsonElement);
        var javaFile = JavaFile.builder(packageElement, enumWriterType).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    public void generateWriter(TypeElement jsonElement) {
        var wrapper = new ComparableTypeMirror(this.types, jsonElement.asType());
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var writerClassName = JsonUtils.jsonWriterName(this.types, wrapper.typeMirror());
        var writerElement = this.elements.getTypeElement(packageElement + "." + writerClassName);
        if (writerElement != null) {
            return;
        }
        log.info("Generating JsonWriter for {}", jsonElement);
        if (jsonElement.getKind() == ElementKind.ENUM) {
            this.generateEnumWriter(jsonElement);
            return;
        }
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            this.generateSealedWriter(jsonElement);
            return;
        }
        this.tryGenerateWriter(jsonElement, jsonElement.asType());
    }


    private void generateSealedWriter(TypeElement jsonElement) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var sealedWriterType = this.sealedWriterGenerator.generateSealedWriter(jsonElement, SealedTypeUtils.collectFinalPermittedSubtypes(types, elements, jsonElement));

        var javaFile = JavaFile.builder(packageElement, sealedWriterType).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    private void tryGenerateWriter(TypeElement jsonElement, TypeMirror jsonTypeMirror) {
        var meta = Objects.requireNonNull(this.writerTypeMetaParser.parse(jsonElement, jsonTypeMirror));
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var writerType = Objects.requireNonNull(this.writerGenerator.generate(meta));

        var javaFile = JavaFile.builder(packageElement, writerType).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }
}
