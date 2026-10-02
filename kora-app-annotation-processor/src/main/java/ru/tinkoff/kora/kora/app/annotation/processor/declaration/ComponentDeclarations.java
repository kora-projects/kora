package ru.tinkoff.kora.kora.app.annotation.processor.declaration;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;

/**
 * Component declarations in insertion order with an index "raw type name -> declarations".
 * <p>
 * The index only narrows the list of candidates: {@link #getByType(TypeMirror)} returns every declaration
 * that can be assignable (directly or through {@code Wrapped<T>}) to the requested type, in insertion order.
 * Callers still apply the exact same matching checks as before.
 */
public final class ComponentDeclarations {
    private final ProcessingContext ctx;
    private final List<ComponentDeclaration> declarations;
    private final Map<String, List<DeclarationWithIndex>> typeToDeclarations;
    // declarations whose type hierarchy can't be indexed reliably: they are candidates for every type
    private final List<DeclarationWithIndex> notIndexed;
    private final List<ComponentDeclaration> interceptors;

    public ComponentDeclarations(ProcessingContext ctx, List<ComponentDeclaration> declarations) {
        this.ctx = ctx;
        this.declarations = new ArrayList<>(declarations.size());
        this.typeToDeclarations = new HashMap<>();
        this.notIndexed = new ArrayList<>();
        this.interceptors = new ArrayList<>();
        for (var declaration : declarations) {
            this.add(declaration);
        }
    }

    public ComponentDeclarations(ComponentDeclarations that) {
        this.ctx = that.ctx;
        this.declarations = new ArrayList<>(that.declarations);
        this.typeToDeclarations = new HashMap<>(that.typeToDeclarations.size());
        for (var entry : that.typeToDeclarations.entrySet()) {
            this.typeToDeclarations.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        this.notIndexed = new ArrayList<>(that.notIndexed);
        this.interceptors = new ArrayList<>(that.interceptors);
    }

    public void add(ComponentDeclaration declaration) {
        var declarationWithIndex = new DeclarationWithIndex(declaration, this.declarations.size());
        this.declarations.add(declaration);
        var keys = indexKeys(this.ctx, declaration.type());
        if (keys == null) {
            this.notIndexed.add(declarationWithIndex);
        } else {
            for (var key : keys) {
                this.typeToDeclarations.computeIfAbsent(key, k -> new ArrayList<>()).add(declarationWithIndex);
            }
        }
        if (declaration.isInterceptor()) {
            this.interceptors.add(declaration);
        }
    }

    /**
     * @return declarations that may be assignable to the given type, in insertion order
     */
    public List<ComponentDeclaration> getByType(TypeMirror type) {
        var key = lookupKey(type);
        if (key == null) {
            return Collections.unmodifiableList(this.declarations);
        }
        var indexed = this.typeToDeclarations.getOrDefault(key, List.of());
        var candidates = indexed;
        if (!this.notIndexed.isEmpty()) {
            candidates = new ArrayList<>(indexed.size() + this.notIndexed.size());
            candidates.addAll(indexed);
            candidates.addAll(this.notIndexed);
            candidates.sort(Comparator.comparingInt(DeclarationWithIndex::index));
        }
        var result = new ArrayList<ComponentDeclaration>(candidates.size());
        for (var candidate : candidates) {
            result.add(candidate.declaration());
        }
        return result;
    }

    /**
     * @return declarations with {@link ComponentDeclaration#isInterceptor()}, in insertion order
     */
    public List<ComponentDeclaration> interceptors() {
        return Collections.unmodifiableList(this.interceptors);
    }

    /**
     * @return index key of the requested type or null if every declaration should be considered
     */
    @Nullable
    public static String lookupKey(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
    }

    /**
     * @return raw names of the type, all of its supertypes and of its unwrapped type with supertypes;
     * null if the type can't be indexed and should be considered for every requested type
     */
    @Nullable
    public static Set<String> indexKeys(ProcessingContext ctx, TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var keys = new HashSet<String>();
        // every declared type is assignable to Object, but interfaces don't have it as a supertype
        keys.add(Object.class.getCanonicalName());
        if (!collectKeys(type, keys)) {
            return null;
        }
        final TypeMirror unwrapped;
        try {
            unwrapped = ctx.serviceTypeHelper.unwrap(type);
        } catch (RuntimeException e) {
            return null;
        }
        if (unwrapped != null && !collectKeys(unwrapped, keys)) {
            return null;
        }
        return keys;
    }

    private static boolean collectKeys(TypeMirror type, Set<String> keys) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        var typeElement = (TypeElement) ((DeclaredType) type).asElement();
        if (!keys.add(typeElement.getQualifiedName().toString())) {
            return true;
        }
        var superclass = typeElement.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE && !collectKeys(superclass, keys)) {
            return false;
        }
        for (var anInterface : typeElement.getInterfaces()) {
            if (!collectKeys(anInterface, keys)) {
                return false;
            }
        }
        return true;
    }
}
