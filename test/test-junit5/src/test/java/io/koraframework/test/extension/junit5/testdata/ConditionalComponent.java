package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.application.graph.GraphCondition;

public record ConditionalComponent(String name) {

    public static final class Enabled implements GraphCondition {
        @Override
        public ConditionResult eval() {
            return ConditionResult.matched("enabled");
        }
    }

    public static final class Disabled implements GraphCondition {
        @Override
        public ConditionResult eval() {
            return ConditionResult.failed("disabled");
        }
    }
}
