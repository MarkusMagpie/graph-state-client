package com.graphstate.util;

import java.io.File;
import java.sql.*;
import java.util.logging.*;

public class AppLogger {
    private static final String DB_PATH = "logs.db"; // имя sqlite бд
    private static final Logger logger = Logger.getLogger("GraphStateLogger"); // для вывода в файл
    private static Connection conn;

    static {
        try {
            // настройка java.util.logging для вывод в файл
            File logDir = new File("logs");
            if (!logDir.exists()) logDir.mkdir();

            FileHandler fh = new FileHandler("logs/app.log", true); // файл дополняется а не перезаписывается
            fh.setFormatter(new SimpleFormatter());

            logger.addHandler(fh);
            logger.setUseParentHandlers(false); // логи не выводятся в консоль
            logger.setLevel(Level.INFO);

            // connect to SQLite
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

            createTables();
        } catch (Exception e) {
            System.err.println("Ошибка инициализации логера: " + e.getMessage());
        }
    }

    private static void createTables() throws SQLException {
        String httpTable = """
            CREATE TABLE IF NOT EXISTS http_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                method TEXT,
                url TEXT,
                params TEXT,
                status INTEGER,
                duration_ms INTEGER
            )
        """;

        String infoTable = """
            CREATE TABLE IF NOT EXISTS app_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                level TEXT,
                message TEXT
            )
        """;

        try (Statement stmt = conn.createStatement()) {

            stmt.execute(httpTable);
            stmt.execute(infoTable);
        }
    }

    public static void logHttp(String method, String url, String params, int status, long durationMs) {
        String sql = "INSERT INTO http_log(method, url, params, status, duration_ms) VALUES(?,?,?,?,?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, method);
            pstmt.setString(2, url);
            pstmt.setString(3, params);
            pstmt.setInt(4, status);
            pstmt.setLong(5, durationMs);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to save http log", e);
        }

        logger.log(Level.INFO, String.format("[HTTP] %s %s -> %d (%d ms) params: %s",
                method, url, status, durationMs, params));
    }

    public static void logInfo(String message) {
        saveAppLog("INFO", message);
        logger.log(Level.INFO, message);
    }

    public static void logError(String message) {
        saveAppLog("ERROR", message);
        logger.log(Level.SEVERE, message);
    }

    public static void saveAppLog(String level, String message) {
        String sql = "INSERT INTO app_log(level, message) VALUES(?,?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, level);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to save app log", e);
        }
    }

    public static void clearDatabase() {
        String sql1 = "DELETE FROM http_log";
        String sql2 = "DELETE FROM app_log";

        String sqlSeq = "DELETE FROM sqlite_sequence WHERE name IN ('http_log','app_log')";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql1);
            stmt.executeUpdate(sql2);
            stmt.executeUpdate(sqlSeq);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to clear database", e);
        }
    }
}