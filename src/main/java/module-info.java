module com.example.michiru {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires org.kordamp.bootstrapfx.core;

    // ── Persistence ──────────────────────────────────────────────────────────
    requires java.sql;          // java.sql.Connection, PreparedStatement, etc.
    requires mysql.connector.j; // mysql-connector-j automatic module (jar filename → mysql.connector.j)

    // ── Opens/Exports ────────────────────────────────────────────────────────
    opens com.example.michiru to javafx.fxml;
    exports com.example.michiru;

    exports com.example.michiru.model;
    opens com.example.michiru.model to javafx.fxml;

    exports com.example.michiru.db;
    exports com.example.michiru.session;
    opens com.example.michiru.session to javafx.fxml;
}