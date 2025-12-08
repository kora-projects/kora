package ru.tinkoff.kora.aws.s3.impl.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class InitiateMultipartUploadResultSaxHandler extends DefaultHandler {
    private int level = 0;
    private final StringBuilder buf = new StringBuilder();

    private String bucket;
    private String key;
    private String uploadId;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        level++;
        if (level == 1) {
            if (!qName.equals("InitiateMultipartUploadResult")) {
                throw new SAXException("Wrong element: " + qName + " ; expected <InitiateMultipartUploadResult>");
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        level--;
        if (level != 1) {
            return;
        }
        switch (qName) {
            case "Bucket":
                bucket = buf.toString();
                break;
            case "Key":
                key = buf.toString();
                break;
            case "UploadId":
                uploadId = buf.toString();
                break;
            default:
                break;
        }
        buf.setLength(0);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (level == 2) {
            buf.append(ch, start, length);
        }
    }

    public InitiateMultipartUploadResult toResult() {
        return new InitiateMultipartUploadResult(bucket, key, uploadId);
    }
}
