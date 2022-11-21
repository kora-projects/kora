package ru.tinkoff.kora.soap.client.common.jakarta;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.xml.bind.attachment.AttachmentUnmarshaller;
import ru.tinkoff.kora.soap.client.common.MultipartParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JakartaXopAttachmentUnmarshaller extends AttachmentUnmarshaller {

    private final Map<String, MultipartParser.Part> parts;

    public JakartaXopAttachmentUnmarshaller(Map<String, MultipartParser.Part> parts) {
        this.parts = parts;
    }

    @Override
    public DataHandler getAttachmentAsDataHandler(String cid) {
        if (cid.startsWith("cid:")) {
            cid = cid.replace("cid:", "");
        }
        cid = URLDecoder.decode("<" + cid + ">", StandardCharsets.UTF_8);
        var c = parts.get(cid);
        var finalCid = cid;
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
                return finalCid;
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
