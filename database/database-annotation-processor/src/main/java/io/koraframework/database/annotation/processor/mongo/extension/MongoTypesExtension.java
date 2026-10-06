package io.koraframework.database.annotation.processor.mongo.extension;

import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.database.annotation.processor.mongo.MongoTypes;
import io.koraframework.kora.app.annotation.processor.extension.KoraExtension;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Objects;

// Codec<T> for types annotated with @EntityMongo
public class MongoTypesExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;

    public MongoTypesExtension(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, @Nullable String tag) {
        if (tag != null) {
            return null;
        }
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return null;
        }
        if (!(TypeName.get(typeMirror) instanceof ParameterizedTypeName ptn) || !Objects.equals(ptn.rawType(), MongoTypes.CODEC)) {
            return null;
        }

        var entityTypeMirror = declaredType.getTypeArguments().get(0);
        if (!(this.types.asElement(entityTypeMirror) instanceof TypeElement entityTypeElement)) {
            return null;
        }
        if (!AnnotationUtils.isAnnotationPresent(entityTypeElement, MongoTypes.MONGO_ENTITY)) {
            return null;
        }
        return KoraExtensionDependencyGenerator.generatedFromWithName(this.elements, entityTypeElement, NameUtils.generatedType(entityTypeElement, "MongoCodec"));
    }
}
