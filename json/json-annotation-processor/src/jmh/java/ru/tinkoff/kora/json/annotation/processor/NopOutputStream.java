package ru.tinkoff.kora.json.annotation.processor;


import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.io.OutputStream;

class NopOutputStream extends OutputStream {
    private final Blackhole bh;

    public NopOutputStream(Blackhole bh) {
        this.bh = bh;
    }

    @Override
    public void write(int b) throws IOException {
        bh.consume(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        bh.consume(b);
    }

    @Override
    public void write(byte[] b, int offset, int len) throws IOException {
        bh.consume(b);
    }
}
