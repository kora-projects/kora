package ru.tinkoff.kora.http.common.header;

public abstract class AbstractHttpHeaders implements HttpHeaders {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (o instanceof HttpHeaders headers) {
            if (headers.size() != this.size()) {
                return false;
            }
            for (var entry : headers) {
                var key = entry.getKey();
                var value = entry.getValue();
                if (value == null) {
                    return false;
                } else {
                    var thisValue = this.getAll(key);
                    if (!value.equals(thisValue)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int h = 0;
        for (var entry : this)
            h += entry.hashCode();
        return h;
    }

    @Override
    public String toString() {
        return HttpHeaders.toString(this);
    }
}
