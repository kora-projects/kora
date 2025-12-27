package ru.tinkoff.kora.json.common.util;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.json.common.JsonCommonModule;
import tools.jackson.core.*;
import tools.jackson.core.io.ContentReference;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.json.JsonParserBase;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

public class BufferingJsonParser extends JsonParserBase {
    private final ArrayList<JsonSegment> tokens = new ArrayList<>();
    private final JsonParser delegate;
    private int currentToken;

    public BufferingJsonParser(JsonParser delegate) {
        super(
            delegate.objectReadContext(),
            new IOContext(
                delegate.streamReadConstraints(),
                StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                JsonCommonModule.JSON_FACTORY._getBufferRecycler(),
                ContentReference.rawReference(delegate),
                false,
                JsonEncoding.UTF8
            ),
            delegate.streamReadFeatures(),
            delegate.objectReadContext().getFormatReadFeatures(0)
        );
        this.delegate = delegate;
        this._currToken = delegate.currentToken();
        this.tokens.add(this.data(this._currToken, this.delegate));
        this.currentToken = 1;
    }

    public JsonSegmentJsonParser reset() {
        this.currentToken = -1;
        var data = this.tokens.get(0);
        this._currToken = data.token();
        this._textBuffer.resetWithShared(data.data(), 0, data.data().length);

        return new JsonSegmentJsonParser(this._objectReadContext, this._ioContext, this._streamReadFeatures, this._formatReadFeatures, new ArrayList<>(this.tokens));
    }

    @Override
    protected void _closeInput() {

    }

    private JsonSegment data(JsonToken token, JsonParser parser) {
        var textCharacters = parser.getStringCharacters();
        var textOffset = parser.getStringOffset();
        var textLength = parser.getStringLength();
        final boolean isNegative;
        if (!token.isNumeric()) {
            isNegative = false;
        } else {
            isNegative = isCurrentNumberNegative(parser);
        }
        return new JsonSegment(token, Arrays.copyOfRange(textCharacters, textOffset, textOffset + textLength), isNegative);
    }

    private boolean isCurrentNumberNegative(JsonParser parser) {
        return switch (parser.getNumberType()) {
            case INT -> parser.getIntValue() < 0;
            case LONG -> parser.getLongValue() < 0;
            case BIG_INTEGER -> ((BigInteger) parser.getNumberValue()).signum() < 0;
            case FLOAT -> parser.getFloatValue() < 0;
            case DOUBLE -> parser.getDoubleValue() < 0;
            case BIG_DECIMAL -> ((BigDecimal) parser.getNumberValue()).signum() < 0;
        };
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
        if (this.currentToken < 0) {
            this.currentToken--;
            var data = this.currentData();
            if (data != null) {
                var nextToken = data.token();
                if (nextToken == JsonToken.PROPERTY_NAME) {
                    this._streamReadContext.setCurrentName(new String(data.data()));
                }
                this._currToken = nextToken;
                this._textBuffer.resetWithShared(data.data(), 0, data.data().length);
                return nextToken;
            } else {
                var token = this.delegate.nextToken();
                if (token == JsonToken.PROPERTY_NAME) {
                    this._streamReadContext.setCurrentName(new String(data.data()));
                } else if (token.isNumeric()) {
                    _numTypesValid = NR_UNKNOWN; // to force parsing
                    _numberNegative = isCurrentNumberNegative(this.delegate);
                }
                this._textBuffer.resetWithShared(this.delegate.getTextCharacters(), this.delegate.getTextOffset(), this.delegate.getTextLength());
                this._currToken = token;
                return token;
            }
        }
        if (this.currentToken >= this.tokens.size()) {
            var nextToken = this.delegate.nextToken();
            if (nextToken == null) {
                return null;
            }
            var data = this.data(nextToken, this.delegate);
            this.currentToken++;
            this.tokens.add(data);
            this._currToken = nextToken;
            if (nextToken == JsonToken.PROPERTY_NAME) {
                this._streamReadContext.setCurrentName(new String(data.data()));
            }
            this._textBuffer.resetWithShared(data.data(), 0, data.data().length);
            this._currToken = nextToken;
            return nextToken;
        }
        this.currentToken++;
        var data = this.tokens.get(this.currentToken - 1);
        var nextToken = data.token();
        this._currToken = nextToken;
        if (nextToken == JsonToken.PROPERTY_NAME) {
            this._streamReadContext.setCurrentName(new String(data.data()));
        }
        this._textBuffer.resetWithShared(data.data(), 0, data.data().length);
        this._currToken = nextToken;
        return nextToken;
    }

    @Nullable
    public JsonSegment currentData() {
        var number = Math.abs(this.currentToken);
        if (number > this.tokens.size()) {
            return null;
        }
        return this.tokens.get(number - 1);
    }

    @Override
    public String getString() throws JacksonException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getString();
        }
        return new String(currentData.data());
    }

    @Override
    public char[] getStringCharacters() throws JacksonException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getStringCharacters();
        }
        return currentData.data();
    }

    @Override
    public int getStringLength() throws JacksonException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getStringLength();
        }
        return currentData.data().length;
    }

    @Override
    public int getStringOffset() throws JacksonException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getStringOffset();
        }
        return 0;
    }

}
