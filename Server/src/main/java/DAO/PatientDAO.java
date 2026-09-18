package DAO;

import DataBase.DBHelper;
import models.Patient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class PatientDAO {

    private static final Logger LOG = Logger.getLogger(PatientDAO.class.getName());

    public Patient findByUserId(long userId) {
        String sql = "SELECT * FROM patients WHERE user_id = ?";
        try (ResultSet rs = DBHelper.getInstance().query(sql, userId)) {
            if (rs.next()) return map(rs);
        } catch (SQLException e) {
            LOG.severe("[PatientDAO] findByUserId: " + e.getMessage());
        }
        return null;
    }

    public Patient findById(long id) {
        String sql = "SELECT * FROM patients WHERE id = ?";
        try (ResultSet rs = DBHelper.getInstance().query(sql, id)) {
            if (rs.next()) return map(rs);
        } catch (SQLException e) {
            LOG.severe("[PatientDAO] findById: " + e.getMessage());
        }
        return null;
    }

    public List<Patient> search(String query) {
        List<Patient> list = new ArrayList<>();
        String q   = "%" + query.toLowerCase() + "%";
        String sql = "SELECT * FROM patients WHERE LOWER(full_name) LIKE ? " +
                "OR LOWER(phone) LIKE ? OR LOWER(policy) LIKE ? ORDER BY full_name";
        try (ResultSet rs = DBHelper.getInstance().query(sql, q, q, q)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            LOG.severe("[PatientDAO] search: " + e.getMessage());
        }
        return list;
    }

    public long create(long userId, String fullName, String phone, String email) {
        String sql = "INSERT INTO patients (user_id, full_name, phone, email) VALUES (?, ?, ?, ?)";
        try {
            return DBHelper.getInstance().insert(sql, userId, fullName, phone, email);
        } catch (SQLException e) {
            LOG.severe("[PatientDAO] create: " + e.getMessage());
            return -1;
        }
    }

    public boolean update(Patient p) {
        String sql = "UPDATE patients SET full_name=?, phone=?, email=?, date_of_birth=?, " +
                "policy=?, allergy=?, blood_type=? WHERE id=?";
        try {
            return DBHelper.getInstance().update(sql,
                    p.getFullName(), p.getPhone(), p.getEmail(), p.getDateOfBirth(),
                    p.getPolicy(), p.getAllergy(), p.getBloodType(), p.getId()) > 0;
        } catch (SQLException e) {
            LOG.severe("[PatientDAO] update: " + e.getMessage());
            return false;
        }
    }

    private Patient map(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setId(rs.getLong("id"));
        p.setUserId(rs.getLong("user_id"));
        p.setFullName(rs.getString("full_name"));
        p.setPhone(rs.getString("phone"));
        p.setEmail(rs.getString("email"));
        java.sql.Date dob = rs.getDate("date_of_birth");
        if (dob != null) p.setDateOfBirth(dob.toLocalDate());
        p.setPolicy(rs.getString("policy"));
        p.setAllergy(rs.getString("allergy"));
        p.setBloodType(rs.getString("blood_type"));
        return p;
    }
}