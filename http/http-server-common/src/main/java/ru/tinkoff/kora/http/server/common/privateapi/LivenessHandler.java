package ru.tinkoff.kora.http.server.common.privateapi;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.liveness.LivenessProbeFailure;
import ru.tinkoff.kora.http.server.common.PrivateHttpServerConfig;

public class LivenessHandler extends ProbeHandler<LivenessProbe, LivenessProbeFailure> {
    private final ValueOf<PrivateHttpServerConfig> config;

    public LivenessHandler(ValueOf<PrivateHttpServerConfig> config, All<PromiseOf<LivenessProbe>> probes) {
        super(probes);
        this.config = config;
    }

    @Override
    public String routeTemplate() {return this.config.get().livenessPath();}

    @Override
    protected LivenessProbeFailure performProbe(LivenessProbe livenessProbe) throws Exception {return livenessProbe.probe();}

    @Override
    protected String getMessage(LivenessProbeFailure livenessProbeFailure) {return livenessProbeFailure.message();}
}
