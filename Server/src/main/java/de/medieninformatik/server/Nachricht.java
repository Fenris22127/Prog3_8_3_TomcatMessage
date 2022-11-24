package de.medieninformatik.server;

import de.medieninformatik.Message.Message;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint("/news")
public class Nachricht {
    /**
     * Stores connections
     */
    private static List<Session> verbindung = new CopyOnWriteArrayList<>();

    /**
     * Stores users and their session ID's
     */
    private static HashMap<String, String> users = new HashMap<>();

    /**
     * Collect all connected users in a list
     * @param session
     */
    @OnOpen
    public void onOpen(Session session) {
        // Get session and WebSocket connection
        System.out.printf("%s: onOpen aufgerufen%n", session.getId());
        verbindung.add(session);
    }

    @OnClose
    public void onClose(Session session) {
        // WebSocket connection closes
        System.out.printf("%s: onClose aufgerufen%n", session.getId());
        verbindung.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        // Do error handling here
        System.err.printf("%s: %s%n", session.getId(), error.getMessage());
    }

    @OnMessage
    public void handleChatMsg(Session session, String message) {
        //message.setFrom(users.get(session.getId()));
        //broadcast(message);
        // Handle new messages
        System.out.println("Nachricht (Server): Got message: " + message);
    }

    public static Runnable quelle() {
        return () -> {
            while(!Thread.interrupted()) {
                String nachricht = "[Nachricht]";
                verbindung.forEach( s -> {
                    try {
                        s.getBasicRemote().sendText(nachricht);
                    } catch(IOException e) {
                        verbindung.remove(s);
                        try {
                            s.close();
                        } catch(IOException e1) {
                            //
                        }
                    }
                });
            }
        };
    }

}
