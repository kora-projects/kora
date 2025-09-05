package ru.tinkoff.kora.annotation.processor.common;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.*;

public class RecordClassBuilder {

    private final Class<?> origin;
    public final String name;
    public final Set<Modifier> modifiers = new HashSet<>();
    public final List<RecordComponent> components = new ArrayList<>();
    public final List<TypeName> interfaces = new ArrayList<>();
    public final List<Element> originatingElements = new ArrayList<>();
    private boolean enforceEquals = false;
    private CodeBlock defaultConstructorBody;

    public record RecordComponent(String name, TypeName type, List<AnnotationSpec> annotations, CodeBlock defaultValue, boolean notNullCheck) {}

    public RecordClassBuilder(String name, Class<?> origin) {
        this.name = name;
        this.origin = origin;
    }

    public RecordClassBuilder defaultConstructorBody(CodeBlock defaultConstructorBody) {
        this.defaultConstructorBody = defaultConstructorBody;
        return this;
    }

    public RecordClassBuilder enforceEquals() {
        this.enforceEquals = true;
        return this;
    }

    public RecordClassBuilder addModifier(Modifier modifier) {
        this.modifiers.add(modifier);
        return this;
    }

    public RecordClassBuilder addComponent(String name, TypeName type, List<AnnotationSpec> annotations) {
        this.components.add(new RecordComponent(name, type, annotations, null, false));
        return this;
    }

    public RecordClassBuilder addComponent(String name, TypeName type) {
        this.components.add(new RecordComponent(name, type, List.of(), null, false));
        return this;
    }

    public RecordClassBuilder addComponent(String name, TypeName type, CodeBlock defaultValue) {
        this.components.add(new RecordComponent(name, type, List.of(), defaultValue, false));
        return this;
    }

    public RecordClassBuilder addComponent(String name, TypeName type, boolean checkNotNull) {
        this.components.add(new RecordComponent(name, type, List.of(), null, checkNotNull));
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
        sb.append("@").append(CommonClassNames.koraGenerated.canonicalName()).append("(\"").append(origin.getCanonicalName()).append("\")").append("\n");

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
                var hasNullable = component.annotations.stream().anyMatch(a -> a.type().toString().endsWith(".Nullable"));
                if (!hasNullable) {
                    sb.append("  @jakarta.annotation.Nullable ");
                }
            } else if(component.notNullCheck && !component.type.isPrimitive()) {
                sb.append("  @jakarta.annotation.Nonnull ");
            } else {
                sb.append("  ");
            }

            sb.append(component.type.toString()).append(" ").append(component.name);
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
                    sb.append(", ");
                }
                sb.append(anInterface.toString());
            }
        }
        sb.append(" {\n");
        sb.append("  public ").append(this.name).append(" {\n");
        for (var component : components) {
            if (component.defaultValue != null) {
                sb.append("    if (").append(component.name).append(" == null) ").append(component.name).append(" = ").append(component.defaultValue.toString()).append(";\n");
            } else if(component.notNullCheck && !component.type.isPrimitive()) {
                sb.append("    ").append(Objects.class.getCanonicalName()).append(".requireNonNull(").append(component.name).append(");\n");
            }
        }

        if(this.defaultConstructorBody != null) {
            sb.append(this.defaultConstructorBody.toString().indent(4));
            sb.append("\n  }\n");
        } else {
            sb.append("  }\n");
        }

        if(enforceEquals) {
            if (components.stream().anyMatch(f -> f.type() instanceof ArrayTypeName)) {
                sb.append("    @Override\n");
                sb.append("    public boolean equals(Object o) {\n");
                sb.append("      return this == o || o instanceof ").append(name).append(" that\n");
                for (var component : components) {
                    if (component.type() instanceof ArrayTypeName) {
                        sb.append("        && java.util.Arrays.equals(this.").append(component.name()).append("(), that.").append(component.name()).append("())\n");
                    } else if (component.type.isPrimitive()) {
                        sb.append("        && this.").append(component.name()).append("() == that.").append(component.name()).append("()\n");
                    } else {
                        sb.append("        && java.util.Objects.equals(this.").append(component.name()).append("(), that.").append(component.name()).append("())\n");
                    }
                }
                sb.append("      ;\n");
                sb.append("    }\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        return render();
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
