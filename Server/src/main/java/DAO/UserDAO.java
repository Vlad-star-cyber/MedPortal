package DAO;

import DataBase.DBHelper;
import models.Role;
import models.Session;
import models.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class UserDAO {

    private static final Logger LOG = Logger.getLogger(UserDAO.class.getName());

    public User findByLogin(String login) {
        String sql = "SELECT id, login, password_hash, salt, role, blocked, created_at " +
                "FROM users WHERE login = ?";
        try (ResultSet rs = DBHelper.getInstance().query(sql, login)) {
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            LOG.severe("[UserDAO] findByLogin: " + e.getMessage());
        }
        return null;
    }

    public User findById(long id) {
        String sql = "SELECT id, login, password_hash, salt, role, blocked, created_at " +
                "FROM users WHERE id = ?";
        try (ResultSet rs = DBHelper.getInstance().query(sql, id)) {
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            LOG.severe("[UserDAO] findById: " + e.getMessage());
        }
        return null;
    }

    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, login, password_hash, salt, role, blocked, created_at " +
                "FROM users ORDER BY id";
        try (ResultSet rs = DBHelper.getInstance().query(sql)) {
            while (rs.next()) list.add(mapUser(rs));
        } catch (SQLException e) {
            LOG.severe("[UserDAO] findAll: " + e.getMessage());
        }
        return list;
    }

    public long create(String login, String passwordHash, String salt, Role role) {
        String sql = "INSERT INTO users (login, password_hash, salt, role) VALUES (?, ?, ?, ?::role)";
        try {
            return DBHelper.getInstance().insert(sql, login, passwordHash, salt, role.name());
        } catch (SQLException e) {
            LOG.severe("[UserDAO] create: " + e.getMessage());
            return -1;
        }
    }

    public boolean setBlocked(long userId, boolean blocked) {
        String sql = "UPDATE users SET blocked = ? WHERE id = ?";
        try {
            return DBHelper.getInstance().update(sql, blocked, userId) > 0;
        } catch (SQLException e) {
            LOG.severe("[UserDAO] setBlocked: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(long userId) {
        String sql = "DELETE FROM users WHERE id = ?";
        try {
            return DBHelper.getInstance().update(sql, userId) > 0;
        } catch (SQLException e) {
            LOG.severe("[UserDAO] delete: " + e.getMessage());
            return false;
        }
    }

    public boolean changePassword(long userId, String newHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try {
            return DBHelper.getInstance().update(sql, newHash, userId) > 0;
        } catch (SQLException e) {
            LOG.severe("[UserDAO] changePassword: " + e.getMessage());
            return false;
        }
    }

    public String createSession(long userId, String ip) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusHours(24);
        String sql = "INSERT INTO sessions (user_id, token, created_at, expires_at, ip_address) " +
                "VALUES (?, ?, ?, ?, ?)";
        try {
            long id = DBHelper.getInstance().insert(sql, userId, token, now, expires, ip);
            return id > 0 ? token : null;
        } catch (SQLException e) {
            LOG.severe("[UserDAO] createSession: " + e.getMessage());
            return null;
        }
    }

    public Session findSession(String token) {
        String sql = "SELECT id, user_id, token, created_at, expires_at, ip_address " +
                "FROM sessions WHERE token = ? AND expires_at > NOW()";
        try (ResultSet rs = DBHelper.getInstance().query(sql, token)) {
            if (rs.next()) {
                Session s = new Session();
                s.setId(rs.getLong("id"));
                s.setUserId(rs.getLong("user_id"));
                s.setToken(rs.getString("token"));
                s.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                s.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
                s.setIpAddress(rs.getString("ip_address"));
                return s;
            }
        } catch (SQLException e) {
            LOG.severe("[UserDAO] findSession: " + e.getMessage());
        }
        return null;
    }

    public void deleteSession(String token) {
        String sql = "DELETE FROM sessions WHERE token = ?";
        try {
            DBHelper.getInstance().update(sql, token);
        } catch (SQLException e) {
            LOG.severe("[UserDAO] deleteSession: " + e.getMessage());
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("login"),
                rs.getString("password_hash"),
                rs.getString("salt"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("blocked"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}