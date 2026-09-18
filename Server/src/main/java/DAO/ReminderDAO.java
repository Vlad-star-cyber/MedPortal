package DAO;

import DataBase.DBHelper;
import models.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ReminderDAO {

    private static final Logger LOG = Logger.getLogger(ReminderDAO.class.getName());

    private static final String BASE_SELECT =
            "SELECT r.*, " +
                    "       p.email AS patient_email, p.full_name AS patient_name, " +
                    "       d.full_name AS doctor_name, s.name AS specialization, " +
                    "       a.appointment_datetime " +
                    "FROM reminders r " +
                    "JOIN appointments a ON r.appointment_id = a.id " +
                    "JOIN patients p ON a.patient_id = p.id " +
                    "JOIN doctors  d ON a.doctor_id  = d.id " +
                    "LEFT JOIN specializations s ON d.specialization_id = s.id ";

    public List<Reminder> findByPatientUserId(long userId, String status) {
        String sql = BASE_SELECT +
                "WHERE p.user_id = ? " +
                (status != null ? "AND r.status = ? " : "") +
                "ORDER BY r.scheduled_send_time";
        Object[] params = status != null ? new Object[]{userId, status} : new Object[]{userId};
        return fetchList(sql, params);
    }

    public List<Reminder> findDueReminders() {
        String sql = BASE_SELECT +
                "WHERE r.status = 'SCHEDULED' AND r.enabled = true " +
                "AND r.scheduled_send_time <= NOW() " +
                "ORDER BY r.scheduled_send_time";
        return fetchList(sql);
    }

    public long create(long appointmentId, LocalDateTime sendTime,
                       int intervalMinutes, String channel) {
        String sql = "INSERT INTO reminders " +
                "(appointment_id, scheduled_send_time, interval_minutes, channel) " +
                "VALUES (?, ?, ?, ?)";
        try {
            return DBHelper.getInstance().insert(sql,
                    appointmentId, sendTime, intervalMinutes, channel);
        } catch (SQLException e) {
            LOG.severe("[ReminderDAO] create: " + e.getMessage());
            return -1;
        }
    }

    public boolean update(long reminderId, int intervalMinutes, String channel) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE reminders SET interval_minutes = ?, channel = ? WHERE id = ?",
                    intervalMinutes, channel, reminderId) > 0;
        } catch (SQLException e) {
            LOG.severe("[ReminderDAO] update: " + e.getMessage());
            return false;
        }
    }

    public boolean cancel(long reminderId) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE reminders SET status = 'CANCELLED' WHERE id = ?",
                    reminderId) > 0;
        } catch (SQLException e) {
            LOG.severe("[ReminderDAO] cancel: " + e.getMessage());
            return false;
        }
    }

    public boolean toggle(long reminderId, boolean enabled) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE reminders SET enabled = ? WHERE id = ?",
                    enabled, reminderId) > 0;
        } catch (SQLException e) {
            LOG.severe("[ReminderDAO] toggle: " + e.getMessage());
            return false;
        }
    }

    public boolean markSent(long reminderId) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE reminders SET status = 'SENT', actual_send_time = NOW() WHERE id = ?",
                    reminderId) > 0;
        } catch (SQLException e) {
            LOG.severe("[ReminderDAO] markSent: " + e.getMessage());
            return false;
        }
    }

    public void cancelByAppointment(long appointmentId) {
        try {
            DBHelper.getInstance().update(
                    "UPDATE reminders SET status = 'CANCELLED' WHERE appointment_id = ? AND status = 'SCHEDULED'",
                    appointmentId);
        } catch (SQLException e) {
            LOG.severe("[ReminderDAO] cancelByAppointment: " + e.getMessage());
        }
    }

    private List<Reminder> fetchList(String sql, Object... params) {
        List<Reminder> list = new ArrayList<>();
        try (ResultSet rs = DBHelper.getInstance().query(sql, params)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            LOG.severe("[ReminderDAO] fetchList: " + e.getMessage());
        }
        return list;
    }

    public List<Reminder> findAll() {
        String sql = BASE_SELECT + "ORDER BY r.scheduled_send_time DESC";
        return fetchList(sql);
    }

    private Reminder map(ResultSet rs) throws SQLException {
        Reminder r = new Reminder();
        r.setId(rs.getLong("id"));
        r.setAppointmentId(rs.getLong("appointment_id"));
        r.setScheduledSendTime(rs.getTimestamp("scheduled_send_time").toLocalDateTime());
        r.setIntervalMinutes(rs.getInt("interval_minutes"));
        r.setChannel(rs.getString("channel"));
        r.setStatus(ReminderStatus.valueOf(rs.getString("status")));
        r.setEnabled(rs.getBoolean("enabled"));
        java.sql.Timestamp ast = rs.getTimestamp("actual_send_time");
        if (ast != null) r.setActualSendTime(ast.toLocalDateTime());
        r.setPatientEmail(rs.getString("patient_email"));
        r.setPatientName(rs.getString("patient_name"));
        r.setDoctorName(rs.getString("doctor_name"));
        r.setSpecialization(rs.getString("specialization"));
        r.setAppointmentDatetime(rs.getTimestamp("appointment_datetime").toLocalDateTime());
        return r;
    }
}