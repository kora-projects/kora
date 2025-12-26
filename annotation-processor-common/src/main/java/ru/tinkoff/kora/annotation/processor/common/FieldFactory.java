package ru.tinkoff.kora.annotation.processor.common;

import com.palantir.javapoet.*;
import jakarta.annotation.Nullable;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FieldFactory {
    private final Types types;
    private final Elements elements;
    @Nullable
    private final TypeSpec.Builder builder;
    private final MethodSpec.Builder constructor;
    private final Map<FieldKey, String> fields = new HashMap<>();
    private final String prefix;

    public String get(ClassName mapperType, TypeMirror mappedType, Element element) {
        var type = ParameterizedTypeName.get(mapperType, TypeName.get(mappedType).box());
        var tags = TagUtils.parseTagValue(element);
        var key = new FieldKey(type, tags);
        return this.fields.get(key);
    }

    public String get(ClassName mapperType, CommonUtils.MappingData mappingData, TypeMirror type) {
        var mapperClass = mappingData.mapperClass();
        if (mapperClass == null) {
            var mapperTypeName = ParameterizedTypeName.get(mapperType, TypeName.get(type).box());
            return this.fields.get(new FieldKey(mapperTypeName, mappingData.mapperTag()));
        }
        var mapperTypeName = TypeName.get(mapperClass);
        return this.fields.get(new FieldKey(mapperTypeName, mappingData.mapperTag()));
    }

    record FieldKey(TypeName typeName, @Nullable String tag) {}

    public FieldFactory(Types types, Elements elements, @Nullable TypeSpec.Builder builder, MethodSpec.Builder constructor, String prefix) {
        this.types = types;
        this.elements = elements;
        this.builder = builder;
        this.constructor = constructor;
        this.prefix = prefix;
    }

    public String add(TypeName typeName, @Nullable String tag) {
        var key = new FieldKey(typeName, tag);
        var existed = this.fields.get(key);
        if (existed != null) {
            return existed;
        }
        var name = this.prefix + (this.fields.size() + 1);
        this.fields.put(key, name);
        if (this.builder != null) {
            this.builder.addField(typeName, name, Modifier.PRIVATE, Modifier.FINAL);
            this.constructor.addStatement("this.$N = $N", name, name);
        }
        var parameter = ParameterSpec.builder(typeName, name);
        var tagAnnotation = TagUtils.makeAnnotationSpec(tag);
        if (tagAnnotation != null) {
            parameter.addAnnotation(tagAnnotation);
        }
        this.constructor.addParameter(parameter.build());
        return name;
    }

    public String add(TypeName typeName, CodeBlock initializer) {
        var key = new FieldKey(typeName, null);
        var existed = this.fields.get(key);
        if (existed != null) {
            return existed;
        }
        var name = this.prefix + (this.fields.size() + 1);
        this.fields.put(key, name);
        if (this.builder != null) {
            this.builder.addField(typeName, name, Modifier.PRIVATE, Modifier.FINAL);
            this.constructor.addStatement("this.$N = $L", name, initializer);
        } else {
            this.constructor.addStatement("var $N = $L", name, initializer);
        }
        return name;
    }

    public String add(TypeMirror typeMirror, @Nullable String tag) {
        var typeName = TypeName.get(typeMirror);
        var key = new FieldKey(typeName, tag);
        var existed = this.fields.get(key);
        if (existed != null) {
            return existed;
        }
        var name = this.prefix + (this.fields.size() + 1);
        this.fields.put(key, name);
        if (tag == null && CommonUtils.hasDefaultConstructorAndFinal(this.types, typeMirror)) {
            if (this.builder != null) {
                this.builder.addField(typeName, name, Modifier.PRIVATE, Modifier.FINAL);
                this.constructor.addStatement("this.$N = new $T()", name, typeName);
            } else {
                this.constructor.addStatement("var $N = new $T()", name, typeName);
            }
        } else {
            this.constructor.addParameter(typeName, name);
            if (this.builder != null) {
                this.builder.addField(typeName, name, Modifier.PRIVATE, Modifier.FINAL);
                this.constructor.addStatement("this.$N = $N", name, name);
            }
        }
        return name;
    }

    public String add(@Nullable CommonUtils.MappingData mapping, TypeName defaultType) {
        var tags = mapping == null
            ? null
            : mapping.mapperTag();
        var typeName = mapping == null
            ? defaultType
            : TypeName.get(Objects.requireNonNull(mapping.mapperClass()));
        var typeElement = typeName instanceof ParameterizedTypeName ptn
            ? this.elements.getTypeElement(ptn.rawType().canonicalName())
            : this.elements.getTypeElement(((ClassName) typeName).canonicalName());

        var key = new FieldKey(typeName, tags);
        var existed = this.fields.get(key);
        if (existed != null) {
            return existed;
        }
        var name = this.prefix + (this.fields.size() + 1);
        this.fields.put(key, name);
        if (tags == null && CommonUtils.hasDefaultConstructorAndFinal(typeElement)) {
            if (this.builder != null) {
                this.builder.addField(typeName, name, Modifier.PRIVATE, Modifier.FINAL);
                this.constructor.addStatement("this.$N = new $T()", name, typeName);
            } else {
                this.constructor.addStatement("var $N = new $T()", name, typeName);
            }
        } else {
            var parameter = ParameterSpec.builder(typeName, name);
            var tag = TagUtils.makeAnnotationSpec(tags);
            if (tag != null) {
                parameter.addAnnotation(tag);
            }
            this.constructor.addParameter(parameter.build());
            if (this.builder != null) {
                this.builder.addField(typeName, name, Modifier.PRIVATE, Modifier.FINAL);
                this.constructor.addStatement("this.$N = $N", name, name);
            }
        }
        return name;
    }
}
