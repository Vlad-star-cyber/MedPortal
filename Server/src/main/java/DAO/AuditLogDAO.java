package DAO;

import DataBase.DBHelper;
import models.AuditLog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AuditLogDAO {

    private static final Logger LOG = Logger.getLogger(AuditLogDAO.class.getName());

    public void log(AuditLog entry) {
        String sql = "INSERT INTO audit_log " +
                "(user_id, user_login, action, entity_type, entity_id, details, level) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            DBHelper.getInstance().insert(sql,
                    entry.getUserId(), entry.getUserLogin(),
                    entry.getAction(), entry.getEntityType(),
                    entry.getEntityId(), entry.getDetails(), entry.getLevel());
        } catch (SQLException e) {
            LOG.severe("[AuditLogDAO] log: " + e.getMessage());
        }
    }

    public List<AuditLog> findAll(String from, String to, Long userId) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM audit_log WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (from   != null) { sql.append("AND timestamp >= ? "); params.add(from); }
        if (to     != null) { sql.append("AND timestamp <= ? "); params.add(to);   }
        if (userId != null) { sql.append("AND user_id = ? ");    params.add(userId);}
        sql.append("ORDER BY timestamp DESC LIMIT 1000");
        List<AuditLog> list = new ArrayList<>();
        try (ResultSet rs = DBHelper.getInstance().query(sql.toString(), params.toArray())) {
            while (rs.next()) {
                AuditLog a = new AuditLog();
                a.setId(rs.getLong("id"));
                a.setUserId(rs.getLong("user_id"));
                a.setUserLogin(rs.getString("user_login"));
                a.setAction(rs.getString("action"));
                a.setEntityType(rs.getString("entity_type"));
                a.setEntityId(rs.getString("entity_id"));
                a.setDetails(rs.getString("details"));
                a.setLevel(rs.getString("level"));
                a.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                list.add(a);
            }
        } catch (SQLException e) {
            LOG.severe("[AuditLogDAO] findAll: " + e.getMessage());
        }
        return list;
    }
}