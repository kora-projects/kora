package ru.tinkoff.kora.aws.s3.impl.xml;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record DeleteObjectsResult(List<Deleted> deleted) {
    public record Deleted(String key) {}

    public static DeleteObjectsResult fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new DeleteObjectsResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }
}

