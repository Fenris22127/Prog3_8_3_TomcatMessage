module Prog3_8_3_TomcatMessage.Client.main {
    requires javafx.controls;
    requires javafx.fxml;
    requires Prog3_8_3_TomcatMessages.Message.main;
    requires Prog3_8_3_TomcatMessages.Server.main;
    requires java.net.http;
    requires org.apache.tomcat.embed.jasper;
    requires org.apache.tomcat.embed.websocket;
    requires org.apache.tomcat.embed.core;
    requires org.apache.tomcat.embed.el;
    requires java.desktop;
    requires java.instrument; // wichtig für Reflection von tomcat

    opens de.medieninformatik.client to javafx.fxml;
    exports de.medieninformatik.client;
}