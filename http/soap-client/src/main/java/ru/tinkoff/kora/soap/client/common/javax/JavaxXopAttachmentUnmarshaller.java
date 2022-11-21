package ru.tinkoff.kora.soap.client.common.javax;

import ru.tinkoff.kora.soap.client.common.MultipartParser;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class JavaxXopAttachmentUnmarshaller extends AttachmentUnmarshaller {

    private final Map<String, MultipartParser.Part> parts;

    public JavaxXopAttachmentUnmarshaller(Map<String, MultipartParser.Part> parts) {
        this.parts = parts;
    }

    @Override
    public DataHandler getAttachmentAsDataHandler(String cid) {
        var c = parts.get(cid);
        var ds = new DataSource() {
            @Override
            public InputStream getInputStream() {
                return c.getContentStream();
            }

            @Override
            public OutputStream getOutputStream() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getContentType() {
                return c.contentType();
            }

            @Override
            public String getName() {
                return cid;
            }
        };
        return new DataHandler(ds);
    }

    @Override
    public byte[] getAttachmentAsByteArray(String cid) {
        var c = parts.get(cid);
        return c.getContentArray();
    }

    @Override
    public boolean isXOPPackage() {
        return this.parts.size() > 1;
    }
}
