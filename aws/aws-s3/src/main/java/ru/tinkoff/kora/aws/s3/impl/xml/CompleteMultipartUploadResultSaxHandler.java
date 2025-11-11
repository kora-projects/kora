package ru.tinkoff.kora.aws.s3.impl.xml;


import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class CompleteMultipartUploadResultSaxHandler extends DefaultHandler {
    private int level = 0;
    private final StringBuilder buf = new StringBuilder();

    private String location;
    private String bucket;
    private String key;
    private String etag;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        level++;
        if (level == 1) {
            if (!qName.equals("CompleteMultipartUploadResult")) {
                throw new SAXException("Invalid element '" + qName + "'. Expected 'CompleteMultipartUploadResult'");
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        level--;
        if (level > 1) {
            return;
        }
        if (level == 1) {
            switch (qName) {
                case "Location":
                    location = buf.toString();
                    break;
                case "Bucket":
                    bucket = buf.toString();
                    break;
                case "Key":
                    key = buf.toString();
                    break;
                case "ETag":
                    etag = buf.toString();
                    break;
                default:
                    break;
            }
            buf.setLength(0);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (level > 1) {
            return;
        }
        buf.append(ch, start, length);
    }

    public CompleteMultipartUploadResult toResult() {
        return new CompleteMultipartUploadResult(location, bucket, key, etag);
    }
}
