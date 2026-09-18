package DAO;

import DataBase.DBHelper;
import models.Doctor;
import models.Specialization;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class DoctorDAO {

    private static final Logger LOG = Logger.getLogger(DoctorDAO.class.getName());

    public List<Doctor> findAll() {
        return fetchList(
                "SELECT d.*, s.name AS spec_name FROM doctors d " +
                        "LEFT JOIN specializations s ON d.specialization_id = s.id ORDER BY d.full_name");
    }

    public List<Doctor> findBySpecialization(int specId) {
        return fetchList(
                "SELECT d.*, s.name AS spec_name FROM doctors d " +
                        "LEFT JOIN specializations s ON d.specialization_id = s.id " +
                        "WHERE d.specialization_id = ? AND d.active = true ORDER BY d.full_name", specId);
    }

    public Doctor findById(long id) {
        String sql = "SELECT d.*, s.name AS spec_name FROM doctors d " +
                "LEFT JOIN specializations s ON d.specialization_id = s.id WHERE d.id = ?";
        try (ResultSet rs = DBHelper.getInstance().query(sql, id)) {
            if (rs.next()) return map(rs);
        } catch (SQLException e) {
            LOG.severe("[DoctorDAO] findById: " + e.getMessage());
        }
        return null;
    }

    public long create(Doctor d) {
        String sql = "INSERT INTO doctors (user_id, full_name, specialization_id, " +
                "office_number, education, about, experience_years, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            Integer specId = d.getSpecialization() != null ? d.getSpecialization().getId() : null;
            return DBHelper.getInstance().insert(sql,
                    d.getUserId() > 0 ? d.getUserId() : null,
                    d.getFullName(), specId,
                    d.getOfficeNumber(), d.getEducation(), d.getAbout(),
                    d.getExperienceYears(), d.isActive());
        } catch (SQLException e) {
            LOG.severe("[DoctorDAO] create: " + e.getMessage());
            return -1;
        }
    }

    public boolean update(Doctor d) {
        String sql = "UPDATE doctors SET full_name=?, specialization_id=?, office_number=?, " +
                "education=?, about=?, experience_years=?, active=? WHERE id=?";
        try {
            Integer specId = d.getSpecialization() != null ? d.getSpecialization().getId() : null;
            return DBHelper.getInstance().update(sql,
                    d.getFullName(), specId, d.getOfficeNumber(),
                    d.getEducation(), d.getAbout(), d.getExperienceYears(),
                    d.isActive(), d.getId()) > 0;
        } catch (SQLException e) {
            LOG.severe("[DoctorDAO] update: " + e.getMessage());
            return false;
        }
    }

    public boolean setActive(long doctorId, boolean active) {
        try {
            return DBHelper.getInstance().update(
                    "UPDATE doctors SET active = ? WHERE id = ?", active, doctorId) > 0;
        } catch (SQLException e) {
            LOG.severe("[DoctorDAO] setActive: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(long doctorId) {
        try {
            return DBHelper.getInstance().update("DELETE FROM doctors WHERE id = ?", doctorId) > 0;
        } catch (SQLException e) {
            LOG.severe("[DoctorDAO] delete: " + e.getMessage());
            return false;
        }
    }

    public List<Specialization> findAllSpecializations() {
        List<Specialization> list = new ArrayList<>();
        try (ResultSet rs = DBHelper.getInstance().query(
                "SELECT id, name FROM specializations ORDER BY name")) {
            while (rs.next())
                list.add(new Specialization(rs.getInt("id"), rs.getString("name")));
        } catch (SQLException e) {
            LOG.severe("[DoctorDAO] findAllSpecializations: " + e.getMessage());
        }
        return list;
    }

    private List<Doctor> fetchList(String sql, Object... params) {
        List<Doctor> list = new ArrayList<>();
        try (ResultSet rs = DBHelper.getInstance().query(sql, params)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            LOG.severe("[DoctorDAO] fetchList: " + e.getMessage());
        }
        return list;
    }

    private Doctor map(ResultSet rs) throws SQLException {
        Doctor d = new Doctor();
        d.setId(rs.getLong("id"));
        long uid = rs.getLong("user_id");
        if (!rs.wasNull()) d.setUserId(uid);
        d.setFullName(rs.getString("full_name"));
        int specId = rs.getInt("specialization_id");
        if (!rs.wasNull()) {
            d.setSpecialization(new Specialization(specId, rs.getString("spec_name")));
        }
        d.setOfficeNumber(rs.getString("office_number"));
        d.setEducation(rs.getString("education"));
        d.setAbout(rs.getString("about"));
        d.setExperienceYears(rs.getInt("experience_years"));
        d.setActive(rs.getBoolean("active"));
        d.setPrice(rs.getInt("price"));
        return d;
    }

    public Doctor findByUserId(long userId) {
        String sql = "SELECT d.*, s.name AS spec_name FROM doctors d " +
                "LEFT JOIN specializations s ON d.specialization_id = s.id " +
                "WHERE d.user_id = ?";
        try (ResultSet rs = DBHelper.getInstance().query(sql, userId)) {
            if (rs.next()) return map(rs);
        } catch (SQLException e) {
            LOG.severe("[DoctorDAO] findByUserId: " + e.getMessage());
        }
        return null;
    }
}