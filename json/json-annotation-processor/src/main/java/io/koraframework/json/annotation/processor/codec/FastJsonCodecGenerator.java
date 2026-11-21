package io.koraframework.json.annotation.processor.codec;

import com.alibaba.fastjson2.util.Fnv;
import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.json.annotation.processor.*;
import io.koraframework.json.annotation.processor.reader.*;
import io.koraframework.json.annotation.processor.reader.ReaderFieldType.*;
import io.koraframework.json.annotation.processor.writer.*;
import org.jspecify.annotations.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.koraframework.json.annotation.processor.JsonTypes.*;

public class FastJsonCodecGenerator {
    private final Types types;

    public FastJsonCodecGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
    }

    @Nullable
    public TypeSpec generate(@Nullable JsonClassReaderMeta readerMeta, @Nullable JsonClassWriterMeta writerMeta) {
        if (readerMeta == null && writerMeta == null) {
            return null;
        }
        var typeElement = readerMeta != null ? readerMeta.typeElement() : writerMeta.typeElement();
        var targetType = TypeName.get(typeElement.asType());
        String codecClassName = JsonUtils.jsonReaderName(typeElement).replace("JsonReader", "JsonCodec");

        var typeBuilder = TypeSpec.classBuilder(codecClassName)
            .addAnnotation(AnnotationUtils.generated(JsonCodecGenerator.class))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement);

        if (readerMeta != null) {
            typeBuilder.addSuperinterface(ParameterizedTypeName.get(jsonReader, targetType));
        }
        if (writerMeta != null) {
            typeBuilder.addSuperinterface(ParameterizedTypeName.get(jsonWriter, targetType));
        }

        for (TypeParameterElement typeParameter : typeElement.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        if (readerMeta != null) {
            for (var field : readerMeta.fields()) {
                String rawFieldName = field.parameter().getSimpleName().toString();
                String hashFieldName = "HASH_" + rawFieldName;
                long hash = Fnv.hashCode64(field.jsonName());

                typeBuilder.addField(FieldSpec.builder(long.class, hashFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$LL", hash)
                    .build());
            }
        }

        if (writerMeta != null) {
            for (var field : writerMeta.fields()) {
                String rawFieldName = field.field().getSimpleName().toString();
                String bytesFieldName = "BYTES_" + rawFieldName;
                String jsonFieldNameWithQuotes = "\"" + field.jsonName() + "\":";
                byte[] utf8Bytes = jsonFieldNameWithQuotes.getBytes(StandardCharsets.UTF_8);

                CodeBlock.Builder byteInit = CodeBlock.builder().add("{ ");
                for (int i = 0; i < utf8Bytes.length; i++) {
                    byteInit.add("$L", utf8Bytes[i]);
                    if (i < utf8Bytes.length - 1) byteInit.add(", ");
                }
                byteInit.add(" }");

                typeBuilder.addField(FieldSpec.builder(byte[].class, bytesFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer(byteInit.build())
                    .build());
            }
        }

        if (readerMeta != null) {
            addBitSet(typeBuilder, readerMeta);
        }
        addCombinedDependencies(typeBuilder, readerMeta, writerMeta);
        if (readerMeta != null) {
            generateReadMethod(typeBuilder, readerMeta);
        }
        if (writerMeta != null) {
            generateWriteMethod(typeBuilder, writerMeta);
        }

        return typeBuilder.build();
    }

    private void generateReadMethod(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        var targetType = TypeName.get(meta.typeElement().asType());

        var legacyJacksonReadMethod = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(jsonParser, "parser")
            .addException(jacksonException)
            .addAnnotation(Override.class)
            .returns(targetType.withoutAnnotations().annotated(CommonClassNames.nullableAnnotation))
            .addStatement("return null");
        typeBuilder.addMethod(legacyJacksonReadMethod.build());

        var readMethod = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(FASTJSON_READER, "__reader")
            .addAnnotation(Override.class)
            .returns(targetType.withoutAnnotations().annotated(CommonClassNames.nullableAnnotation));

        readMethod.beginControlFlow("if (__reader.wasNull())")
            .addStatement("return null")
            .endControlFlow();
        readMethod.beginControlFlow("if (!__reader.nextIfObjectStart())")
            .addStatement("throw new $T(\"Expected object start\")", FASTJSON_EXCEPTION)
            .endControlFlow();

        if (meta.fields().size() <= 32) {
            readMethod.addStatement("int __receivedFields = NULLABLE_FIELDS_RECEIVED");
        } else {
            readMethod.addStatement("var __receivedFields = ($T) NULLABLE_FIELDS_RECEIVED.clone()", BitSet.class);
        }
        readMethod.addCode("\n");

        addFieldVariables(readMethod, meta);

        readMethod.beginControlFlow("while (!__reader.nextIfObjectEnd())");
        readMethod.addStatement("long __hash = __reader.readFieldNameHashCode()");
        readMethod.addStatement("if (__hash == 0L) continue");
        readMethod.addCode("\n");

        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            String rawFieldName = field.parameter().getSimpleName().toString();
            String hashFieldName = "HASH_" + rawFieldName;

            if (i == 0) {
                readMethod.beginControlFlow("if (__hash == $L)", hashFieldName);
            } else {
                readMethod.nextControlFlow("else if (__hash == $L)", hashFieldName);
            }

            String commonReadMethod = getCommonReadMethodName(field);
            if (commonReadMethod != null) {
                readMethod.addStatement("$L = __reader.$L()", field.parameter().getSimpleName(), commonReadMethod);
            } else {
                var readerName = field.parameter().getSimpleName() + "Reader";
                readMethod.addStatement("$L = $L.read(__reader)", field.parameter().getSimpleName(), readerName);
            }

            readMethod.addCode(markReceived(meta, i));
        }

        if (!meta.fields().isEmpty()) {
            readMethod.nextControlFlow("else");
            readMethod.addStatement("__reader.skipValue()");
            readMethod.endControlFlow();
        } else {
            readMethod.addStatement("__reader.skipValue()");
        }
        readMethod.endControlFlow();
        readMethod.addCode("\n");

        if (meta.fields().size() <= 32) {
            readMethod.beginControlFlow("if (__receivedFields != ALL_FIELDS_RECEIVED)");
        } else {
            readMethod.beginControlFlow("if (!__receivedFields.equals(ALL_FIELDS_RECEIVED))");
        }
        readMethod.addStatement("throw __throwMissingRequiredFields(__reader, __receivedFields)");
        readMethod.endControlFlow();
        readMethod.addCode("\n");

        readMethod.addCode("return new $T(", meta.typeElement());
        for (int i = 0; i < meta.fields().size(); i++) {
            readMethod.addCode("$L", meta.fields().get(i).parameter().getSimpleName());
            if (i < meta.fields().size() - 1) readMethod.addCode(", ");
        }
        readMethod.addCode(");\n");

        typeBuilder.addMethod(readMethod.build());
        generateMissingFieldsExceptionMethod(typeBuilder, meta);
    }

    private void addCombinedDependencies(TypeSpec.Builder typeBuilder, JsonClassReaderMeta readerMeta, JsonClassWriterMeta writerMeta) {
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var addedFields = new HashSet<String>();
        boolean hasParameters = false;

        for (var field : writerMeta.fields()) {
            if (field.writer() != null) {
                var fieldName = field.accessor().getSimpleName() + "Writer";
                if (addedFields.add(fieldName)) {
                    final TypeName fieldType;
                    if (field.writer().mapperClass() != null) {
                        fieldType = TypeName.get(field.writer().mapperClass());
                        var writerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                        var writerElement = (TypeElement) this.types.asElement(field.writer().mapperClass());
                        if (CommonUtils.hasDefaultConstructorAndFinal(writerElement)) {
                            writerField.addModifiers(Modifier.STATIC).initializer("new $T()", fieldType);
                            typeBuilder.addField(writerField.build());
                            continue;
                        }
                    } else {
                        fieldType = ParameterizedTypeName.get(jsonWriter, TypeName.get(field.writerTypeMeta().typeMirror()));
                    }
                    var writerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                    var fieldTag = field.writer().toTagAnnotation();
                    if (fieldTag != null) writerField.addAnnotation(fieldTag);

                    typeBuilder.addField(writerField.build());
                    constructor.addParameter(fieldType, fieldName).addStatement("this.$L = $L", fieldName, fieldName);
                    hasParameters = true;
                }
            } else if (field.writerTypeMeta() instanceof WriterFieldType.UnknownWriterFieldType) {
                var fieldName = field.accessor().getSimpleName() + "Writer";
                if (addedFields.add(fieldName)) {
                    var fieldType = ParameterizedTypeName.get(jsonWriter, TypeName.get(field.writerTypeMeta().typeMirror()));
                    var writerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                    typeBuilder.addField(writerField.build());
                    constructor.addParameter(fieldType, fieldName).addStatement("this.$L = $L", fieldName, fieldName);
                    hasParameters = true;
                }
            }
        }

        for (var field : readerMeta.fields()) {
            if (field.reader() == null && field.typeMeta() instanceof KnownTypeReaderMeta) {
                continue;
            }
            var fieldName = field.parameter().getSimpleName() + "Reader";
            if (addedFields.add(fieldName)) {
                final TypeName fieldType;
                if (field.reader() != null && field.reader().mapperClass() != null) {
                    fieldType = TypeName.get(field.reader().mapperClass());
                    var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                    var readerElement = (TypeElement) this.types.asElement(field.reader().mapperClass());
                    if (CommonUtils.hasDefaultConstructorAndFinal(readerElement)) {
                        readerField.addModifiers(Modifier.STATIC).initializer("new $T()", fieldType);
                        typeBuilder.addField(readerField.build());
                        continue;
                    }
                } else {
                    fieldType = ParameterizedTypeName.get(jsonReader, field.typeName());
                }
                var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                if (field.reader() != null) {
                    var fieldTag = field.reader().toTagAnnotation();
                    if (fieldTag != null) readerField.addAnnotation(fieldTag);
                }

                typeBuilder.addField(readerField.build());
                constructor.addParameter(fieldType, fieldName).addStatement("this.$L = $L", fieldName, fieldName);
                hasParameters = true;
            }
        }

        if (hasParameters) {
            typeBuilder.addMethod(constructor.build());
        }
    }

    private void generateWriteMethod(TypeSpec.Builder typeBuilder, JsonClassWriterMeta meta) {
        var targetType = TypeName.get(meta.typeMirror());

        var jacksonMethod = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(jsonGenerator, "generator")
            .addParameter(ParameterSpec.builder(targetType.annotated(CommonClassNames.nullableAnnotation), "object").build())
            .addException(jacksonException)
            .addAnnotation(Override.class);
        typeBuilder.addMethod(jacksonMethod.build());

        var method = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(FASTJSON_WRITER, "_gen")
            .addParameter(ParameterSpec.builder(targetType.annotated(CommonClassNames.nullableAnnotation), "_object").build())
            .addAnnotation(Override.class);

        method.addCode("if (_object == null) {$>\n_gen.writeNull();\nreturn;$<\n}\n");
        method.addStatement("_gen.startObject()");

        var discriminatorField = JsonUtils.discriminatorField(types, meta.typeElement());
        if (discriminatorField != null) {
            if (meta.fields().stream().noneMatch(f -> f.jsonName().equals(discriminatorField))) {
                var discriminatorFieldValues = JsonUtils.discriminatorValue(meta.typeElement());
                method.addStatement("_gen.writeName($S)", discriminatorField);
                method.addStatement("_gen.writeString($S)", discriminatorFieldValues.getFirst());
            }
        }
        for (var field : meta.fields()) {
            this.addWriteParam(method, field);
        }
        method.addStatement("_gen.endObject()");
        typeBuilder.addMethod(method.build());
    }

    private void generateMissingFieldsExceptionMethod(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        String simpleDtoName = meta.typeElement().getSimpleName().toString();

// Собираем инлайненную цепочку тернарников вида: (__i == 0) ? "userId" : (__i == 1) ? "userEmail" : ...
        var errorSwitch = CodeBlock.builder();
        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            if (i == 0) {
                errorSwitch.add("(__i == 0) ? $S : ", field.jsonName());
            } else {
                errorSwitch.add("(__i == $L) ? $S : ", i, field.jsonName());
            }
        }
        errorSwitch.add("$S", "");

        var fieldType = meta.fields().size() > 32 ? ClassName.get(BitSet.class) : TypeName.INT;

        var methodBuilder = MethodSpec.methodBuilder("__throwMissingRequiredFields")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(FASTJSON_READER, "__reader")
            .addParameter(fieldType, "__receivedFields")
            .returns(FASTJSON_EXCEPTION);

        if (meta.fields().size() > 32) {
            methodBuilder.addCode("""
                __receivedFields.flip(0, $L);
                var __missing = new $T();
                for (int __i = __receivedFields.nextSetBit(0); __i >= 0; __i = __receivedFields.nextSetBit(__i+1)) {
                    if (__missing.length() > 0) __missing.append(", ");
                        __missing.append($L);
                }
                return new $T(__reader.info("Missing required fields: [" + __missing.toString() + "] in class $L"));
                """, meta.fields().size(), StringBuilder.class, errorSwitch.build(), FASTJSON_EXCEPTION, simpleDtoName);
        } else {
            methodBuilder.addCode("""
                var _nonReceivedFields = (~__receivedFields) & ALL_FIELDS_RECEIVED;
                var __missing = new $T();
                for (int __i = 0; __i < $L; __i++) {
                if ((_nonReceivedFields & (1 << __i)) != 0) {
                if (__missing.length() > 0) __missing.append(", ");
                __missing.append($L);
                }
                }
                return new $T(__reader.info("Missing required fields: [" + __missing.toString() + "] in class $L"));
                """, StringBuilder.class, meta.fields().size(), errorSwitch.build(), FASTJSON_EXCEPTION, simpleDtoName);
        }

        typeBuilder.addMethod(methodBuilder.build());
    }


    private void addFieldVariables(MethodSpec.Builder method, JsonClassReaderMeta meta) {
        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            method.addCode("$T $L", field.parameter(), field.parameter().getSimpleName());
            var parameterType = field.parameter().asType();
            if (parameterType instanceof PrimitiveType) {
                if (parameterType.toString().equals("boolean")) {
                    method.addCode(" = false;\n");
                } else {
                    method.addCode(" = 0;\n");
                }
            } else if (field.typeMeta() != null && field.typeMeta().isJsonNullable()) {
                method.addCode(" = $T.undefined();\n", jsonNullable);
            } else {
                method.addCode(" = null;\n");
            }
        }
    }

    private void addBitSet(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        if (meta.fields().size() <= 32) {
            var sb = new StringBuilder();
            for (int i = meta.fields().size() - 1; i >= 0; i--) {
                sb.append(isNullable(meta.fields().get(i)) ? "1" : "0");
            }
            var nullableFieldsReceived = meta.fields().isEmpty() ? "0" : "0b" + sb;
            var allFieldsReceived = meta.fields().isEmpty() ? "0" : "0b" + "1".repeat(meta.fields().size());

            typeBuilder.addField(FieldSpec.builder(TypeName.INT, "ALL_FIELDS_RECEIVED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).initializer(CodeBlock.of(allFieldsReceived)).build())
                .addField(FieldSpec.builder(TypeName.INT, "NULLABLE_FIELDS_RECEIVED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).initializer(CodeBlock.of(nullableFieldsReceived)).build());
        } else {
            typeBuilder.addField(ClassName.get(BitSet.class), "ALL_FIELDS_RECEIVED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addField(ClassName.get(BitSet.class), "NULLABLE_FIELDS_RECEIVED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
            var fieldReceivedInitBlock = CodeBlock.builder().add("""
                ALL_FIELDS_RECEIVED = new $T($L);
                ALL_FIELDS_RECEIVED.set(0, $L);
                NULLABLE_FIELDS_RECEIVED = new $T($L);
                """, BitSet.class, meta.fields().size(), meta.fields().size(), BitSet.class, meta.fields().size());
            for (int i = 0; i < meta.fields().size(); i++) {
                if (isNullable(meta.fields().get(i))) fieldReceivedInitBlock.add("NULLABLE_FIELDS_RECEIVED.set($L);\n", i);
            }
            typeBuilder.addStaticBlock(fieldReceivedInitBlock.build());
        }
    }

    private void addWriteParam(MethodSpec.Builder method, JsonClassWriterMeta.FieldMeta field) {
        String rawFieldName = field.field().getSimpleName().toString();
        String bytesFieldName = "BYTES_" + rawFieldName;
        String writerFieldName = field.accessor().getSimpleName() + "Writer";

        if (field.typeMirror().getKind().isPrimitive()) {
            method.addStatement("_gen.writeNameRaw($L)", bytesFieldName);
            if (field.writer() == null && field.writerTypeMeta() instanceof WriterFieldType.KnownWriterFieldType typeMeta) {
                method.addCode(writeKnownType(typeMeta.knownType(), CodeBlock.of("_object.$L", field.accessor())));
            } else {
                method.addStatement("$L.write(_gen, _object.$L)", writerFieldName, field.accessor());
            }
            return;
        }

        var isEmptyCheck = field.includeType() == JsonClassWriterMeta.IncludeType.NON_EMPTY
            && (CommonUtils.isCollection(field.writerTypeMeta().typeMirror()) || CommonUtils.isMap(field.writerTypeMeta().typeMirror()));

        if (field.writerTypeMeta().isJsonNullable()) {
            method.addCode("if (_object.$L.isDefined()) {$>\n", field.accessor());
            if (isEmptyCheck) method.beginControlFlow("if (_object.$L.value() != null && !_object.$L.value().isEmpty())", field.accessor(), field.accessor());
        } else if (isEmptyCheck) {
            method.addCode("if (_object.$L != null && !_object.$L.isEmpty()) {$>\n", field.accessor(), field.accessor());
        } else if (field.includeType() != JsonClassWriterMeta.IncludeType.ALWAYS) {
            method.addCode("if (_object.$L != null) {$>\n", field.accessor());
        }

        method.addStatement("_gen.writeNameRaw($L)", bytesFieldName);
        if (field.writer() == null && field.writerTypeMeta() instanceof WriterFieldType.KnownWriterFieldType typeMeta) {
            if (typeMeta.isJsonNullable()) {
                method.beginControlFlow("if (_object.$L.isNull())").addStatement("_gen.writeNull()").nextControlFlow("else")
                    .addCode(writeKnownType(typeMeta.knownType(), CodeBlock.of("_object.$L.value()", field.accessor()))).endControlFlow();
            } else if (field.includeType() == JsonClassWriterMeta.IncludeType.ALWAYS) {
                method.beginControlFlow("if (_object.$L == null)").addStatement("_gen.writeNull()").nextControlFlow("else")
                    .addCode(writeKnownType(typeMeta.knownType(), CodeBlock.of("_object.$L", field.accessor()))).endControlFlow();
            } else {
                method.addCode(writeKnownType(typeMeta.knownType(), CodeBlock.of("_object.$L", field.accessor())));
            }
        } else {
            if (field.writerTypeMeta().isJsonNullable()) {
                method.beginControlFlow("if (_object.$L.isNull())").addStatement("_gen.writeNull()").nextControlFlow("else")
                    .addStatement("$L.write(_gen, _object.$L.value())", writerFieldName, field.accessor()).endControlFlow();
            } else {
                method.addStatement("$L.write(_gen, _object.$L)", writerFieldName, field.accessor());
            }
        }

        if (field.writerTypeMeta().isJsonNullable()) {
            method.addCode("$<}\n");
            if (isEmptyCheck) method.addCode("$<}\n");
        } else if (field.includeType() != JsonClassWriterMeta.IncludeType.ALWAYS) {
            method.addCode("$<}\n");
        }
    }

    private CodeBlock writeKnownType(KnownType.KnownTypesEnum knownType, CodeBlock value) {
        return switch (knownType) {
            case STRING -> CodeBlock.of("_gen.writeString($L);\n", value);

            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> CodeBlock.of("_gen.writeBool($L);\n", value);
            case INTEGER_OBJECT, INTEGER_PRIMITIVE -> CodeBlock.of("_gen.writeInt32($L);\n", value);
            case LONG_OBJECT, LONG_PRIMITIVE -> CodeBlock.of("_gen.writeInt64($L);\n", value);
            case FLOAT_OBJECT, FLOAT_PRIMITIVE -> CodeBlock.of("_gen.writeFloat($L);\n", value);
            case DOUBLE_OBJECT, DOUBLE_PRIMITIVE -> CodeBlock.of("_gen.writeDouble($L);\n", value);
            case SHORT_OBJECT, SHORT_PRIMITIVE -> CodeBlock.of("_gen.writeInt16($L);\n", value);
            case BYTE_OBJECT, BYTE_PRIMITIVE -> CodeBlock.of("_gen.writeInt8($L);\n", value);

            case BIG_DECIMAL -> CodeBlock.of("_gen.writeDecimal($L);\n", value);
            case BIG_INTEGER -> CodeBlock.of("_gen.writeBigInt($L);\n", value);
            case BINARY -> CodeBlock.of("_gen.writeBinary($L);\n", value);

            case LOCAL_DATE -> CodeBlock.of("_gen.writeLocalDate($L);\n", value);
            case LOCAL_DATE_TIME -> CodeBlock.of("_gen.writeLocalDateTime($L);\n", value);
            case OFFSET_DATE_TIME -> CodeBlock.of("_gen.writeOffsetDateTime($L);\n", value);
            case ZONED_DATE_TIME -> CodeBlock.of("_gen.writeZonedDateTime($L);\n", value);
            case INSTANT -> CodeBlock.of("_gen.writeInstant($L);\n", value);

            case UUID -> CodeBlock.of("_gen.writeString($L.toString());\n", value);
        };
    }

    private boolean isNullable(JsonClassReaderMeta.FieldMeta field) {
        if (field.parameter().asType().getKind().isPrimitive()) return false;
        if (field.typeMeta() != null && field.typeMeta().isJsonNullable()) return true;
        return CommonUtils.isNullable(field.parameter());
    }

    private String markReceived(JsonClassReaderMeta meta, int index) {
        if (meta.fields().size() <= 32) {
            return "__receivedFields |= 1 << " + index + ";\n";
        } else {
            return "__receivedFields.set(" + index + ");\n";
        }
    }

    private @Nullable String getCommonReadMethodName(JsonClassReaderMeta.FieldMeta field) {
        if (field.reader() != null) {
            return null;
        }
        if (!(field.typeMeta() instanceof KnownTypeReaderMeta knownMeta)) {
            return null;
        }

        return switch (knownMeta.knownType()) {
            case STRING -> "readString";
            case BOOLEAN_PRIMITIVE -> "readBoolValue";
            case BOOLEAN_OBJECT -> "readBool";
            case INTEGER_PRIMITIVE -> "readInt32Value";
            case INTEGER_OBJECT -> "readInt32";
            case LONG_PRIMITIVE -> "readInt64Value";
            case LONG_OBJECT -> "readInt64";
            case FLOAT_PRIMITIVE -> "readFloatValue";
            case FLOAT_OBJECT -> "readFloat";
            case DOUBLE_PRIMITIVE -> "readDoubleValue";
            case DOUBLE_OBJECT -> "readDouble";
            case SHORT_PRIMITIVE -> "readInt16Value";
            case SHORT_OBJECT -> "readInt16";
            case BIG_DECIMAL -> "readBigDecimal";
            case BIG_INTEGER -> "readBigInteger";
            case UUID -> "readUUID";
            case INSTANT -> "readInstant";
            case LOCAL_DATE -> "readLocalDate";
            case LOCAL_DATE_TIME -> "readLocalDateTime";
            case ZONED_DATE_TIME -> "readZonedDateTime";
            case OFFSET_DATE_TIME -> "readOffsetDateTime";
            default -> null;
        };
    }
}
