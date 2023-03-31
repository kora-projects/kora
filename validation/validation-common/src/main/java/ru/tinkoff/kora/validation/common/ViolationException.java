package ru.tinkoff.kora.validation.common;

import javax.annotation.Nonnull;

import java.util.List;

public final class ViolationException extends RuntimeException {

    private String _message;
    private final List<Violation> violations;

    public ViolationException(@Nonnull List<Violation> violations) {
        super();
        this.violations = violations;
    }

    @Nonnull
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
        final StringBuilder builder = new StringBuilder("Validation failed with violations:\n");
        for (int i = 1; i <= violations.size(); i++) {
            final Violation violation = violations.get(i - 1);
            builder.append(i)
                .append(") Path '")
                .append(violation.path().full())
                .append("' violation: ")
                .append(violation.message())
                .append(';');

            if (i != violations.size()) {
                builder.append("\n");
            }
        }

        return builder.toString();
    }
}
