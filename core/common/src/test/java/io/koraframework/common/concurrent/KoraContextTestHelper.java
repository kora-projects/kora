package io.koraframework.common.concurrent;

import io.koraframework.common.Principal;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.logging.common.MDC;
import io.opentelemetry.context.Context;

import java.util.concurrent.Callable;

final class KoraContextTestHelper {
    private KoraContextTestHelper() {}

    static <T> T withContexts(Context context, Principal principal, Observation observation, Callable<T> block) throws Exception {
        return ScopedValue.where(OpentelemetryContext.VALUE, context)
            .where(Principal.VALUE, principal)
            .where(Observation.VALUE, observation)
            .call(block::call);
    }

    static <T> T withMdc(MDC mdc, Callable<T> block) throws Exception {
        return ScopedValue.where(MDC.VALUE, mdc).call(block::call);
    }
}
