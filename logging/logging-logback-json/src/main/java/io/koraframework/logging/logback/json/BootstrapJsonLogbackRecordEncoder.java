package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;

public final class BootstrapJsonLogbackRecordEncoder extends AbstractJsonLogbackRecordEncoder {

    private volatile boolean disabled;

    public BootstrapJsonLogbackRecordEncoder() {
        super(BootstrapWriters.DEFAULT);
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (this.disabled) {
            return new byte[0];
        }
        return super.encode(event);
    }

    public void disable() {
        this.disabled = true;
    }

    public void enable() {
        this.disabled = false;
    }
}
