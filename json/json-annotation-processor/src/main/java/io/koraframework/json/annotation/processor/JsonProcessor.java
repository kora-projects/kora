package io.koraframework.json.annotation.processor;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeSpec;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.GeneratedHolder;
import io.koraframework.annotation.processor.common.SealedTypeUtils;
import io.koraframework.json.annotation.processor.reader.DelegatingReaderGenerator;
import io.koraframework.json.annotation.processor.reader.EnumReaderGenerator;
import io.koraframework.json.annotation.processor.reader.JsonReaderGenerator;
import io.koraframework.json.annotation.processor.reader.ReaderTypeMetaParser;
import io.koraframework.json.annotation.processor.reader.SealedInterfaceReaderGenerator;
import io.koraframework.json.annotation.processor.writer.DelegatingWriterGenerator;
import io.koraframework.json.annotation.processor.writer.EnumWriterGenerator;
import io.koraframework.json.annotation.processor.writer.JsonWriterGenerator;
import io.koraframework.json.annotation.processor.writer.SealedInterfaceWriterGenerator;
import io.koraframework.json.annotation.processor.writer.WriterTypeMetaParser;
import org.jspecify.annotations.Nullable;

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
    private final DelegatingReaderGenerator delegatingReaderGenerator;
    private final DelegatingWriterGenerator delegatingWriterGenerator;

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
        this.delegatingReaderGenerator = new DelegatingReaderGenerator();
        this.delegatingWriterGenerator = new DelegatingWriterGenerator();
    }

    /**
     * @return true if reader and writer holder for the type already exists
     */
    public boolean isGenerated(TypeElement jsonElement) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        return this.elements.getTypeElement(packageElement + "." + JsonUtils.jsonHolderName(jsonElement)) != null;
    }

    public TypeSpec generateReader(TypeElement jsonElement) {
        if (jsonElement.getKind() == ElementKind.ENUM) {
            return this.enumReaderGenerator.generateForEnum(jsonElement);
        }
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            return this.sealedReaderGenerator.generateSealedReader(jsonElement);
        }
        if (this.delegatingReaderGenerator.detectReaderFactory(jsonElement) != null) {
            return this.delegatingReaderGenerator.generate(jsonElement);
        }
        var meta = Objects.requireNonNull(this.readerTypeMetaParser.parse(jsonElement, jsonElement.asType()));
        return Objects.requireNonNull(this.readerGenerator.generate(meta));
    }

    public TypeSpec generateWriter(TypeElement jsonElement) {
        if (jsonElement.getKind() == ElementKind.ENUM) {
            return this.enumWriterGenerator.generateEnumWriter(jsonElement);
        }
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            return this.sealedWriterGenerator.generateSealedWriter(jsonElement, SealedTypeUtils.collectFinalPermittedSubtypes(types, elements, jsonElement));
        }
        if (this.delegatingWriterGenerator.detectWriterMethod(jsonElement) != null) {
            return this.delegatingWriterGenerator.generate(jsonElement);
        }
        var meta = Objects.requireNonNull(this.writerTypeMetaParser.parse(jsonElement, jsonElement.asType()));
        return Objects.requireNonNull(this.writerGenerator.generate(meta));
    }

    /**
     * Reader and writer are written as nested classes of one holder: the number of generated source files matters for compilation time
     */
    public void write(TypeElement jsonElement, @Nullable TypeSpec reader, @Nullable TypeSpec writer) {
        if (reader == null && writer == null) {
            return;
        }
        var holderName = GeneratedHolder.name(this.elements, jsonElement, JsonUtils.HOLDER_POSTFIX);
        var holder = GeneratedHolder.classBuilder(holderName, JsonAnnotationProcessor.class)
            .addOriginatingElement(jsonElement);
        if (reader != null) {
            holder.addType(reader);
        }
        if (writer != null) {
            holder.addType(writer);
        }
        CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(holderName.packageName(), holder.build()).build());
    }
}
