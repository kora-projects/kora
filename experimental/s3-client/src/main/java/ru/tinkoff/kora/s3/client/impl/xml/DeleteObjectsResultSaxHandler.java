package ru.tinkoff.kora.s3.client.impl.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

public class DeleteObjectsResultSaxHandler extends DefaultHandler {
    private int level = 0;
    private final StringBuilder buf = new StringBuilder();
    private final List<DeleteObjectsResult.Deleted> deleted = new ArrayList<>();
    private final List<DeleteObjectsResult.Error> errors = new ArrayList<>();

    private DefaultHandler delegate = null;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        level++;
        if (level == 1) {
            if (!qName.equals("DeleteResult")) {
                throw new SAXException("Expected <DeleteResult>, got " + qName);
            }
        }
        if (level > 2 && delegate != null) {
            delegate.startElement(uri, localName, qName, attributes);
            return;
        }
        if (level == 2) {
            switch (qName) {
                case "Deleted" -> delegate = new DeleteObjectResultDeletedSaxHandler(buf);
                case "Error" -> delegate = new DeleteObjectResultErrorSaxHandler(buf);
                default -> {
                }
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
                case "Deleted" -> {
                    assert delegate instanceof DeleteObjectResultDeletedSaxHandler;
                    deleted.add(((DeleteObjectResultDeletedSaxHandler) delegate).toResult());
                    delegate = null;
                }
                case "Error" -> {
                    assert delegate instanceof DeleteObjectResultErrorSaxHandler;
                    errors.add(((DeleteObjectResultErrorSaxHandler) delegate).toResult());
                    delegate = null;
                }
                default -> {
                }
            }
            buf.setLength(0);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (level > 1) {
            if (delegate != null) {
                delegate.characters(ch, start, length);
            }
            return;
        }
        buf.append(ch, start, length);
    }

    public DeleteObjectsResult toResult() {
        return new DeleteObjectsResult(deleted, errors);
    }

    public static class DeleteObjectResultDeletedSaxHandler extends DefaultHandler {
        private int level = 0;
        private final StringBuilder buf;

        private String key;
        private Boolean deleteMarker;
        private String deleteMarkerVersion;
        private String versionId;

        public DeleteObjectResultDeletedSaxHandler(StringBuilder buf) {
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
                    case "Key" -> key = buf.toString();
                    case "VersionId" -> versionId = buf.toString();
                    case "DeleteMarker" -> deleteMarker = Boolean.valueOf(buf.toString());
                    case "DeleteMarkerVersion" -> deleteMarkerVersion = buf.toString();
                    default -> {
                    }
                }
                buf.setLength(0);
            }

        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (level == 1) {
                while (Character.isSpaceChar(ch[start]) && length > 0) {
                    start++;
                    length--;
                }
                while (Character.isSpaceChar(ch[start + length]) && length > 0) {
                    length--;
                }
                if (level > 0) {
                    buf.append(ch, start, length);
                }
            }
        }

        public DeleteObjectsResult.Deleted toResult() {
            return new DeleteObjectsResult.Deleted(deleteMarker, deleteMarkerVersion, key, versionId);
        }
    }

    public static class DeleteObjectResultErrorSaxHandler extends DefaultHandler {
        private int level = 0;
        private final StringBuilder buf;

        private String code;
        private String key;
        private String message;
        private String versionId;

        public DeleteObjectResultErrorSaxHandler(StringBuilder buf) {
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
                    case "Code" -> code = buf.toString();
                    case "Key" -> key = buf.toString();
                    case "Message" -> message = buf.toString();
                    case "versionId" -> versionId = buf.toString();
                    default -> {
                    }
                }
                buf.setLength(0);
            }

        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (level == 1) {
                while (Character.isSpaceChar(ch[start]) && length > 0) {
                    start++;
                    length--;
                }
                while (Character.isSpaceChar(ch[start + length]) && length > 0) {
                    length--;
                }
                if (level > 0) {
                    buf.append(ch, start, length);
                }
            }
        }

        public DeleteObjectsResult.Error toResult() {
            return new DeleteObjectsResult.Error(code, key, message, versionId);
        }
    }
}
