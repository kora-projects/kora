package ru.tinkoff.kora.s3.client.impl.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

public class ListBucketResultSaxHandler extends DefaultHandler {
    private int level = 0;
    private final StringBuilder buf = new StringBuilder();

    private String name;
    private String prefix;
    private int keyCount;
    private int maxKeys;
    private String delimiter;
    private boolean isTruncated;
    private List<ListBucketResult.Content> contents = new ArrayList<>();
    private String nextContinuationToken;

    private DefaultHandler delegate = null;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        level++;
        if (level > 2 && delegate != null) {
            delegate.startElement(uri, localName, qName, attributes);
            return;
        }
        if (level == 2) {
            switch (qName) {
                case "Contents":
                    delegate = new ListBucketResultContentSaxHandler(buf);
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
                case "Name":
                    name = buf.toString();
                    break;
                case "Prefix":
                    prefix = buf.toString();
                    break;
                case "KeyCount":
                    keyCount = Integer.parseInt(buf.toString());
                    break;
                case "MaxKeys":
                    maxKeys = Integer.parseInt(buf.toString());
                    break;
                case "Delimiter":
                    delimiter = buf.toString();
                    break;
                case "IsTruncated":
                    isTruncated = Boolean.parseBoolean(buf.toString());
                    break;
                case "NextContinuationToken":
                    nextContinuationToken = buf.toString();
                    break;
                case "Contents":
                    assert delegate instanceof ListBucketResultContentSaxHandler;
                    contents.add(((ListBucketResultContentSaxHandler) delegate).toResult());
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
        assert level >= 1 : "Expected level>=1, got level=" + level;
        buf.append(ch, start, length);
    }

    public ListBucketResult toResult() {
        return new ListBucketResult(name, prefix, keyCount, maxKeys, delimiter, isTruncated, contents, nextContinuationToken);
    }

    public static class ListBucketResultContentSaxHandler extends DefaultHandler {
        private int level = 0;
        private final StringBuilder buf;

        private String key;
        private String lastModified;
        private String eTag;
        private long size;
        private String storageClass;

        public ListBucketResultContentSaxHandler(StringBuilder buf) {
            this.buf = buf;
            buf.setLength(0);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            level++;
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            level--;
            if (level > 0) {
                return;
            }
            if (level == 0) {
                switch (qName) {
                    case "Key":
                        key = buf.toString();
                        break;
                    case "LastModified":
                        lastModified = buf.toString();
                        break;
                    case "ETag":
                        eTag = buf.toString();
                        break;
                    case "Size":
                        size = Long.parseLong(buf.toString());
                        break;
                    case "StorageClass":
                        storageClass = buf.toString();
                        break;
                    default:
                        break;
                }
                buf.setLength(0);
            }

        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (level == 1) {
                buf.append(ch, start, length);
            }
        }

        public ListBucketResult.Content toResult() {
            return new ListBucketResult.Content(key, lastModified, eTag, size, storageClass);
        }
    }
}
