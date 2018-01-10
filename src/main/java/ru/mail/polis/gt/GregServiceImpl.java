package ru.mail.polis.gt;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVService;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Set;

public class GregServiceImpl implements KVService {

    static final int OK = 200;
    static final int CREATED = 201;
    static final int ACCEPTED = 202;
    static final int BAD_REQUEST = 400;
    static final int NOT_FOUND = 404;
    static final int NOT_ALLOWED = 405;
    static final int SERVER_ERROR = 500;
    static final int NOT_ENOUGH_REPLICAS = 504;

    final static String PREFIX = "id=";
    @NotNull
    private final HttpServer server;
    @NotNull
    private final GregDAO dao;
    private Set<String> topology;


    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {   //todo: возвращать инфу по поводу количества поднятых нод
            ResponseWrapper resp = new ResponseWrapper(200, httpExchange);
            resp.successSend();
            httpExchange.close();
        }
    }

    private class EntityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            RequestWrapper request = new RequestWrapper(httpExchange.getRequestURI().getQuery());

            if(request.isIdMissed()) {
                httpExchange.sendResponseHeaders(400, 0);
                httpExchange.close();
                return;
            }

            switch(httpExchange.getRequestMethod()) {
                case "GET":
                    try {
                        final byte[] valueGet = dao.get(request.getId());
                        new ResponseWrapper(200, valueGet, httpExchange).successSend();
                    } catch (IOException e) {
                        httpExchange.sendResponseHeaders(404, 0);
                    }
                    break;

                case "PUT":
                    final int contentLength = Integer.valueOf(httpExchange.getRequestHeaders().getFirst("Content-Length"));
                    final byte[] valuePut = new byte[contentLength];

                    InputStream is = httpExchange.getRequestBody();
                    for (int n = is.read(valuePut); n > 0; n = is.read(valuePut));
                    dao.upsert(request.getId(), valuePut);

                    new ResponseWrapper(201, httpExchange)
                            .successSend();
                    break;

                case "DELETE":
                    dao.delete(request.getId());
                    new ResponseWrapper(202, httpExchange)
                            .successSend();
                    break;

                default:
                    httpExchange.sendResponseHeaders(405, 0);
            }
            httpExchange.close();
        }
    }


    @NotNull
    private static String extractId(@NotNull final String query) {
        if(!query.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Shitty string");
        }

        return query.substring(PREFIX.length());
    }

    public GregServiceImpl(int port, @NotNull final GregDAO dao, Set<String> topology) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.dao = dao;
        this.topology = topology;

        this.server.createContext("/v0/status", new StatusHandler());
        this.server.createContext("/v0/entity", new EntityHandler());
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
