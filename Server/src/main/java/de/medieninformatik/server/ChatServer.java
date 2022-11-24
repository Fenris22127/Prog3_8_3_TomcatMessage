package de.medieninformatik.server;

import de.medieninformatik.Message.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ChatServer extends Thread {
    /**
     * Zuordnung Nutzer-> ObjectOutpuStream
     */
    private static class Connection {
        private final String user;
        private final ObjectOutputStream out;

        /**
         * Der Konstruktor assoziert den Nutzer mit dem Ausgabstrom
         * @param user Chat-Nutzer
         * @param out  Chat-Datenstrom zu den Klienten
         */
        public Connection(String user, ObjectOutputStream out) {
            this.user = user;
            this.out = out;
        }

        /**
         * Standard getter für den Nutzer
         * @return der Nutzer
         */
        public String getUser() {
            return user;
        }

        /**
         * Standard getter für den Ausgabestrom zu den Klienten
         * @return Ausgabestrom
         */
        public ObjectOutputStream getOutputStream() {
            return out;
        }

        /**
         * Überprüfe zwei Connections auf Gleichheit
         * @param o die andere Connection
         * @return true falls gleich, false sonst
         */
        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            Connection that = (Connection) o;
            return Objects.equals(user, that.user) &&
                    Objects.equals(out, that.out);
        }

        /**
         * Standard hash-Code für Connection
         * @return der errechnete hashcode.
         */
        @Override
        public int hashCode() {
            return Objects.hash(user, out);
        }
    }

    /**
     * Der im Thread-Pool ausgeführte ChatHandler
     */
    private class ChatHandler implements Runnable {
        private final Socket socket;
        private String user;

        /**
         * Konstruktor
         * @param socket de Socket für die Verbindung zum Client
         */
        public ChatHandler(Socket socket) {
            this.socket = socket;
            this.user = null;
        }

        /**
         * Die run Methode implementierte den Chat. Sie stellt die Verbindung
         * mit dem Chat-Server her und sendet und empfängt die Nachrichten.
         */
        @Override
        public void run() {
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                while(true) {
                    Message msg = (Message) in.readObject();
                    Message.Action action = msg != null ?
                            msg.action() : Message.Action.LEAVE;
                    if(action == Message.Action.LEAVE) break;
                    if(action == Message.Action.JOIN) join(msg, out);
                    if(action == Message.Action.SEND) broadcast(msg);
                }
            } catch(IOException | ClassNotFoundException e) {
                System.err.println(e);
            } finally {
                // Verbindung beendet, aufräumen
                try {
                    logout(user, out);
                    socket.shutdownInput();
                    socket.shutdownOutput();
                    socket.close();
                } catch(IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        }

        /**
         * Anmeldung eines neuen Nutzers
         * @param msg Nachricht vom Client
         * @param out Ausgabestrom zum Client
         */
        private void join(Message msg, ObjectOutputStream out) {
            assert msg.action() == Message.Action.JOIN;
            Connection c = new Connection(msg.user(), out);
            if(!connection.contains(c)) { // User noch nicht registriert
                this.user = msg.user();
                connection.add(c);
                //msg = new Message(Message.Action.JOIN, user, null);
                broadcast(msg);
                System.out.printf("JOIN: %d Nutzer eingeloggt%n", connection.size());
            }
        }

        /**
         * Bei bestehnder Verbindung: Behandle ankommende Nachricht
         * @param msg Nachricht
         */
        private void broadcast(Message msg) {
            Consumer<Connection> consume = c -> {
                try {  // Sende Nachricht an Client
                    ObjectOutputStream out = c.getOutputStream();
                    out.writeObject(msg);
                    out.flush();
                } catch(IOException e) {
                    System.err.println(e);
                }
            };
            // für alle registrierten Clienten: sende Nachricht
            connection.forEach(consume);
        }

        /**
         * Beende Verbindung zu Nutzer
         * @param user Nutzer
         * @param out Datenstrom zum Klienten
         */
        private void logout(String user, ObjectOutputStream out) {
            Connection c = new Connection(user, out);
            if(connection.contains(c)) {
                Message msg = new Message(Message.Action.LEAVE, user, null);
                broadcast(msg);
                connection.remove(c);
                System.out.printf("LEAVE: %d Nutzer eingeloggt%n", connection.size());
            }
        }
    }

    /**
     * Attribute
     */
    private final Set<Connection> connection;
    private final int port;
    private final ServerSocket server;
    private final ExecutorService pool;

    /**
     * Konstruktor
     * @param port an diesm Port wird auf Verbindungen gewartet
     * @throws IOException
     */
    public ChatServer(int port) throws IOException {
        this.port = port;
        this.connection = new CopyOnWriteArraySet<Connection>();
        this.server = new ServerSocket(port);
        this.pool  = Executors.newCachedThreadPool();
    }

    /**
     * Der Server-Thread nimmt initiale Verbindsanfragen entgegen
     * und deligiert deren Bearbeitung an Threads im Pool
     */
    @Override
    public void run() { // Server-Thread
        try {
            while(true) {
                final Socket socket = server.accept();
                // Verbindungen werden an Pool übergeben
                pool.submit(new ChatHandler(socket));
            }
        } catch(SocketException e) {
            // ausgelöst durch stopServer
        } catch(Exception e) {
            System.err.println(e);
        }
    }

    /**
     * Beendet Server
     */
    public void stopServer() {
        try {
            server.close();
        } catch(IOException e) {
            // Nothing to do
        } finally {
            pool.shutdown();
        }
    }
}
