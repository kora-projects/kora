package io.koraframework.http.server.netty.buffer;

import io.netty.buffer.*;
import org.jspecify.annotations.*;

public final class OwnedBuffer implements AutoCloseable {
    @Nullable
    private ByteBuf buffer;

    public OwnedBuffer(ByteBuf buffer) {
        this.buffer = buffer;
    }

    public ByteBuf take() {
        ByteBuf b = buffer;
        if (b == null) {
            throw new IllegalStateException("Buffer already taken or released");
        }
        buffer = null;
        return b;
    }

    @Override
    public void close() {
        ByteBuf b = buffer;
        if (b != null) {
            buffer = null;
            b.release();
        }
    }
}
