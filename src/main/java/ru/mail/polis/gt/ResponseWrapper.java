package ru.mail.polis.gt;

public class ResponseWrapper {
    private final byte[] data;
    private final int code;

    public ResponseWrapper(int code) {
        this.code = code;
        this.data = null;
    }

    public ResponseWrapper(int code, byte[] data) {
        this.code = code;
        this.data = data;
    }

    int getCode() {
        return code;
    }

    boolean hasData() {
        return data != null;
    }

    byte[] getData() {
        return data;
    }
}
