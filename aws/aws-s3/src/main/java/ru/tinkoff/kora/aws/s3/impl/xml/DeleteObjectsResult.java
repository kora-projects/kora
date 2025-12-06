package ru.tinkoff.kora.aws.s3.impl.xml;

import jakarta.annotation.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        var bytes = is.readAllBytes();
        var str = new String(bytes, StandardCharsets.UTF_8);
        var handler = new DeleteObjectsResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(new ByteArrayInputStream(bytes), handler);

        return handler.toResult();
    }
}

