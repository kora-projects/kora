package ru.tinkoff.kora.json.common.util;

import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.json.JsonParserBase;

import java.io.IOException;
import java.util.List;

public class JsonSegmentJsonParser extends JsonParserBase {
    private final List<JsonSegment> segments;
    private int currentSegment = -1;

    public JsonSegmentJsonParser(ObjectReadContext objectReadContext, IOContext ioContext, int features, int formatReadFeatures, List<JsonSegment> segments) {
        super(objectReadContext, ioContext, features, formatReadFeatures);
        this.segments = segments;
    }

    @Override
    protected void _closeInput() throws IOException {
    }


    @Override
    public TokenStreamLocation currentTokenLocation() {
        return TokenStreamLocation.NA;
    }

    @Override
    public TokenStreamLocation currentLocation() {
        return TokenStreamLocation.NA;
    }

    @Override
    public Object streamReadInputSource() {
        return null;
    }

    @Override
    public JsonToken nextToken() {
        currentSegment++;
        if (currentSegment >= this.segments.size()) {
            return null;
        }
        var segment = this.segments.get(currentSegment);
        var token = segment.token();
        this._currToken = token;
        _textBuffer.resetWithShared(segment.data(), 0, segment.data().length);
        if (token == JsonToken.PROPERTY_NAME) {
            _streamReadContext.setCurrentName(new String(segment.data()));
        } else if (token.isNumeric()) {
            _numTypesValid = NR_UNKNOWN;
            _numberNegative = segment.isNumberNegative();
            _intLength = segment.data().length;
        }
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
    public String getString() {
        var segment = this.current();
        return new String(segment.data());
    }

    @Override
    public char[] getStringCharacters() {
        var segment = this.current();
        return segment.data();
    }

    @Override
    public int getStringLength() {
        return this.current().data().length;
    }

    @Override
    public int getStringOffset() {
        return 0;
    }

}
