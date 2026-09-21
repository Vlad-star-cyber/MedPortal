package org.example;

import javafx.application.Platform;
import org.example.common.Command;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class NetworkClient {
    private static NetworkClient instance;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, Consumer<Command>> pendingRequests = new ConcurrentHashMap<>();

    private volatile boolean connected = false;

    private NetworkClient() {}

    public static synchronized NetworkClient getInstance() {
        if (instance == null) instance = new NetworkClient();
        return instance;
    }

    public void connect(String host, int port) throws Exception {
        if (connected) return;
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        connected = true;

        readerExecutor.submit(this::readLoop);
    }

    public CompletableFuture<Command> send(Command cmd) {
        CompletableFuture<Command> future = new CompletableFuture<>();
        pendingRequests.put(cmd.getRequestId(), response -> {
            future.complete(response);
            pendingRequests.remove(cmd.getRequestId());
        });
        try {
            out.writeObject(cmd);
            out.flush();
            out.reset();
        } catch (Exception e) {
            future.completeExceptionally(e);
            pendingRequests.remove(cmd.getRequestId());
        }
        return future;
    }

    private void readLoop() {
        while (connected && !Thread.currentThread().isInterrupted()) {
            try {
                Object obj = in.readObject();
                if (obj instanceof Command response) {
                    String corrId = response.getCorrelationId();
                    Consumer<Command> callback = pendingRequests.get(corrId);
                    if (callback != null) {
                        Platform.runLater(() -> callback.accept(response));
                    }
                }
            } catch (Exception e) {
                if (connected) e.printStackTrace();
                break;
            }
        }
    }

    public void close() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        readerExecutor.shutdownNow();
    }
}