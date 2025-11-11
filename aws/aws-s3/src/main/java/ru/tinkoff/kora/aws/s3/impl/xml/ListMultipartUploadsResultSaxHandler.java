package ru.tinkoff.kora.aws.s3.impl.xml;

import jakarta.annotation.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ListMultipartUploadsResultSaxHandler extends DefaultHandler {

    private String bucket;
    @Nullable
    private String keyMarker;
    @Nullable
    private String uploadIdMarker;
    @Nullable
    private String nextKeyMarker;
    @Nullable
    private String nextUploadIdMarker;
    @Nullable
    private String prefix;
    @Nullable
    private Integer maxUploads;
    boolean isTruncated = false;
    private List<ListMultipartUploadsResult.Upload> uploads = new ArrayList<>();


    private int level = 0;
    private final StringBuilder buf = new StringBuilder();

    private DefaultHandler delegate = null;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        level++;
        if (level == 1) {
            if (!qName.equals("ListMultipartUploadsResult")) {
                throw new SAXException("Expected <ListMultipartUploadsResult>, got " + qName);
            }
            return;
        }
        if (level > 2 && delegate != null) {
            delegate.startElement(uri, localName, qName, attributes);
            return;
        }
        if (level == 2) {
            switch (qName) {
                case "Upload":
                    delegate = new UploadSaxHandler(buf);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        level--;
        if (level > 1 && delegate != null) {
            delegate.endElement(uri, localName, qName);
            return;
        }
        if (level == 1) {
            switch (qName) {
                case "Bucket":
                    bucket = buf.toString().trim();
                    break;
                case "KeyMarker":
                    keyMarker = buf.toString().trim();
                    break;
                case "UploadIdMarker":
                    uploadIdMarker = buf.toString().trim();
                    break;
                case "NextKeyMarker":
                    nextKeyMarker = buf.toString().trim();
                    break;
                case "NextUploadIdMarker":
                    nextUploadIdMarker = buf.toString().trim();
                    break;
                case "Prefix":
                    prefix = buf.toString().trim();
                    break;
                case "IsTruncated":
                    isTruncated = Boolean.parseBoolean(buf.toString().trim());
                    break;
                case "MaxUploads":
                    maxUploads = Integer.parseInt(buf.toString().trim());
                    break;
                case "Upload":
                    assert delegate instanceof UploadSaxHandler;
                    uploads.add(((UploadSaxHandler) delegate).toResult());
                    delegate = null;
                    break;
                default:
                    break;
            }
            buf.setLength(0);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (level > 2) {
            if (delegate != null) {
                delegate.characters(ch, start, length);
            }
            return;
        }
        buf.append(ch, start, length);
    }

    public ListMultipartUploadsResult toResult() {
        return new ListMultipartUploadsResult(
            bucket, keyMarker, uploadIdMarker, nextKeyMarker, nextUploadIdMarker, prefix, maxUploads, isTruncated, uploads
        );
    }

    private static final class UploadSaxHandler extends DefaultHandler {
        private int level = 0;
        private final StringBuilder buf;
        private String key;
        private String uploadId;
        private Instant initiated;

        public UploadSaxHandler(StringBuilder buf) {
            this.buf = buf;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            level++;
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            level--;
            if (level == 0) {
                switch (qName) {
                    case "Key":
                        key = buf.toString().trim();
                        break;
                    case "UploadId":
                        uploadId = buf.toString().trim();
                        break;
                    case "Initiated":
                        initiated = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(buf.toString().trim()));
                        break;
                    default:
                        break;
                }
                buf.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (level > 2) {
                return;
            }
            buf.append(ch, start, length);
        }

        public ListMultipartUploadsResult.Upload toResult() {
            return new ListMultipartUploadsResult.Upload(key, uploadId, initiated);
        }

    }
}
