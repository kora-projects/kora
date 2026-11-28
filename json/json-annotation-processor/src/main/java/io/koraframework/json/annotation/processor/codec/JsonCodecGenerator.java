package io.koraframework.json.annotation.processor.codec;

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
import java.util.*;

public class JsonCodecGenerator {
    private final Types types;

    public JsonCodecGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
    }

    @Nullable
    public TypeSpec generate(JsonClassReaderMeta readerMeta, JsonClassWriterMeta writerMeta) {
        var typeElement = readerMeta.typeElement();
        var targetType = TypeName.get(typeElement.asType());
        String codecClassName = JsonUtils.jsonReaderName(typeElement).replace("JsonReader", "JsonCodec");

        var typeBuilder = TypeSpec.classBuilder(codecClassName)
            .addAnnotation(AnnotationUtils.generated(JsonCodecGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, targetType))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonWriter, targetType))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(typeElement);

        for (TypeParameterElement typeParameter : typeElement.getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }

        for (var field : writerMeta.fields()) {
            String rawFieldName = field.field().getSimpleName().toString();
            String stringFieldName = "STR_" + rawFieldName;
            String serializedFieldName = jsonNameStaticName(rawFieldName);
            typeBuilder.addField(FieldSpec.builder(String.class, stringFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", field.jsonName())
                .build());
            typeBuilder.addField(FieldSpec.builder(JsonTypes.serializedString, serializedFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T($L)", JsonTypes.serializedString, stringFieldName)
                .build());
        }
        addBitSet(typeBuilder, readerMeta);
        addCombinedDependencies(typeBuilder, readerMeta, writerMeta);
        generateReadMethod(typeBuilder, readerMeta);
        generateWriteMethod(typeBuilder, writerMeta);

        return typeBuilder.build();
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
                        fieldType = ParameterizedTypeName.get(JsonTypes.jsonReader, TypeName.get(field.writerTypeMeta().typeMirror()));
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
                    var fieldType = ParameterizedTypeName.get(JsonTypes.jsonWriter, TypeName.get(field.writerTypeMeta().typeMirror()));
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
                    fieldType = ParameterizedTypeName.get(JsonTypes.jsonReader, field.typeName());
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
        var method = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonGenerator, "_gen")
            .addParameter(ParameterSpec.builder(TypeName.get(meta.typeMirror()).annotated(CommonClassNames.nullableAnnotation), "_object").build())
            .addAnnotation(Override.class);

        method.addCode("if (_object == null) {$>\n_gen.writeNull();\nreturn;$<\n}\n");
        method.addStatement("_gen.writeStartObject(_object)");

        var discriminatorField = JsonUtils.discriminatorField(types, meta.typeElement());
        if (discriminatorField != null) {
            if (meta.fields().stream().noneMatch(f -> f.jsonName().equals(discriminatorField))) {
                var discriminatorFieldValues = JsonUtils.discriminatorValue(meta.typeElement());
                method.addCode("_gen.writeName($S);\n", discriminatorField);
                method.addStatement("_gen.writeString($S)", discriminatorFieldValues.getFirst());
            }
        }
        for (var field : meta.fields()) {
            this.addWriteParam(method, field);
        }
        method.addStatement("_gen.writeEndObject()");
        typeBuilder.addMethod(method.build());
    }

    private void generateReadMethod(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .addAnnotation(Override.class)
            .returns(TypeName.get(meta.typeElement().asType()).withoutAnnotations().annotated(CommonClassNames.nullableAnnotation));

        method.addStatement("var __token = __parser.currentToken()");
        method.addCode("if (__token == VALUE_NULL) $>\nreturn null;$<\n");
        assertTokenType(method, "START_OBJECT", "ERR_OBJECT_EXPECTED");

        if (meta.fields().size() <= 32) {
            method.addStatement("int __receivedFields = NULLABLE_FIELDS_RECEIVED");
        } else {
            method.addStatement("var __receivedFields = ($T) NULLABLE_FIELDS_RECEIVED.clone()", BitSet.class);
        }
        method.addCode("\n");

        String simpleDtoName = meta.typeElement().getSimpleName().toString();
        typeBuilder.addField(FieldSpec.builder(String.class, "CLASS_NAME", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", simpleDtoName)
            .build());

        addFieldVariables(method, meta);
        addFastPath(method, meta);

        method.addStatement("String __fieldName");
        method.addCode("while ((__fieldName = __parser.nextName()) != null) {$>\n");
        method.addCode("if (__fieldName.isEmpty()) continue;\n");
        method.addCode("switch (__fieldName.charAt(0)) {$>\n");

        var groups = new LinkedHashMap<Character, List<Integer>>();
        for (int i = 0; i < meta.fields().size(); i++) {
            String rawFieldName = meta.fields().get(i).parameter().getSimpleName().toString();
            if (!rawFieldName.isEmpty()) {
                char firstChar = rawFieldName.charAt(0);
                groups.computeIfAbsent(firstChar, k -> new ArrayList<>()).add(i);
            }
        }

        for (var entry : groups.entrySet()) {
            char c = entry.getKey();
            var fieldIndices = entry.getValue();

            method.addCode("case '$L': {$>\n", c);

            var byLength = new LinkedHashMap<Integer, List<Integer>>();
            for (int idx : fieldIndices) {
                String rawFieldName = meta.fields().get(idx).parameter().getSimpleName().toString();
                byLength.computeIfAbsent(rawFieldName.length(), k -> new ArrayList<>()).add(idx);
            }

            if (byLength.size() == 1 && byLength.values().iterator().next().size() == 1) {
                int idx = fieldIndices.getFirst();
                var field = meta.fields().get(idx);
                String rawFieldName = field.parameter().getSimpleName().toString();
                String stringFieldName = "STR_" + rawFieldName;

                method.addCode("if (__fieldName.equals($L)) {$>\n", stringFieldName);
                String commonReadMethod = getCommonReadMethodName(field);
                if (commonReadMethod != null) {
                    method.addCode("$L = $L(__parser, CLASS_NAME, $L);\n", field.parameter().getSimpleName(), commonReadMethod, stringFieldName);
                } else {
                    var readerName = field.parameter().getSimpleName() + "Reader";
                    method.addStatement("$L = readRequiredObject(__parser, $L, CLASS_NAME, $L)", field.parameter().getSimpleName(), readerName, stringFieldName);
                }
                method.addCode(markReceived(meta, idx));
                method.addCode("} else {$>\n__parser.nextToken();\n__parser.skipChildren();\n$<}\n");
                method.addCode("$<");
            } else {
                method.addStatement("var __len = __fieldName.length()");
                int lenCount = 0;
                for (Map.Entry<Integer, List<Integer>> lenEntry : byLength.entrySet()) {
                    int len = lenEntry.getKey();
                    List<Integer> lenIndices = lenEntry.getValue();

                    if (lenCount == 0) {
                        method.addCode("if (__len == $L) {$>\n", len);
                    } else {
                        method.addCode("} else if (__len == $L) {$>\n", len);
                    }
                    lenCount++;

                    int fieldCount = 0;
                    for (int idx : lenIndices) {
                        var field = meta.fields().get(idx);
                        String rawFieldName = field.parameter().getSimpleName().toString();
                        String stringFieldName = "STR_" + rawFieldName;

                        if (fieldCount == 0) {
                            method.addCode("if (__fieldName.equals($L)) {$>\n", stringFieldName);
                        } else {
                            method.addCode("} else if (__fieldName.equals($L)) {$>\n", stringFieldName);
                        }
                        fieldCount++;

                        String commonReadMethod = getCommonReadMethodName(field);
                        if (commonReadMethod != null) {
                            method.addCode("$L = $L(__parser, CLASS_NAME, $L);\n", field.parameter().getSimpleName(), commonReadMethod, stringFieldName);
                        } else {
                            var readerName = field.parameter().getSimpleName() + "Reader";
                            method.addStatement("$L = readRequiredObject(__parser, $L, CLASS_NAME, $L)", field.parameter().getSimpleName(), readerName, stringFieldName);
                        }
                        method.addCode(markReceived(meta, idx));
                    }
                    method.addCode("} else {$>\n__parser.nextToken();\n__parser.skipChildren();\n$<}\n");
                    method.addCode("$<");
                }
                method.addCode("} else {$>\n__parser.nextToken();\n__parser.skipChildren();\n$<}\n");
                method.addCode("$<");
            }
            method.addCode("break;\n$<}\n");
        }

        method.addCode("default: {$>\n__parser.nextToken();\n__parser.skipChildren();\nbreak;\n$<}\n");
        method.addCode("$<\n}\n");
        method.addCode("$<}\n");

        method.addCode("if (__parser.currentToken() != END_OBJECT) {$>\n");
        method.addCode("    throw __unexpectedToken(__parser, CLASS_NAME, ERR_FIELD_NAME_EXPECTED);\n");
        method.addCode("$<}\n");

        method.addCode("""
            if (__receivedFields != ALL_FIELDS_RECEIVED) {
                throw __throwMissingRequiredFields(__parser, __receivedFields);
            }
            """);

        method.addCode("return new $T(", meta.typeElement());
        for (int i = 0; i < meta.fields().size(); i++) {
            method.addCode("$L", meta.fields().get(i).parameter().getSimpleName());
            if (i < meta.fields().size() - 1) method.addCode(", ");
        }
        method.addCode(");");

        typeBuilder.addMethod(method.build());
        generateMissingFieldsExceptionMethod(typeBuilder, meta);
    }

    private void addFastPath(MethodSpec.Builder method, JsonClassReaderMeta meta) {
        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            String rawFieldName = field.parameter().getSimpleName().toString();
            String stringFieldName = "STR_" + rawFieldName;
            String serializedFieldName = this.jsonNameStaticName(rawFieldName);
            method.addCode("if (__parser.nextName($L)) {$>\n", serializedFieldName);

            String commonReadMethod = this.getCommonReadMethodName(field);
            if (commonReadMethod != null) {
                method.addCode("$L = $L(__parser, CLASS_NAME, $L);\n",
                    field.parameter().getSimpleName(), commonReadMethod, stringFieldName);
            } else {
                var readerName = field.parameter().getSimpleName() + "Reader";
                method.addStatement("$L = readRequiredObject(__parser, $L, CLASS_NAME, $L)", field.parameter().getSimpleName(), readerName, stringFieldName);
            }

            method.addCode(markReceived(meta, i));

            if (i == meta.fields().size() - 1) {
                method.addCode("""
                    __token = __parser.nextToken();
                    while (__token != END_OBJECT) {
                        __parser.nextToken();
                        __parser.skipChildren();
                        __token = __parser.nextToken();
                    }
                    """);
                method.addCode("return new $T(", meta.typeMirror());
                for (int j = 0; j < meta.fields().size(); j++) {
                    method.addCode("$L", meta.fields().get(j).parameter());
                    if (j < meta.fields().size() - 1) method.addCode(", ");
                }
                method.addCode(");$<\n");
            }
        }
        for (int i = 0; i < meta.fields().size(); i++) {
            method.addCode("}");
            if (i < meta.fields().size() - 1) method.addCode("$<");
            method.addCode("\n");
        }
    }

    private void generateMissingFieldsExceptionMethod(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        var errorSwitch = CodeBlock.builder();
        for (int i = 0; i < meta.fields().size(); i++) {
            String rawFieldName = meta.fields().get(i).parameter().getSimpleName().toString();
            String stringFieldName = "STR_" + rawFieldName;
            if (i == 0) {
                errorSwitch.add("(__i == 0) ? $L : ", stringFieldName);
            } else {
                errorSwitch.add("(__i == $L) ? $L : ", i, stringFieldName);
            }
        }
        errorSwitch.add("$S", "");

        var fieldType = meta.fields().size() > 32 ? ClassName.get(BitSet.class) : TypeName.INT;
        var jsonReader = ClassName.get("io.koraframework.json.common", "JsonCommonReader");

        var methodBuilder = MethodSpec.methodBuilder("__throwMissingRequiredFields")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .addParameter(fieldType, "__receivedFields")
            .returns(JsonTypes.jsonParseException);

        if (meta.fields().size() > 32) {
            methodBuilder.addCode("""
                __receivedFields.flip(0, $L);
                var __missing = new $T();
                for (int __i = __receivedFields.nextSetBit(0); __i >= 0; __i = __receivedFields.nextSetBit(__i+1)) {
                    if (__missing.length() > 0) __missing.append(", ");
                    __missing.append($L);
                }
                return $T.createMissingRequiredFieldsException(__parser, CLASS_NAME, __missing.toString());
                """, meta.fields().size(), StringBuilder.class, errorSwitch.build(), jsonReader);
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
                return $T.createMissingRequiredFieldsException(__parser, CLASS_NAME, __missing.toString());
                """, StringBuilder.class, meta.fields().size(), errorSwitch.build(), jsonReader);
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
                method.addCode(" = $T.undefined();\n", JsonTypes.jsonNullable);
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
        String serializedFieldName = this.jsonNameStaticName(rawFieldName);
        String writerFieldName = field.accessor().getSimpleName() + "Writer";

        if (field.typeMirror().getKind().isPrimitive()) {
            method.addCode("_gen.writeName($L);\n", serializedFieldName);
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

        method.addCode("_gen.writeName($L);\n", serializedFieldName);
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
            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> CodeBlock.of("_gen.writeBoolean($L);\n", value);
            case INTEGER_OBJECT, BIG_INTEGER, BIG_DECIMAL, DOUBLE_OBJECT, FLOAT_OBJECT, LONG_OBJECT, SHORT_OBJECT, BYTE_OBJECT,
                 INTEGER_PRIMITIVE, DOUBLE_PRIMITIVE, FLOAT_PRIMITIVE, LONG_PRIMITIVE, SHORT_PRIMITIVE, BYTE_PRIMITIVE -> CodeBlock.of("_gen.writeNumber($L);\n", value);
            case BINARY -> CodeBlock.of("_gen.writeBinary($L);\n", value);
            case UUID, INSTANT, LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME -> CodeBlock.of("_gen.writeString($L.toString());\n", value);
        };
    }

    private boolean isNullable(JsonClassReaderMeta.FieldMeta field) {
        if (field.parameter().asType().getKind().isPrimitive()) return false;
        if (field.typeMeta() != null && field.typeMeta().isJsonNullable()) return true;
        return CommonUtils.isNullable(field.parameter());
    }

    private void assertTokenType(MethodSpec.Builder method, String expectedToken, String expectedPhraseConstantName) {
        method.addCode("if (__token != $L) $>\nthrow __unexpectedToken(__parser, CLASS_NAME, $L);$<\n", expectedToken, expectedPhraseConstantName);
    }

    private CodeBlock markReceived(JsonClassReaderMeta meta, int index) {
        return meta.fields().size() > 32 ? CodeBlock.of("__receivedFields.set($L);\n", index) : CodeBlock.of("__receivedFields |= (1 << $L);\n", index);
    }

    private String jsonNameStaticName(String name) {
        return "_" + name + "optimized_field_name";
    }

    private String getCommonReadMethodName(JsonClassReaderMeta.FieldMeta field) {
        if (field.reader() != null) return null;
        if (!(field.typeMeta() instanceof KnownTypeReaderMeta metaData)) return null;

        boolean isPrimitive = field.parameter().asType().getKind().isPrimitive();

        return switch (metaData.knownType()) {
            case STRING -> "readString";
            case LONG_PRIMITIVE, LONG_OBJECT -> isPrimitive ? "readLongPrimitive" : "readLongObject";
            case INTEGER_PRIMITIVE, INTEGER_OBJECT -> isPrimitive ? "readIntegerPrimitive" : "readIntegerObject";
            case BOOLEAN_PRIMITIVE, BOOLEAN_OBJECT -> isPrimitive ? "readBooleanPrimitive" : "readBooleanObject";
            case DOUBLE_PRIMITIVE, DOUBLE_OBJECT -> isPrimitive ? "readDoublePrimitive" : "readDoubleObject";
            case FLOAT_PRIMITIVE, FLOAT_OBJECT -> isPrimitive ? "readFloatPrimitive" : "readFloatObject";
            case SHORT_PRIMITIVE, SHORT_OBJECT -> isPrimitive ? "readShortPrimitive" : "readShortObject";
            case BYTE_PRIMITIVE, BYTE_OBJECT -> isPrimitive ? "readBytePrimitive" : "readByteObject";
            case UUID -> "readUUID";
            case BIG_DECIMAL -> "readBigDecimal";
            case BIG_INTEGER -> "readBigInteger";
            case INSTANT -> "readInstant";
            case LOCAL_DATE -> "readLocalDate";
            case LOCAL_DATE_TIME -> "readLocalDateTime";
            case OFFSET_DATE_TIME -> "readOffsetDateTime";
            default -> null;
        };
    }
}
