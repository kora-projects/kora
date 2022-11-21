package ru.tinkoff.kora.json.annotation.processor.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonReader;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Value class for performance tests
 */
@Json
public class MediaItem {
    public static final MediaItem SAMPLE;
    @JsonProperty("images")
    @JsonField("images")
    private List<Photo> _photos;
    @JsonProperty("content")
    @JsonField("content")
    private Content _content;

    public MediaItem() {}

    public MediaItem(Content c) {
        _content = c;
    }

    @JsonReader
    public MediaItem(@Nullable List<Photo> _photos, @Nullable Content _content) {
        this._photos = _photos;
        this._content = _content;
    }

    static {
        MediaItem.Content content = new MediaItem.Content();
        content.set_player(MediaItem.Content.Player.JAVA);
        content.set_uri("http://javaone.com/keynote.mpg");
        content.set_title("Javaone Keynote");
        content.set_width(640);
        content.set_height(480);
        content.set_format("video/mpeg4");
        content.set_duration(18000000L);
        content.set_size(58982400L);
        content.set_bitrate(262144);
        content.set_copyright("None");
        content.addPerson("Bill Gates");
        content.addPerson("Steve Jobs");

        MediaItem item = new MediaItem(content);

        item.addPhoto(new MediaItem.Photo("http://javaone.com/keynote_large.jpg", "Javaone Keynote", 1024, 768, MediaItem.Photo.Size.LARGE));
        item.addPhoto(new MediaItem.Photo("http://javaone.com/keynote_small.jpg", "Javaone Keynote", 320, 240, MediaItem.Photo.Size.SMALL));

        SAMPLE = item;
    }

    public void addPhoto(Photo p) {
        if (_photos == null) {
            _photos = new ArrayList<Photo>();
        }
        _photos.add(p);
    }

    public List<Photo> get_photos() {
        return _photos;
    }

    public void set_photos(List<Photo> _photos) {
        this._photos = _photos;
    }

    public Content get_content() {
        return _content;
    }

    public void set_content(Content _content) {
        this._content = _content;
    }

    /*
    /**********************************************************
    /* Helper types
    /**********************************************************
     */

    @Json
    public static class Photo {
        public enum Size {SMALL, LARGE;}

        @JsonProperty("uri")
        @JsonField("uri")
        private String _uri;
        @JsonProperty("title")
        @JsonField("title")
        private String _title;
        @JsonProperty("width")
        @JsonField("width")
        private int _width;
        @JsonProperty("height")
        @JsonField("height")
        private int _height;
        @JsonProperty("size")
        @JsonField("size")
        private Size _size;

        public Photo() {}

        @JsonReader
        public Photo(
            @Nullable String _uri,
            @Nullable String _title,
            int _width,
            int _height,
            @Nullable Size _size) {
            this._uri = _uri;
            this._title = _title;
            this._width = _width;
            this._height = _height;
            this._size = _size;
        }

        public String get_uri() {
            return _uri;
        }

        public void set_uri(String _uri) {
            this._uri = _uri;
        }

        public String get_title() {
            return _title;
        }

        public void set_title(String _title) {
            this._title = _title;
        }

        public int get_width() {
            return _width;
        }

        public void set_width(int _width) {
            this._width = _width;
        }

        public int get_height() {
            return _height;
        }

        public void set_height(int _height) {
            this._height = _height;
        }

        public Size get_size() {
            return _size;
        }

        public void set_size(Size _size) {
            this._size = _size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Photo photo = (Photo) o;
            return _width == photo._width && _height == photo._height && Objects.equals(_uri, photo._uri) && Objects.equals(_title, photo._title) && _size == photo._size;
        }

        @Override
        public int hashCode() {
            return Objects.hash(_uri, _title, _width, _height, _size);
        }
    }

    @Json
    public static class Content {
        public enum Player {JAVA, FLASH;}

        @JsonProperty("player")
        @JsonField("player")
        private Player _player;
        @JsonProperty("uri")
        @JsonField("uri")
        private String _uri;
        @JsonProperty("title")
        @JsonField("title")
        private String _title;
        @JsonProperty("width")
        @JsonField("width")
        private int _width;
        @JsonProperty("height")
        @JsonField("height")
        private int _height;
        @JsonProperty("format")
        @JsonField("format")
        private String _format;
        @JsonProperty("duration")
        @JsonField("duration")
        private long _duration;
        @JsonProperty("size")
        @JsonField("size")
        private long _size;
        @JsonProperty("bitrate")
        @JsonField("bitrate")
        private int _bitrate;
        @JsonProperty("persons")
        @JsonField("persons")
        private List<String> _persons;
        @JsonProperty("copyright")
        @JsonField("copyright")
        private String _copyright;

        @JsonCreator
        public Content() {}


        @JsonReader
        public Content(
            @Nullable Player _player,
            @Nullable String _uri,
            @Nullable String _title,
            int _width,
            int _height,
            @Nullable String _format,
            long _duration,
            long _size,
            int _bitrate,
            @Nullable List<String> _persons,
            @Nullable String _copyright) {
            this._player = _player;
            this._uri = _uri;
            this._title = _title;
            this._width = _width;
            this._height = _height;
            this._format = _format;
            this._duration = _duration;
            this._size = _size;
            this._bitrate = _bitrate;
            this._persons = _persons;
            this._copyright = _copyright;
        }

        public void addPerson(String p) {
            if (_persons == null) {
                _persons = new ArrayList<String>();
            }
            _persons.add(p);
        }

        public Player get_player() {
            return _player;
        }

        public void set_player(Player _player) {
            this._player = _player;
        }

        public String get_uri() {
            return _uri;
        }

        public void set_uri(String _uri) {
            this._uri = _uri;
        }

        public String get_title() {
            return _title;
        }

        public void set_title(String _title) {
            this._title = _title;
        }

        public int get_width() {
            return _width;
        }

        public void set_width(int _width) {
            this._width = _width;
        }

        public int get_height() {
            return _height;
        }

        public void set_height(int _height) {
            this._height = _height;
        }

        public String get_format() {
            return _format;
        }

        public void set_format(String _format) {
            this._format = _format;
        }

        public long get_duration() {
            return _duration;
        }

        public void set_duration(long _duration) {
            this._duration = _duration;
        }

        public long get_size() {
            return _size;
        }

        public void set_size(long _size) {
            this._size = _size;
        }

        public int get_bitrate() {
            return _bitrate;
        }

        public void set_bitrate(int _bitrate) {
            this._bitrate = _bitrate;
        }

        public List<String> get_persons() {
            return _persons;
        }

        public void set_persons(List<String> _persons) {
            this._persons = _persons;
        }

        public String get_copyright() {
            return _copyright;
        }

        public void set_copyright(String _copyright) {
            this._copyright = _copyright;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Content content = (Content) o;
            return _width == content._width && _height == content._height && _duration == content._duration && _size == content._size && _bitrate == content._bitrate && _player == content._player && Objects.equals(_uri, content._uri) && Objects.equals(_title, content._title) && Objects.equals(_format, content._format) && Objects.equals(_persons, content._persons) && Objects.equals(_copyright, content._copyright);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_player, _uri, _title, _width, _height, _format, _duration, _size, _bitrate, _persons, _copyright);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaItem mediaItem = (MediaItem) o;
        return Objects.equals(_photos, mediaItem._photos) && Objects.equals(_content, mediaItem._content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_photos, _content);
    }
}
