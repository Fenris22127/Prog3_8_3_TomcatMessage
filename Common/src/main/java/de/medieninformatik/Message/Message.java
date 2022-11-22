package de.medieninformatik.Message;

import java.io.Serializable;

/**
 * Serialisierbare Klasse zum Austausch von Nachrichten
 * zwischen Client und Server.
 */
public record Message(
        Action action,
        String user,
        String content
    ) implements Serializable {

    public enum Action {
        JOIN, SEND, LEAVE // Anmelden, Nachricht senden, Abmelden
    }
}