package ru.tinkoff.kora.s3.client.impl.xml;

import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record ListBucketResult(
    String name,
    String prefix,
    int keyCount,
    int maxKeys,
    String delimiter,
    boolean isTruncated,
    @Nullable List<Content> contents,
    @Nullable String nextContinuationToken,
    @Nullable List<String> commonPrefixes) {

    public record Content(
        String checksumAlgorithm,
        String checksumType,
        String key,
        String lastModified,
        String eTag,
        long size,
        String storageClass,
        Owner owner) {

        public record Owner(String displayName, String id) {}
    }

    public static ListBucketResult fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new ListBucketResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }
}
