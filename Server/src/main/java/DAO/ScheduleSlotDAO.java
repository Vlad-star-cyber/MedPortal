package DAO;

import DataBase.DBHelper;
import models.ScheduleSlot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ScheduleSlotDAO {

    private static final Logger LOG = Logger.getLogger(ScheduleSlotDAO.class.getName());

    public List<ScheduleSlot> findFree(long doctorId, String date) {
        String sql = "SELECT * FROM schedule_slots " +
                "WHERE doctor_id = ? AND slot_date = ?::date AND booked = false AND blocked = false " +
                "ORDER BY start_time";
        return fetchList(sql, doctorId, date);
    }

    public List<ScheduleSlot> findByDoctorAndPeriod(long doctorId, String from, String to) {
        String sql = "SELECT * FROM schedule_slots " +
                "WHERE doctor_id = ? AND slot_date BETWEEN ?::date AND ?::date " +
                "ORDER BY slot_date, start_time";
        return fetchList(sql, doctorId, from, to);
    }

    public long create(ScheduleSlot slot) {
        String sql = "INSERT INTO schedule_slots (doctor_id, slot_date, start_time, end_time) " +
                "VALUES (?, ?, ?, ?)";
        try {
            return DBHelper.getInstance().insert(sql,
                    slot.getDoctorId(), slot.getSlotDate(),
                    java.sql.Time.valueOf(slot.getStartTime()),
                    java.sql.Time.valueOf(slot.getEndTime()));
        } catch (SQLException e) {
            LOG.severe("[SlotDAO] create: " + e.getMessage());
            return -1;
        }
    }

    public void ensureSlotsExist(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(daysAhead);

        String sql = """
        INSERT INTO schedule_slots (doctor_id, slot_date, start_time, end_time, booked, blocked)
        SELECT
            d.id,
            day::date,
            start_time::time,
            (start_time::time + INTERVAL '30 minutes')::time,
            false,
            false
        FROM doctors d
        CROSS JOIN generate_series(?::date, ?::date, '1 day'::interval) AS day
        CROSS JOIN (VALUES
            ('09:00'), ('09:30'), ('10:00'), ('10:30'),
            ('11:00'), ('11:30'), ('14:00'), ('14:30'),
            ('15:00'), ('15:30'), ('16:00')
        ) AS t(start_time)
        WHERE d.active = true
          AND NOT EXISTS (
            SELECT 1 FROM schedule_slots s
            WHERE s.doctor_id = d.id
              AND s.slot_date = day::date
              AND s.start_time = t.start_time::time
          )
    """;

        try {
            DBHelper.getInstance().update(sql, today, endDate);
            LOG.info("[SlotDAO] Слоты на " + daysAhead + " дней вперёд проверены и при необходимости добавлены.");
        } catch (SQLException e) {
            LOG.severe("[SlotDAO] Ошибка при проверке/создании слотов: " + e.getMessage());
        }
    }

    public boolean setBooked(long slotId, boolean booked) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE schedule_slots SET booked = ? WHERE id = ?", booked, slotId) > 0;
        } catch (SQLException e) {
            LOG.severe("[SlotDAO] setBooked: " + e.getMessage());
            return false;
        }
    }

    public boolean setBlocked(long slotId, boolean blocked) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE schedule_slots SET blocked = ? WHERE id = ?", blocked, slotId) > 0;
        } catch (SQLException e) {
            LOG.severe("[SlotDAO] setBlocked: " + e.getMessage());
            return false;
        }
    }

    private List<ScheduleSlot> fetchList(String sql, Object... params) {
        List<ScheduleSlot> list = new ArrayList<>();
        try (ResultSet rs = DBHelper.getInstance().query(sql, params)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            LOG.severe("[SlotDAO] fetchList: " + e.getMessage());
        }
        return list;
    }

    private ScheduleSlot map(ResultSet rs) throws SQLException {
        ScheduleSlot s = new ScheduleSlot();
        s.setId(rs.getLong("id"));
        s.setDoctorId(rs.getLong("doctor_id"));
        s.setSlotDate(rs.getDate("slot_date").toLocalDate());
        s.setStartTime(rs.getTime("start_time").toLocalTime());
        s.setEndTime(rs.getTime("end_time").toLocalTime());
        s.setBooked(rs.getBoolean("booked"));
        s.setBlocked(rs.getBoolean("blocked"));
        return s;
    }
}