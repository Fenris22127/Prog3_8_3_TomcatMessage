module Prog3_8_3_TomcatMessages.Server.main {
    requires org.apache.tomcat.embed.jasper;
    requires org.apache.tomcat.embed.websocket;
    requires org.apache.tomcat.embed.core;
    requires org.apache.tomcat.embed.el;
    requires java.desktop;
    requires java.instrument; // wichtig für Reflection von tomcat
    requires Prog3_8_3_TomcatMessages.Message.main;
    exports de.medieninformatik.server;
}
