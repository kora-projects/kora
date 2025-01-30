package ru.tinkoff.kora.json.common.util;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.json.common.JsonCommonModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

public class BufferingJsonParser extends ParserBase {
    private final ArrayList<JsonSegment> tokens = new ArrayList<>();
    private final JsonParser delegate;
    private int currentToken;

    public BufferingJsonParser(JsonParser delegate) throws IOException {
        super(
            new IOContext(
                delegate.streamReadConstraints(),
                StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                JsonCommonModule.JSON_FACTORY._getBufferRecycler(),
                ContentReference.rawReference(delegate),
                false
            ),
            delegate.getFeatureMask()
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

        return new JsonSegmentJsonParser(this._ioContext, this._features, new ArrayList<>(this.tokens));
    }

    @Override
    protected void _closeInput() {

    }

    @Override
    public ObjectCodec getCodec() {
        return this.delegate.getCodec();
    }

    @Override
    public void setCodec(ObjectCodec oc) {
        this.delegate.setCodec(oc);
    }

    private JsonSegment data(JsonToken token, JsonParser parser) throws IOException {
        var textCharacters = parser.getTextCharacters();
        var textOffset = parser.getTextOffset();
        var textLength = parser.getTextLength();
        final boolean isNegative;
        if (!token.isNumeric()) {
            isNegative = false;
        } else {
            isNegative = isCurrentNumberNegative(parser);
        }
        return new JsonSegment(token, Arrays.copyOfRange(textCharacters, textOffset, textOffset + textLength), isNegative);
    }

    private boolean isCurrentNumberNegative(JsonParser parser) throws IOException {
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
    public JsonToken nextToken() throws IOException {
        if (this.currentToken < 0) {
            this.currentToken--;
            var data = this.currentData();
            if (data != null) {
                var nextToken = data.token();
                if (nextToken == JsonToken.FIELD_NAME) {
                    this._parsingContext.setCurrentName(new String(data.data()));
                }
                this._currToken = nextToken;
                this._textBuffer.resetWithShared(data.data(), 0, data.data().length);
                return nextToken;
            } else {
                var token = this.delegate.nextToken();
                if (token == JsonToken.FIELD_NAME) {
                    this._parsingContext.setCurrentName(this.delegate.currentName());
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
            if (nextToken == JsonToken.FIELD_NAME) {
                this._parsingContext.setCurrentName(new String(data.data()));
            }
            this._textBuffer.resetWithShared(data.data(), 0, data.data().length);
            this._currToken = nextToken;
            return nextToken;
        }
        this.currentToken++;
        var data = this.tokens.get(this.currentToken - 1);
        var nextToken = data.token();
        this._currToken = nextToken;
        if (nextToken == JsonToken.FIELD_NAME) {
            this._parsingContext.setCurrentName(new String(data.data()));
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
    public String getText() throws IOException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getText();
        }
        return new String(currentData.data());
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getTextCharacters();
        }
        return currentData.data();
    }

    @Override
    public int getTextLength() throws IOException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getTextLength();
        }
        return currentData.data().length;
    }

    @Override
    public int getTextOffset() throws IOException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getTextOffset();
        }
        return 0;
    }
}
