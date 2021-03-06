package ru.mail.polis.gt;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.mail.polis.KVService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import static ru.mail.polis.gt.GregServiceImpl.HttpMethod.DELETE;
import static ru.mail.polis.gt.GregServiceImpl.HttpMethod.GET;
import static ru.mail.polis.gt.GregServiceImpl.HttpMethod.PUT;

public class GregServiceImpl implements KVService {

    @NotNull
    private final HttpServer server;
    @NotNull
    private final GregDAO dao;
    private List<String> topology;
    StatusHandler statusHandler;
    EntityHandler entityHandler;
    InsideHandler insideHandler;
    private final CompletionService<ResponseWrapper> completionService;

    PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager();
    CloseableHttpClient httpClient = null;

    private LFUCache cache;

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            final String response = "ONLINE";
            httpExchange.sendResponseHeaders(200, response.length());
            httpExchange.getResponseBody().write(response.getBytes());
            httpExchange.close();
        }
    }

    private class InsideHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            RequestWrapper request = new RequestWrapper(httpExchange.getRequestURI().getQuery(), topology);

            ResponseWrapper response = null;
            switch(httpExchange.getRequestMethod()) {
                case "GET":
                    response = handleGet(request);
                    break;
                case "PUT":
                    byte[] valPut = getDataFromHttpExchange(httpExchange);
                    response = handlePut(request, valPut);
                    break;
                case "DELETE":
                    response = handleDelete(request);
                    break;
                default:
                    httpExchange.sendResponseHeaders(405,0);
            }

            if (response.hasData()) {
                httpExchange.sendResponseHeaders(response.getCode(), response.getData().length);
                httpExchange.getResponseBody().write(response.getData());
            } else {
                httpExchange.sendResponseHeaders(response.getCode(), 0);
            }

            httpExchange.close();
        }

        private ResponseWrapper handlePut(RequestWrapper request, byte[] data) {
            try {
                cache.addCacheEntry(request.getId(), data);
                dao.upsert(request.getId(), data);
                return new ResponseWrapper(201);
            } catch (IOException | IllegalArgumentException e) {
                return new ResponseWrapper(404);
            }
        }

        private ResponseWrapper handleGet(RequestWrapper request) {
            try {
                byte[] valueGet;
                if (null != (valueGet = cache.getCacheEntry(request.getId()))) {
                } else {
                    valueGet = dao.get(request.getId());
                    cache.addCacheEntry(request.getId(), valueGet);
                }
                return new ResponseWrapper(200, valueGet);
            } catch(NoSuchElementException e) {
                return new ResponseWrapper(404);
            } catch (IOException e) {
                return new ResponseWrapper(500);
            }
        }

        private ResponseWrapper handleDelete(RequestWrapper request) {
            try {
                cache.rm(request.getId());
                dao.delete(request.getId());
                return new ResponseWrapper(202);
            } catch (IOException e) {
                return new ResponseWrapper(500);
            }
        }
    }

    private class EntityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            RequestWrapper request = new RequestWrapper(httpExchange.getRequestURI().getQuery(), topology);

            if (request.isWrongFormat() || request.isIdMissed()) {
                httpExchange.sendResponseHeaders(400, 0);
                httpExchange.close();
                return;
            }

            if (!request.isOldAPI()) {
                //проверим что можем поддерживать требуемое количество реплик
                try {
                    int workingNodes = checkStatusOfOtherNodes(request);
                    if (workingNodes < request.getAck()) {
                        httpExchange.sendResponseHeaders(504, 0);
                        httpExchange.close();
                        return;
                    }
                } catch (IOException e) {
                    httpExchange.sendResponseHeaders(500, 0);
                    httpExchange.close();
                    return;
                }
            }

            ResponseWrapper response = null;
            switch(httpExchange.getRequestMethod()) {
                case "GET":
                    response = handleGet(request);
                    break;
                case "PUT":
                    response = handlePut(httpExchange, request);
                    break;
                case "DELETE":
                    response = handleDelete(request);
                    break;
                default:
                    httpExchange.sendResponseHeaders(405, 0);
            }

            if (response.hasData()) {
                httpExchange.sendResponseHeaders(response.getCode(), response.getData().length);
                httpExchange.getResponseBody().write(response.getData());
            } else {
                httpExchange.sendResponseHeaders(response.getCode(), 0);
            }

            httpExchange.close();
        }

        public ResponseWrapper handleDelete(RequestWrapper request) throws IOException {
            List<String> nodes = getNodesById(request.getId(), request.getFrom());

            performTaskOnSeveralNodes(DELETE, request, nodes, null);

            try {
                int success = 0;
                for(int i = 0; i < request.getFrom(); i++) {
                    Thread.sleep(500);
                    ResponseWrapper resp = completionService.take().get();
                    if (resp.getCode() == 202) {
                        success++;
                    }
                }
                if (success >= request.getAck()) {
                    return new ResponseWrapper(202);
                } else {
                    return new ResponseWrapper(504);
                }
            } catch(InterruptedException | ExecutionException e) {
                return new ResponseWrapper(500);
            }
        }

        public ResponseWrapper handlePut(HttpExchange httpExchange, RequestWrapper request) throws IOException {   //todo: Добавить отдельную ветку для обрабтки старого API(также нужно для реализации функционала INNER_YRI)
            List<String> nodes = getNodesById(request.getId(), request.getFrom());
            byte[] valPut = getDataFromHttpExchange(httpExchange);

            performTaskOnSeveralNodes(PUT, request, nodes, valPut);

            try {
                int success = 0;
                for(int i = 0; i < request.getFrom(); i++) {
                    ResponseWrapper resp = completionService.take().get();
                    if(resp.getCode() == 201) {
                        success++;
                    }
                }
                if(success >= request.getAck()) {
                    return new ResponseWrapper(201);
                } else {
                    return new ResponseWrapper(504);
                }
            } catch(InterruptedException | ExecutionException e) {
                return new ResponseWrapper(500);
            }
        }

        public ResponseWrapper handleGet(RequestWrapper request) throws IOException {
            List<String> nodes = getNodesById(request.getId(), request.getFrom());

            performTaskOnSeveralNodes(GET, request, nodes, null);

            try {
                byte[] data = null;
                int success = 0;
                int fail = 0;
                ResponseWrapper resp = null;
                for (int i = 0; i < request.getFrom(); i++) {
                    resp = completionService.take().get();
                    if (resp.getCode() == 200) {
                        success++;
                        data = resp.getData();
                    } else if(resp.getCode() == 404) {
                        //return resp;
                        fail++;
                    }
                }
                //handling missed wright
                boolean hasOnThisNode = insideHandler.handleGet(request).getCode() == 200;
                if (!hasOnThisNode && fail == 1 && success > 0) {
                    insideHandler.handlePut(request, data);
                    success++;
                    fail--;
                } else if (success == 0) {
                    return new ResponseWrapper(404);
                }

                if (success >= request.getAck()) {
                    return new ResponseWrapper(200, data);
                } else {
                    if (success == 1 && hasOnThisNode) {
                        return new ResponseWrapper(404);
                    } else {
                        return new ResponseWrapper(504);
                    }
                }
            } catch(InterruptedException | ExecutionException e) {
                return new ResponseWrapper(500);
            }
        }
    }

    public GregServiceImpl(int port, @NotNull final GregDAO dao, Set<String> topology) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.dao = dao;
        this.topology = new ArrayList<>(topology);
        Executor executor = Executors.newFixedThreadPool(topology.size());
        this.completionService = new ExecutorCompletionService<>(executor);

        this.server.createContext("/v0/status", statusHandler = new StatusHandler());
        this.server.createContext("/v0/entity", entityHandler = new EntityHandler());
        this.server.createContext("/v0/inside", insideHandler = new InsideHandler());

        //pool.setDefaultMaxPerRoute(10);
        pool.setMaxTotal(200);

        httpClient = HttpClients.custom()
                .setConnectionManager(pool)
                .build();

        this.cache = new LFUCache(100);
    }

    public List<String> getNodesById(String id, int from) {
        List<String> res = new ArrayList<>(from);
        int hash = Math.abs(id.hashCode());
        for (int i = 0; i < from; i++) {
            res.add(topology.get((hash+i) % topology.size()));
        }
        return res;
    }

    private void performTaskOnSeveralNodes(@NotNull HttpMethod method,
                                           @NotNull RequestWrapper request,
                                           @NotNull List<String> nodes,
                                           @Nullable byte[] data) {
        String thisNode = "http://localhost:" + server.getAddress().getPort();
        for (String node : nodes) {
            if (node.equals(thisNode)) {
                switch (method) {
                    case GET:
                        completionService.submit(() -> insideHandler.handleGet(request));
                        break;
                    case PUT:
                        completionService.submit(() -> insideHandler.handlePut(request, data));
                        break;
                    case DELETE:
                        completionService.submit(() -> insideHandler.handleDelete(request));
                        break;
                    default:
                        throw new IllegalArgumentException("405");
                }
            } else {
                completionService.submit(
                        () -> makeRequest(method, node + "/v0/inside", "?id=" + request.getId(), data));
            }
        }
    }

    private ResponseWrapper makeRequest(HttpMethod method,
                                        @NotNull String to,
                                        @NotNull String idString,
                                        @Nullable byte[] data) {

        CloseableHttpResponse resp = null;
        HttpURLConnection conn = null;
        int code;
        try {
            switch (method) {
                case GET:
                    URL url = new URL(to + idString);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod(method.toString());
                    conn.connect();

                    code = conn.getResponseCode();
                    if (code == 200) {
                        InputStream dataStream = conn.getInputStream();
                        byte[] inputData = readData(dataStream);
                        return new ResponseWrapper(code, inputData);
                    }
                    return new ResponseWrapper(code);
                case PUT:
                    HttpPut httpPut = new HttpPut(to + idString);
                    httpPut.setEntity(new ByteArrayEntity(data));
                    resp = httpClient.execute(httpPut);
                    code = resp.getStatusLine().getStatusCode();
                    return new ResponseWrapper(code);
                case DELETE:
                    HttpDelete httpDelete = new HttpDelete(to + idString);
                    resp = httpClient.execute(httpDelete);
                    code = resp.getStatusLine().getStatusCode();
                    return new ResponseWrapper(code);
                default:
                    return new ResponseWrapper(405);
            }
        } catch (IOException e) {
            return new ResponseWrapper(500);
        } finally {
            try {
                if (resp != null) resp.close();
                if (conn != null) conn.disconnect();
            } catch (IOException e) {
                return new ResponseWrapper(500);
            }
        }
    }

    private int checkStatusOfOtherNodes(RequestWrapper request) throws IOException {
        List<String> nodes = getNodesById(request.getId(), request.getFrom());
        int workingNodes = 0;
        String thisNode = "http://localhost:" + server.getAddress().getPort();
        ResponseWrapper resp;
        for(String node : nodes) {
            if (node.equals(thisNode)) {
                workingNodes++;
            } else {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(node + "/v0/status");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.connect();

                    if (conn.getResponseCode() == 200) {
                        workingNodes++;
                    }
                } catch (java.net.ConnectException e) {
                    //все ок, просто не удалось достучаться до одной из нод => workingNodes не инкрементируется
                } catch (IOException e) {
                    throw e;
                }
            }
        }
        return workingNodes;
    }

    private byte[] readData(@NotNull InputStream is) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            for (int len; (len = is.read(buffer, 0, 1024)) != -1; ) {
                os.write(buffer, 0, len);
            }
            os.flush();
            return os.toByteArray();
        }
    }

    public byte[] getDataFromHttpExchange(HttpExchange httpExchange) throws IOException {
        final int contentLength = Integer.valueOf(httpExchange.getRequestHeaders().getFirst("Content-Length"));
        final byte[] res = new byte[contentLength];
        InputStream is = httpExchange.getRequestBody();
        for (int n = is.read(res); n > 0; n = is.read(res));
        return res;
    }

    enum HttpMethod {
        GET,
        PUT,
        DELETE
    }

    @Override
    public void start() {
        cache.empty();
        this.server.start();
    }

    @Override
    public void stop() {
        this.server.stop(0);
    }
}
