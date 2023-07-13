package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecordClassBuilder {
    public final String name;
    public final Set<Modifier> modifiers = new HashSet<>();
    public final List<RecordComponent> components = new ArrayList<>();
    public final List<TypeName> interfaces = new ArrayList<>();
    public final List<Element> originatingElements = new ArrayList<>();
    private CodeBlock defaultConstructorBody = CodeBlock.of("");

    public record RecordComponent(String name, TypeName type, List<AnnotationSpec> annotations, CodeBlock defaultValue) {}

    public RecordClassBuilder(String name) {
        this.name = name;
    }

    public RecordClassBuilder defaultConstructorBody(CodeBlock defaultConstructorBody) {
        this.defaultConstructorBody = defaultConstructorBody;
        return this;
    }

    public RecordClassBuilder addModifier(Modifier modifier) {
        this.modifiers.add(modifier);
        return this;
    }

    public RecordClassBuilder addComponent(String name, TypeName type, List<AnnotationSpec> annotations) {
        this.components.add(new RecordComponent(name, type, annotations, null));
        return this;
    }

    public RecordClassBuilder addComponent(String name, TypeName type) {
        this.components.add(new RecordComponent(name, type, List.of(), null));
        return this;
    }
    public RecordClassBuilder addComponent(String name, TypeName type, CodeBlock defaultValue) {
        this.components.add(new RecordComponent(name, type, List.of(), defaultValue));
        return this;
    }

    public RecordClassBuilder superinterface(TypeName type) {
        this.interfaces.add(type);
        return this;
    }

    public RecordClassBuilder originatingElement(Element element) {
        this.originatingElements.add(element);
        return this;
    }

    public String render() {
        var sb = new StringBuilder();
        for (var modifier : this.modifiers) {
            sb.append(modifier.toString()).append(' ');
        }
        sb.append("record ").append(this.name).append("(\n");
        for (int i = 0; i < this.components.size(); i++) {
            var component = this.components.get(i);
            for (var annotation : component.annotations) {
                sb.append("  ").append(annotation.toString()).append("\n");
            }
            if (component.defaultValue != null) {
                var hasNullable = component.annotations.stream().anyMatch(a -> a.type.toString().endsWith(".Nullable"));
                if (!hasNullable) {
                    sb.append("  @javax.annotation.Nullable\n");
                }

            }
            sb.append("  ").append(component.type.toString()).append(" ").append(component.name);
            if (i < this.components.size() - 1) {
                sb.append(',');
            }
            sb.append("\n");
        }
        sb.append(")");
        if (!this.interfaces.isEmpty()) {
            sb.append(" implements ");
            for (int i = 0; i < this.interfaces.size(); i++) {
                var anInterface = this.interfaces.get(i);
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("\n  ").append(anInterface.toString());
            }
        }
        sb.append(" {\n");
        sb.append("  public ").append(this.name).append("{\n");
        for (var component : components) {
            if (component.defaultValue != null) {
                sb.append("    if (").append(component.name).append(" == null) ").append(component.name).append(" = ").append(component.defaultValue.toString()).append(";\n");
            }
        }

        sb.append(this.defaultConstructorBody.toString().indent(4));
        sb.append("\n  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    public void writeTo(Filer filer, String packageName) throws IOException {
        var configFile = filer.createSourceFile(packageName + "." + name, this.originatingElements.toArray(Element[]::new));
        try (var w = configFile.openWriter()) {
            w.write("package ");
            w.write(packageName);
            w.write(";\n\n");
            w.write(this.render());
        }
    }
}
