package ru.tinkoff.kora.s3.client.annotation.processor;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractS3ClientTest extends AbstractAnnotationProcessorTest {

    protected AbstractThrowableAssert<?, ? extends Throwable> assertCompileFailed(ThrowableAssert.ThrowingCallable shouldRaiseThrowable) {
        return assertThatThrownBy(() -> {
            shouldRaiseThrowable.call();
            this.compileResult.assertSuccess();
        });
    }

}
