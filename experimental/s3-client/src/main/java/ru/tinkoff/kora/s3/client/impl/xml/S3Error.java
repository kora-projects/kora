package ru.tinkoff.kora.s3.client.impl.xml;

import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;

public record S3Error(
    String code,
    @Nullable String message,
    @Nullable String key,
    @Nullable String bucketName,
    @Nullable String resource,
    String requestId,
    @Nullable String hostId
) {

    public static S3Error fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new S3ErrorSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }
}
