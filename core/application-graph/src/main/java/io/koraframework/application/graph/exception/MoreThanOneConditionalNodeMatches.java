package io.koraframework.application.graph.exception;

import io.koraframework.application.graph.GraphCondition;
import io.koraframework.application.graph.Node;

import java.util.List;
import java.util.Map;

public class MoreThanOneConditionalNodeMatches extends IllegalStateException {
    public MoreThanOneConditionalNodeMatches(List<Map.Entry<Node<?>, GraphCondition.ConditionResult.Matched>> matchReasons) {
        var sb = new StringBuilder("More than one conditional candidates was created:\n");
        for (var matchReason : matchReasons) {
            sb.append("- node ").append(matchReason.getKey()).append(" of type ").append(matchReason.getKey().type()).append(":\n");
            sb.append(matchReason.getValue().reason().indent(4));
        }
        super(sb.toString());
    }
}
