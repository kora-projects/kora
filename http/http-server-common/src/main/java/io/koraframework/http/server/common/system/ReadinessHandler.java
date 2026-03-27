package io.koraframework.http.server.common.system;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import org.jspecify.annotations.Nullable;

public class ReadinessHandler extends ProbeHandler<ReadinessProbe, ReadinessProbeFailure> {
    private final ValueOf<HttpServerSystemConfig> config;

    public ReadinessHandler(ValueOf<HttpServerSystemConfig> config, All<PromiseOf<ReadinessProbe>> probes) {
        super(probes);
        this.config = config;
    }

    @Override
    public String routeTemplate() {
        return this.config.get().readinessPath();
    }

    @Nullable
    @Override
    protected ReadinessProbeFailure performProbe(ReadinessProbe probe) throws Exception {
        return probe.probe();
    }

    @Override
    protected String getMessage(ReadinessProbeFailure failure) {return failure.message();}
}
