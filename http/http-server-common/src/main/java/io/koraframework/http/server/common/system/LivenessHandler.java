package io.koraframework.http.server.common.system;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.liveness.LivenessProbe;
import io.koraframework.common.liveness.LivenessProbeFailure;
import org.jspecify.annotations.Nullable;

public class LivenessHandler extends ProbeHandler<LivenessProbe, LivenessProbeFailure> {
    private final ValueOf<HttpServerSystemConfig> config;

    public LivenessHandler(ValueOf<HttpServerSystemConfig> config, All<PromiseOf<LivenessProbe>> probes) {
        super(probes);
        this.config = config;
    }

    @Override
    public String routeTemplate() {
        return this.config.get().livenessPath();
    }

    @Nullable
    @Override
    protected LivenessProbeFailure performProbe(LivenessProbe livenessProbe) throws Exception {
        return livenessProbe.probe();
    }

    @Override
    protected String getMessage(LivenessProbeFailure livenessProbeFailure) {
        return livenessProbeFailure.message();
    }
}
