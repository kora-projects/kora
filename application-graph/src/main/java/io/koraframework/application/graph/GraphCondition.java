package io.koraframework.application.graph;

import java.util.ArrayList;

public interface GraphCondition {
    ConditionResult eval();

    static GraphCondition or(GraphCondition... conditions) {
        assert conditions.length > 0;
        return () -> {
            var failedReasons = new ArrayList<ConditionResult.Failed>();
            for (var condition : conditions) {
                switch (condition.eval()) {
                    case ConditionResult.Failed failed -> failedReasons.add(failed);
                    case ConditionResult.Matched matched -> {
                        return matched;
                    }
                }
            }
            var sb = new StringBuilder("All of required conditions were failed: \n");
            for (var failedReason : failedReasons) {
                sb.append(failedReason.reason().indent(2)).append("\n");
            }
            return new ConditionResult.Failed(sb.toString());
        };
    }

    static GraphCondition and(GraphCondition... conditions) {
        assert conditions.length > 0;
        return () -> {
            var failedReasons = new ArrayList<ConditionResult.Failed>();
            var matchedReasons = new ArrayList<ConditionResult.Matched>();
            for (var condition : conditions) {
                switch (condition.eval()) {
                    case ConditionResult.Failed failed -> failedReasons.add(failed);
                    case ConditionResult.Matched matched -> matchedReasons.add(matched);
                }
            }
            if (failedReasons.isEmpty()) {
                var sb = new StringBuilder("All of required conditions were matched:\n");
                for (var matched : matchedReasons) {
                    sb.append(matched.reason().indent(2));
                }
                return new ConditionResult.Failed(sb.toString());
            } else {
                var sb = new StringBuilder("Some of required conditions were failed:\n");
                for (var failedReason : failedReasons) {
                    sb.append(failedReason.reason().indent(2));
                }
                return new ConditionResult.Failed(sb.toString());
            }
        };
    }

    sealed interface ConditionResult {
        static ConditionResult matched(String reason) {
            return new Matched(reason);
        }

        static ConditionResult failed(String reason) {
            return new Failed(reason);
        }

        record Matched(String reason) implements ConditionResult {}

        record Failed(String reason) implements ConditionResult {}
    }
}
