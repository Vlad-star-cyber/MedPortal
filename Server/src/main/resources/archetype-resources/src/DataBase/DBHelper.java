package DataBase;

import java.sql.*;
import java.util.logging.Logger;

public class DBHelper {

    private static final Logger LOG = Logger.getLogger(DBHelper.class.getName());

    private static final String URL = "jdbc:postgresql://127.0.0.1:1521/Kursach_ProgSP";
    private static final String USER = "postgres";
    private static final String PASSWORD = "1234";

    private static DBHelper instance;
    private Connection connection;

    private DBHelper() {
        connect();
    }

    public static synchronized DBHelper getInstance() {
        if (instance == null) instance = new DBHelper();
        return instance;
    }

    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(3)) {
                LOG.info("[DB] Переподключение к базе данных...");
                connect();
            }
        } catch (SQLException e) {
            LOG.severe("[DB] Ошибка проверки соединения: " + e.getMessage());
            connect();
        }
        return connection;
    }

    private void connect() {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            connection.setAutoCommit(true);
            LOG.info("[DB] Соединение с БД установлено.");
        } catch (Exception e) {
            LOG.severe("[DB] Не удалось подключиться: " + e.getMessage());
            throw new RuntimeException("Ошибка подключения к БД", e);
        }
    }

    public ResultSet query(String sql, Object... params) throws SQLException {
        PreparedStatement ps = prepare(sql, params);
        return ps.executeQuery();
    }

    public int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(sql, params)) {
            return ps.executeUpdate();
        }
    }

    public long insert(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParams(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement(sql);
        bindParams(ps, params);
        return ps;
    }

    private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) ps.setNull(i + 1, Types.NULL);
            else if (params[i] instanceof String  s) ps.setString(i + 1, s);
            else if (params[i] instanceof Integer iv) ps.setInt(i + 1, iv);
            else if (params[i] instanceof Long    lv) ps.setLong(i + 1, lv);
            else if (params[i] instanceof Boolean bv) ps.setBoolean(i + 1, bv);
            else if (params[i] instanceof Double  dv) ps.setDouble(i + 1, dv);
            else if (params[i] instanceof java.sql.Timestamp ts) ps.setTimestamp(i + 1, ts);
            else if (params[i] instanceof java.sql.Date      dt) ps.setDate(i + 1, dt);
            else if (params[i] instanceof java.time.LocalDate ld)
                ps.setDate(i + 1, java.sql.Date.valueOf(ld));
            else if (params[i] instanceof java.time.LocalDateTime ldt)
                ps.setTimestamp(i + 1, java.sql.Timestamp.valueOf(ldt));
            else ps.setObject(i + 1, params[i]);
        }
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException ignored) {}
    }
}
