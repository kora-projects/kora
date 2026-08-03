package io.koraframework.validation.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViolationExceptionTests {

    @Test
    void messageWithSingleViolation() {
        var exception = new ViolationException(ValidationContext.builder().build().addPath("field").violates("Should be valid"));

        assertEquals("""
            Validation failed with 1 violation:
            1) Path 'field' violation: Should be valid""", exception.getMessage());
    }

    @Test
    void messageWithMultipleViolations() {
        var violations = List.of(
            ValidationContext.builder().build().addPath("field1").violates("Should be valid"),
            ValidationContext.builder().build().addPath("field2").violates("Should be present")
        );

        var exception = new ViolationException(violations);

        assertEquals("""
            Validation failed with 2 violations:
            1) Path 'field1' violation: Should be valid
            2) Path 'field2' violation: Should be present""", exception.getMessage());
    }

    @Test
    void violationsCopied() {
        var violations = new ArrayList<Violation>();
        violations.add(ValidationContext.builder().build().addPath("field").violates("Should be valid"));

        var exception = new ViolationException(violations);
        violations.clear();

        assertEquals(1, exception.getViolations().size());
        assertThrows(UnsupportedOperationException.class, () -> exception.getViolations().clear());
    }
}
