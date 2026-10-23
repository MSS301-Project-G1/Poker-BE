package com.msspoker.authservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Local HTTP fixture for the real AWS client/presigner; this does not validate AWS signatures or IAM.
final class S3TestServer implements AutoCloseable {
    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpServer server;

    S3TestServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    boolean exists(String key) { return objects.containsKey("test-bucket/" + key); }

    byte[] bytes(String key) { return objects.get("test-bucket/" + key).bytes(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String key = exchange.getRequestURI().getPath().substring(1);
            String method = exchange.getRequestMethod();
            if (method.equals("PUT")) {
                String copySource = exchange.getRequestHeaders().getFirst("x-amz-copy-source");
                if (copySource != null) {
                    StoredObject source = objects.get(URLDecoder.decode(copySource, StandardCharsets.UTF_8).replaceFirst("^/", ""));
                    if (source == null) { error(exchange, 404); return; }
                    if (!source.etag().equals(exchange.getRequestHeaders().getFirst("x-amz-copy-source-if-match"))) {
                        error(exchange, 412); return;
                    }
                    objects.put(key, source);
                    exchange.getResponseHeaders().set("Content-Type", "application/xml");
                    send(exchange, 200, ("<CopyObjectResult><ETag>" + source.etag()
                            + "</ETag><LastModified>2026-10-17T00:00:00Z</LastModified></CopyObjectResult>").getBytes(StandardCharsets.UTF_8));
                } else {
                    byte[] bytes = exchange.getRequestBody().readAllBytes();
                    String etag;
                    try { etag = "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "\""; }
                    catch (Exception exception) { throw new IllegalStateException(exception); }
                    objects.put(key, new StoredObject(bytes, exchange.getRequestHeaders().getFirst("Content-Type"), etag));
                    exchange.getResponseHeaders().set("ETag", etag);
                    send(exchange, 200, new byte[0]);
                }
                return;
            }
            if (method.equals("DELETE")) {
                objects.remove(key);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            StoredObject object = objects.get(key);
            if (object == null) { error(exchange, 404); return; }
            String ifMatch = exchange.getRequestHeaders().getFirst("If-Match");
            if (ifMatch != null && !ifMatch.equals(object.etag())) { error(exchange, 412); return; }
            exchange.getResponseHeaders().set("Content-Type", object.contentType());
            exchange.getResponseHeaders().set("ETag", object.etag());
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(object.bytes().length));
            if (method.equals("HEAD")) exchange.sendResponseHeaders(200, -1);
            else send(exchange, 200, object.bytes());
        }
    }

    private void error(HttpExchange exchange, int status) throws IOException {
        if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(status, -1);
        else {
            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            send(exchange, status, ("<Error><Code>" + (status == 404 ? "NoSuchKey" : "PreconditionFailed")
                    + "</Code><Message>Test fixture</Message></Error>").getBytes(StandardCharsets.UTF_8));
        }
    }

    private void send(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length != 0) exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private record StoredObject(byte[] bytes, String contentType, String etag) { }
}
