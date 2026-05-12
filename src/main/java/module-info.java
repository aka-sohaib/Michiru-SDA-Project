/**
 * JPMS module descriptor defining JavaFX, SQL, HTTP, and Gson dependencies for the MICHIRU desktop application.
 */
module com.example.michiru {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires org.kordamp.bootstrapfx.core;

    requires java.sql;
    requires mysql.connector.j;

    requires java.net.http;
    requires com.google.gson;

    opens com.example.michiru to javafx.fxml;
    exports com.example.michiru;

    exports com.example.michiru.model;
    exports com.example.michiru.model.dashboard;
    opens com.example.michiru.model to javafx.fxml;

    exports com.example.michiru.db;
    exports com.example.michiru.session;
    opens com.example.michiru.session to javafx.fxml;

    exports com.example.michiru.service;
}
