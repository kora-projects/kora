package ru.tinkoff.kora.aws.s3.impl.xml;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;

//<InitiateMultipartUploadResult>
//   <Bucket>string</Bucket>
//   <Key>string</Key>
//   <UploadId>string</UploadId>
//</InitiateMultipartUploadResult>
public record InitiateMultipartUploadResult(String bucket, String key, String uploadId) {

    public static InitiateMultipartUploadResult fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new InitiateMultipartUploadResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }
}
