package io.koraframework.json.annotation.processor.codec;

import com.alibaba.fastjson2.util.*;
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
import java.nio.charset.*;
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

//        var legacyJacksonReadMethod = MethodSpec.methodBuilder("read")
//            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
//            .addParameter(jsonParser, "parser")
//            .addException(jacksonException)
//            .addAnnotation(Override.class)
//            .returns(targetType.withoutAnnotations().annotated(CommonClassNames.nullableAnnotation))
//            .addStatement("return null");
//        typeBuilder.addMethod(legacyJacksonReadMethod.build());

        var readMethod = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(FASTJSON_READER, "__reader")
            .addAnnotation(Override.class)
            .returns(targetType.withoutAnnotations().annotated(CommonClassNames.nullableAnnotation));

        readMethod.beginControlFlow("if (!__reader.nextIfObjectStart())")
            .beginControlFlow("if (__reader.wasNull())")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("throw new $T(\"Expected object start\")", FASTJSON_EXCEPTION)
            .endControlFlow();

        int fieldCount = meta.fields().size();
        int[] requiredBitIndex = new int[fieldCount];
        var packedRequiredNames = new StringBuilder();
        int requiredCount = 0;

        var intHashes = new HashSet<Integer>(fieldCount);
        boolean canUseSwitch = fieldCount >= 4;

        for (int i = 0; i < fieldCount; i++) {
            var field = meta.fields().get(i);
            long hash = com.alibaba.fastjson2.util.Fnv.hashCode64(field.jsonName());
            if (!intHashes.add((int) hash)) {
                canUseSwitch = false;
            }

            if (!isNullable(field)) {
                if (requiredCount > 0) packedRequiredNames.append('\0');
                packedRequiredNames.append(field.jsonName());
                requiredBitIndex[i] = requiredCount++;
            } else {
                requiredBitIndex[i] = -1;
            }
        }

        if (requiredCount > 0) {
            if (requiredCount <= 32) {
                readMethod.addStatement("int __receivedFields = 0");
            } else {
                readMethod.addStatement("var __receivedFields = new $T($L)", BitSet.class, requiredCount);
            }
            readMethod.addCode("\n");
        }

        addFieldVariables(readMethod, meta);

        readMethod.beginControlFlow("while (!__reader.nextIfObjectEnd())");
        readMethod.addStatement("long __hash = __reader.readFieldNameHashCode()");

        if (fieldCount == 0) {
            readMethod.addStatement("__reader.skipValue()");
        } else if (canUseSwitch) {
            readMethod.beginControlFlow("switch ((int) __hash)");
            for (int i = 0; i < fieldCount; i++) {
                var field = meta.fields().get(i);
                String rawFieldName = field.parameter().getSimpleName().toString();
                String hashFieldName = "HASH_" + rawFieldName;

                readMethod.addCode("case (int) $L:$>\n", hashFieldName);
                readMethod.beginControlFlow("if (__hash == $L)", hashFieldName);
                emitFieldReadAndMark(readMethod, field, requiredBitIndex[i], requiredCount);
                readMethod.addStatement("continue");
                readMethod.endControlFlow();
                readMethod.addStatement("break");
                readMethod.addCode("$<");
            }
            readMethod.endControlFlow();
            readMethod.addStatement("__reader.skipValue()");
        } else {
            for (int i = 0; i < fieldCount; i++) {
                var field = meta.fields().get(i);
                String rawFieldName = field.parameter().getSimpleName().toString();
                String hashFieldName = "HASH_" + rawFieldName;

                if (i == 0) {
                    readMethod.beginControlFlow("if (__hash == $L)", hashFieldName);
                } else {
                    readMethod.nextControlFlow("else if (__hash == $L)", hashFieldName);
                }
                emitFieldReadAndMark(readMethod, field, requiredBitIndex[i], requiredCount);
            }
            readMethod.nextControlFlow("else");
            readMethod.addStatement("__reader.skipValue()");
            readMethod.endControlFlow();
        }

        readMethod.endControlFlow();
        readMethod.addCode("\n");

        if (requiredCount > 0) {
            String simpleDtoName = meta.typeElement().getSimpleName().toString();
            if (requiredCount <= 32) {
                int allRequiredMask = (requiredCount == 32) ? -1 : ((1 << requiredCount) - 1);
                readMethod.beginControlFlow("if (__receivedFields != $L)", allRequiredMask);
                readMethod.addStatement("throw missingRequiredFields(__reader, __receivedFields, $L, $S, $S)",
                    allRequiredMask, packedRequiredNames.toString(), simpleDtoName);
                readMethod.endControlFlow();
            } else {
                readMethod.beginControlFlow("if (__receivedFields.cardinality() != $L)", requiredCount);
                readMethod.addStatement("throw missingRequiredFields(__reader, __receivedFields, $L, $S, $S)",
                    requiredCount, packedRequiredNames.toString(), simpleDtoName);
                readMethod.endControlFlow();
            }
            readMethod.addCode("\n");
        }

        readMethod.addCode("return new $T(", meta.typeElement());
        for (int i = 0; i < fieldCount; i++) {
            readMethod.addCode("$L", meta.fields().get(i).parameter().getSimpleName());
            if (i < fieldCount - 1) readMethod.addCode(", ");
        }
        readMethod.addCode(");\n");

        typeBuilder.addMethod(readMethod.build());
    }

    private void emitFieldReadAndMark(MethodSpec.Builder readMethod, JsonClassReaderMeta.FieldMeta field, int bitIdx, int requiredCount) {
        String commonReadMethod = getCommonReadMethodName(field);
        if (commonReadMethod != null) {
            readMethod.addStatement("$L = __reader.$L()", field.parameter().getSimpleName(), commonReadMethod);
        } else {
            var readerName = field.parameter().getSimpleName() + "Reader";
            readMethod.addStatement("$L = $L.read(__reader)", field.parameter().getSimpleName(), readerName);
        }

        if (bitIdx >= 0) {
            if (requiredCount <= 32) {
                readMethod.addStatement("__receivedFields |= $L", 1 << bitIdx);
            } else {
                readMethod.addStatement("__receivedFields.set($L)", bitIdx);
            }
        }
    }

    private void addCombinedDependencies(TypeSpec.Builder typeBuilder, @Nullable JsonClassReaderMeta readerMeta, @Nullable JsonClassWriterMeta writerMeta) {
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var addedFields = new HashSet<String>();
        boolean hasParameters = false;

        if (writerMeta != null) {
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
                } else if (field.writerTypeMeta() instanceof WriterFieldType.UnknownWriterFieldType
                    || (field.writerTypeMeta() instanceof WriterFieldType.KnownWriterFieldType k && isCustomTemporalType(k.knownType()))) {
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
        }

        if (readerMeta != null) {
            for (var field : readerMeta.fields()) {
                if (field.reader() == null
                    && field.typeMeta() instanceof KnownTypeReaderMeta knownMeta
                    && !isCustomTemporalType(knownMeta.knownType())) {
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
        }

        if (hasParameters) {
            typeBuilder.addMethod(constructor.build());
        }
    }

    private void generateWriteMethod(TypeSpec.Builder typeBuilder, JsonClassWriterMeta meta) {
        var targetType = TypeName.get(meta.typeMirror());

//        var legacyJacksonWriteMethod = MethodSpec.methodBuilder("write")
//            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
//            .addParameter(jsonGenerator, "generator")
//            .addParameter(ParameterSpec.builder(targetType.annotated(CommonClassNames.nullableAnnotation), "object").build())
//            .addException(jacksonException)
//            .addAnnotation(Override.class);
//        typeBuilder.addMethod(legacyJacksonWriteMethod.build());

        var writeMethod = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(FASTJSON_WRITER, "_gen")
            .addParameter(ParameterSpec.builder(targetType.annotated(CommonClassNames.nullableAnnotation), "_object").build())
            .addAnnotation(Override.class);

        writeMethod.beginControlFlow("if (_object == null)")
            .addStatement("_gen.writeNull()")
            .addStatement("return")
            .endControlFlow();
        writeMethod.addStatement("_gen.startObject()");

        var discriminatorField = JsonUtils.discriminatorField(types, meta.typeElement());
        if (discriminatorField != null) {
            if (meta.fields().stream().noneMatch(f -> f.jsonName().equals(discriminatorField))) {
                var discriminatorFieldValues = JsonUtils.discriminatorValue(meta.typeElement());
                byte[] discBytes = ("\"" + discriminatorField + "\":").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var discInit = CodeBlock.builder().add("{ ");
                for (int i = 0; i < discBytes.length; i++) {
                    discInit.add("$L", discBytes[i]);
                    if (i < discBytes.length - 1) discInit.add(", ");
                }
                discInit.add(" }");
                typeBuilder.addField(FieldSpec.builder(byte[].class, "BYTES_DISCRIMINATOR", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer(discInit.build())
                    .build());
                writeMethod.addStatement("_gen.writeNameRaw(BYTES_DISCRIMINATOR)");
                writeMethod.addStatement("_gen.writeString($S)", discriminatorFieldValues.getFirst());
            }
        }
        for (var field : meta.fields()) {
            addWriteParam(writeMethod, field);
        }
        writeMethod.addStatement("_gen.endObject()");
        typeBuilder.addMethod(writeMethod.build());
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
            if (field.writer() == null
                && field.writerTypeMeta() instanceof WriterFieldType.KnownWriterFieldType typeMeta
                && !isCustomTemporalType(typeMeta.knownType())) {
                method.addCode(writeKnownType(typeMeta.knownType(), CodeBlock.of("_object.$L", field.accessor()), false));
            } else {
                method.addStatement("$L.write(_gen, _object.$L)", writerFieldName, field.accessor());
            }
            return;
        }

        String localVarName = "_" + rawFieldName;
        method.addStatement("var $L = _object.$L", localVarName, field.accessor());

        var isEmptyCheck = field.includeType() == JsonClassWriterMeta.IncludeType.NON_EMPTY
            && (CommonUtils.isCollection(field.writerTypeMeta().typeMirror()) || CommonUtils.isMap(field.writerTypeMeta().typeMirror()));

        if (field.writerTypeMeta().isJsonNullable()) {
            method.beginControlFlow("if ($L.isDefined())", localVarName);
            if (isEmptyCheck) {
                method.beginControlFlow("if ($L.value() != null && !$L.value().isEmpty())", localVarName, localVarName);
            }
        } else if (isEmptyCheck) {
            method.beginControlFlow("if ($L != null && !$L.isEmpty())", localVarName, localVarName);
        } else if (field.includeType() != JsonClassWriterMeta.IncludeType.ALWAYS) {
            method.beginControlFlow("if ($L != null)", localVarName);
        }

        method.addStatement("_gen.writeNameRaw($L)", bytesFieldName);
        if (field.writer() == null
            && field.writerTypeMeta() instanceof WriterFieldType.KnownWriterFieldType typeMeta
            && !isCustomTemporalType(typeMeta.knownType())) {
            if (typeMeta.isJsonNullable()) {
                method.beginControlFlow("if ($L.isNull())", localVarName)
                    .addStatement("_gen.writeNull()")
                    .nextControlFlow("else")
                    .addCode(writeKnownType(typeMeta.knownType(), CodeBlock.of("$L.value()", localVarName), true))
                    .endControlFlow();
            } else if (field.includeType() == JsonClassWriterMeta.IncludeType.ALWAYS) {
                method.beginControlFlow("if ($L == null)", localVarName)
                    .addStatement("_gen.writeNull()")
                    .nextControlFlow("else")
                    .addCode(writeKnownType(typeMeta.knownType(), CodeBlock.of("$L", localVarName), true))
                    .endControlFlow();
            } else {
                method.addCode(writeKnownType(typeMeta.knownType(), CodeBlock.of("$L", localVarName), true));
            }
        } else {
            if (field.writerTypeMeta().isJsonNullable()) {
                method.beginControlFlow("if ($L.isNull())", localVarName)
                    .addStatement("_gen.writeNull()")
                    .nextControlFlow("else")
                    .addStatement("$L.write(_gen, $L.value())", writerFieldName, localVarName)
                    .endControlFlow();
            } else {
                method.addStatement("$L.write(_gen, $L)", writerFieldName, localVarName);
            }
        }

        if (field.writerTypeMeta().isJsonNullable()) {
            if (isEmptyCheck) method.endControlFlow();
            method.endControlFlow();
        } else if (field.includeType() != JsonClassWriterMeta.IncludeType.ALWAYS) {
            method.endControlFlow();
        }
    }

    private CodeBlock writeKnownType(KnownType.KnownTypesEnum knownType, CodeBlock value, boolean unboxWrapper) {
        return switch (knownType) {
            case STRING -> CodeBlock.of("_gen.writeString($L);\n", value);

            case BOOLEAN_PRIMITIVE -> CodeBlock.of("_gen.writeBool($L);\n", value);
            case BOOLEAN_OBJECT -> unboxWrapper
                ? CodeBlock.of("_gen.writeBool($L.booleanValue());\n", value)
                : CodeBlock.of("_gen.writeBool($L);\n", value);

            case INTEGER_PRIMITIVE -> CodeBlock.of("_gen.writeInt32($L);\n", value);
            case INTEGER_OBJECT -> unboxWrapper
                ? CodeBlock.of("_gen.writeInt32($L.intValue());\n", value)
                : CodeBlock.of("_gen.writeInt32($L);\n", value);

            case LONG_PRIMITIVE -> CodeBlock.of("_gen.writeInt64($L);\n", value);
            case LONG_OBJECT -> unboxWrapper
                ? CodeBlock.of("_gen.writeInt64($L.longValue());\n", value)
                : CodeBlock.of("_gen.writeInt64($L);\n", value);

            case FLOAT_PRIMITIVE -> CodeBlock.of("_gen.writeFloat($L);\n", value);
            case FLOAT_OBJECT -> unboxWrapper
                ? CodeBlock.of("_gen.writeFloat($L.floatValue());\n", value)
                : CodeBlock.of("_gen.writeFloat($L);\n", value);

            case DOUBLE_PRIMITIVE -> CodeBlock.of("_gen.writeDouble($L);\n", value);
            case DOUBLE_OBJECT -> unboxWrapper
                ? CodeBlock.of("_gen.writeDouble($L.doubleValue());\n", value)
                : CodeBlock.of("_gen.writeDouble($L);\n", value);

            case SHORT_PRIMITIVE -> CodeBlock.of("_gen.writeInt16($L);\n", value);
            case SHORT_OBJECT -> unboxWrapper
                ? CodeBlock.of("_gen.writeInt16($L.shortValue());\n", value)
                : CodeBlock.of("_gen.writeInt16($L);\n", value);

            case BYTE_PRIMITIVE -> CodeBlock.of("_gen.writeInt8($L);\n", value);
            case BYTE_OBJECT -> unboxWrapper
                ? CodeBlock.of("_gen.writeInt8($L.byteValue());\n", value)
                : CodeBlock.of("_gen.writeInt8($L);\n", value);

            case BIG_DECIMAL -> CodeBlock.of("_gen.writeDecimal($L);\n", value);
            case BIG_INTEGER -> CodeBlock.of("_gen.writeBigInt($L);\n", value);
            case BINARY -> CodeBlock.of("_gen.writeBinary($L);\n", value);

            case UUID -> CodeBlock.of("_gen.writeString($L.toString());\n", value);
            case LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME, INSTANT ->
                throw new AssertionError("Temporal type must be handled via JsonWriter: " + knownType);
        };
    }

    private boolean isNullable(JsonClassReaderMeta.FieldMeta field) {
        if (field.parameter().asType().getKind().isPrimitive()) return false;
        if (field.typeMeta() != null && field.typeMeta().isJsonNullable()) return true;
        return CommonUtils.isNullable(field.parameter());
    }

    private static boolean isCustomTemporalType(KnownType.KnownTypesEnum type) {
        return switch (type) {
            case INSTANT, LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME -> true;
            default -> false;
        };
    }

    private @Nullable String getCommonReadMethodName(JsonClassReaderMeta.FieldMeta field) {
        if (field.reader() != null) {
            return null;
        }
        if (!(field.typeMeta() instanceof KnownTypeReaderMeta knownMeta) || isCustomTemporalType(knownMeta.knownType())) {
            return null;
        }

        boolean required = !isNullable(field);
        return switch (knownMeta.knownType()) {
            case STRING -> "readString";
            case BOOLEAN_PRIMITIVE -> "readBoolValue";
            case BOOLEAN_OBJECT -> required ? "readBoolValue" : "readBool";
            case INTEGER_PRIMITIVE -> "readInt32Value";
            case INTEGER_OBJECT -> required ? "readInt32Value" : "readInt32";
            case LONG_PRIMITIVE -> "readInt64Value";
            case LONG_OBJECT -> required ? "readInt64Value" : "readInt64";
            case FLOAT_PRIMITIVE -> "readFloatValue";
            case FLOAT_OBJECT -> required ? "readFloatValue" : "readFloat";
            case DOUBLE_PRIMITIVE -> "readDoubleValue";
            case DOUBLE_OBJECT -> required ? "readDoubleValue" : "readDouble";
            case SHORT_PRIMITIVE -> "readInt16Value";
            case SHORT_OBJECT -> required ? "readInt16Value" : "readInt16";
            case BIG_DECIMAL -> "readBigDecimal";
            case BIG_INTEGER -> "readBigInteger";
            case UUID -> "readUUID";
            default -> null;
        };
    }
}
