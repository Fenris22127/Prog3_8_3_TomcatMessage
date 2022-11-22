package de.medieninformatik.client;

import de.medieninformatik.Message.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jurgen
 * date 2020-11-04
 * @version 1.0
 *
 * Der Client stellt das Nutzer-Interface für den Chat zur Verfügung.
 * Die Nachrichten vom Server werden mit einem JavaFX-Service empfangen.
 * Die lokalen Eingaben werden jedoch direkt (ohne Threads) an den Server
 * gesendet. Es können mehrere Clients zugleich laufen.
 *
 * Das Fenster besteht aus 3 Bereichen:
 * 1.das Verlaufs-Fenster gibt den Chat-Verlauf seit dem Einlogen wieder.
 *  Ebenso werden Meldungen zum Ein-/und Ausloggen angezeigt.
 *
 * 2. Die Eingabezeile hat zwei Funktionen:
 *   a) Vor dem Einloggen wird hier der Nutzername angegeben (ohne die
 *   Return-Taste zu drücken).
 *   b) Nach dem Einloggen werden hier die Chat-Nachrichten an den Server
 *   eingegeben. Durch Drücken der Return-taste wird die Nachricht an
 *   den Server geschickt.
 *
 * 3. Der Eingabeknopf:
 *   a) Falls der Nutzer noch nicht eingeloggt ist, dann wird durch Drücken
 *   des Knopfes der Text in der Eingabezeile als Nutzername interpretiert
 *   und dieser Nutzer am Server angemeldet.
 *   b) Falls der Nutzer angemeldet ist, wird er durch Drücken des Knopfes
 *   abgemeldet.
 */
public class Client extends Application {
    private final int HEIGHT = 500;
    private final int WIDTH = 800;
    private final int FONTSIZE = 14;
    private final String FONT = " -fx-font: " + FONTSIZE + "pt \"Arial\";";
    private final String BG_GRAY = " -fx-background-color: \"lightgray\";";
    private final String BG_RED  = " -fx-background-color: \"red\";";
    private String host;
    private int port;
    private Stage stage;
    private Button button;
    private TextField eingabeZeile;
    private TextArea verlauf;
    private String user;
    private AtomicBoolean isLoggedIn; // in JavaFX-Thread und in Task
    private Semaphore semaphore; // verhindert cleanup-run bevor letzte msg gesendet
    private Service<Void> service;
    private Service<Void> sendService;
    private BlockingQueue<Message> messages;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    /**
     * Die vom Service ausgeführte Task.
     * Sie nimmt vom Server Nachrichten entgegen,
     * gibt diese aus und reagiert gegebenenfalls.
     *
     * Falls eine LEAVE-Nachricht des Nutzers eintrifft wird
     * die Task und damit der Service beendet.
     */
    private class ChatTask extends Task<Void> {
        @Override
        protected Void call() throws Exception {
            semaphore.acquire(); // Verhindert zu frühen cleanup
            while(isLoggedIn.get()) {
                Message msg = (Message) in.readObject();
                Message.Action action = msg.action();
                final String ausgabe = switch(action) {
                    case JOIN -> String.format(">>> %s ist angemeldet%n", msg.user());
                    case SEND -> String.format("%s: %s%n", msg.user(), msg.content());
                    case LEAVE -> String.format("<<< %s ist abgemeldet%n", msg.user());
                };
                Platform.runLater( () -> verlauf.appendText(ausgabe) );
                if(action == Message.Action.LEAVE && user.equals(msg.user()))  {
                    isLoggedIn.set(false);
                }
            }
            semaphore.release();
            return null;
        }
    }

    /**
     * Wartet auf Nachricht in Warteschlange.
     * Diese Nachricht wird dann an den Server gesendet.
     * LEAVE-Nachricht oder isLoggedIn == false beendet die Task
     */
    private class SendTask extends Task<Void> {
        @Override
        protected Void call() throws Exception {
            while(isLoggedIn.get()) {
               Message msg = messages.take(); // Blockiert
               out.writeObject(msg);
               out.flush();
               if(msg.action() == Message.Action.LEAVE) {
                   break;
               }
            }
            return null;
        }
    }

    /**
     * Bereitet JavaFX vor (ohne GUI-Elemente)
     * Auf der Kommandozeile des Clienten kann
     * der Host und der Port mit
     * java client --host=localhost --port=60000
     * übergeben werden.
     * @throws Exception
     */
    @Override
    public void init() throws Exception {
        Parameters p = this.getParameters();
        Map<String, String> map = p.getNamed();
        host = map.getOrDefault("host", "localhost");
        port = Integer.parseInt(map.getOrDefault("port", "60000"));
        isLoggedIn = new AtomicBoolean(false);
        semaphore = new Semaphore(1);
        messages = new LinkedBlockingQueue<Message>();
    }

    /**
     * Erzeugt das GUI.
     * @param stage
     * @throws Exception
     */
    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        eingabeZeile = new TextField();
        eingabeZeile.setStyle(FONT);
        // Wenn return gedrückt, dann rufe Eventhandler sendenachricht auf
        eingabeZeile.setOnAction(this::sendeNachricht);

        verlauf = new TextArea();
        verlauf.setWrapText(true);
        verlauf.setStyle(FONT);
        verlauf.setEditable(false);
        verlauf.setPrefHeight(HEIGHT-6*FONTSIZE);
        verlauf.setPrefWidth(WIDTH-20);

        button = new Button("Anmelden");
        button.setStyle(FONT + BG_GRAY);
        button.setOnAction(this::handleButton);

        final VBox vbox = new VBox();
        final ScrollPane pane = new ScrollPane(verlauf);
        vbox.getChildren().addAll(pane, eingabeZeile, button);
        stage.setScene(new Scene(vbox, WIDTH, HEIGHT));
        stage.setTitle("ChatClient");
        stage.setOnCloseRequest( e -> {
            logout();
            Platform.exit();
        } );
        stage.show();
    }


    /**
     * Button dient sowohl zum login als auch zum logout.
     * @param e
     */
    private void handleButton(ActionEvent e) {
        if(isLoggedIn.get()) {
            logout();
        } else {
            user = eingabeZeile.getText();
            if(user.length() > 0) login();
        }
    }

    /**
     * Melde Benutzer an.
     */
    private void login() {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            sendService = new Service<Void> () {
                @Override
                protected Task<Void> createTask() {
                    return new SendTask();
                }
            };
            // cleanup wartet auf freie semaphorenvon ChatTask
            sendService.setOnSucceeded(this::cleanup);
            sendService.setOnFailed(this::cleanup);

            service = new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new ChatTask();
                }
            };
            isLoggedIn.set(true);// muss vor service.start stehen
            button.setText("Abmelden");
            button.setStyle(FONT + BG_RED);

            sendService.start();
            service.start();

            messages.offer(
                    new Message(Message.Action.JOIN, user, "")
            );
            stage.setTitle("ChatClient -- " + user);
        } catch(IOException e) {
            verlauf.appendText(e.getMessage());
            isLoggedIn.set(false); // beendet Service
        } finally {
            eingabeZeile.setText("");
            eingabeZeile.requestFocus();
        }
    }

    /**
     * Melde Nutzer ab
     */
    private void logout() {
        if(isLoggedIn.get()) {
            messages.offer(
                    new Message(Message.Action.LEAVE, user, "")
            );
            button.setText("Anmelden");
            button.setStyle(FONT + BG_GRAY);
        }
    }

    private void cleanup(WorkerStateEvent e) {
        try {
            semaphore.acquire(); // warte bis ChatTask logout von Server hat
        } catch(InterruptedException interrupted) {
            System.err.println(interrupted);
        }
        messages.clear();
        sendService = null;
        service = null;
        socket = null;
        out = null;
        in = null;
        eingabeZeile.setText("");
        eingabeZeile.requestFocus();
        stage.setTitle("ChatClient");
        semaphore.release();
    }

    /**
     * Schreibt den Inhalt der Eingabezeile als Nachricht in
     * die Sende-Warteschlange.
     * Event wird ausgelöst, wenn Nutzer eingeloggt ist und
     * Return auf Eingabezeile gedrückt wird.
     * @param event
     */
    private void sendeNachricht(ActionEvent event) {
        messages.offer(
            new Message(Message.Action.SEND, user, eingabeZeile.getText())
        );
        eingabeZeile.setText("");
        eingabeZeile.requestFocus();
    }
}
