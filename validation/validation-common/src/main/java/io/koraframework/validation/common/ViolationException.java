package io.koraframework.validation.common;

import java.util.List;

public final class ViolationException extends RuntimeException {

    private String _message;
    private final List<Violation> violations;

    public ViolationException(Violation violation) {
        super();
        this.violations = List.of(violation);
    }

    public ViolationException(List<Violation> violations) {
        super();
        this.violations = List.copyOf(violations);
    }

    public List<Violation> getViolations() {
        return violations;
    }

    @Override
    public String getMessage() {
        if (_message == null) {
            _message = buildViolationMessage(violations);
        }

        return _message;
    }

    private static String buildViolationMessage(List<Violation> violations) {
        if (violations.isEmpty()) {
            return "Validation failed with no violations";
        }

        final StringBuilder builder = new StringBuilder("Validation failed with ")
            .append(violations.size())
            .append(violations.size() == 1 ? " violation:\n" : " violations:\n");
        for (int i = 1; i <= violations.size(); i++) {
            final Violation violation = violations.get(i - 1);
            builder.append(i)
                .append(") Path '")
                .append(violation.path().full())
                .append("' violation: ")
                .append(violation.message());

            if (i != violations.size()) {
                builder.append("\n");
            }
        }

        return builder.toString();
    }
}
