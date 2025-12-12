package ru.tinkoff.kora.s3.client.impl.xml;

import jakarta.annotation.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record DeleteObjectsResult(List<Deleted> deleted, List<Error> errors) {
    public record Deleted(@Nullable Boolean deleteMarker, @Nullable String deleteMarkerVersionId, String key, @Nullable String versionId) {}

    public record Error(
        String code,
        String key,
        String message,
        @Nullable String versionId
    ) {}


    public static DeleteObjectsResult fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new DeleteObjectsResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }
}

