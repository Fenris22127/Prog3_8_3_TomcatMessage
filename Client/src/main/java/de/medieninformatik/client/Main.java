package de.medieninformatik.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class Main {
    private static final String URL = "ws://localhost:8080/NewsWS/news";

    public static void main (String[] args) {
        WebSocket.Listener listener = new WebSocket.Listener() {


            @Override
            public void onOpen(WebSocket webSocket) {

            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                System.out.print(data);
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                System.out.printf("onClose: %d %s%n", statusCode, reason);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                System.err.printf("onError: %s%n", error.getMessage());
            }
        };

        HttpClient client = HttpClient.newHttpClient();
        CompletableFuture<WebSocket> future =
                client.newWebSocketBuilder().buildAsync(URI.create(URL), listener);
        try {
            WebSocket ws = future.get();
            ws.request(1);
            System.in.read();
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Closed");
        } catch(InterruptedException | ExecutionException | IOException e) {
            System.err.println(e);
        }
    }
}
