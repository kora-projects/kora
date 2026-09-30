package io.koraframework.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.ProcessingError;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.kora.app.annotation.processor.DependencyModuleHintProvider;
import io.koraframework.kora.app.annotation.processor.GraphBuilder;
import io.koraframework.kora.app.annotation.processor.component.DependencyClaim;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;

public class UnresolvedDependencyException extends ProcessingErrorException {
    private final ComponentDeclaration component;
    private final DependencyClaim dependencyClaim;
    private final Deque<GraphBuilder.ResolutionFrame> stack;

    private static final int MAX_CANDIDATES = 5;

    public UnresolvedDependencyException(Elements elements,
                                         TypeElement koraApp,
                                         ComponentDeclaration component,
                                         DependencyClaim dependencyClaim,
                                         Deque<GraphBuilder.ResolutionFrame> stack,
                                         List<DependencyModuleHintProvider.Hint> hints,
                                         List<ComponentDeclaration> sameTypeDifferentTag) {

        this(component, dependencyClaim, List.of(getError(elements, koraApp, component, dependencyClaim, stack, hints, sameTypeDifferentTag)), stack);
    }

    private static ProcessingError getError(Elements elements, TypeElement koraApp, ComponentDeclaration component, DependencyClaim dependencyClaim, Deque<GraphBuilder.ResolutionFrame> stack, List<DependencyModuleHintProvider.Hint> hints, List<ComponentDeclaration> sameTypeDifferentTag) {
        var errorSource = dependencyClaim.source() == null ? component.source() : dependencyClaim.source();
        return new ProcessingError(constructErrorMessage(elements, koraApp, component, dependencyClaim, stack, hints, sameTypeDifferentTag), errorSource);
    }


    public UnresolvedDependencyException(ComponentDeclaration component,
                                         DependencyClaim dependencyClaim,
                                         List<ProcessingError> errors,
                                         Deque<GraphBuilder.ResolutionFrame> stack) {
        super(errors);
        this.component = component;
        this.dependencyClaim = dependencyClaim;
        this.stack = stack;
    }

    public ComponentDeclaration getComponent() {
        return component;
    }

    public DependencyClaim getDependencyClaim() {
        return dependencyClaim;
    }

    public Deque<GraphBuilder.ResolutionFrame> getStack() {
        return stack;
    }

    private static String constructErrorMessage(Elements elements, TypeElement koraApp, ComponentDeclaration component, DependencyClaim dependencyClaim, Deque<GraphBuilder.ResolutionFrame> stack, List<DependencyModuleHintProvider.Hint> hints, List<ComponentDeclaration> sameTypeDifferentTag) {
        var msg = new StringBuilder();
        msg.append("No component found for dependency:\n  ");
        msg.append(TypeName.get(dependencyClaim.type()));
        msg.append(DependencySourceFormatter.tagSuffix(elements, dependencyClaim.tag()));

        msg.append(DependencySourceFormatter.requiredAtSection(component, dependencyClaim.source()));

        var treeMsg = getDependencyTreeSimpleMessage(elements, koraApp, stack, component, dependencyClaim);
        msg.append("\n\n").append(treeMsg);
        if (!sameTypeDifferentTag.isEmpty()) {
            msg.append("\n\nNote:");
            msg.append("\n  Found component(s) of the same type with other tags. Maybe the tag was forgotten or mixed up:");
            for (int i = 0; i < Math.min(sameTypeDifferentTag.size(), MAX_CANDIDATES); i++) {
                var candidate = sameTypeDifferentTag.get(i);
                msg.append("\n  - ").append(TypeName.get(candidate.type()))
                    .append(DependencySourceFormatter.tagSuffix(elements, candidate.tag()))
                    .append(" from ").append(candidate.declarationString());
            }
            if (sameTypeDifferentTag.size() > MAX_CANDIDATES) {
                msg.append("\n  - ... and ").append(sameTypeDifferentTag.size() - MAX_CANDIDATES).append(" more");
            }
        }
        if (!hints.isEmpty()) {
            msg.append("\n\nHint:");
            for (var hint : hints) {
                // hint lines are aligned under the bullet, whatever indentation hint author used
                var lines = hint.message().strip().lines().map(String::strip).toList();
                msg.append("\n  - ").append(String.join("\n    ", lines));
            }
        }
        msg.append("\n\nFix:");
        appendTagFixes(msg, elements, dependencyClaim, sameTypeDifferentTag);
        msg.append("\n  - Add @").append(CommonClassNames.component.simpleName()).append(" to an implementation of ").append(TypeName.get(dependencyClaim.type())).append('.');
        msg.append("\n  - Add a module method that returns ").append(TypeName.get(dependencyClaim.type())).append('.');
        msg.append("\n  - Include a module that provides ").append(TypeName.get(dependencyClaim.type())).append(" in @KoraApp.");
        return msg.toString();
    }

    /**
     * Suggests how to make the dependency and the found same type components agree on the tag, in both directions:
     * request the tag the components have, or change the tag of the component.
     */
    private static void appendTagFixes(StringBuilder msg, Elements elements, DependencyClaim claim, List<ComponentDeclaration> sameTypeDifferentTag) {
        if (sameTypeDifferentTag.isEmpty()) {
            return;
        }
        var candidateTags = new LinkedHashSet<String>();
        var hasUntaggedCandidate = false;
        for (var candidate : sameTypeDifferentTag) {
            if (candidate.tag() == null) {
                hasUntaggedCandidate = true;
            } else {
                candidateTags.add(candidate.tag());
            }
        }
        for (var tag : candidateTags) {
            msg.append("\n  - Request the dependency with ").append(DependencySourceFormatter.tag(elements, tag)).append(" to use the component with this tag.");
        }
        if (claim.tag() != null && hasUntaggedCandidate) {
            msg.append("\n  - Remove ").append(DependencySourceFormatter.tag(elements, claim.tag())).append(" from the dependency to use the component without tags.");
        }
        if (claim.tag() != null) {
            msg.append("\n  - Or add ").append(DependencySourceFormatter.tag(elements, claim.tag())).append(" to the component declaration so it matches this dependency.");
        } else {
            msg.append("\n  - Or remove the tag from the component declaration so it matches this dependency.");
        }
    }

    private static String getDependencyTreeSimpleMessage(Elements elements,
                                                         TypeElement koraApp,
                                                         Deque<GraphBuilder.ResolutionFrame> stack,
                                                         ComponentDeclaration declaration,
                                                         DependencyClaim dependencyClaim) {
        var msg = new StringBuilder();
        msg.append("Dependency resolution path:");

        var stackFrames = new ArrayList<GraphBuilder.ResolutionFrame>();
        var i = stack.descendingIterator();
        while (i.hasNext()) {
            var iFrame = i.next();
            if (iFrame instanceof GraphBuilder.ResolutionFrame.Root root) {
                stackFrames.add(root);
                break;
            }
            stackFrames.add(iFrame);
        }

        // reversed order
        var delimiterRoot = "\n  @--- ";
        var delimiter = "\n  ^--- ";
        for (int i1 = stackFrames.size() - 1; i1 >= 0; i1--) {
            var iFrame = stackFrames.get(i1);
            if (iFrame instanceof GraphBuilder.ResolutionFrame.Root root) {
                var rootDeclaration = root.componentDeclaration();
                var rootDeclarationAsStr = rootDeclaration.declarationString();
                var koraAppName = koraApp.getQualifiedName().toString();
                if (rootDeclaration instanceof ComponentDeclaration.FromModuleComponent mc && !rootDeclarationAsStr.contains(koraAppName)) {
                    var moduleTypeName = mc.module().element().getQualifiedName().toString();
                    msg.append(delimiterRoot).append(rootDeclarationAsStr.replace(moduleTypeName, koraAppName));
                    msg.append(delimiter).append(rootDeclarationAsStr);
                } else {
                    msg.append(delimiterRoot).append(rootDeclarationAsStr);
                }
            } else {
                var c = (GraphBuilder.ResolutionFrame.Component) iFrame;
                msg.append(delimiter).append(c.declaration().declarationString());
            }
        }

        msg.append(delimiter).append(declaration.declarationString());

        msg.append(delimiter).append(TypeName.get(dependencyClaim.type()));
        if (dependencyClaim.tag() != null) {
            msg.append(" ").append(DependencySourceFormatter.tag(elements, dependencyClaim.tag()));
        }
        msg.append(" [MISSING]");

        return msg.toString();
    }

}
