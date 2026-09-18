package DAO;

import DataBase.DBHelper;
import models.Appointment;
import models.AppointmentStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AppointmentDAO {

    private static final Logger LOG = Logger.getLogger(AppointmentDAO.class.getName());

    private static final String BASE_SELECT =
            "SELECT a.id, a.patient_id, a.doctor_id, a.slot_id, a.appointment_datetime, " +
                    "       a.status, a.reason, a.created_at, " +
                    "       p.full_name AS patient_name, d.full_name AS doctor_name, " +
                    "       s.name AS specialization " +
                    "FROM appointments a " +
                    "LEFT JOIN patients p ON a.patient_id = p.id " +
                    "LEFT JOIN doctors  d ON a.doctor_id  = d.id " +
                    "LEFT JOIN specializations s ON d.specialization_id = s.id ";

    public Appointment findById(long id) {
        try (ResultSet rs = DBHelper.getInstance().query(BASE_SELECT + "WHERE a.id = ?", id)) {
            if (rs.next()) return map(rs);
        } catch (SQLException e) {
            LOG.severe("[AppointmentDAO] findById: " + e.getMessage());
        }
        return null;
    }

    public List<Appointment> findByPatient(long patientId, String status) {
        String sql = BASE_SELECT + "WHERE a.patient_id = ? " +
                (status != null ? "AND a.status = ? " : "") +
                "ORDER BY a.appointment_datetime DESC";
        Object[] params = status != null ? new Object[]{patientId, status}
                : new Object[]{patientId};
        return fetchList(sql, params);
    }

    public List<Appointment> findAll(Long doctorId, String date) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (doctorId != null) { sql.append("AND a.doctor_id = ? ");       params.add(doctorId); }
        if (date     != null) { sql.append("AND DATE(a.appointment_datetime) = ?::date "); params.add(date); }
        sql.append("ORDER BY a.appointment_datetime");
        return fetchList(sql.toString(), params.toArray());
    }

    public List<Appointment> findHistory(long patientId) {
        String sql =
                "SELECT a.id, a.patient_id, a.doctor_id, a.slot_id, a.appointment_datetime, " +
                        "       a.status, a.reason, a.created_at, " +
                        "       p.full_name AS patient_name, d.full_name AS doctor_name, " +
                        "       s.name AS specialization, " +
                        "       vd.diagnosis_text AS diagnosis, vd.prescription AS prescription " +
                        "FROM appointments a " +
                        "LEFT JOIN patients p ON a.patient_id = p.id " +
                        "LEFT JOIN doctors  d ON a.doctor_id  = d.id " +
                        "LEFT JOIN specializations s ON d.specialization_id = s.id " +
                        "LEFT JOIN visit_details vd ON a.id = vd.appointment_id " +
                        "WHERE a.patient_id = ? AND a.status IN ('COMPLETED', 'MISSED') " +
                        "ORDER BY a.appointment_datetime DESC";
        return fetchList(sql, patientId);
    }

    public long create(long patientId, long doctorId, long slotId,
                       java.time.LocalDateTime dt, String reason) {
        String sql = "INSERT INTO appointments " +
                "(patient_id, doctor_id, slot_id, appointment_datetime, status, reason) " +
                "VALUES (?, ?, ?, ?, 'PENDING', ?)";
        try {
            return DBHelper.getInstance().insert(sql, patientId, doctorId, slotId, dt, reason);
        } catch (SQLException e) {
            LOG.severe("[AppointmentDAO] create: " + e.getMessage());
            return -1;
        }
    }

    public boolean updateStatus(long id, AppointmentStatus status) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE appointments SET status = ? WHERE id = ?",
                    status.name(), id) > 0;
        } catch (SQLException e) {
            LOG.severe("[AppointmentDAO] updateStatus: " + e.getMessage());
            return false;
        }
    }

    public boolean reschedule(long id, long newSlotId, java.time.LocalDateTime newDt) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE appointments SET slot_id = ?, appointment_datetime = ? WHERE id = ?",
                    newSlotId, newDt, id) > 0;
        } catch (SQLException e) {
            LOG.severe("[AppointmentDAO] reschedule: " + e.getMessage());
            return false;
        }
    }

    private List<Appointment> fetchList(String sql, Object... params) {
        List<Appointment> list = new ArrayList<>();
        try (ResultSet rs = DBHelper.getInstance().query(sql, params)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            LOG.severe("[AppointmentDAO] fetchList: " + e.getMessage());
        }
        return list;
    }

    private Appointment map(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setId(rs.getLong("id"));
        a.setPatientId(rs.getLong("patient_id"));
        a.setDoctorId(rs.getLong("doctor_id"));
        a.setSlotId(rs.getLong("slot_id"));
        a.setAppointmentDatetime(rs.getTimestamp("appointment_datetime").toLocalDateTime());
        a.setStatus(AppointmentStatus.valueOf(rs.getString("status")));
        a.setReason(rs.getString("reason"));
        a.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        a.setPatientName(rs.getString("patient_name"));
        a.setDoctorName(rs.getString("doctor_name"));
        a.setSpecialization(rs.getString("specialization"));
        try {
            a.setDiagnosis(rs.getString("diagnosis"));
            a.setPrescription(rs.getString("prescription"));
        } catch (SQLException e) {
            a.setDiagnosis("—");
            a.setPrescription("—");
        }
        return a;
    }
}
