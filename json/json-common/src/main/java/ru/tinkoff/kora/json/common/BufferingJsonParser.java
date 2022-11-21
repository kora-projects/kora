package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.IOContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;

public class BufferingJsonParser extends ParserBase {
    record JsonTokenData(JsonToken token, char[] textCharacters, int textOffset, int textLength) {}

    private final ArrayList<JsonTokenData> tokens = new ArrayList<>();
    private final JsonParser delegate;
    private int currentToken = -1;

    public BufferingJsonParser(JsonParser delegate) throws IOException {
        super(new IOContext(JsonCommonModule.JSON_FACTORY._getBufferRecycler(), delegate, false), delegate.getFeatureMask());
        this.delegate = delegate;
        this._currToken = delegate.currentToken();
        this.tokens.add(this.data(this._currToken, this.delegate));
        this.currentToken = 1;
    }

    public void reset() {
        this.currentToken = 1;
        this._currToken = this.tokens.get(0).token();
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

    private JsonTokenData data(JsonToken token, JsonParser parser) throws IOException {
        var textCharacters = parser.getTextCharacters();
        var textOffset = parser.getTextOffset();
        var textLength = parser.getTextLength();
        return new JsonTokenData(token, textCharacters, textOffset, textLength);
    }

    @Override
    public JsonToken nextToken() throws IOException {
        if (this.currentToken >= this.tokens.size() || this.currentToken < 0) {
            var nextToken = this.delegate.nextToken();
            var data = this.data(nextToken, this.delegate);
            this.currentToken++;
            this.tokens.add(data);
            this._currToken = nextToken;
            if (nextToken == JsonToken.FIELD_NAME) {
                this._parsingContext.setCurrentName(new String(data.textCharacters(), data.textOffset(), data.textLength()));
            }
            return nextToken;
        }
        this.currentToken++;
        var data = this.tokens.get(this.currentToken - 1);
        var nextToken = data.token();
        this._currToken = nextToken;
        if (nextToken == JsonToken.FIELD_NAME) {
            this._parsingContext.setCurrentName(new String(data.textCharacters(), data.textOffset(), data.textLength()));
        }
        return nextToken;
    }

    @Nullable
    public JsonTokenData currentData() {
        if (this.currentToken < 0) {
            return null;
        }
        return this.tokens.get(this.currentToken - 1);
    }

    @Override
    public String getText() {
        var currentData = this.currentData();
        if (currentData == null) {
            return null;
        }
        return new String(currentData.textCharacters(), currentData.textOffset(), currentData.textLength());
    }

    @Override
    public char[] getTextCharacters() {
        var currentData = this.currentData();
        if (currentData == null) {
            return null;
        }
        return currentData.textCharacters();
    }

    @Override
    public int getTextLength() {
        var currentData = this.currentData();
        if (currentData == null) {
            return -1;
        }
        return currentData.textLength();
    }

    @Override
    public int getTextOffset() {
        var currentData = this.currentData();
        if (currentData == null) {
            return -1;
        }
        return currentData.textOffset();
    }
}
