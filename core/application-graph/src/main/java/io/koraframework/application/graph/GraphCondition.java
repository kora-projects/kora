package io.koraframework.application.graph;

import java.util.*;

public interface GraphCondition {

    ConditionResult eval();

    static GraphCondition or(GraphCondition... conditions) {
        assert conditions.length > 0;
        return new OrCondition(conditions);
    }

    static GraphCondition and(GraphCondition... conditions) {
        assert conditions.length > 0;
        return new AndCondition(conditions);
    }

    final class OrCondition implements GraphCondition {
        private final GraphCondition[] conditions;

        public OrCondition(GraphCondition[] conditions) {
            this.conditions = conditions;
        }

        @Override
        public ConditionResult eval() {
            for (int i = 0; i < conditions.length; i++) {
                var res = conditions[i].eval();
                if (res instanceof ConditionResult.Matched) {
                    return res;
                }
            }

            var failedReasons = new ArrayList<ConditionResult.Failed>(conditions.length);
            for (int i = 0; i < conditions.length; i++) {
                var res = conditions[i].eval();
                if (res instanceof ConditionResult.Failed) {
                    failedReasons.add((ConditionResult.Failed) res);
                }
            }

            var sb = new StringBuilder("All of required conditions were failed: \n");
            for (int i = 0; i < failedReasons.size(); i++) {
                sb.append(failedReasons.get(i).reason().indent(2)).append("\n");
            }
            return new ConditionResult.Failed(sb.toString());
        }
    }

    final class AndCondition implements GraphCondition {
        private final GraphCondition[] conditions;

        public AndCondition(GraphCondition[] conditions) {
            this.conditions = conditions;
        }

        @Override
        public ConditionResult eval() {
            var failedCount = 0;
            for (int i = 0; i < conditions.length; i++) {
                if (conditions[i].eval() instanceof ConditionResult.Failed) {
                    failedCount++;
                }
            }

            if (failedCount == 0) {
                var sb = new StringBuilder("All of required conditions were matched:\n");
                for (int i = 0; i < conditions.length; i++) {
                    var res = conditions[i].eval();
                    if (res instanceof ConditionResult.Matched) {
                        sb.append(((ConditionResult.Matched) res).reason().indent(2));
                    }
                }
                return new ConditionResult.Matched(sb.toString());
            } else {
                var sb = new StringBuilder("Some of required conditions were failed:\n");
                for (int i = 0; i < conditions.length; i++) {
                    var res = conditions[i].eval();
                    if (res instanceof ConditionResult.Failed) {
                        sb.append(((ConditionResult.Failed) res).reason().indent(2));
                    }
                }
                return new ConditionResult.Failed(sb.toString());
            }
        }
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
