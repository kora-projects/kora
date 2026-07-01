package io.koraframework.application.graph.exception;

import io.koraframework.application.graph.GraphCondition;
import io.koraframework.application.graph.Node;

import java.util.List;
import java.util.Map;

public class NoneOfConditionalNodeMatches extends IllegalStateException {
    public NoneOfConditionalNodeMatches(List<Map.Entry<Node<?>, GraphCondition.ConditionResult.Failed>> errors) {
        var sb = new StringBuilder("None of conditional candidates was created:\n");
        for (var failedReason : errors) {
            sb.append("- node ").append(" of type ").append(failedReason.getKey().type()).append(failedReason.getKey()).append(":\n");
            sb.append(failedReason.getValue().reason().indent(4));
        }
        super(sb.toString());
    }
}
