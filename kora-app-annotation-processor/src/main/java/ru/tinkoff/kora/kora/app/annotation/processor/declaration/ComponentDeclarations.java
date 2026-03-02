package ru.tinkoff.kora.kora.app.annotation.processor.declaration;

import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;

public class ComponentDeclarations {
    private final ProcessingContext ctx;
    private final Map<TypeName, List<DeclarationWithIndex>> typeToDeclarations = new LinkedHashMap<>();
    private final List<ComponentDeclaration> declarations = new ArrayList<>();
    private final List<DeclarationWithIndex> interceptors = new ArrayList<>();


    public ComponentDeclarations(ProcessingContext ctx) {
        this.ctx = ctx;
    }

    public ComponentDeclarations(ComponentDeclarations that) {
        this.ctx = that.ctx;
        for (var typeWithDeclarations : that.typeToDeclarations.entrySet()) {
            this.typeToDeclarations.put(typeWithDeclarations.getKey(), new ArrayList<>(typeWithDeclarations.getValue()));
        }
        this.declarations.addAll(that.declarations);
    }

    public int add(ComponentDeclaration declaration) {
        assert !declaration.isTemplate();
        var types = collectTypeNames(ctx, declaration.type());
        var index = this.declarations.size();
        this.declarations.add(declaration);
        var declarationWithIndex = new DeclarationWithIndex(declaration, index);
        for (var type : types) {
            this.typeToDeclarations.computeIfAbsent(type, _ -> new ArrayList<>())
                .add(declarationWithIndex);
        }
        if (declaration.isInterceptor()) {
            this.interceptors.add(declarationWithIndex);
        }
        return index;
    }

    public List<DeclarationWithIndex> getByType(TypeMirror type) {
        var typeName = TypeName.get(type);
        if (typeName instanceof ParameterizedTypeName ptn) {
            typeName = ptn.rawType();
        }
        return Collections.unmodifiableList(this.typeToDeclarations.getOrDefault(typeName, List.of()));
    }

    public List<DeclarationWithIndex> interceptors() {
        return Collections.unmodifiableList(this.interceptors);
    }


    private static List<TypeName> collectTypeNames(ProcessingContext ctx, TypeMirror type) {
        var set = new HashSet<TypeName>();
        visit(ctx, set, type);
        return new ArrayList<>(set);
    }

    private static void visit(ProcessingContext ctx, Set<TypeName> set, TypeMirror type) {
        if (type.getKind() == TypeKind.NONE) {
            return;
        }
        var typeName = TypeName.get(type);
        if (typeName instanceof ParameterizedTypeName ptn) {
            typeName = ptn.rawType();
        }
        set.add(typeName);
        if (type instanceof DeclaredType dt) {
            var typeElement = (TypeElement) dt.asElement();
            var wrappedType = ctx.serviceTypeHelper.unwrap(type);
            if (wrappedType != null) {
                visit(ctx, set, wrappedType);
            }

            visit(ctx, set, typeElement.getSuperclass());
            for (var anInterface : typeElement.getInterfaces()) {
                visit(ctx, set, anInterface);
            }
        }
    }

}
