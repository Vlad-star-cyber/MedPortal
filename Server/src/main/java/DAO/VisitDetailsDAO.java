package DAO;

import DataBase.DBHelper;
import models.VisitDetails;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

public class VisitDetailsDAO {

    private static final Logger LOG = Logger.getLogger(VisitDetailsDAO.class.getName());

    public VisitDetails findByAppointmentId(long appointmentId) {
        String sql = "SELECT * FROM visit_details WHERE appointment_id = ?";
        try (ResultSet rs = DBHelper.getInstance().query(sql, appointmentId)) {
            if (rs.next()) return map(rs);
        } catch (SQLException e) {
            LOG.severe("[VisitDetailsDAO] findByAppointmentId: " + e.getMessage());
        }
        return null;
    }

    public long save(VisitDetails v) {
        VisitDetails existing = findByAppointmentId(v.getAppointmentId());
        if (existing != null) {
            String sql = "UPDATE visit_details SET complaints=?, examination=?, " +
                    "diagnosis_code=?, diagnosis_text=?, prescription=?, notes=? " +
                    "WHERE appointment_id=?";
            try {
                DBHelper.getInstance().update(sql,
                        v.getComplaints(), v.getExamination(),
                        v.getDiagnosisCode(), v.getDiagnosisText(),
                        v.getPrescription(), v.getNotes(),
                        v.getAppointmentId());
                return existing.getId();
            } catch (SQLException e) {
                LOG.severe("[VisitDetailsDAO] update: " + e.getMessage());
                return -1;
            }
        } else {
            String sql = "INSERT INTO visit_details " +
                    "(appointment_id, complaints, examination, diagnosis_code, " +
                    "diagnosis_text, prescription, notes) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try {
                return DBHelper.getInstance().insert(sql,
                        v.getAppointmentId(), v.getComplaints(), v.getExamination(),
                        v.getDiagnosisCode(), v.getDiagnosisText(),
                        v.getPrescription(), v.getNotes());
            } catch (SQLException e) {
                LOG.severe("[VisitDetailsDAO] insert: " + e.getMessage());
                return -1;
            }
        }
    }

    private VisitDetails map(ResultSet rs) throws SQLException {
        VisitDetails v = new VisitDetails();
        v.setId(rs.getLong("id"));
        v.setAppointmentId(rs.getLong("appointment_id"));
        v.setComplaints(rs.getString("complaints"));
        v.setExamination(rs.getString("examination"));
        v.setDiagnosisCode(rs.getString("diagnosis_code"));
        v.setDiagnosisText(rs.getString("diagnosis_text"));
        v.setPrescription(rs.getString("prescription"));
        v.setNotes(rs.getString("notes"));
        v.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return v;
    }
}