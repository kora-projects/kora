package ru.tinkoff.kora.json.annotation.processor.reader;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonClassReaderMeta.FieldMeta;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderFieldType.KnownTypeReaderMeta;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Arrays;
import java.util.BitSet;
import java.util.UUID;

public class JsonReaderGenerator {
    private final Types types;

    public JsonReaderGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
    }

    @Nullable
    public TypeSpec generate(JsonClassReaderMeta meta) {
        return this.generateForClass(meta);
    }

    private boolean isNullable(JsonClassReaderMeta.FieldMeta field) {
        if (field.parameter().asType().getKind().isPrimitive()) {
            return false;
        }
        if (field.typeMeta() != null && field.typeMeta().isJsonNullable()) {
            return true;
        }

        return CommonUtils.isNullable(field.parameter());
    }

    private TypeSpec generateForClass(JsonClassReaderMeta meta) {
        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonReaderName(meta.typeElement()))
            .addAnnotation(AnnotationUtils.generated(JsonReaderGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(JsonTypes.jsonReader, TypeName.get(meta.typeMirror())))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(meta.typeElement());

        for (TypeParameterElement typeParameter : meta.typeElement().getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }


        this.addBitSet(typeBuilder, meta);
        this.addReaders(typeBuilder, meta);
        this.addFieldNames(typeBuilder, meta);
        this.addReadMethods(typeBuilder, meta);

        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .returns(TypeName.get(meta.typeElement().asType()).withoutAnnotations().annotated(CommonClassNames.nullableAnnotation));
        method.addStatement("var __token = __parser.currentToken()");
        method.addCode("if (__token == $T.VALUE_NULL) $>\nreturn null;$<\n", JsonTypes.jsonToken);
        assertTokenType(method, "START_OBJECT");

        if (meta.fields().size() <= 32) {
            method.addStatement("var __receivedFields = new int[]{NULLABLE_FIELDS_RECEIVED}");
        } else {
            method.addStatement("var __receivedFields = ($T) NULLABLE_FIELDS_RECEIVED.clone()", BitSet.class);
        }
        method.addCode("\n");

        this.addFieldVariables(method, meta);
        this.addFastPath(method, meta);

        if (meta.fields().isEmpty()) {
            method.addStatement("__token = __parser.nextToken()");
        } else {
            method.addStatement("__token = __parser.currentToken()");
        }
        method.addCode("while (__token != $T.END_OBJECT) {$>\n", JsonTypes.jsonToken);
        assertTokenType(method, "PROPERTY_NAME");
        method.addStatement("var __fieldName = __parser.currentName()");
        method.addCode("switch (__fieldName) {$>\n");
        for (int i = 0, fieldsSize = meta.fields().size(); i < fieldsSize; i++) {
            var field = meta.fields().get(i);
            method.addCode("case $S -> {$>\n", field.jsonName());
            method.addCode("$L = $L(__parser, __receivedFields);", field.parameter(), this.readerMethodName(field));
            method.addCode("$<\n}\n");
        }


        method.addCode("default -> {$>\n__parser.nextToken();\n__parser.skipChildren();$<\n}");
        method.addCode("$<\n}\n");
        method.addCode("__token = __parser.nextToken();");

        method.addCode("$<\n}\n");
        var errorSwitch = CodeBlock.builder()
            .add("switch (__i) {$>");
        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            errorSwitch.add("\n    case $L -> $S;", i, "%s(%s)".formatted(field.parameter().getSimpleName(), field.jsonName()));
        }
        errorSwitch.add("\n    default -> \"\";");
        errorSwitch.add("$<\n    }");

        if (meta.fields().size() > 32) {
            method.addCode("""
                if (!__receivedFields.equals(ALL_FIELDS_RECEIVED)) {
                  __receivedFields.flip(0, $L);
                  var __error = new $T("Some of required json fields were not received:");
                  for (int __i = __receivedFields.nextSetBit(0); __i >= 0; __i = __receivedFields.nextSetBit(__i+1)) {
                    __error.append(" ").append($L);
                  }
                  throw new $T(__parser, __error.toString());
                }
                """, meta.fields().size(), StringBuilder.class, errorSwitch.build(), JsonTypes.jsonParseException);
        } else {
            method.addCode("""
                if (__receivedFields[0] != ALL_FIELDS_RECEIVED) {
                  var _nonReceivedFields = (~__receivedFields[0]) & ALL_FIELDS_RECEIVED;
                  var __error = new $T("Some of required json fields were not received:");
                  for (int __i = 0; __i < $L; __i++) {
                    if ((_nonReceivedFields & (1 << __i)) != 0) {
                      __error.append(" ").append($L);
                    }
                  }
                  throw new $T(__parser, __error.toString());
                }
                """, StringBuilder.class, meta.fields().size(), errorSwitch.build(), JsonTypes.jsonParseException);
        }

        method.addCode("return new $T(", meta.typeElement());
        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            method.addCode("$L", field.parameter().getSimpleName());
            if (i < meta.fields().size() - 1) {
                method.addCode(", ");
            }
        }
        method.addCode(");");


        typeBuilder.addMethod(method.build());

        return typeBuilder.build();
    }

    private void addBitSet(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        if (meta.fields().size() <= 32) {
            var sb = new StringBuilder();
            for (int i = meta.fields().size() - 1; i >= 0; i--) {
                var f = meta.fields().get(i);
                sb.append(isNullable(f) ? "1" : "0");
            }
            var nullableFieldsReceived = meta.fields().isEmpty()
                ? "0"
                : "0b" + sb;
            var allFieldsReceived = meta.fields().isEmpty()
                ? "0"
                : "0b" + "1".repeat(meta.fields().size());

            typeBuilder
                .addField(FieldSpec.builder(TypeName.INT, "ALL_FIELDS_RECEIVED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer(CodeBlock.of(allFieldsReceived))
                    .build())
                .addField(FieldSpec.builder(TypeName.INT, "NULLABLE_FIELDS_RECEIVED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer(CodeBlock.of(nullableFieldsReceived))
                    .build());
        } else {
            typeBuilder
                .addField(ClassName.get(BitSet.class), "ALL_FIELDS_RECEIVED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addField(ClassName.get(BitSet.class), "NULLABLE_FIELDS_RECEIVED", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
            var fieldReceivedInitBlock = CodeBlock.builder()
                .add("""
                    ALL_FIELDS_RECEIVED = new $T($L);
                    ALL_FIELDS_RECEIVED.set(0, $L);
                    NULLABLE_FIELDS_RECEIVED = new $T($L);
                    """, BitSet.class, meta.fields().size(), meta.fields().size(), BitSet.class, meta.fields().size());
            for (int i = 0; i < meta.fields().size(); i++) {
                var field = meta.fields().get(i);
                if (isNullable(field)) {
                    fieldReceivedInitBlock.add("NULLABLE_FIELDS_RECEIVED.set($L);\n", i);
                }
            }
            typeBuilder.addStaticBlock(fieldReceivedInitBlock.build());
        }
    }

    private void addFastPath(MethodSpec.Builder method, JsonClassReaderMeta meta) {
        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            method.addCode("if (__parser.nextName($L)) {$>\n", jsonNameStaticName(field));
            method.addCode("$L = $L(__parser, __receivedFields);\n", field.parameter(), readerMethodName(field));
            if (i == meta.fields().size() - 1) {
                method.addCode("""
                    __token = __parser.nextToken();
                    while (__token != JsonToken.END_OBJECT) {
                        __parser.nextToken();
                        __parser.skipChildren();
                        __token = __parser.nextToken();
                    }
                    """);
                method.addCode("return new $T(", meta.typeMirror());
                for (int j = 0; j < meta.fields().size(); j++) {
                    method.addCode("$L", meta.fields().get(j).parameter());
                    if (j < meta.fields().size() - 1) {
                        method.addCode(", ");
                    }
                }
                method.addCode(");$<\n");
            }
        }
        for (int i = 0; i < meta.fields().size(); i++) {
            method.addCode("}");
            if (i < meta.fields().size() - 1) {
                method.addCode("$<");
            }
            method.addCode("\n");
        }
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

    private void addReadMethods(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        var fields = meta.fields();
        for (int i = 0; i < fields.size(); i++) {
            typeBuilder.addMethod(this.readParamMethod(i, fields.size(), fields.get(i)));
        }
    }


    private void addFieldNames(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        for (var field : meta.fields()) {
            typeBuilder.addField(FieldSpec.builder(JsonTypes.serializedString, this.jsonNameStaticName(field), Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(CodeBlock.of("new $T($S)", JsonTypes.serializedString, field.jsonName()))
                .build());
        }
    }

    private void addReaders(TypeSpec.Builder typeBuilder, JsonClassReaderMeta classMeta) {
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        for (var field : classMeta.fields()) {
            if (field.reader() == null && field.typeMeta() instanceof KnownTypeReaderMeta) {
                continue;
            }
            if (field.reader() != null) {
                var fieldName = this.readerFieldName(field);
                var fieldType = TypeName.get(field.reader());
                var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                var readerElement = (TypeElement) this.types.asElement(field.reader());
                if (CommonUtils.hasDefaultConstructorAndFinal(readerElement)) {
                    readerField.addModifiers(Modifier.STATIC);
                    readerField.initializer("new $T()", field.reader());
                    typeBuilder.addField(readerField.build());
                    continue;
                }
                typeBuilder.addField(readerField.build());
                constructor.addParameter(fieldType, fieldName);
                constructor.addStatement("this.$L = $L", fieldName, fieldName);
            } else if (field.typeMeta() instanceof ReaderFieldType.UnknownTypeReaderMeta) {
                var fieldName = this.readerFieldName(field);
                var fieldType = ParameterizedTypeName.get(JsonTypes.jsonReader, TypeName.get(field.typeMeta().typeMirror()));
                var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                constructor.addParameter(fieldType, fieldName);
                constructor.addStatement("this.$L = $L", fieldName, fieldName);
                typeBuilder.addField(readerField.build());
            }
        }
        typeBuilder.addMethod(constructor.build());
    }

    private String readerFieldName(FieldMeta field) {
        return field.parameter().getSimpleName() + "Reader";
    }

    private MethodSpec readParamMethod(int index, int size, FieldMeta field) {
        var method = MethodSpec.methodBuilder(this.readerMethodName(field))
            .addModifiers(Modifier.PRIVATE)
            .addParameter(JsonTypes.jsonParser, "__parser")
            .addParameter(size > 32 ? TypeName.get(BitSet.class) : ArrayTypeName.of(TypeName.INT), "__receivedFields")
            .returns(field.typeName());
        if (field.reader() != null) {
            method.addCode("var __token = __parser.nextToken();\n");
            if (!isNullable(field)) {
                method.addCode("""
                    if (__token == $T.VALUE_NULL)
                      throw new $T(__parser, $S);
                    """, JsonTypes.jsonToken, JsonTypes.jsonParseException, "Expecting nonnull value for field %s, got VALUE_NULL token".formatted(field.jsonName()));
                if (size > 32) {
                    method.addCode("__receivedFields.set($L);\n", index);
                } else {
                    method.addCode("__receivedFields[0] = __receivedFields[0] | (1 << $L);\n", index);
                }
            }
            method.addCode("return $L.read(__parser);\n", this.readerFieldName(field));
            return method.build();
        }
        method.addStatement("var __token = __parser.nextToken()");
        if (field.typeMeta() instanceof KnownTypeReaderMeta meta) {
            method.addModifiers(Modifier.STATIC);
            var block = CodeBlock.builder();
            if (size > 32) {
                block.add("__receivedFields.set($L);\n", index);
            } else {
                block.add("__receivedFields[0] = __receivedFields[0] | (1 << $L);\n", index);
            }

            CodeBlock prefix = field.typeMeta().isJsonNullable()
                ? CodeBlock.of("return $T.ofNullable(", JsonTypes.jsonNullable)
                : CodeBlock.of("return ");

            CodeBlock suffix = field.typeMeta().isJsonNullable()
                ? CodeBlock.of(")")
                : CodeBlock.of("");

            boolean isJsonNullable = field.typeMeta().isJsonNullable();
            block.add(readKnownType(field.jsonName(), prefix, suffix, meta.knownType(), isNullable(field), isJsonNullable, meta.typeMirror()));
            method.addCode(block.build());
            return method.build();
        }

        if (field.typeMeta() != null && field.typeMeta().isJsonNullable()) {
            method.addCode("""
                if (__token == $T.VALUE_NULL) {
                  return $T.nullValue();
                }
                """, JsonTypes.jsonToken, JsonTypes.jsonNullable);
        } else if (isNullable(field)) {
            method.addCode("""
                if (__token == $T.VALUE_NULL) {
                  return null;
                }
                """, JsonTypes.jsonToken);
        } else {
            method.addCode("""
                if (__token == $T.VALUE_NULL)
                  throw new $T(__parser, $S);
                """, JsonTypes.jsonToken, JsonTypes.jsonParseException, "Expecting nonnull value for field %s, got VALUE_NULL token".formatted(field.jsonName()));
            if (size > 32) {
                method.addCode("__receivedFields.set($L);\n", index);
            } else {
                method.addCode("__receivedFields[0] = __receivedFields[0] | (1 << $L);\n", index);
            }
        }

        if (field.typeMeta() != null && field.typeMeta().isJsonNullable()) {
            method.addStatement("return $T.ofNullable($L.read(__parser))", JsonTypes.jsonNullable, readerFieldName(field));
        } else {
            method.addStatement("return $L.read(__parser)", readerFieldName(field));
        }
        return method.build();
    }

    private String readerMethodName(FieldMeta field) {
        return "read_" + field.parameter().getSimpleName().toString();
    }

    private CodeBlock readKnownType(String jsonName, CodeBlock prefix, CodeBlock suffix, KnownType.KnownTypesEnum knownType, boolean nullable, boolean jsonNullable, TypeMirror typeMirror) {
        var method = CodeBlock.builder();
        var code = switch (knownType) {
            case STRING -> CodeBlock.of("""
                    if (__token == $T.VALUE_STRING) {
                      $L__parser.getText()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> CodeBlock.of("""
                    if (__token == $T.VALUE_TRUE) {
                      $Ltrue$L;
                    } else if (__token == $T.VALUE_FALSE) {
                      $Lfalse$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix, JsonTypes.jsonToken, prefix, suffix);
            case INTEGER_OBJECT, INTEGER_PRIMITIVE -> CodeBlock.of("""
                    if (__token == $T.VALUE_NUMBER_INT) {
                      $L__parser.getIntValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case BIG_INTEGER -> CodeBlock.of("""
                    if (__token == $T.VALUE_NUMBER_INT) {
                      $L__parser.getBigIntegerValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case DOUBLE_OBJECT, DOUBLE_PRIMITIVE -> CodeBlock.of("""
                    if (__token == $T.VALUE_NUMBER_FLOAT || __token == $T.VALUE_NUMBER_INT) {
                      $L__parser.getDoubleValue()$L;
                    }""",
                JsonTypes.jsonToken, JsonTypes.jsonToken, prefix, suffix);
            case FLOAT_OBJECT, FLOAT_PRIMITIVE -> CodeBlock.of("""
                    if (__token == $T.VALUE_NUMBER_FLOAT || __token == $T.VALUE_NUMBER_INT) {
                      $L__parser.getFloatValue()$L;
                    }""",
                JsonTypes.jsonToken, JsonTypes.jsonToken, prefix, suffix);
            case LONG_OBJECT, LONG_PRIMITIVE -> CodeBlock.of("""
                    if (__token == $T.VALUE_NUMBER_INT) {
                      $L__parser.getLongValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case SHORT_OBJECT, SHORT_PRIMITIVE -> CodeBlock.of("""
                    if (__token == $T.VALUE_NUMBER_INT) {
                      $L__parser.getShortValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case BINARY -> CodeBlock.of("""
                    if (__token == $T.VALUE_STRING) {
                      $L__parser.getBinaryValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case UUID -> CodeBlock.of("""
                    if (__token == $T.VALUE_STRING) {
                      $L$T.fromString(__parser.getText())$L;
                    }""",
                JsonTypes.jsonToken, prefix, UUID.class, suffix);
        };
        method.add(code);
        if (jsonNullable) {
            method.add(" else if (__token == $T.VALUE_NULL) {$>\nreturn $T.nullValue();$<\n}", JsonTypes.jsonToken, JsonTypes.jsonNullable);
        } else if (nullable) {
            method.add(" else if (__token == $T.VALUE_NULL) {$>\nreturn null;$<\n}", JsonTypes.jsonToken);
        }
        method.add(" else {$>\nthrow new $T(__parser, $S + __token);$<\n}", JsonTypes.jsonParseException, "Expecting %s token for field '%s', got ".formatted(Arrays.toString(expectedTokens(knownType, nullable)), jsonName));
        return method.build();
    }

    private String[] expectedTokens(KnownType.KnownTypesEnum knownType, boolean nullable) {
        var result = switch (knownType) {
            case STRING, BINARY, UUID -> new String[]{"VALUE_STRING"};
            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> new String[]{"VALUE_TRUE", "VALUE_FALSE"};
            case SHORT_OBJECT, INTEGER_OBJECT, LONG_OBJECT, BIG_INTEGER, INTEGER_PRIMITIVE, LONG_PRIMITIVE, SHORT_PRIMITIVE -> new String[]{"VALUE_NUMBER_INT"};
            case DOUBLE_OBJECT, FLOAT_OBJECT, DOUBLE_PRIMITIVE, FLOAT_PRIMITIVE -> new String[]{"VALUE_NUMBER_FLOAT", "VALUE_NUMBER_INT"};
        };
        if (nullable) {
            result = Arrays.copyOf(result, result.length + 1);
            result[result.length - 1] = "VALUE_NULL";
        }
        return result;
    }

    private void assertTokenType(MethodSpec.Builder method, String expectedToken) {
        method.addCode("if (__token != $T.$L) $>\nthrow new $T(__parser, $S + __token);$<\n",
            JsonTypes.jsonToken, expectedToken, JsonTypes.jsonParseException, "Expecting %s token, got ".formatted(expectedToken)
        );
    }

    private String jsonNameStaticName(JsonClassReaderMeta.FieldMeta field) {
        return "_" + field.parameter().getSimpleName().toString() + "_optimized_field_name";
    }
}
