// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.completion;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small test-only HTTP/1.1 server using java.net, which is in the Android test API. */
public final class LoopbackHttpFixture implements Closeable {
    public interface Handler { Response respond(Request request) throws Exception; }

    public static final class Request {
        public final String path;
        public final String body;
        public final Map<String, String> headers;
        private Request(String path, String body, Map<String, String> headers) {
            this.path = path;
            this.body = body;
            this.headers = headers;
        }
    }

    public static final class Response {
        public final int status;
        public final String body;
        public final String location;
        public Response(int status, String body, String location) {
            this.status = status;
            this.body = body;
            this.location = location;
        }
    }

    private final ServerSocket listener = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
    private final CopyOnWriteArrayList<Socket> sockets = new CopyOnWriteArrayList<>();
    private final ExecutorService worker = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "SenseKey-test-http");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public LoopbackHttpFixture() throws IOException {}
    public int getPort() { return listener.getLocalPort(); }

    public void start(final Handler handler) {
        Thread acceptor = new Thread(() -> {
            while (!closed) {
                try {
                    Socket socket = listener.accept();
                    socket.setSoTimeout(5000);
                    sockets.add(socket);
                    worker.execute(() -> serve(socket, handler));
                } catch (IOException ignored) {
                    if (!closed) throw new AssertionError("Test listener failed");
                }
            }
        }, "SenseKey-test-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void serve(final Socket socket, final Handler handler) {
        try (Socket connection = socket;
                BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
            String[] requestLine = line(input).split(" ");
            if (requestLine.length != 3) throw new IOException("Invalid request line");
            Map<String, String> headers = new HashMap<>();
            String header;
            while (!(header = line(input)).isEmpty()) {
                int separator = header.indexOf(':');
                if (separator < 0 || headers.size() > 64) throw new IOException("Invalid headers");
                headers.put(header.substring(0, separator).toLowerCase(Locale.ROOT),
                        header.substring(separator + 1).trim());
            }
            int size = Integer.parseInt(headers.containsKey("content-length")
                    ? headers.get("content-length") : "0");
            if (size < 0 || size > 100_000) throw new IOException("Invalid body size");
            byte[] bytes = new byte[size];
            for (int offset = 0; offset < size;) {
                int count = input.read(bytes, offset, size - offset);
                if (count < 0) throw new IOException("Incomplete request");
                offset += count;
            }
            Response response = handler.respond(new Request(requestLine[1],
                    new String(bytes, StandardCharsets.UTF_8), headers));
            byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + response.status + " Test response\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\nConnection: close\r\n"
                    + (response.location.isEmpty() ? "" : "Location: " + response.location + "\r\n")
                    + "\r\n";
            connection.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
            connection.getOutputStream().write(body);
            connection.getOutputStream().flush();
        } catch (Exception ignored) {
            // A cancelled client can close the socket before the delayed reply is sent.
        } finally { sockets.remove(socket); }
    }

    private static String line(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int count = 0; count < 8192; count++) {
            int value = input.read();
            if (value < 0) throw new IOException("Incomplete headers");
            if (value == '\n') return new String(bytes.toByteArray(), StandardCharsets.US_ASCII).trim();
            if (value != '\r') bytes.write(value);
        }
        throw new IOException("Header too long");
    }

    @Override public void close() throws IOException {
        closed = true;
        listener.close();
        for (Socket socket : sockets) socket.close();
        worker.shutdownNow();
    }
}
