package io.koraframework.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.ProcessingError;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.kora.app.annotation.processor.component.DependencyClaim;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import org.jspecify.annotations.Nullable;

import javax.lang.model.util.Elements;
import java.util.List;

public class CircularDependencyException extends ProcessingErrorException {

    /**
     * @param cycle     components forming the cycle, the last one is the component that closes the cycle
     * @param requester component whose dependency closes the cycle
     * @param claim     dependency of the requester that closes the cycle
     * @param note      why the cycle cannot be broken automatically
     * @param fix       fix specific to the reason from note
     */
    public CircularDependencyException(Elements elements,
                                       List<ComponentDeclaration> cycle,
                                       ComponentDeclaration declaration,
                                       ComponentDeclaration requester,
                                       DependencyClaim claim,
                                       @Nullable String note,
                                       @Nullable String fix) {
        super(getError(elements, cycle, declaration, requester, claim, note, fix));
    }

    private static ProcessingError getError(Elements elements,
                                            List<ComponentDeclaration> cycle,
                                            ComponentDeclaration declaration,
                                            ComponentDeclaration requester,
                                            DependencyClaim claim,
                                            @Nullable String note,
                                            @Nullable String fix) {
        var msg = new StringBuilder();
        msg.append("Circular dependency found:\n  ").append(TypeName.get(declaration.type()));
        msg.append(DependencySourceFormatter.tagSuffix(elements, declaration.tag()));
        msg.append("\n\nDependency cycle:");
        for (int i = 0; i < cycle.size(); i++) {
            msg.append(i == 0 ? "\n  @--- " : "\n  ^--- ").append(cycle.get(i).declarationString());
        }
        msg.append(" [CYCLE]");
        msg.append(DependencySourceFormatter.requiredAtSection(requester, claim.source()));
        if (note != null) {
            msg.append("\n\nNote:\n  ").append(note);
        }
        msg.append("\n\nFix:");
        if (fix != null) {
            msg.append("\n  - ").append(fix);
        }
        msg.append("\n  - Move shared state into a separate component.");
        msg.append("\n  - Do not create dependency cycles in ").append(CommonClassNames.lifecycle.simpleName()).append('.');
        return new ProcessingError(msg.toString(), claim.source() == null ? declaration.source() : claim.source());
    }
}
