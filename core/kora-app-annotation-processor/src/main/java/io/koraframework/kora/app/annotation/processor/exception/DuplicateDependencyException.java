package io.koraframework.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.ProcessingError;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.kora.app.annotation.processor.component.DependencyClaim;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.lang.model.util.Elements;
import java.util.List;

public class DuplicateDependencyException extends ProcessingErrorException {

    public DuplicateDependencyException(Elements elements,
                                        DependencyClaim claim,
                                        ComponentDeclaration declaration,
                                        List<ComponentDeclaration> foundDeclarations) {
        super(List.of(getError(elements, claim, declaration, foundDeclarations)));
    }

    private static ProcessingError getError(Elements elements,
                                            DependencyClaim claim,
                                            ComponentDeclaration declaration,
                                            List<ComponentDeclaration> foundDeclarations) {
        var msg = new StringBuilder();
        msg.append("Multiple components match dependency:\n  ").append(TypeName.get(claim.type()));
        msg.append(DependencySourceFormatter.tagSuffix(elements, claim.tag()));
        msg.append(DependencySourceFormatter.requiredAtSection(declaration, claim.source()));
        msg.append("\n\nCandidates:");
        for (var candidate : foundDeclarations) {
            msg.append("\n  - ").append(TypeName.get(candidate.type()))
                .append(DependencySourceFormatter.tagSuffix(elements, candidate.tag()))
                .append(" from ").append(candidate.declarationString());
        }
        msg.append("\n\nFix:");
        msg.append("\n  - Add different @Tag(...) annotations to candidates and request the needed tag.");
        msg.append("\n  - Mark fallback candidate with @DefaultComponent.");
        msg.append("\n  - Remove one duplicate provider.");
        return new ProcessingError(msg.toString(), claim.source() == null ? declaration.source() : claim.source());
    }
}
