package ru.mail.polis.gt;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVService;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

public class GregService implements KVService {
    final static String PREFIX = "id=";
    @NotNull
    private final HttpServer server;
    @NotNull
    private final GregDAO dao;

    @NotNull
    private static String extractId(@NotNull final String query) {
        if(!query.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Shitty string");
        }

        return query.substring(PREFIX.length());
    }

    public GregService(int port, @NotNull final GregDAO dao) throws IOException{
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.dao = dao;

        this.server.createContext("/v0/status",
                new HttpHandler() {
                    @Override
                    public void handle(HttpExchange httpExchange) throws IOException {
                        final String response = "ONLINE";
                        httpExchange.sendResponseHeaders(200, response.length());
                        httpExchange.getResponseBody().write(response.getBytes());
                        httpExchange.close();
                    }
                });

        this.server.createContext("/v0/entity",
                httpExchange -> {
                    final String id = extractId(httpExchange.getRequestURI().getQuery());

                    if(id.isEmpty()) {
                        httpExchange.sendResponseHeaders(400, 0);
                        httpExchange.close();
                        return;
                    }

                    switch (httpExchange.getRequestMethod()) {
                        case "GET":

                            try {
                                final byte[] valueGet = dao.get(id);
                                httpExchange.sendResponseHeaders(200, valueGet.length);
                                httpExchange.getResponseBody().write(valueGet);
                            } catch (IOException e) {
                                httpExchange.sendResponseHeaders(404, 0);
                            }

                            break;

                        case "DELETE":
                            dao.delete(id);
                            httpExchange.sendResponseHeaders(202, 0);
                            break;

                        case "PUT":
                            final int contentLength = Integer.valueOf(httpExchange.getRequestHeaders().getFirst("Content-Length"));
                            final byte[] valuePut = new byte[contentLength];

                            InputStream is = httpExchange.getRequestBody();
                            for (int n = is.read(valuePut); n > 0; n = is.read(valuePut));
                            dao.upsert(id, valuePut);

                            httpExchange.sendResponseHeaders(201, contentLength);
                            httpExchange.getResponseBody().write(valuePut);

                            break;

                        default:
                            httpExchange.sendResponseHeaders(405, 0);

                    }

                    httpExchange.close();

                });
    }

    @Override
    public void start() {
        this.server.start();
    }

    @Override
    public void stop() {
        this.server.stop(0);
    }
}
