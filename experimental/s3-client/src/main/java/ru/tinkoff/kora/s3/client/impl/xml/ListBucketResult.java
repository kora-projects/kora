package ru.tinkoff.kora.s3.client.impl.xml;

import jakarta.annotation.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record ListBucketResult(String name, String prefix, int keyCount, int maxKeys, String delimiter, boolean isTruncated, List<Content> contents, @Nullable String nextContinuationToken) {
    public record Content(String key, String lastModified, String eTag, long size, String storageClass) {}

    public static ListBucketResult fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new ListBucketResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }
}
