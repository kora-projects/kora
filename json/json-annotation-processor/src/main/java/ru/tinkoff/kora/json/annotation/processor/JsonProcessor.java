package ru.tinkoff.kora.json.annotation.processor;

import com.squareup.javapoet.JavaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.ComparableTypeMirror;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonClassReaderMeta;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderTypeMetaParser;
import ru.tinkoff.kora.json.annotation.processor.reader.SealedInterfaceReaderGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.JsonClassWriterMeta;
import ru.tinkoff.kora.json.annotation.processor.writer.JsonWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.SealedInterfaceWriterGenerator;
import ru.tinkoff.kora.json.annotation.processor.writer.WriterTypeMetaParser;

import javax.annotation.Nullable;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.kora.app.annotation.processor.KoraAppUtils.*;

public class JsonProcessor {
    private static final Logger log = LoggerFactory.getLogger(JsonProcessor.class);

    private final Set<ComparableTypeMirror> processedReaders = new HashSet<>();
    private final Set<ComparableTypeMirror> processedWriters = new HashSet<>();
    private final Set<ComparableTypeMirror> nonProcessedReaders = new HashSet<>();
    private final Set<ComparableTypeMirror> nonProcessedWriters = new HashSet<>();
    private final ProcessingEnvironment processingEnv;
    private final Elements elements;
    private final Types types;
    private final ReaderTypeMetaParser readerTypeMetaParser;
    private final WriterTypeMetaParser writerTypeMetaParser;
    private final JsonWriterGenerator writerGenerator;
    private final JsonReaderGenerator readerGenerator;
    private final SealedInterfaceReaderGenerator sealedReaderGenerator;
    private final SealedInterfaceWriterGenerator sealedWriterGenerator;
    private final Messager messager;

    public JsonProcessor(ProcessingEnvironment processingEnv, ReaderTypeMetaParser readerTypeMetaParser, WriterTypeMetaParser writerTypeMetaParser, JsonWriterGenerator writerGenerator, JsonReaderGenerator readerGenerator, SealedInterfaceReaderGenerator sealedReaderGenerator, SealedInterfaceWriterGenerator sealedWriterGenerator) {
        this.processingEnv = processingEnv;
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.readerTypeMetaParser = readerTypeMetaParser;
        this.writerTypeMetaParser = writerTypeMetaParser;
        this.writerGenerator = writerGenerator;
        this.readerGenerator = readerGenerator;
        this.sealedReaderGenerator = sealedReaderGenerator;
        this.sealedWriterGenerator = sealedWriterGenerator;
        this.messager = this.processingEnv.getMessager();
    }

    public void generateReader(TypeElement jsonElement) {
        var wrapper = new ComparableTypeMirror(this.types, jsonElement.asType());

        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        if (this.processedReaders.contains(wrapper)) {
            return;
        }
        var readerClassName = JsonUtils.jsonReaderName(this.types, wrapper.typeMirror());
        var readerElement = this.elements.getTypeElement(packageElement + "." + readerClassName);
        if (readerElement != null) {
            this.processedReaders.add(wrapper);
            return;
        }
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            var jsonElements = jsonElement.getPermittedSubclasses().stream().map(types::asElement).toList();
            jsonElements.forEach(elem -> this.nonProcessedReaders.add(new ComparableTypeMirror(this.types, elem.asType())));
            var sealedReaderType = this.sealedReaderGenerator.generateSealedReader(jsonElement, jsonElements);
            try {
                var javaFile = JavaFile.builder(packageElement, sealedReaderType).build();
                CommonUtils.safeWriteTo(this.processingEnv, javaFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else this.nonProcessedReaders.add(wrapper);
        var failedReaders = new HashSet<ComparableTypeMirror>();
        while (failedReaders.size() < this.nonProcessedReaders.size()) {
            var processedReadersAtStart = this.processedReaders.size();
            for (var jsonClass : this.nonProcessedReaders) {
                log.info("Generating JsonReader for {}", jsonClass);
                if (failedReaders.contains(jsonClass)) {
                    continue;
                }
                try {
                    this.tryGenerateReader(jsonClass.typeMirror());
                } catch (Exception e) {
                    failedReaders.add(jsonClass);
                }
            }
            this.nonProcessedReaders.removeAll(this.processedReaders);
            var processedReadersAtEnd = this.processedReaders.size();
            if (processedReadersAtEnd != processedReadersAtStart) {
                failedReaders.clear();
            }
        }
    }

    private void tryGenerateReader(TypeMirror jsonTypeMirror) {
        var meta = this.readerTypeMetaParser.parse(jsonTypeMirror);
        if (meta == null) {
            throw new RuntimeException("Can't parse meta data");
        }
        var packageElement = JsonUtils.jsonClassPackage(this.elements, meta.typeElement());
        var readerClassName = JsonUtils.jsonReaderName(meta.typeElement());
        var readerElement = this.elements.getTypeElement(packageElement + "." + readerClassName);

        if (readerElement == null) {
            var readerType = this.readerGenerator.generate(meta);
            if (readerType == null) {
                throw new RuntimeException("Can't generate readet");
            }

            try {
                var javaFile = JavaFile.builder(packageElement, readerType).build();
                CommonUtils.safeWriteTo(this.processingEnv, javaFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        this.processedReaders.add(new ComparableTypeMirror(this.types, jsonTypeMirror));
    }

    public void generateWriter(TypeElement jsonElement) {
        var wrapper = new ComparableTypeMirror(this.types, jsonElement.asType());
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        if (this.processedWriters.contains(wrapper)) {
            return;
        }
        var writerClassName = JsonUtils.jsonWriterName(this.types, wrapper.typeMirror());
        var writerElement = this.elements.getTypeElement(packageElement + "." + writerClassName);
        if (writerElement != null) {
            this.processedWriters.add(wrapper);
            return;
        }
        var requiresDiscriminator = new HashSet<ComparableTypeMirror>();
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            var jsonElements = jsonElement.getPermittedSubclasses().stream().map(types::asElement).toList();
            jsonElements.forEach(elem -> {
                var typeMirror = new ComparableTypeMirror(this.types, elem.asType());
                this.nonProcessedWriters.add(typeMirror);
                requiresDiscriminator.add(typeMirror);
            });
            var sealedReaderType = this.sealedWriterGenerator.generateSealedWriter(jsonElement, jsonElements);
            try {
                var javaFile = JavaFile.builder(packageElement, sealedReaderType).build();
                CommonUtils.safeWriteTo(this.processingEnv, javaFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else this.nonProcessedWriters.add(wrapper);

        var failedWriters = new HashSet<ComparableTypeMirror>();
        while (failedWriters.size() < this.nonProcessedWriters.size()) {
            var newClassesToProcess = new HashSet<ComparableTypeMirror>();
            var processedWritersAtStart = this.processedWriters.size();
            for (var jsonClass : this.nonProcessedWriters) {
                log.info("Generating JsonWriter for {}", jsonClass);
                if (failedWriters.contains(jsonClass)) {
                    continue;
                }

                var moreClasses = this.tryGenerateWriter(jsonClass.typeMirror());
                if (moreClasses != null) {
                    newClassesToProcess.addAll(moreClasses);
                    if (moreClasses.isEmpty()) {
                        this.processedWriters.add(jsonClass);
                    } else {
                        failedWriters.add(jsonClass);
                    }
                } else {
                    failedWriters.add(jsonClass);
                }
            }
            this.nonProcessedWriters.addAll(newClassesToProcess);
            this.nonProcessedWriters.removeAll(this.processedWriters);
            var processedReadersAtEnd = this.processedWriters.size();
            if (processedReadersAtEnd != processedWritersAtStart) {
                failedWriters.clear();
            }
        }
    }

    @Nullable
    private Set<ComparableTypeMirror> tryGenerateWriter(TypeMirror jsonTypeMirror) {
        var meta = this.writerTypeMetaParser.parse(jsonTypeMirror);
        if (meta == null) {
            return null;
        }

        var packageElement = JsonUtils.jsonClassPackage(this.elements, meta.typeElement());
        var writerClassName = JsonUtils.jsonWriterName(meta.typeElement());
        var writerElement = this.elements.getTypeElement(packageElement + "." + writerClassName);


        if (writerElement == null) {
            var writerType = this.writerGenerator.generate(meta);
            if (writerType == null) {
                return null;
            }

            try {
                var javaFile = JavaFile.builder(packageElement, writerType).build();
                CommonUtils.safeWriteTo(this.processingEnv, javaFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        this.processedWriters.add(new ComparableTypeMirror(this.types, jsonTypeMirror));
        return Set.of();
    }

    public void printError() {
        if (this.nonProcessedReaders.isEmpty() && this.nonProcessedWriters.isEmpty()) {
            return;
        }
        for (var writer : this.nonProcessedWriters) {
            messager.printMessage(Diagnostic.Kind.ERROR, "JsonWriter was not generated for class", this.types.asElement(writer));
        }
        for (var reader : this.nonProcessedReaders) {
            messager.printMessage(Diagnostic.Kind.ERROR, "JsonReader was not generated for class", this.types.asElement(reader));
        }
    }
}
