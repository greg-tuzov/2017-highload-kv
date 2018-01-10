package ru.mail.polis.gt;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public class ResponseWrapper {
    private final byte[] data;
    private final int code;
    HttpExchange httpExchange;

    public ResponseWrapper(int code, HttpExchange httpExchange) {
        this.code = code;
        this.data = null;
        this.httpExchange = httpExchange;
    }

    public ResponseWrapper(int code, byte[] data, HttpExchange httpExchange) {
        this.code = code;
        this.data = data;
        this.httpExchange = httpExchange;
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

    public void successSend() throws IOException {
        if(hasData()) {
            httpExchange.sendResponseHeaders(code, data.length);
            httpExchange.getResponseBody().write(data);
        } else {
            httpExchange.sendResponseHeaders(code, 0);
        }
        //httpExchange.close();   //todo: try with resources
    }
}
