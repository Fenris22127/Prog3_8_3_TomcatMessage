package de.medieninformatik.server;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint("/news")
public class Nachricht {
    private static List<Session> verbindung = new CopyOnWriteArrayList<>();
    private static DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    @OnOpen
    public void onOpen(Session session) {
        System.out.printf("%s: onOpen aufgerufen%n", session.getId());
        verbindung.add(session);
    }

    @OnClose
    public void onClose(Session session) {
        System.out.printf("%s: onClose aufgerufen%n", session.getId());
        verbindung.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.printf("%s: %s%n", session.getId(), error.getMessage());
    }

    public static Runnable quelle() {
        return () -> {
            AtomicInteger counter = new AtomicInteger();
            Random r = new Random();
            while(!Thread.interrupted()) {
                try {
                    Thread.sleep(1000*(1+r.nextInt(5)));
                    LocalTime current = LocalTime.now();
                    String nachricht = String.format("[Nachricht um %s] Counter: %d%n",
                            current.format(fmt), counter.incrementAndGet());
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
                } catch(InterruptedException e) {
                    break;
                }
            }
        };
    }

}
