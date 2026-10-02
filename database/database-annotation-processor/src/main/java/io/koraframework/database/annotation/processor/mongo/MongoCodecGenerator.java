package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.FieldFactory;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class MongoCodecGenerator {

    private static final String WRITER = "_writer";
    private static final String READER = "_reader";
    private static final String VALUE = "_value";
    private static final String CONTEXT = "_context";

    private final Types types;
    private final Elements elements;
    private final Filer filer;

    public MongoCodecGenerator(Types types, Elements elements, Filer filer) {
        this.types = types;
        this.elements = elements;
        this.filer = filer;
    }

    public static ClassName codecName(Elements elements, TypeElement entityTypeElement) {
        var codecName = NameUtils.generatedType(entityTypeElement, "MongoCodec");
        var packageElement = elements.getPackageOf(entityTypeElement);
        return ClassName.get(packageElement.getQualifiedName().toString(), codecName);
    }

    public void generate(MongoEntity entity) throws IOException {
        if (!entity.typeElement().getTypeParameters().isEmpty()) {
            throw new ProcessingErrorException("""
                Mongo entity type is invalid:
                  %s

                Problem:
                  @EntityMongo does not support generic types.

                Hint:
                  A generated codec must report a concrete Class<T> from getEncoderClass(), which a generic type can not provide.

                Fix:
                  Use a non-generic entity type, or supply a custom Codec for this type.
                """.formatted(entity.typeElement().getQualifiedName()), entity.typeElement());
        }

        var codecClassName = codecName(this.elements, entity.typeElement());
        var entityType = TypeName.get(entity.typeMirror());

        var type = TypeSpec.classBuilder(codecClassName)
            .addOriginatingElement(entity.typeElement())
            .addAnnotation(AnnotationUtils.generated(MongoCodecGenerator.class))
            .addSuperinterface(ParameterizedTypeName.get(MongoTypes.CODEC, entityType))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var codecs = new FieldFactory(this.types, this.elements, type, constructor, "_codec_");

        type.addMethod(this.generateEncode(entity, entityType, codecs));
        type.addMethod(this.generateDecode(entity, entityType, codecs));
        type.addMethod(MethodSpec.methodBuilder("getEncoderClass")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), entityType))
            .addStatement("return $T.class", entityType)
            .build());
        type.addMethod(constructor.build());

        JavaFile.builder(codecClassName.packageName(), type.build()).build().writeTo(this.filer);
    }

    private MethodSpec generateEncode(MongoEntity entity, TypeName entityType, FieldFactory codecs) {
        var encode = MethodSpec.methodBuilder("encode")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(MongoTypes.BSON_WRITER, WRITER)
            .addParameter(entityType, VALUE)
            .addParameter(MongoTypes.ENCODER_CONTEXT, CONTEXT);

        encode.addStatement("$N.writeStartDocument()", WRITER);
        var names = new Names();
        for (var field : entity.fields()) {
            encode.addCode(this.encodeField(entity, field, codecs, names));
        }
        encode.addStatement("$N.writeEndDocument()", WRITER);
        return encode.build();
    }

    private CodeBlock encodeField(MongoEntity entity, MongoEntity.Field field, FieldFactory codecs, Names names) {
        var b = CodeBlock.builder();
        var accessor = CodeBlock.of("$N.$N()", VALUE, field.accessor());

        if (field.bsonName().equals("_id") && field.nullable()) {
            var idLocal = names.next("_v");
            b.addStatement("var $N = $L", idLocal, accessor);
            b.beginControlFlow("if ($N != null)", idLocal);
            b.addStatement("$N.writeName($S)", WRITER, field.bsonName());
            b.add(this.writeValue(field.type(), CodeBlock.of("$N", idLocal), field.element(), codecs, names));
            b.endControlFlow();
            return b.build();
        }

        if (field.type().getKind().isPrimitive()) {
            b.addStatement("$N.writeName($S)", WRITER, field.bsonName());
            b.add(this.writeValue(field.type(), accessor, field.element(), codecs, names));
            return b.build();
        }

        var local = names.next("_v");
        b.addStatement("var $N = $L", local, accessor);
        b.addStatement("$N.writeName($S)", WRITER, field.bsonName());
        b.beginControlFlow("if ($N == null)", local);
        if (field.nullable()) {
            b.addStatement("$N.writeNull()", WRITER);
        } else {
            b.addStatement("throw new $T($S)", NullPointerException.class,
                "Field %s.%s is not nullable, but its value is null".formatted(entity.typeElement().getSimpleName(), field.element().getSimpleName()));
        }
        b.nextControlFlow("else");
        b.add(this.writeValue(field.type(), CodeBlock.of("$N", local), field.element(), codecs, names));
        b.endControlFlow();
        return b.build();
    }

    /**
     * Produces statements writing {@code valueExpr}. The field name is already written by the caller, so the
     * same code works both for document fields and for array elements.
     */
    private CodeBlock writeValue(TypeMirror type, CodeBlock valueExpr, VariableElement origin, FieldFactory codecs, Names names) {
        var mapping = CommonUtils.parseMapping(origin).getMapping(MongoTypes.CODEC);
        if (mapping == null) {
            var nativeType = MongoNativeTypes.find(TypeName.get(type));
            if (nativeType != null) {
                return statement(nativeType.write().apply(WRITER, valueExpr));
            }
            if (isEnum(type)) {
                return statement(CodeBlock.of("$N.writeString($L.name())", WRITER, valueExpr));
            }
            var collectionElement = this.collectionElementType(type);
            if (collectionElement != null) {
                return this.writeCollection(collectionElement, valueExpr, origin, codecs, names);
            }
            var mapValue = this.mapValueType(type, origin);
            if (mapValue != null) {
                return this.writeMap(mapValue, valueExpr, origin, codecs, names);
            }
        }

        var codecField = codecs.add(mapping, ParameterizedTypeName.get(MongoTypes.CODEC, TypeName.get(type).box()));
        return statement(CodeBlock.of("this.$N.encode($N, $L, $N)", codecField, WRITER, valueExpr, CONTEXT));
    }

    private CodeBlock writeCollection(TypeMirror elementType, CodeBlock valueExpr, VariableElement origin, FieldFactory codecs, Names names) {
        var element = names.next("_e");
        return CodeBlock.builder()
            .addStatement("$N.writeStartArray()", WRITER)
            .beginControlFlow("for (var $N : $L)", element, valueExpr)
            .beginControlFlow("if ($N == null)", element)
            .add(this.nullElement(elementType, CodeBlock.of("$N.writeNull()", WRITER), origin))
            .nextControlFlow("else")
            .add(this.writeValue(elementType, CodeBlock.of("$N", element), origin, codecs, names))
            .endControlFlow()
            .endControlFlow()
            .addStatement("$N.writeEndArray()", WRITER)
            .build();
    }

    private CodeBlock writeMap(TypeMirror valueType, CodeBlock valueExpr, VariableElement origin, FieldFactory codecs, Names names) {
        var entry = names.next("_entry");
        return CodeBlock.builder()
            .addStatement("$N.writeStartDocument()", WRITER)
            .beginControlFlow("for (var $N : $L.entrySet())", entry, valueExpr)
            .addStatement("$N.writeName($N.getKey())", WRITER, entry)
            .beginControlFlow("if ($N.getValue() == null)", entry)
            .add(this.nullElement(valueType, CodeBlock.of("$N.writeNull()", WRITER), origin))
            .nextControlFlow("else")
            .add(this.writeValue(valueType, CodeBlock.of("$N.getValue()", entry), origin, codecs, names))
            .endControlFlow()
            .endControlFlow()
            .addStatement("$N.writeEndDocument()", WRITER)
            .build();
    }

    private MethodSpec generateDecode(MongoEntity entity, TypeName entityType, FieldFactory codecs) {
        var decode = MethodSpec.methodBuilder("decode")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(MongoTypes.BSON_READER, READER)
            .addParameter(MongoTypes.DECODER_CONTEXT, CONTEXT)
            .returns(entityType);

        var names = new Names();
        for (var field : entity.fields()) {
            decode.addStatement("$T $N = null", TypeName.get(field.type()).box(), field.variableName());
        }

        decode.addStatement("$N.readStartDocument()", READER);
        decode.beginControlFlow("while ($N.readBsonType() != $T.END_OF_DOCUMENT)", READER, MongoTypes.BSON_TYPE);
        decode.beginControlFlow("switch ($N.readName())", READER);
        for (var field : entity.fields()) {
            decode.beginControlFlow("case $S ->", field.bsonName());
            decode.beginControlFlow("if ($N.getCurrentBsonType() == $T.NULL)", READER, MongoTypes.BSON_TYPE);
            decode.addStatement("$N.readNull()", READER);
            decode.nextControlFlow("else");
            decode.addCode(this.readValue(field.type(), field.variableName(), field.element(), codecs, names));
            decode.endControlFlow();
            decode.endControlFlow();
        }
        decode.addStatement("default -> $N.skipValue()", READER);
        decode.endControlFlow();
        decode.endControlFlow();
        decode.addStatement("$N.readEndDocument()", READER);

        for (var field : entity.fields()) {
            if (!field.nullable()) {
                decode.beginControlFlow("if ($N == null)", field.variableName());
                decode.addStatement("throw new $T($S)", NullPointerException.class,
                    "Field %s.%s is not nullable, but document field '%s' is null or absent".formatted(
                        entity.typeElement().getSimpleName(), field.element().getSimpleName(), field.bsonName()));
                decode.endControlFlow();
            }
        }

        decode.addCode(entity.buildInstance("_entity"));
        decode.addStatement("return _entity");
        return decode.build();
    }

    /**
     * Produces statements that read a value from the reader into an already declared {@code target} variable.
     */
    private CodeBlock readValue(TypeMirror type, String target, VariableElement origin, FieldFactory codecs, Names names) {
        var mapping = CommonUtils.parseMapping(origin).getMapping(MongoTypes.CODEC);
        if (mapping == null) {
            var nativeType = MongoNativeTypes.find(TypeName.get(type));
            if (nativeType != null) {
                return CodeBlock.builder().addStatement("$N = $L", target, nativeType.read().apply(READER)).build();
            }
            if (isEnum(type)) {
                return CodeBlock.builder().addStatement("$N = $T.valueOf($N.readString())", target, TypeName.get(type), READER).build();
            }
            var collectionElement = this.collectionElementType(type);
            if (collectionElement != null) {
                return this.readCollection(type, collectionElement, target, origin, codecs, names);
            }
            var mapValue = this.mapValueType(type, origin);
            if (mapValue != null) {
                return this.readMap(mapValue, target, origin, codecs, names);
            }
        }

        var codecField = codecs.add(mapping, ParameterizedTypeName.get(MongoTypes.CODEC, TypeName.get(type).box()));
        return CodeBlock.builder().addStatement("$N = this.$N.decode($N, $N)", target, codecField, READER, CONTEXT).build();
    }

    private CodeBlock readCollection(TypeMirror collectionType, TypeMirror elementType, String target, VariableElement origin, FieldFactory codecs, Names names) {
        var collection = names.next("_c");
        var element = names.next("_i");
        var implementation = this.isSet(collectionType)
            ? ClassName.get(LinkedHashSet.class)
            : ClassName.get(ArrayList.class);

        return CodeBlock.builder()
            .addStatement("var $N = new $T<$T>()", collection, implementation, TypeName.get(elementType).box())
            .addStatement("$N.readStartArray()", READER)
            .beginControlFlow("while ($N.readBsonType() != $T.END_OF_DOCUMENT)", READER, MongoTypes.BSON_TYPE)
            .beginControlFlow("if ($N.getCurrentBsonType() == $T.NULL)", READER, MongoTypes.BSON_TYPE)
            .addStatement("$N.readNull()", READER)
            .add(this.nullElement(elementType, CodeBlock.of("$N.add(null)", collection), origin))
            .nextControlFlow("else")
            .addStatement("$T $N = null", TypeName.get(elementType).box(), element)
            .add(this.readValue(elementType, element, origin, codecs, names))
            .addStatement("$N.add($N)", collection, element)
            .endControlFlow()
            .endControlFlow()
            .addStatement("$N.readEndArray()", READER)
            .addStatement("$N = $N", target, collection)
            .build();
    }

    private CodeBlock readMap(TypeMirror valueType, String target, VariableElement origin, FieldFactory codecs, Names names) {
        var map = names.next("_m");
        var key = names.next("_k");
        var value = names.next("_mv");

        return CodeBlock.builder()
            .addStatement("var $N = new $T<$T, $T>()", map, ClassName.get(LinkedHashMap.class), ClassName.get(String.class), TypeName.get(valueType).box())
            .addStatement("$N.readStartDocument()", READER)
            .beginControlFlow("while ($N.readBsonType() != $T.END_OF_DOCUMENT)", READER, MongoTypes.BSON_TYPE)
            .addStatement("var $N = $N.readName()", key, READER)
            .beginControlFlow("if ($N.getCurrentBsonType() == $T.NULL)", READER, MongoTypes.BSON_TYPE)
            .addStatement("$N.readNull()", READER)
            .add(this.nullElement(valueType, CodeBlock.of("$N.put($N, null)", map, key), origin))
            .nextControlFlow("else")
            .addStatement("$T $N = null", TypeName.get(valueType).box(), value)
            .add(this.readValue(valueType, value, origin, codecs, names))
            .addStatement("$N.put($N, $N)", map, key, value)
            .endControlFlow()
            .endControlFlow()
            .addStatement("$N.readEndDocument()", READER)
            .addStatement("$N = $N", target, map)
            .build();
    }

    /**
     * A null collection element or map value is kept only when its type is {@code @Nullable}, otherwise it fails like a null non-nullable field.
     */
    private CodeBlock nullElement(TypeMirror elementType, CodeBlock onNullable, VariableElement origin) {
        if (CommonUtils.isNullable(elementType)) {
            return statement(onNullable);
        }
        return CodeBlock.builder()
            .addStatement("throw new $T($S)", NullPointerException.class,
                "Field %s.%s contains a null element, but its element type is not @Nullable".formatted(origin.getEnclosingElement().getSimpleName(), origin.getSimpleName()))
            .build();
    }

    private static boolean isEnum(TypeMirror type) {
        return type instanceof DeclaredType declaredType && declaredType.asElement().getKind() == ElementKind.ENUM;
    }

    private boolean isSet(TypeMirror type) {
        return this.types.erasure(type).toString().equals(java.util.Set.class.getCanonicalName());
    }

    /**
     * @return element type when the type is a {@link List}, {@link java.util.Set} or {@link java.util.Collection}, otherwise {@code null}
     */
    private TypeMirror collectionElementType(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType)) {
            return null;
        }
        var erasure = this.types.erasure(type).toString();
        if (!erasure.equals(List.class.getCanonicalName())
            && !erasure.equals(java.util.Set.class.getCanonicalName())
            && !erasure.equals(java.util.Collection.class.getCanonicalName())) {
            return null;
        }
        var arguments = declaredType.getTypeArguments();
        return arguments.size() == 1
            ? arguments.get(0)
            : null;
    }

    /**
     * @return value type when the type is a {@link Map} with {@link String} keys, otherwise {@code null}
     */
    private TypeMirror mapValueType(TypeMirror type, VariableElement origin) {
        if (!(type instanceof DeclaredType declaredType)) {
            return null;
        }
        if (!this.types.erasure(type).toString().equals(Map.class.getCanonicalName())) {
            return null;
        }
        var arguments = declaredType.getTypeArguments();
        if (arguments.size() != 2) {
            return null;
        }
        if (!arguments.get(0).toString().equals(String.class.getCanonicalName())) {
            throw new ProcessingErrorException("""
                Mongo entity field is invalid:
                  %s

                Problem:
                  Map key type is %s, but a BSON document can only have string keys.

                Hint:
                  A Map field is stored as a nested document whose field names are the map keys.

                Fix:
                  Use Map<String, ?>, or supply a Codec for this field via @Mapping.
                """.formatted(origin.getSimpleName(), arguments.get(0)), origin);
        }
        return arguments.get(1);
    }

    private static CodeBlock statement(CodeBlock expression) {
        return CodeBlock.builder().addStatement(expression).build();
    }

    private static final class Names {

        private int counter;

        private String next(String prefix) {
            return prefix + (++this.counter);
        }
    }
}
