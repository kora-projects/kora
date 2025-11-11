package ru.tinkoff.kora.aws.s3.impl.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

public class DeleteObjectsResultSaxHandler extends DefaultHandler {
    private int level = 0;
    private final StringBuilder buf = new StringBuilder();
    private final List<DeleteObjectsResult.Deleted> deleted = new ArrayList<>();

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
                case "Deleted":
                    delegate = new DeleteObjectResultDeletedSaxHandler(buf);
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
                case "Deleted":
                    assert delegate instanceof DeleteObjectResultDeletedSaxHandler;
                    deleted.add(((DeleteObjectResultDeletedSaxHandler) delegate).toResult());
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
        if (level > 1) {
            if (delegate != null) {
                delegate.characters(ch, start, length);
            }
            return;
        }
        buf.append(ch, start, length);
    }

    public DeleteObjectsResult toResult() {
        return new DeleteObjectsResult(deleted);
    }

    public static class DeleteObjectResultDeletedSaxHandler extends DefaultHandler {
        private int level = 0;
        private final StringBuilder buf;

        private String key;

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
                    case "Key":
                        key = buf.toString();
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
            return new DeleteObjectsResult.Deleted(key);
        }
    }
}
