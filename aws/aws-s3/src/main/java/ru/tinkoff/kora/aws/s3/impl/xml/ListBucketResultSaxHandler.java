package ru.tinkoff.kora.aws.s3.impl.xml;

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
    private List<ListBucketResult.Content> contents;
    private String nextContinuationToken;

    private DefaultHandler delegate = null;
    private List<String> commonPrefixes;

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
                case "CommonPrefixes":
                    delegate = new ListBucketResultCommonPrefixesSaxHandler(buf);
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
                    if (contents == null) {
                        contents = new ArrayList<>();
                    }
                    contents.add(((ListBucketResultContentSaxHandler) delegate).toResult());
                    delegate = null;
                    break;
                case "CommonPrefixes":
                    assert delegate instanceof ListBucketResultCommonPrefixesSaxHandler;
                    if (commonPrefixes == null) {
                        commonPrefixes = new ArrayList<>();
                    }
                    commonPrefixes.add(((ListBucketResultCommonPrefixesSaxHandler) delegate).toResult());
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
        return new ListBucketResult(name, prefix, keyCount, maxKeys, delimiter, isTruncated, contents, nextContinuationToken, commonPrefixes);
    }

    public static class ListBucketResultCommonPrefixesSaxHandler extends DefaultHandler {
        private String result;
        private final StringBuilder buf;
        private int level = 0;

        public ListBucketResultCommonPrefixesSaxHandler(StringBuilder buf) {
            this.buf = buf;
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
                    case "Prefix":
                        result = buf.toString();
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

        public String toResult() {
            return result;
        }
    }

    public static class ListBucketResultOwnerSaxHandler extends DefaultHandler {
        private String id;
        private String displayName;
        private final StringBuilder buf;
        private int level = 0;

        public ListBucketResultOwnerSaxHandler(StringBuilder buf) {
            this.buf = buf;
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
                    case "ID":
                        id = buf.toString();
                        break;
                    case "DisplayName":
                        displayName = buf.toString();
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

        public ListBucketResult.Content.Owner toResult() {
            return new ListBucketResult.Content.Owner(displayName, id);
        }
    }

    public static class ListBucketResultContentSaxHandler extends DefaultHandler {
        private int level = 0;
        private final StringBuilder buf;

        private String checksumAlgorithm;
        private String checksumType;
        private String key;
        private String lastModified;
        private String eTag;
        private long size;
        private String storageClass;
        private DefaultHandler delegate;
        private ListBucketResult.Content.Owner owner;

        public ListBucketResultContentSaxHandler(StringBuilder buf) {
            this.buf = buf;
            buf.setLength(0);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            level++;
            if (level == 1) {
                switch (qName) {
                    case "Owner":
                        this.delegate = new ListBucketResultOwnerSaxHandler(buf);
                        break;
                    default:
                        break;
                }
            }
            if (level > 1 && delegate != null) {
                delegate.startElement(uri, localName, qName, attributes);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            level--;
            if (level > 0 && delegate != null) {
                delegate.endElement(uri, localName, qName);
                return;
            }
            if (level == 0) {
                switch (qName) {
                    case "ChecksumAlgorithm":
                        checksumAlgorithm = buf.toString();
                        break;
                    case "ChecksumType":
                        checksumType = buf.toString();
                        break;
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
                    case "Owner":
                        owner = ((ListBucketResultOwnerSaxHandler) delegate).toResult();
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
            if (level == 1) {
                buf.append(ch, start, length);
            } else if (delegate != null) {
                delegate.characters(ch, start, length);
            }
        }

        public ListBucketResult.Content toResult() {
            return new ListBucketResult.Content(checksumAlgorithm, checksumType, key, lastModified, eTag, size, storageClass, owner);
        }
    }
}
