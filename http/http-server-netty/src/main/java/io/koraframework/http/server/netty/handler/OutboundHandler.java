package io.koraframework.http.server.netty.handler;

import io.koraframework.http.server.netty.compression.*;
import io.netty.buffer.*;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.*;

import java.util.*;

public abstract class OutboundHandler {
    protected final KoraOutboundAccess outboundAccess;
    protected final PipeliningServerHandler parent;

    Compressor.@Nullable Session compressionSession;
    boolean responseWritten = false;

    OutboundHandler(KoraOutboundAccess outboundAccess, PipeliningServerHandler parent) {
        this.outboundAccess = Objects.requireNonNull(outboundAccess, "outboundAccess");
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    final void markResponseWritten() {
        if (!responseWritten) {
            responseWritten = true;
            parent.requestHandler.responseWritten(outboundAccess.attachment());
        }
    }

    private boolean shouldCloseAfterContent(boolean last) {
        return last && (outboundAccess.isCloseAfterWrite() || (parent.shuttingDown && parent.outboundQueue.isEmpty()));
    }

    protected final void writeCompressing(HttpContent content, boolean flush, boolean last) {
        if (compressionSession == null) {
            writePotentialEnd(content, flush, shouldCloseAfterContent(last), last);
        } else {
            writeCompressing0(content, flush, last);
        }
    }

    void writePotentialEnd(Object content, boolean flush, boolean close, boolean last) {
        if (!last) {
            writeIntermediate(content, flush, close);
            return;
        }
        outboundAccess.setFinalWriteIssued(true);
        parent.write0(content, flush, close, false, null);
    }

    final void writeIntermediate(Object content, boolean flush, boolean close) {
        parent.write0(content, flush, close, false, null);
    }

    private void writeCompressing0(HttpContent content, boolean flush, boolean last) {
        var session = Objects.requireNonNull(compressionSession);
        session.push(content.content());
        if (last) {
            session.finish();
        }
        boolean close = shouldCloseAfterContent(last);
        var toSend = session.poll();
        if (content instanceof HttpResponse hr) {
            assert last;
            session.fixContentLength(hr);
            var payload = toSend != null ? toSend : Unpooled.EMPTY_BUFFER;
            var trailing = ((LastHttpContent) content).trailingHeaders();
            writePotentialEnd(new DefaultFullHttpResponse(hr.protocolVersion(), hr.status(), payload, hr.headers(), trailing), flush, close, true);
            return;
        }
        if (toSend == null) {
            if (last) {
                var trailingHeaders = ((LastHttpContent) content).trailingHeaders();
                writePotentialEnd(trailingHeaders.isEmpty() ? LastHttpContent.EMPTY_LAST_CONTENT : new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, trailingHeaders), flush, close, true);
            } else if (flush || close) {
                writeIntermediate(new DefaultHttpContent(Unpooled.EMPTY_BUFFER), flush, close);
            }
        } else {
            if (last) {
                writePotentialEnd(new DefaultLastHttpContent(toSend, ((LastHttpContent) content).trailingHeaders()), flush, close, true);
            } else {
                writeIntermediate(new DefaultHttpContent(toSend), flush, close);
            }
        }
    }

    abstract void writeSome();

    abstract void markCloseAfterWrite();

    void discardOutbound() {
        if (compressionSession != null) {
            compressionSession.discard();
        }
    }

    void discardRefused() {
        if (compressionSession != null) {
            compressionSession.discard();
        }
    }
}
