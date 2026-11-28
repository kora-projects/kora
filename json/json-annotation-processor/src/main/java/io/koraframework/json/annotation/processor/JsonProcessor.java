package io.koraframework.json.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.json.annotation.processor.codec.*;
import io.koraframework.json.annotation.processor.reader.*;
import io.koraframework.json.annotation.processor.writer.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import java.util.*;

public class JsonProcessor {
    private final ProcessingEnvironment processingEnv;
    private final Elements elements;
    private final Types types;
    private final ReaderTypeMetaParser readerTypeMetaParser;
    private final WriterTypeMetaParser writerTypeMetaParser;
    private final FastJsonCodecGenerator fastJsonCodecGenerator;
    private final JsonCodecGenerator codecGenerator;
    private final JsonSealedCodecGenerator sealedCodecGenerator;
    private final JsonEnumCodecGenerator enumCodecGenerator;
    private final JsonDelegatingCodecGenerator delegatingCodecGenerator;
    private final Set<String> generatedCodecs = new HashSet<>();

    public JsonProcessor(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        var knownTypes = new KnownType();
        this.readerTypeMetaParser = new ReaderTypeMetaParser(this.processingEnv, knownTypes);
        this.writerTypeMetaParser = new WriterTypeMetaParser(processingEnv, knownTypes);
        this.fastJsonCodecGenerator = new FastJsonCodecGenerator(this.processingEnv);
        this.codecGenerator = new JsonCodecGenerator(this.processingEnv);
        this.sealedCodecGenerator = new JsonSealedCodecGenerator(this.processingEnv);
        this.enumCodecGenerator = new JsonEnumCodecGenerator();
        this.delegatingCodecGenerator = new JsonDelegatingCodecGenerator();
    }

    public void generateReader(TypeElement jsonElement) {
        generateCodec(jsonElement, true, false);
    }

    public void generateWriter(TypeElement jsonElement) {
        generateCodec(jsonElement, false, true);
    }

    public void generateCodec(TypeElement jsonElement, boolean needReader, boolean needWriter) {
        if (!needReader && !needWriter) {
            return;
        }
        var jsonElementType = jsonElement.asType();
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var codecClassName = JsonUtils.jsonReaderName(this.types, jsonElementType).replace("JsonReader", "JsonCodec");
        var fullCodecName = packageElement + "." + codecClassName;

        if (!generatedCodecs.add(fullCodecName) || this.elements.getTypeElement(fullCodecName) != null) {
            return;
        }
        if (jsonElement.getKind() == ElementKind.ENUM) {
            this.generateEnumCodec(jsonElement);
            return;
        }
        if (jsonElement.getModifiers().contains(Modifier.SEALED)) {
            this.generateSealedCodec(jsonElement);
            return;
        }
        if (this.delegatingCodecGenerator.detectReaderFactory(jsonElement) != null || this.delegatingCodecGenerator.detectWriterMethod(jsonElement) != null) {
            var codecType = this.delegatingCodecGenerator.generate(jsonElement);
            if (codecType != null) {
                CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageElement, codecType).build());
            }
            return;
        }
        this.generateDtoCodec(jsonElement, jsonElementType, needReader, needWriter);
    }

    private void generateDtoCodec(TypeElement typeElement, TypeMirror jsonTypeMirror, boolean needReader, boolean needWriter) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, typeElement);
        var readerMeta = needReader ? Objects.requireNonNull(this.readerTypeMetaParser.parse(typeElement, jsonTypeMirror)) : null;
        var writerMeta = needWriter ? Objects.requireNonNull(this.writerTypeMetaParser.parse(typeElement, jsonTypeMirror)) : null;
        var codecType = Objects.requireNonNull(this.fastJsonCodecGenerator.generate(readerMeta, writerMeta));

        var jsonCommonClass = ClassName.get("io.koraframework.json.common", "JsonCommon");
        var jsonCommonReaderClass = ClassName.get("io.koraframework.json.common", "JsonCommonReader");
        var fastJsonCodecSupportClass = ClassName.get("io.koraframework.json.common", "FastJsonCodecSupport");
        var javaFile = JavaFile.builder(packageElement, codecType)
            .addStaticImport(jsonCommonClass, "*")
            .addStaticImport(jsonCommonReaderClass, "*")
//            .addStaticImport(JsonTypes.jsonToken, "*")
            .addStaticImport(fastJsonCodecSupportClass, "missingRequiredFields")
            .skipJavaLangImports(true)
            .build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    private void generateEnumCodec(TypeElement jsonElement) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var codecType = this.enumCodecGenerator.generateForEnum(jsonElement);

        var javaFile = JavaFile.builder(packageElement, codecType).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }

    private void generateSealedCodec(TypeElement jsonElement) {
        var packageElement = JsonUtils.jsonClassPackage(this.elements, jsonElement);
        var sealedCodecType = this.sealedCodecGenerator.generateSealedCodec(jsonElement);

        var javaFile = JavaFile.builder(packageElement, sealedCodecType).build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }
}
