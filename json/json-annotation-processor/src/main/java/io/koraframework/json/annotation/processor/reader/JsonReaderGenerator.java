package io.koraframework.json.annotation.processor.reader;

import com.palantir.javapoet.*;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.json.annotation.processor.JsonTypes;
import io.koraframework.json.annotation.processor.JsonUtils;
import io.koraframework.json.annotation.processor.KnownType;
import io.koraframework.json.annotation.processor.reader.JsonClassReaderMeta.FieldMeta;
import io.koraframework.json.annotation.processor.reader.ReaderFieldType.KnownTypeReaderMeta;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
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
        this.addErrorMethods(typeBuilder, meta);

        var method = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(JsonTypes.jsonParser, "_parser")
            .returns(TypeName.get(meta.typeElement().asType()).withoutAnnotations().annotated(CommonClassNames.nullableAnnotation));
        method.addStatement("var _token = _parser.currentToken()");
        method.addCode("if (_token == $T.VALUE_NULL) $>\nreturn null;$<\n", JsonTypes.jsonToken);
        assertTokenType(method, "START_OBJECT", "an object '{...}'");

        if (meta.fields().size() <= 32) {
            method.addStatement("int _receivedFields = NULLABLE_FIELDS_RECEIVED");
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
        method.addCode("while (_token != $T.END_OBJECT) {$>\n", JsonTypes.jsonToken);
        assertTokenType(method, "PROPERTY_NAME", "a field name");
        method.addStatement("var _fieldName = _parser.currentName()");
        method.addCode("switch (_fieldName) {$>\n");
        for (int i = 0, fieldsSize = meta.fields().size(); i < fieldsSize; i++) {
            var field = meta.fields().get(i);
            method.addCode("case $S -> {$>\n", field.jsonName());
            method.addCode("$L = $L(_parser);\n", field.parameter(), this.readerMethodName(field));
            method.addCode(markReceived(meta, i));
            method.addCode("$<\n}\n");
        }


        method.addCode("default -> {$>\n_parser.nextToken();\n_parser.skipChildren();$<\n}");
        method.addCode("$<\n}\n");
        method.addCode("_token = _parser.nextToken();");

        method.addCode("$<\n}\n");
        var errorSwitch = CodeBlock.builder()
            .add("switch (_i) {$>");
        for (int i = 0; i < meta.fields().size(); i++) {
            var field = meta.fields().get(i);
            errorSwitch.add("\n    case $L -> $S;", i, field.jsonName());
        }
        errorSwitch.add("\n    default -> \"\";");
        errorSwitch.add("$<\n    }");

        if (meta.fields().size() > 32) {
            method.addCode("""
                if (!_receivedFields.equals(ALL_FIELDS_RECEIVED)) {
                  _receivedFields.flip(0, $L);
                  var _missing = new $T();
                  for (int _i = _receivedFields.nextSetBit(0); _i >= 0; _i = _receivedFields.nextSetBit(_i+1)) {
                    if (_missing.length() > 0) _missing.append(", ");
                    _missing.append($L);
                  }
                  throw _missingRequiredFields(_parser, _missing.toString());
                }
                """, meta.fields().size(), StringBuilder.class, errorSwitch.build());
        } else {
            method.addCode("""
                if (_receivedFields != ALL_FIELDS_RECEIVED) {
                  var _nonReceivedFields = (~_receivedFields) & ALL_FIELDS_RECEIVED;
                  var _missing = new $T();
                  for (int _i = 0; _i < $L; _i++) {
                    if ((_nonReceivedFields & (1 << _i)) != 0) {
                      if (_missing.length() > 0) _missing.append(", ");
                      _missing.append($L);
                    }
                  }
                  throw _missingRequiredFields(_parser, _missing.toString());
                }
                """, StringBuilder.class, meta.fields().size(), errorSwitch.build());
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
            method.addCode("if (_parser.nextName($L)) {$>\n", jsonNameStaticName(field));
            method.addCode("$L = $L(_parser);\n", field.parameter(), readerMethodName(field));
            method.addCode(markReceived(meta, i));
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
                final TypeName fieldType;
                if (field.reader().mapperClass() != null) {
                    fieldType = TypeName.get(field.reader().mapperClass());
                    var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                    var readerElement = (TypeElement) this.types.asElement(field.reader().mapperClass());
                    if (CommonUtils.hasDefaultConstructorAndFinal(readerElement)) {
                        readerField.addModifiers(Modifier.STATIC);
                        readerField.initializer("new $T()", fieldType);
                        typeBuilder.addField(readerField.build());
                        continue;
                    }
                } else {
                    fieldType = ParameterizedTypeName.get(JsonTypes.jsonReader, field.typeName());
                }
                var readerField = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                var fieldTag = field.reader().toTagAnnotation();
                if (fieldTag != null) {
                    readerField.addAnnotation(fieldTag);
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
            .addParameter(JsonTypes.jsonParser, "_parser")
            .returns(field.typeName());
        if (field.reader() != null) {
            method.addCode("var _token = _parser.nextToken();\n");
            if (!isNullable(field)) {
                method.addCode("""
                    if (_token == $T.VALUE_NULL)
                      throw _requiredFieldNull(_parser, $S);
                    """, JsonTypes.jsonToken, "." + field.jsonName());
            }
            method.addCode("return $L.read(_parser);\n", this.readerFieldName(field));
            return method.build();
        }
        method.addStatement("var _token = _parser.nextToken()");
        if (field.typeMeta() instanceof KnownTypeReaderMeta meta) {
            method.addModifiers(Modifier.STATIC);
            var block = CodeBlock.builder();

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
                if (_token == $T.VALUE_NULL) {
                  return $T.nullValue();
                }
                """, JsonTypes.jsonToken, JsonTypes.jsonNullable);
        } else if (isNullable(field)) {
            method.addCode("""
                if (_token == $T.VALUE_NULL) {
                  return null;
                }
                """, JsonTypes.jsonToken);
        } else {
            method.addCode("""
                if (_token == $T.VALUE_NULL)
                  throw _requiredFieldNull(_parser, $S);
                """, JsonTypes.jsonToken, "." + field.jsonName());
        }

        if (field.typeMeta() != null && field.typeMeta().isJsonNullable()) {
            method.addStatement("return $T.ofNullable($L.read(_parser))", JsonTypes.jsonNullable, readerFieldName(field));
        } else {
            method.addStatement("return $L.read(_parser)", readerFieldName(field));
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
                    if (_token == $T.VALUE_STRING) {
                      $L_parser.getText()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> CodeBlock.of("""
                    if (_token == $T.VALUE_TRUE) {
                      $Ltrue$L;
                    } else if (_token == $T.VALUE_FALSE) {
                      $Lfalse$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix, JsonTypes.jsonToken, prefix, suffix);
            case INTEGER_OBJECT, INTEGER_PRIMITIVE -> CodeBlock.of("""
                    if (_token == $T.VALUE_NUMBER_INT) {
                      $L_parser.getIntValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case BIG_INTEGER -> CodeBlock.of("""
                    if (_token == $T.VALUE_NUMBER_INT) {
                      $L_parser.getBigIntegerValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case DOUBLE_OBJECT, DOUBLE_PRIMITIVE -> CodeBlock.of("""
                    if (_token == $T.VALUE_NUMBER_FLOAT || _token == $T.VALUE_NUMBER_INT) {
                      $L_parser.getDoubleValue()$L;
                    }""",
                JsonTypes.jsonToken, JsonTypes.jsonToken, prefix, suffix);
            case FLOAT_OBJECT, FLOAT_PRIMITIVE -> CodeBlock.of("""
                    if (_token == $T.VALUE_NUMBER_FLOAT || _token == $T.VALUE_NUMBER_INT) {
                      $L_parser.getFloatValue()$L;
                    }""",
                JsonTypes.jsonToken, JsonTypes.jsonToken, prefix, suffix);
            case LONG_OBJECT, LONG_PRIMITIVE -> CodeBlock.of("""
                    if (_token == $T.VALUE_NUMBER_INT) {
                      $L_parser.getLongValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case SHORT_OBJECT, SHORT_PRIMITIVE -> CodeBlock.of("""
                    if (_token == $T.VALUE_NUMBER_INT) {
                      $L_parser.getShortValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case BINARY -> CodeBlock.of("""
                    if (_token == $T.VALUE_STRING) {
                      $L_parser.getBinaryValue()$L;
                    }""",
                JsonTypes.jsonToken, prefix, suffix);
            case UUID -> CodeBlock.of("""
                    if (_token == $T.VALUE_STRING) {
                      $L$T.fromString(_parser.getText())$L;
                    }""",
                JsonTypes.jsonToken, prefix, UUID.class, suffix);
        };
        method.add(code);
        if (jsonNullable) {
            method.add(" else if (_token == $T.VALUE_NULL) {$>\nreturn $T.nullValue();$<\n}", JsonTypes.jsonToken, JsonTypes.jsonNullable);
        } else if (nullable) {
            method.add(" else if (_token == $T.VALUE_NULL) {$>\nreturn null;$<\n}", JsonTypes.jsonToken);
        } else {
            method.add(" else if (_token == $T.VALUE_NULL) {$>\nthrow _requiredFieldNull(_parser, $S);$<\n}", JsonTypes.jsonToken, "." + jsonName);
        }
        method.add(" else {$>\nthrow _unexpectedToken(_parser, $S, $S);$<\n}", "." + jsonName, expectedPhrase(knownType));
        return method.build();
    }

    private String expectedPhrase(KnownType.KnownTypesEnum knownType) {
        return switch (knownType) {
            case STRING -> "a string";
            case BINARY -> "a base64-encoded string";
            case UUID -> "a UUID string";
            case BOOLEAN_OBJECT, BOOLEAN_PRIMITIVE -> "a boolean";
            case SHORT_OBJECT, SHORT_PRIMITIVE, INTEGER_OBJECT, INTEGER_PRIMITIVE,
                 LONG_OBJECT, LONG_PRIMITIVE, BIG_INTEGER -> "an integer number";
            case DOUBLE_OBJECT, DOUBLE_PRIMITIVE, FLOAT_OBJECT, FLOAT_PRIMITIVE -> "a number";
        };
    }

    private void assertTokenType(MethodSpec.Builder method, String expectedToken, String expectedPhrase) {
        method.addCode("if (_token != $T.$L) $>\nthrow _unexpectedToken(_parser, $S, $S);$<\n",
            JsonTypes.jsonToken, expectedToken, "", expectedPhrase
        );
    }

    private CodeBlock markReceived(JsonClassReaderMeta meta, int index) {
        if (meta.fields().size() > 32) {
            return CodeBlock.of("_receivedFields.set($L);\n", index);
        }
        return CodeBlock.of("_receivedFields |= (1 << $L);\n", index);
    }

    /**
     * Generates the private helper methods each reader uses to build detailed, consistent parse-error
     * messages (type + member + JSON path + humanized expected/actual value). Kept inside the mapper
     * itself, not extracted to a shared runtime class.
     */
    private void addErrorMethods(TypeSpec.Builder typeBuilder, JsonClassReaderMeta meta) {
        var typeName = meta.typeElement().getSimpleName().toString();

        typeBuilder.addMethod(MethodSpec.methodBuilder("_jsonPath")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(JsonTypes.jsonParser, "_parser")
            .returns(String.class)
            .addStatement("var _p = _parser.streamReadContext().pathAsPointer().toString()")
            .addStatement("return _p.isEmpty() ? $S : _p", "<root>")
            .build());

        var actual = MethodSpec.methodBuilder("_actualValue")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(JsonTypes.jsonParser, "_parser")
            .returns(String.class);
        actual.addStatement("var _t = _parser.currentToken()");
        actual.beginControlFlow("if (_t == null)");
        actual.addStatement("return $S", "nothing (end of input)");
        actual.endControlFlow();
        actual.addStatement("var _v = _parser.getValueAsString()");
        actual.beginControlFlow("if (_v != null && _v.length() > 128)");
        actual.addStatement("_v = _v.substring(0, 128) + $S", "...(truncated)");
        actual.endControlFlow();
        actual.addCode("return switch (_t) {$>\n");
        actual.addStatement("case VALUE_NULL -> $S", "null");
        actual.addStatement("case START_OBJECT -> $S", "an object");
        actual.addStatement("case START_ARRAY -> $S", "an array");
        actual.addStatement("case VALUE_STRING -> $S + _v + $S", "a string \"", "\"");
        actual.addStatement("case VALUE_NUMBER_INT -> $S + _v", "a number ");
        actual.addStatement("case VALUE_NUMBER_FLOAT -> $S + _v", "a fractional number ");
        actual.addStatement("case VALUE_TRUE, VALUE_FALSE -> $S + _v", "a boolean ");
        actual.addStatement("default -> $S + _t", "token ");
        actual.addCode("$<};\n");
        typeBuilder.addMethod(actual.build());

        typeBuilder.addMethod(MethodSpec.methodBuilder("_unexpectedToken")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(JsonTypes.jsonParser, "_parser")
            .addParameter(String.class, "_member")
            .addParameter(String.class, "_expected")
            .returns(JsonTypes.jsonParseException)
            .addStatement("return new $T(_parser, $S + _member + $S + _expected + $S + _actualValue(_parser) + $S + _jsonPath(_parser) + $S)",
                JsonTypes.jsonParseException, "Failed to read json " + typeName, ": expected ", ", but got ", " (at ", ")")
            .build());

        typeBuilder.addMethod(MethodSpec.methodBuilder("_missingRequiredFields")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .addParameter(JsonTypes.jsonParser, "_parser")
            .addParameter(String.class, "_fields")
            .returns(JsonTypes.jsonParseException)
            .addStatement("return new $T(_parser, $S + _fields + $S + _jsonPath(_parser) + $S)",
                JsonTypes.jsonParseException, "Failed to read json " + typeName + ": missing required field(s): ", " (at ", ")")
            .build());

        boolean anyRequired = meta.fields().stream().anyMatch(f -> !isNullable(f));
        if (anyRequired) {
            typeBuilder.addMethod(MethodSpec.methodBuilder("_requiredFieldNull")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(JsonTypes.jsonParser, "_parser")
                .addParameter(String.class, "_member")
                .returns(JsonTypes.jsonParseException)
                .addStatement("return new $T(_parser, $S + _member + $S + _jsonPath(_parser) + $S)",
                    JsonTypes.jsonParseException, "Failed to read json " + typeName, ": required field must not be null (at ", ")")
                .build());
        }
    }

    private String jsonNameStaticName(JsonClassReaderMeta.FieldMeta field) {
        return "_" + field.parameter().getSimpleName().toString() + "_optimized_field_name";
    }
}
