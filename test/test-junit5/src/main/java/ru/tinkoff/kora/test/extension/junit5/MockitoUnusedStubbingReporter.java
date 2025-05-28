package ru.tinkoff.kora.test.extension.junit5;

import org.mockito.internal.exceptions.Reporter;
import org.mockito.invocation.Invocation;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Stubbing;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class MockitoUnusedStubbingReporter {
    private final Collection<? extends Stubbing> unused;
    private final Strictness strictness;

    public MockitoUnusedStubbingReporter(Collection<? extends Stubbing> unused, Strictness strictness) {
        this.unused = unused;
        this.strictness = strictness;
    }

    public void reportUnused() {
        if (!Strictness.STRICT_STUBS.equals(strictness) || unused.isEmpty()) {
            return;
        }

        List<Invocation> invocations = new LinkedList<>();
        for (Stubbing stubbing : unused) {
            invocations.add(stubbing.getInvocation());
        }

        Reporter.unncessaryStubbingException(invocations);
    }
}
