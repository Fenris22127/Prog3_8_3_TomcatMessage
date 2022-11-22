package de.medieninformatik.server;


import org.apache.catalina.Context;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) throws Exception {
        final int port = 8080;
        String webapps = "NewsWS";
        String doc = "web";

        Logger.getLogger("").setLevel(Level.SEVERE);

        Tomcat tomcat = new Tomcat();
        final String tmpDir = System.getProperty("java.io.tmpdir");
        tomcat.setBaseDir(tmpDir);
        Path docBase = Paths.get(doc).toAbsolutePath();
        Context ctx = tomcat.addWebapp(webapps, docBase.toString());

        Connector con = new Connector();
        con.setPort(port);

        Service service = tomcat.getService();
        service.addConnector(con);

        tomcat.start();
        System.out.printf("Docbase: %s%n", ctx.getDocBase());
        String url = con.getScheme() + "://" +
                InetAddress.getLocalHost().getHostAddress() + ":" +
                con.getPort() + ctx.getPath();
        System.out.printf("URL: %s%n", url);

        Thread t = new Thread(Nachricht.quelle());
        t.start();
        javax.swing.JOptionPane.showMessageDialog(null, "Server beenden!");
        t.interrupt();
        tomcat.stop();
        tomcat.destroy();
    }
}
