package io.koraframework.http.server.common.privateapi;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import io.koraframework.http.server.common.PrivateHttpServerConfig;

public class ReadinessHandler extends ProbeHandler<ReadinessProbe, ReadinessProbeFailure> {
    private final ValueOf<PrivateHttpServerConfig> config;

    public ReadinessHandler(ValueOf<PrivateHttpServerConfig> config, All<PromiseOf<ReadinessProbe>> probes) {
        super(probes);
        this.config = config;
    }

    @Override
    public String routeTemplate() {
        return this.config.get().readinessPath();
    }

    @Override
    protected ReadinessProbeFailure performProbe(ReadinessProbe probe) throws Exception {return probe.probe();}

    @Override
    protected String getMessage(ReadinessProbeFailure failure) {return failure.message();}
}
