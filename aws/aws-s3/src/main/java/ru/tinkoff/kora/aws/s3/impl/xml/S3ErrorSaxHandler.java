package ru.tinkoff.kora.aws.s3.impl.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class S3ErrorSaxHandler extends DefaultHandler {
    private final StringBuilder buf = new StringBuilder();
    private int depth = 0;

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
    }

    private String code;
    private String message;
    private String key;
    private String bucketName;
    private String resource;
    private String requestId;
    private String hostId;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        depth++;
        if (depth == 1 && !qName.equals("Error")) {
            throw new SAXException("Invalid response");
        }
        this.buf.setLength(0);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        depth--;
        if (depth == 1) {
            switch (qName) {
                case "Code":
                    code = buf.toString();
                    break;
                case "Message":
                    message = buf.toString();
                    break;
                case "Key":
                    key = buf.toString();
                    break;
                case "BucketName":
                    bucketName = buf.toString();
                    break;
                case "Resource":
                    resource = buf.toString();
                    break;
                case "RequestId":
                    requestId = buf.toString();
                    break;
                case "HostId":
                    hostId = buf.toString();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        this.buf.append(ch, start, length);
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        throw e;
    }

    public S3Error toResult() throws SAXException {
        if (code == null || message == null) {
            throw new SAXException("Invalid response");
        }
        return new S3Error(code, message, key, bucketName, resource, requestId, hostId);
    }
}
