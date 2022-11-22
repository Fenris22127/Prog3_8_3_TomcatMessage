module Prog3_8_3_TomcatMessage.Client.main {
    requires javafx.controls;
    requires javafx.fxml;
    requires Prog3_8_3_TomcatMessages.Message.main;

    opens de.medieninformatik.client to javafx.fxml;
    exports de.medieninformatik.client;
}