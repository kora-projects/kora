package ru.tinkoff.kora.json.common.util;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.IOContext;

import java.io.IOException;
import java.util.List;

public class JsonSegmentJsonParser extends ParserBase {
    private final List<JsonSegment> segments;
    private int currentSegment = -1;

    public JsonSegmentJsonParser(IOContext context, int features, List<JsonSegment> segments) {
        super(context, features);
        this.segments = segments;
    }

    @Override
    protected void _closeInput() throws IOException {}

    @Override
    public ObjectCodec getCodec() {
        return null;
    }

    @Override
    public void setCodec(ObjectCodec oc) {

    }

    @Override
    public JsonToken nextToken() throws IOException {
        currentSegment++;
        if (currentSegment >= this.segments.size()) {
            return null;
        }
        var segment = this.segments.get(currentSegment);
        var token = segment.token();
        this._currToken = token;
        if (token == JsonToken.FIELD_NAME) {
            _parsingContext.setCurrentName(new String(segment.data()));
        }
        _textBuffer.resetWithShared(segment.data(), 0, segment.data().length);
        return token;
    }

    private JsonSegment current() {
        var currentSegment = this.currentSegment;
        if (currentSegment >= this.segments.size()) {
            return null;
        }
        return this.segments.get(currentSegment);
    }

    @Override
    public String getText() throws IOException {
        var segment = this.current();
        return new String(segment.data());
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        var segment = this.current();
        return segment.data();
    }

    @Override
    public int getTextLength() throws IOException {
        return this.current().data().length;
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }
}
