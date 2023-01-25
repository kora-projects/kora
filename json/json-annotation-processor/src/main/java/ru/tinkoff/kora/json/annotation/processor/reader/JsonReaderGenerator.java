package ru.tinkoff.kora.json.annotation.processor.reader;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonClassReaderMeta.FieldMeta;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderFieldType.KnownTypeReaderMeta;
import ru.tinkoff.kora.json.common.EnumJsonReader;
import ru.tinkoff.kora.json.common.JsonReader;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.BitSet;
import java.util.UUID;

import static com.fasterxml.jackson.core.JsonToken.*;

public class JsonReaderGenerator {
    private final Types types;
    private final Elements elements;
    private final TypeElement readerErasure;
    private final DeclaredType enumType;

    public JsonReaderGenerator(ProcessingEnvironment processingEnvironment) {
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
        this.readerErasure = (TypeElement) this.types.asElement(
            this.types.erasure(this.elements.getTypeElement(JsonReader.class.getCanonicalName()).asType())
        );
        this.enumType = this.types.getDeclaredType(
            this.elements.getTypeElement(Enum.class.getCanonicalName()),
            this.types.getWildcardType(null, null)
        );
    }

    @Nullable
    public TypeSpec generate(JsonClassReaderMeta meta) {
        return this.generateForClass(meta);
    }

    private boolean isNullable(JsonClassReaderMeta.FieldMeta field) {
        if (field.parameter().asType().getKind().isPrimitive()) {
            return false;
        }

        return CommonUtils.isNullable(field.parameter());
    }

    private TypeSpec generateForClass(JsonClassReaderMeta meta) {
        var readerInterface = this.types.getDeclaredType(this.readerErasure, meta.typeMirror());

        var typeBuilder = TypeSpec.classBuilder(JsonUtils.jsonReaderName(meta.typeElement()))
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", CodeBlock.of("$S", JsonReaderGenerator.class.getCanonicalName()))
                .build())
            .addSuperinterface(readerInterface)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addOriginatingElement(meta.typeElement());
        if (this.types.isAssignable(meta.typeMirror(), this.enumType)) {
            return this.generateForEnum(meta, typeBuilder);
        }


        for (TypeParameterElement typeParameter : meta.typeElement().getTypeParameters()) {
            typeBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
        }


        this.addBitSet(typeBuilder, meta);
        this.addReaders(typeBuilder, meta);
        this.addFieldNames(typeBuilder, meta);
        this.addReadMethods(typeBuilder, meta);

        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(TypeName.get(JsonParser.class), "_parser")
            .returns(ClassName.get(meta.typeElement()))
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class);
        method.addStatement("var _token = _parser.currentToken()");
        method.addCode("if (_token == $T.VALUE_NULL) $>\nreturn null;$<\n", JsonToken.class);
        assertTokenType(method, START_OBJECT);

        if (meta.fields().size() <= 32) {
            method.addStatement("var _receivedFields = new int[]{NULLABLE_FIELDS_RECEIVED}", BitSet.class);
        } else {
            method.addStatement("var _receivedFields = ($T) NULLABLE_FIELDS_RECEIVED.clone()", BitSet.class);
        }
        method.addCode("\n");

        this.addFieldVariables(method, meta);
        this.addFastPath(method, meta);

        if (meta.fields().isEmpty()) {
            method.addStatement("_token = _parser.nextToken()");
        } else {
            method.addStatement("_token = _parser.currentToken()");
        }
        method.addCode("while (_token != $T.END_OBJECT) {$>\n", JsonToken.class);
        assertTokenType(method, FIELD_NAME);
        method.addStatement("var _fieldName = _parser.getCurrentName()");
        method.addCode("switch (_fieldName) {$>\n");
        for (int i = 0, fieldsSize = meta.fields().size(); i < fieldsSize; i++) {
            var field = meta.fields().get(i);
            method.addCode("case $S -> {$>\n", field.jsonName());
            method.addCode("$L = $L(_parser, _receivedFields);", field.parameter(), this.readerMethodName(field));
            method.addCode("$<\n}\n");
        }


        method.addCode("default -> {$>\n_parser.nextToken();\n_parser.skipChildren();$<\n}");
        method.addCode("$<\n}\n");
        method.addCode("_token = _parser.nextToken();");

        method.addCode("$<\n}\n");
        var errorSwitch = CodeBlock.builder()
            .add("switch (i) {$>");
        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            errorSwitch.add("\n    case $L -> $S;", i, "%s(%s)".formatted(field.parameter().getSimpleName(), field.jsonName()));
        }
        errorSwitch.add("\n    default -> \"\";");
        errorSwitch.add("$<\n    }");

        if (meta.fields().size() > 32) {
            method.addCode("""
                if (!_receivedFields.equals(ALL_FIELDS_RECEIVED)) {
                  _receivedFields.flip(0, $L);
                  var __error = new $T("Some of required json fields were not received:");
                  for (int i = _receivedFields.nextSetBit(0); i >= 0; i = _receivedFields.nextSetBit(i+1)) {
                    __error.append(" ").append($L);
                  }
                  throw new $T(_parser, __error.toString());
                }
                """, meta.fields().size(), StringBuilder.class, errorSwitch.build(), JsonParseException.class);
        } else {
            method.addCode("""
                if (_receivedFields[0] != ALL_FIELDS_RECEIVED) {
                  var _nonReceivedFields = (~_receivedFields[0]) & ALL_FIELDS_RECEIVED;
                  var __error = new $T("Some of required json fields were not received:");
                  for (int i = 0; i < $L; i++) {
                    if ((_nonReceivedFields & (1 << i)) != 0) {
                      __error.append(" ").append($L);
                    }
                  }
                  throw new $T(_parser, __error.toString());
                }
                """, StringBuilder.class, meta.fields().size(), errorSwitch.build(), JsonParseException.class);
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

    private TypeSpec generateForEnum(JsonClassReaderMeta meta, TypeSpec.Builder typeBuilder) {
        var delegateType = ParameterizedTypeName.get(ClassName.get(EnumJsonReader.class), TypeName.get(meta.typeMirror()));

        typeBuilder.addField(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addCode("this.delegate = new $T<>($T.values(), v -> v.toString());\n", EnumJsonReader.class, meta.typeMirror())
            .build());
        typeBuilder.addMethod(MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addException(IOException.class)
            .addParameter(TypeName.get(JsonParser.class), "_parser")
            .returns(ClassName.get(meta.typeElement()))
            .addAnnotation(Override.class)
            .addAnnotation(Nullable.class)
            .addCode("return this.delegate.read(_parser);\n")
            .build()
        );
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
            method.addCode("if (_parser.nextFieldName($L)) {$>\n", jsonNameStaticName(field));
            method.addCode("$L = $L(_parser, _receivedFields);\n", field.parameter(), readerMethodName(field));
            if (i == meta.fields().size() - 1) {
                method.addCode("""
                    _token = _parser.nextToken();
                    while (_token != JsonToken.END_OBJECT) {
                        _parser.nextToken();
                        _parser.skipChildren();
                        _token = _parser.nextToken();
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
            typeBuilder.addField(FieldSpec.builder(SerializedString.class, this.jsonNameStaticName(field), Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(CodeBlock.of("new $T($S)", SerializedString.class, field.jsonName()))
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
                if (readerElement.getModifiers().contains(Modifier.FINAL)) {
                    var constructors = readerElement.getEnclosedElements().stream()
                        .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                        .toList();
                    if (constructors.size() == 1) {
                        readerField.addModifiers(Modifier.STATIC);
                        readerField.initializer("new $T()", field.reader());
                        typeBuilder.addField(readerField.build());
                        continue;
                    }
                }
                typeBuilder.addField(readerField.build());
                constructor.addParameter(fieldType, fieldName);
                constructor.addStatement("this.$L = $L", fieldName, fieldName);
            } else if (field.typeMeta() instanceof ReaderFieldType.UnknownTypeReaderMeta) {
                var fieldName = this.readerFieldName(field);
                var fieldType = ParameterizedTypeName.get(ClassName.get(JsonReader.class), TypeName.get(field.typeMirror()));
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
            .addParameter(JsonParser.class, "_parser")
            .addException(IOException.class)
            .addParameter(size > 32 ? TypeName.get(BitSet.class) : ArrayTypeName.of(TypeName.INT), "_receivedFields")
            .returns(TypeName.get(field.typeMirror()));
        if (field.reader() != null) {
            method.addCode("var _token = _parser.nextToken();\n");
            if (!isNullable(field)) {
                method.addCode("""
                    if (_token == $T.VALUE_NULL)
                      throw new $T(_parser, $S);
                    """, JsonToken.class, JsonParseException.class, "Expecting nonnull value for field %s, got VALUE_NULL token".formatted(field.jsonName()));
                if (size > 32) {
                    method.addCode("_receivedFields.set($L);\n", index);
                } else {
                    method.addCode("_receivedFields[0] = _receivedFields[0] | (1 << $L);\n", index);
                }
            }
            method.addCode("return $L.read(_parser);\n", this.readerFieldName(field));
            return method.build();
        }
        method.addStatement("var _token = _parser.nextToken()");
        if (field.typeMeta() instanceof KnownTypeReaderMeta meta) {
            method.addModifiers(Modifier.STATIC);
            var block = CodeBlock.builder();
            if (size > 32) {
                block.add("_receivedFields.set($L);\n", index);
            } else {
                block.add("_receivedFields[0] = _receivedFields[0] | (1 << $L);\n", index);
            }

            block.add(readKnownType(field.jsonName(), CodeBlock.of("return "), meta.knownType(), isNullable(field), field.typeMirror()));
            method.addCode(block.build());
            return method.build();
        }

        if (isNullable(field)) {
            method.addCode("""
                if (_token == $T.VALUE_NULL) {
                  return null;
                }
                """, JsonToken.class);
        } else {
            method.addCode("""
                if (_token == $T.VALUE_NULL)
                  throw new $T(_parser, $S);
                """, JsonToken.class, JsonParseException.class, "Expecting nonnull value for field %s, got VALUE_NULL token".formatted(field.jsonName()));
            if (size > 32) {
                method.addCode("_receivedFields.set($L);\n", index);
            } else {
                method.addCode("_receivedFields[0] = _receivedFields[0] | (1 << $L);\n", index);
            }
        }
        method.addStatement("return $L.read(_parser)", readerFieldName(field));
        return method.build();
    }

    private String readerMethodName(FieldMeta field) {
        return "read_" + field.parameter().getSimpleName().toString();
    }

    private CodeBlock readKnownType(String jsonName, CodeBlock parameterName, KnownType.KnownTypesEnum knownType, boolean nullable, TypeMirror typeMirror) {
        var method = CodeBlock.builder();
        var code = switch (knownType) {
            case STRING -> CodeBlock.of("""
                if (_token == $T.VALUE_STRING) {
                  $L _parser.getText();
                }
                """, JsonToken.class, parameterName);
            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> CodeBlock.of("""
                if (_token == $T.VALUE_TRUE) {
                  $L true;
                } else if (_token == $T.VALUE_FALSE) {
                  $L false;
                }
                """, JsonToken.class, parameterName, JsonToken.class, parameterName);
            case INTEGER_OBJECT, INTEGER_PRIMITIVE -> CodeBlock.of("""
                if (_token == $T.VALUE_NUMBER_INT) {
                  $L _parser.getIntValue();
                }
                """, JsonToken.class, parameterName);
            case BIG_INTEGER -> CodeBlock.of("""
                if (_token == $T.VALUE_NUMBER_INT) {
                  $L _parser.getBigIntegerValue();
                }
                """, JsonToken.class, parameterName);
            case BIG_DECIMAL -> CodeBlock.of("""
                if (_token == $T.VALUE_NUMBER_INT || _token == $T.VALUE_NUMBER_FLOAT) {
                  $L _parser.getDecimalValue();
                }
                """, JsonToken.class, JsonToken.class, parameterName);
            case DOUBLE_OBJECT, DOUBLE_PRIMITIVE -> CodeBlock.of("""
                if (_token == $T.VALUE_NUMBER_FLOAT) {
                  $L _parser.getDoubleValue();
                }
                """, JsonToken.class, parameterName);
            case FLOAT_OBJECT, FLOAT_PRIMITIVE -> CodeBlock.of("""
                if (_token == $T.VALUE_NUMBER_FLOAT) {
                  $L _parser.getFloatValue();
                }
                """, JsonToken.class, parameterName);
            case LONG_OBJECT, LONG_PRIMITIVE -> CodeBlock.of("""
                if (_token == $T.VALUE_NUMBER_INT) {
                  $L _parser.getLongValue();
                }
                """, JsonToken.class, parameterName);
            case SHORT_OBJECT, SHORT_PRIMITIVE -> CodeBlock.of("""
                if (_token == $T.VALUE_NUMBER_INT) {
                  $L _parser.getShortValue();
                }
                """, JsonToken.class, parameterName);
            case BINARY -> CodeBlock.of("""
                if (_token == $T.VALUE_STRING) {
                  $L _parser.getBinaryValue();
                }
                """, JsonToken.class, parameterName);
            case UUID -> CodeBlock.of("""
                if (_token == $T.VALUE_STRING) {
                  $L $T.fromString(_parser.getText());
                }
                """, JsonToken.class, parameterName, UUID.class);
        };
        method.add(code);
        if (nullable) {
            method.add("else if (_token == $T.VALUE_NULL) {$>\n$L null;}$<\n", JsonToken.class, parameterName);
        }
        method.add("else {$>\nthrow new $T(_parser, $S + _token);$<\n}", JsonParseException.class, "Expecting %s token for field '%s', got ".formatted(Arrays.toString(expectedTokens(knownType, nullable)), jsonName));
        return method.build();
    }

    private JsonToken[] expectedTokens(KnownType.KnownTypesEnum knownType, boolean nullable) {
        var result = switch (knownType) {
            case STRING, BINARY, UUID -> new JsonToken[]{VALUE_STRING};
            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> new JsonToken[]{VALUE_TRUE, VALUE_FALSE};
            case SHORT_OBJECT, INTEGER_OBJECT, LONG_OBJECT, BIG_INTEGER, INTEGER_PRIMITIVE, LONG_PRIMITIVE, SHORT_PRIMITIVE -> new JsonToken[]{VALUE_NUMBER_INT};
            case BIG_DECIMAL, DOUBLE_OBJECT, FLOAT_OBJECT, DOUBLE_PRIMITIVE, FLOAT_PRIMITIVE -> new JsonToken[]{VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT};
        };
        if (nullable) {
            result = Arrays.copyOf(result, result.length + 1);
            result[result.length - 1] = VALUE_NULL;
        }
        return result;
    }

    private void assertTokenType(MethodSpec.Builder method, JsonToken expectedToken) {
        method.addCode("if (_token != $T.$L) $>\nthrow new $T(_parser, $S + _token);$<\n",
            JsonToken.class, expectedToken.name(), JsonParseException.class, "Expecting %s token, got ".formatted(expectedToken)
        );
    }

    private String jsonNameStaticName(JsonClassReaderMeta.FieldMeta field) {
        return "_" + field.parameter().getSimpleName().toString() + "_optimized_field_name";
    }
}
