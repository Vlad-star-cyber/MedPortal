package org.example;

import common.Command;
import common.CommandStatus;
import DAO.*;
import models.*;

import java.util.List;
import java.util.logging.Logger;

public class CommandHandler {

    private static final Logger LOG = Logger.getLogger(CommandHandler.class.getName());

    private final UserDAO userDAO = new UserDAO();
    private final PatientDAO patientDAO = new PatientDAO();
    private final DoctorDAO doctorDAO = new DoctorDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final ScheduleSlotDAO slotDAO = new ScheduleSlotDAO();
    private final ReminderDAO reminderDAO = new ReminderDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();
    private final VisitDetailsDAO visitDetailsDAO = new VisitDetailsDAO();

    public Command handle(Command cmd, long userId, Role role, String ip) {
        try {
            return switch (cmd.getType()) {
                case LOGIN -> handleLogin(cmd, ip);
                case REGISTER -> handleRegister(cmd, ip);
                case LOGOUT -> handleLogout(cmd);
                case CHANGE_PASSWORD -> handleChangePassword(cmd, userId);
                case GET_PROFILE -> handleGetProfile(cmd, userId);
                case GET_ALL_DOCTORS -> handleGetAllDoctors(cmd);
                case GET_DOCTORS_BY_SPEC-> handleGetDoctorsBySpec(cmd);
                case GET_DOCTOR_BY_ID -> handleGetDoctorById(cmd);
                case GET_SPECIALIZATIONS -> handleGetSpecializations(cmd);
                case GET_SLOTS -> handleGetSlots(cmd);
                case GET_DOCTOR_SCHEDULE -> handleGetDoctorSchedule(cmd);
                case BLOCK_SLOT -> handleBlockSlot(cmd, true);
                case UNBLOCK_SLOT -> handleBlockSlot(cmd, false);
                case BOOK_APPOINTMENT -> handleBookAppointment(cmd, userId, role);
                case CANCEL_APPOINTMENT -> handleCancelAppointment(cmd, userId);
                case RESCHEDULE_APPOINTMENT-> handleRescheduleAppointment(cmd);
                case CONFIRM_APPOINTMENT -> handleStatusChange(cmd, AppointmentStatus.CONFIRMED);
                case MARK_COMPLETED -> handleStatusChange(cmd, AppointmentStatus.COMPLETED);
                case MARK_MISSED -> handleStatusChange(cmd, AppointmentStatus.MISSED);
                case GET_MY_APPOINTMENTS -> handleGetMyAppointments(cmd, userId, role);
                case GET_ALL_APPOINTMENTS -> handleGetAllAppointments(cmd);
                case GET_APPOINTMENT_BY_ID -> handleGetAppointmentById(cmd);
                case GET_HISTORY -> handleGetHistory(cmd, userId, role);
                case GET_APPOINTMENT_DETAILS -> handleGetAppointmentDetails(cmd);
                case SAVE_DIAGNOSIS -> handleSaveDiagnosis(cmd, userId);
                case GET_REMINDERS -> handleGetReminders(cmd, userId, role);
                case CREATE_REMINDER -> handleCreateReminder(cmd);
                case UPDATE_REMINDER -> handleUpdateReminder(cmd);
                case CANCEL_REMINDER -> handleCancelReminder(cmd);
                case TOGGLE_REMINDER -> handleToggleReminder(cmd);
                case GET_PATIENT_BY_ID -> handleGetPatientById(cmd);
                case SEARCH_PATIENTS -> handleSearchPatients(cmd);
                case UPDATE_PATIENT -> handleUpdatePatient(cmd, userId, role);
                case GET_ALL_USERS -> handleGetAllUsers(cmd, role);
                case CREATE_USER -> handleCreateUser(cmd, role);
                case BLOCK_USER -> handleBlockUser(cmd, true,  role, userId);
                case UNBLOCK_USER -> handleBlockUser(cmd, false, role, userId);
                case DELETE_USER -> handleDeleteUser(cmd, role);
                case ADD_DOCTOR -> handleAddDoctor(cmd, role);
                case UPDATE_DOCTOR -> handleUpdateDoctor(cmd, userId, role);
                case REMOVE_DOCTOR -> handleRemoveDoctor(cmd, role);
                case GET_AUDIT_LOG -> handleGetAuditLog(cmd, role);
                case PING -> ok(cmd).message("pong").build();
                default -> error(cmd, "Неизвестная команда: " + cmd.getType());
            };
        } catch (Exception ex) {
            LOG.severe("[Handler] Необработанная ошибка: " + ex.getMessage());
            return error(cmd, "Внутренняя ошибка сервера: " + ex.getMessage());
        }
    }

    private Command handleLogin(Command cmd, String ip) {
        String login = cmd.getParam("login");
        String passHash = cmd.getParam("password");
        if (login == null || passHash == null)
            return clientError(cmd, "Не переданы логин или пароль.");

        User user = userDAO.findByLogin(login);
        if (user == null || !user.getPasswordHash().equals(passHash))
            return clientError(cmd, "Неверный логин или пароль.");
        if (user.isBlocked())
            return clientError(cmd, "Учётная запись заблокирована.");

        String token = userDAO.createSession(user.getId(), ip);
        if (token == null)
            return error(cmd, "Не удалось создать сессию.");

        String fullName = login;
        long doctorId = 0;
        String specialization = "";
        if (user.getRole() == Role.PATIENT) {
            Patient patient = patientDAO.findByUserId(user.getId());
            if (patient != null) fullName = patient.getFullName();
        } else if (user.getRole() == Role.DOCTOR) {
            Doctor doctor = doctorDAO.findByUserId(user.getId());
            if (doctor != null && doctor.getSpecialization() != null) {
                fullName = doctor.getFullName();
                doctorId = doctor.getId();
                specialization = doctor.getSpecialization().getName();
            }
        }

        audit(user.getId(), login, "LOGIN", "User",
                String.valueOf(user.getId()), "Успешный вход с " + ip, "INFO");

        return ok(cmd)
                .param("token", token)
                .param("userId", user.getId())
                .param("role", user.getRole().name())
                .param("fullName", fullName)
                .param("doctorId", doctorId)
                .param("specialization", specialization)
                .build();
    }

    private Command handleRegister(Command cmd, String ip) {
        String fullName = cmd.getParam("fullName");
        String email = cmd.getParam("email");
        String passHash = cmd.getParam("password");

        if (email == null || passHash == null || fullName == null)
            return clientError(cmd, "Заполните все обязательные поля.");

        if (userDAO.findByLogin(email) != null)
            return clientError(cmd, "Email уже зарегистрирован.");

        long userId = userDAO.create(email, passHash, "", Role.PATIENT);
        if (userId < 0)
            return error(cmd, "Не удалось создать пользователя.");

        String phone = cmd.getParam("phone", "");
        patientDAO.create(userId, fullName, phone, email);

        String token = userDAO.createSession(userId, ip);

        audit(userId, email, "REGISTER", "User", String.valueOf(userId),
                "Регистрация: " + fullName, "INFO");

        return ok(cmd)
                .param("token",  token != null ? token : "")
                .param("userId", userId)
                .param("role",   "PATIENT")
                .build();
    }

    private Command handleLogout(Command cmd) {
        String token = cmd.getSessionToken();
        if (token != null) userDAO.deleteSession(token);
        return ok(cmd).message("Сессия завершена.").build();
    }

    private Command handleChangePassword(Command cmd, long userId) {
        String oldHash = cmd.getParam("oldPassword");
        String newHash = cmd.getParam("newPassword");
        if (oldHash == null || newHash == null)
            return clientError(cmd, "Не указаны старый или новый пароль.");

        User user = userDAO.findById(userId);
        if (user == null || !user.getPasswordHash().equals(oldHash))
            return clientError(cmd, "Неверный текущий пароль.");

        boolean ok = userDAO.changePassword(userId, newHash);
        return ok ? ok(cmd).message("Пароль изменён.").build()
                : error(cmd, "Не удалось изменить пароль.");
    }

    private Command handleGetProfile(Command cmd, long userId) {
        User user = userDAO.findById(userId);
        if (user == null) return clientError(cmd, "Пользователь не найден.");

        Command.Builder b = ok(cmd)
                .param("login", user.getLogin())
                .param("role", user.getRole().name());

        if (user.getRole() == Role.PATIENT) {
            Patient p = patientDAO.findByUserId(userId);
            if (p != null) {
                b.param("fullName", p.getFullName())
                        .param("phone", p.getPhone() != null ? p.getPhone() : "")
                        .param("email", p.getEmail() != null ? p.getEmail() : "")
                        .param("policy", p.getPolicy() != null ? p.getPolicy() : "")
                        .param("birthDate", p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : "")
                        .param("allergy", p.getAllergy() != null ? p.getAllergy() : "")
                        .param("bloodType", p.getBloodType() != null ? p.getBloodType() : "");
            }
        }

        if (user.getRole() == Role.DOCTOR) {
            Doctor doctor = doctorDAO.findByUserId(userId);
            if (doctor != null) {
                b.param("doctorId", doctor.getId())
                        .param("fullName", doctor.getFullName())
                        .param("specialization", doctor.getSpecialization() != null ? doctor.getSpecialization().getName() : "");
            }
        }

        return b.build();
    }

    private Command handleGetAllDoctors(Command cmd) {
        List<Doctor> doctors = doctorDAO.findAll();
        return serializeDoctors(cmd, doctors);
    }

    private Command handleGetDoctorsBySpec(Command cmd) {
        int specId = cmd.getIntParam("specializationId");
        List<Doctor> doctors = specId > 0
                ? doctorDAO.findBySpecialization(specId)
                : doctorDAO.findAll();
        return serializeDoctors(cmd, doctors);
    }

    private Command handleGetDoctorById(Command cmd) {
        long id = Long.parseLong(cmd.getParam("doctorId", "0"));
        Doctor d = doctorDAO.findById(id);
        if (d == null) return clientError(cmd, "Врач не найден.");
        return ok(cmd).param("json", serializeDoctor(d)).build();
    }

    private Command handleGetSpecializations(Command cmd) {
        List<Specialization> specs = doctorDAO.findAllSpecializations();
        StringBuilder sb = new StringBuilder();
        for (Specialization s : specs) {
            if (sb.length() > 0) sb.append("|");
            sb.append(s.getId()).append(":").append(s.getName());
        }
        return ok(cmd).param("specializations", sb.toString()).build();
    }

    private Command handleGetSlots(Command cmd) {
        long   doctorId = Long.parseLong(cmd.getParam("doctorId", "0"));
        String date     = cmd.getParam("date");
        System.out.println("GET_SLOTS doctorId=" + doctorId + " date=" + date);
        if (doctorId == 0 || date == null) return clientError(cmd, "Не указан врач или дата.");

        List<ScheduleSlot> slots = slotDAO.findFree(doctorId, date);
        StringBuilder sb = new StringBuilder();
        for (ScheduleSlot s : slots) {
            if (sb.length() > 0) sb.append("|");
            sb.append(s.getId()).append(":").append(s.getStartTime());
        }
        return ok(cmd).param("slots", sb.toString()).build();
    }

    private Command handleGetDoctorSchedule(Command cmd) {
        long doctorId = Long.parseLong(cmd.getParam("doctorId", "0"));
        String from = cmd.getParam("from");
        String to = cmd.getParam("to");
        List<ScheduleSlot> slots = slotDAO.findByDoctorAndPeriod(doctorId, from, to);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) sb.append(",");
            ScheduleSlot s = slots.get(i);
            sb.append(String.format(
                    "{\"id\":\"%d\",\"date\":\"%s\",\"start\":\"%s\",\"end\":\"%s\"," +
                            "\"booked\":\"%s\",\"blocked\":\"%s\"}",
                    s.getId(), s.getSlotDate(), s.getStartTime(), s.getEndTime(),
                    s.isBooked(), s.isBlocked()));
        }
        sb.append("]");
        return ok(cmd).param("slots", sb.toString()).build();
    }

    private Command handleBlockSlot(Command cmd, boolean block) {
        long slotId = Long.parseLong(cmd.getParam("slotId", "0"));
        boolean ok  = block ? slotDAO.setBlocked(slotId, true) : slotDAO.setBlocked(slotId, false);
        return ok ? ok(cmd).message(block ? "Слот заблокирован." : "Слот разблокирован.").build()
                : error(cmd, "Не удалось изменить статус слота.");
    }

    private Command handleBookAppointment(Command cmd, long userId, Role role) {
        long   doctorId = Long.parseLong(cmd.getParam("doctorId", "0"));
        long   slotId   = Long.parseLong(cmd.getParam("slotId",   "0"));
        String reason   = cmd.getParam("reason", "");
        String dtStr    = cmd.getParam("datetime");

        if (doctorId == 0 || slotId == 0 || dtStr == null)
            return clientError(cmd, "Не указаны обязательные параметры.");

        long patientId;
        if (role == Role.PATIENT) {
            Patient p = patientDAO.findByUserId(userId);
            if (p == null) return clientError(cmd, "Профиль пациента не найден.");
            patientId = p.getId();
        } else {
            patientId = Long.parseLong(cmd.getParam("patientId", "0"));
        }

        java.time.LocalDateTime dt = java.time.LocalDateTime.parse(dtStr);
        long apptId = appointmentDAO.create(patientId, doctorId, slotId, dt, reason);
        if (apptId < 0) return error(cmd, "Не удалось создать запись.");

        slotDAO.setBooked(slotId, true);

        reminderDAO.create(apptId, dt.minusHours(24), 1440, "EMAIL");
        reminderDAO.create(apptId, dt.minusHours(2),   120, "EMAIL");

        audit(userId, null, "BOOK_APPOINTMENT", "Appointment", String.valueOf(apptId),
                "Запись #" + apptId + " к врачу #" + doctorId, "INFO");

        return ok(cmd).param("appointmentId", apptId).build();
    }

    private Command handleCancelAppointment(Command cmd, long userId) {
        long apptId = Long.parseLong(cmd.getParam("appointmentId", "0"));
        if (apptId == 0) return clientError(cmd, "Не указан id записи.");

        Appointment appt = appointmentDAO.findById(apptId);
        if (appt == null) return clientError(cmd, "Запись не найдена.");

        boolean ok = appointmentDAO.updateStatus(apptId, AppointmentStatus.CANCELLED);
        if (ok) {
            reminderDAO.cancelByAppointment(apptId);
            if (appt.getSlotId() > 0) {
                slotDAO.setBooked(appt.getSlotId(), false);
            }
            audit(userId, null, "CANCEL_APPOINTMENT", "Appointment",
                    String.valueOf(apptId), "Отмена записи #" + apptId, "WARN");
        }
        return ok ? ok(cmd).message("Запись отменена.").build()
                : error(cmd, "Не удалось отменить запись.");
    }

    private Command handleRescheduleAppointment(Command cmd) {
        long   apptId   = Long.parseLong(cmd.getParam("appointmentId", "0"));
        long   newSlotId= Long.parseLong(cmd.getParam("newSlotId",    "0"));
        String dtStr    = cmd.getParam("datetime");
        if (apptId == 0 || newSlotId == 0 || dtStr == null)
            return clientError(cmd, "Не указаны параметры переноса.");

        java.time.LocalDateTime newDt = java.time.LocalDateTime.parse(dtStr);
        boolean ok = appointmentDAO.reschedule(apptId, newSlotId, newDt);
        return ok ? ok(cmd).message("Запись перенесена.").build()
                : error(cmd, "Не удалось перенести запись.");
    }

    private Command handleStatusChange(Command cmd, AppointmentStatus newStatus) {
        long apptId = Long.parseLong(cmd.getParam("appointmentId", "0"));
        if (apptId == 0) return clientError(cmd, "Не указан id записи.");
        boolean ok = appointmentDAO.updateStatus(apptId, newStatus);
        return ok ? ok(cmd).message("Статус обновлён: " + newStatus).build()
                : error(cmd, "Не удалось обновить статус.");
    }

    private Command handleGetMyAppointments(Command cmd, long userId, Role role) {
        String status = cmd.getParam("status");
        List<Appointment> list;
        if (role == Role.PATIENT) {
            Patient p = patientDAO.findByUserId(userId);
            if (p == null) return ok(cmd).param("json", "[]").build();
            list = appointmentDAO.findByPatient(p.getId(), status);
        } else {
            list = appointmentDAO.findAll(null, null);
        }
        return ok(cmd).param("json", serializeAppointments(list)).build();
    }

    private Command handleGetAllAppointments(Command cmd) {
        String doctorIdStr = cmd.getParam("doctorId");
        Long   doctorId    = doctorIdStr != null ? Long.parseLong(doctorIdStr) : null;
        String date        = cmd.getParam("date");
        List<Appointment> list = appointmentDAO.findAll(doctorId, date);
        return ok(cmd).param("json", serializeAppointments(list)).build();
    }

    private Command handleGetAppointmentById(Command cmd) {
        long id = Long.parseLong(cmd.getParam("appointmentId", "0"));
        Appointment a = appointmentDAO.findById(id);
        if (a == null) return clientError(cmd, "Запись не найдена.");
        return ok(cmd).param("json", serializeAppointment(a)).build();
    }

    private Command handleGetHistory(Command cmd, long userId, Role role) {
        long patientId;
        if (role == Role.PATIENT) {
            Patient p = patientDAO.findByUserId(userId);
            if (p == null) return ok(cmd).param("json", "[]").build();
            patientId = p.getId();
        } else {
            patientId = Long.parseLong(cmd.getParam("patientId", "0"));
        }
        List<Appointment> list = appointmentDAO.findHistory(patientId);
        return ok(cmd).param("json", serializeAppointments(list)).build();
    }

    private Command handleGetAppointmentDetails(Command cmd) {
        long apptId = Long.parseLong(cmd.getParam("appointmentId", "0"));
        VisitDetails v = visitDetailsDAO.findByAppointmentId(apptId);
        if (v == null) return clientError(cmd, "Детали визита не найдены.");
        return ok(cmd).param("json", serializeVisitDetails(v)).build();
    }

    private Command handleSaveDiagnosis(Command cmd, long userId) {
        long apptId = Long.parseLong(cmd.getParam("appointmentId", "0"));
        VisitDetails v = new VisitDetails();
        v.setAppointmentId(apptId);
        v.setComplaints(cmd.getParam("complaints", ""));
        v.setExamination(cmd.getParam("examination", ""));
        v.setDiagnosisCode(cmd.getParam("diagnosisCode", ""));
        v.setDiagnosisText(cmd.getParam("diagnosisText", ""));
        v.setPrescription(cmd.getParam("prescription", ""));
        v.setNotes(cmd.getParam("notes", ""));

        long id = visitDetailsDAO.save(v);
        if (id < 0) return error(cmd, "Не удалось сохранить диагноз.");

        boolean statusUpdated = appointmentDAO.updateStatus(apptId, AppointmentStatus.COMPLETED);
        if (!statusUpdated) return error(cmd, "Не удалось обновить статус записи.");

        Appointment appt = appointmentDAO.findById(apptId);
        if (appt != null && appt.getSlotId() > 0) {
            slotDAO.setBooked(appt.getSlotId(), false);
        }

        reminderDAO.cancelByAppointment(apptId);

        audit(userId, null, "SAVE_DIAGNOSIS", "Visit", String.valueOf(apptId),
                "Диагноз сохранён: " + v.getDiagnosisCode() + ". Приём завершён.", "INFO");

        return ok(cmd).param("visitDetailsId", id).build();
    }

    private Command handleGetReminders(Command cmd, long userId, Role role) {
        String status = cmd.getParam("status");
        List<Reminder> list;
        if (role == Role.ADMIN) {
            list = reminderDAO.findAll();
        } else {
            list = reminderDAO.findByPatientUserId(userId, status);
        }
        return ok(cmd).param("json", serializeReminders(list)).build();
    }

    private Command handleCreateReminder(Command cmd) {
        long   apptId  = Long.parseLong(cmd.getParam("appointmentId", "0"));
        int    minutes = cmd.getIntParam("intervalMinutes");
        String channel = cmd.getParam("channel", "EMAIL");
        String dtStr   = cmd.getParam("sendTime");

        java.time.LocalDateTime sendTime = dtStr != null
                ? java.time.LocalDateTime.parse(dtStr)
                : java.time.LocalDateTime.now().plusMinutes(minutes);

        long id = reminderDAO.create(apptId, sendTime, minutes, channel);
        return id > 0 ? ok(cmd).param("reminderId", id).build()
                : error(cmd, "Не удалось создать напоминание.");
    }

    private Command handleUpdateReminder(Command cmd) {
        long   id      = Long.parseLong(cmd.getParam("reminderId", "0"));
        int    minutes = cmd.getIntParam("intervalMinutes");
        String channel = cmd.getParam("channel", "EMAIL");
        return reminderDAO.update(id, minutes, channel)
                ? ok(cmd).message("Напоминание обновлено.").build()
                : error(cmd, "Не удалось обновить напоминание.");
    }

    private Command handleCancelReminder(Command cmd) {
        long id = Long.parseLong(cmd.getParam("reminderId", "0"));
        return reminderDAO.cancel(id)
                ? ok(cmd).message("Напоминание отменено.").build()
                : error(cmd, "Не удалось отменить напоминание.");
    }

    private Command handleToggleReminder(Command cmd) {
        long    id      = Long.parseLong(cmd.getParam("reminderId", "0"));
        boolean enabled = Boolean.parseBoolean(cmd.getParam("enabled", "true"));
        return reminderDAO.toggle(id, enabled)
                ? ok(cmd).message("Статус напоминания изменён.").build()
                : error(cmd, "Не удалось изменить статус напоминания.");
    }

    private Command handleGetPatientById(Command cmd) {
        long id = Long.parseLong(cmd.getParam("patientId", "0"));
        Patient p = patientDAO.findById(id);
        if (p == null) return clientError(cmd, "Пациент не найден.");
        return ok(cmd).param("json", serializePatient(p)).build();
    }

    private Command handleSearchPatients(Command cmd) {
        String query = cmd.getParam("query", "");
        List<Patient> list = patientDAO.search(query);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializePatient(list.get(i)));
        }
        sb.append("]");
        return ok(cmd).param("json", sb.toString()).build();
    }

    private Command handleUpdatePatient(Command cmd, long userId, Role role) {
        Patient p;
        if (role == Role.PATIENT) {
            p = patientDAO.findByUserId(userId);
        } else {
            long patientId = Long.parseLong(cmd.getParam("patientId", "0"));
            p = patientDAO.findById(patientId);
        }

        if (p == null) return clientError(cmd, "Пациент не найден.");

        if (cmd.hasParam("fullName"))  p.setFullName(cmd.getParam("fullName"));
        if (cmd.hasParam("phone"))     p.setPhone(cmd.getParam("phone"));
        if (cmd.hasParam("email"))     p.setEmail(cmd.getParam("email"));
        if (cmd.hasParam("policy"))    p.setPolicy(cmd.getParam("policy"));
        if (cmd.hasParam("allergy"))   p.setAllergy(cmd.getParam("allergy"));
        if (cmd.hasParam("bloodType")) p.setBloodType(cmd.getParam("bloodType"));

        return patientDAO.update(p)
                ? ok(cmd).message("Данные пациента обновлены.").build()
                : error(cmd, "Не удалось обновить данные пациента.");
    }

    private Command handleGetAllUsers(Command cmd, Role role) {
        if (role != Role.ADMIN) return Command.unauthorized(cmd);
        List<User> users = userDAO.findAll();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) sb.append(",");
            User u = users.get(i);
            sb.append(String.format(
                    "{\"id\":\"%d\",\"login\":\"%s\",\"role\":\"%s\",\"blocked\":\"%s\"}",
                    u.getId(), u.getLogin(), u.getRole(), u.isBlocked()));  // boolean как строка
        }
        sb.append("]");
        return ok(cmd).param("json", sb.toString()).build();
    }

    private Command handleCreateUser(Command cmd, Role role) {
        if (role != Role.ADMIN) return Command.unauthorized(cmd);
        String login    = cmd.getParam("login");
        String passHash = cmd.getParam("password");
        String roleStr  = cmd.getParam("role", "PATIENT");
        if (login == null || passHash == null) return clientError(cmd, "Логин и пароль обязательны.");
        if (userDAO.findByLogin(login) != null) return clientError(cmd, "Логин уже занят.");
        long id = userDAO.create(login, passHash, "", Role.valueOf(roleStr));
        return id > 0 ? ok(cmd).param("userId", id).build()
                : error(cmd, "Не удалось создать пользователя.");
    }

    private Command handleBlockUser(Command cmd, boolean block, Role role, long callerId) {
        if (role != Role.ADMIN) return Command.unauthorized(cmd);
        long userId = Long.parseLong(cmd.getParam("userId", "0"));
        if (userId == callerId) return clientError(cmd, "Нельзя заблокировать самого себя.");
        boolean ok = userDAO.setBlocked(userId, block);
        if (ok) audit(callerId, null, block ? "BLOCK_USER" : "UNBLOCK_USER",
                "User", String.valueOf(userId),
                (block ? "Блокировка" : "Разблокировка") + " userId=" + userId, "WARN");
        return ok ? ok(cmd).message(block ? "Пользователь заблокирован." : "Разблокирован.").build()
                : error(cmd, "Операция не выполнена.");
    }

    private Command handleDeleteUser(Command cmd, Role role) {
        if (role != Role.ADMIN) return Command.unauthorized(cmd);
        long userId = Long.parseLong(cmd.getParam("userId", "0"));
        return userDAO.delete(userId)
                ? ok(cmd).message("Пользователь удалён.").build()
                : error(cmd, "Не удалось удалить пользователя.");
    }

    private Command handleAddDoctor(Command cmd, Role role) {
        if (role != Role.ADMIN) return Command.unauthorized(cmd);
        Doctor d = new Doctor();
        d.setFullName(cmd.getParam("fullName", ""));
        int specId = cmd.getIntParam("specializationId");
        if (specId > 0) d.setSpecialization(new Specialization(specId, ""));
        d.setOfficeNumber(cmd.getParam("officeNumber", ""));
        d.setEducation(cmd.getParam("education", ""));
        d.setAbout(cmd.getParam("about", ""));
        d.setExperienceYears(cmd.getIntParam("experienceYears"));
        d.setActive(true);
        long id = doctorDAO.create(d);
        return id > 0 ? ok(cmd).param("doctorId", id).build()
                : error(cmd, "Не удалось добавить врача.");
    }

    private Command handleUpdateDoctor(Command cmd, long userId, Role role) {
        Doctor d;
        if (role == Role.DOCTOR) {
            d = doctorDAO.findByUserId(userId);
        } else {
            long doctorId = Long.parseLong(cmd.getParam("doctorId", "0"));
            d = doctorDAO.findById(doctorId);
        }

        if (d == null) return clientError(cmd, "Врач не найден.");

        if (cmd.hasParam("fullName"))      d.setFullName(cmd.getParam("fullName"));
        if (cmd.hasParam("officeNumber"))  d.setOfficeNumber(cmd.getParam("officeNumber"));
        if (cmd.hasParam("education"))     d.setEducation(cmd.getParam("education"));
        if (cmd.hasParam("active"))        d.setActive(Boolean.parseBoolean(cmd.getParam("active")));

        return doctorDAO.update(d)
                ? ok(cmd).message("Данные врача обновлены.").build()
                : error(cmd, "Не удалось обновить врача.");
    }

    private Command handleRemoveDoctor(Command cmd, Role role) {
        if (role != Role.ADMIN) return Command.unauthorized(cmd);
        long id = Long.parseLong(cmd.getParam("doctorId", "0"));
        return doctorDAO.delete(id)
                ? ok(cmd).message("Врач удалён.").build()
                : error(cmd, "Не удалось удалить врача.");
    }

    private Command handleGetAuditLog(Command cmd, Role role) {
        if (role != Role.ADMIN) return Command.unauthorized(cmd);
        String from       = cmd.getParam("from");
        String to         = cmd.getParam("to");
        String userIdStr  = cmd.getParam("userId");
        Long   userId     = userIdStr != null ? Long.parseLong(userIdStr) : null;
        List<AuditLog> logs = auditLogDAO.findAll(from, to, userId);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) sb.append(",");
            AuditLog l = logs.get(i);
            sb.append(String.format(
                    "{\"id\":\"%d\",\"userLogin\":\"%s\",\"action\":\"%s\"," +
                            "\"entityType\":\"%s\",\"details\":\"%s\",\"level\":\"%s\",\"timestamp\":\"%s\"}",
                    l.getId(), safe(l.getUserLogin()), safe(l.getAction()),
                    safe(l.getEntityType()), safe(l.getDetails()), safe(l.getLevel()),
                    l.getTimestamp()));
        }
        sb.append("]");
        return ok(cmd).param("json", sb.toString()).build();
    }

    private Command serializeDoctors(Command cmd, List<Doctor> doctors) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < doctors.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeDoctor(doctors.get(i)));
        }
        sb.append("]");
        return ok(cmd).param("json", sb.toString()).build();
    }

    private String serializeDoctor(Doctor d) {
        return String.format(
                "{\"id\":\"%d\",\"fullName\":\"%s\",\"specialization\":\"%s\",\"specId\":\"%d\"," +
                        "\"officeNumber\":\"%s\",\"education\":\"%s\",\"about\":\"%s\"," +
                        "\"experienceYears\":\"%d\",\"active\":\"%s\",\"price\":\"%d\"}",
                d.getId(),
                safe(d.getFullName()),
                d.getSpecialization() != null ? d.getSpecialization().getName() : "",
                d.getSpecialization() != null ? d.getSpecialization().getId()   : 0,
                safe(d.getOfficeNumber()),
                safe(d.getEducation()),
                safe(d.getAbout()),
                d.getExperienceYears(),
                d.isActive(),
                d.getPrice()
        );
    }

    private String serializeAppointments(List<Appointment> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeAppointment(list.get(i)));
        }
        return sb.append("]").toString();
    }

    private String serializeAppointment(Appointment a) {
        return String.format(
                "{\"id\":\"%d\",\"patientId\":\"%d\",\"doctorId\":\"%d\",\"slotId\":\"%d\"," +
                        "\"datetime\":\"%s\",\"status\":\"%s\",\"reason\":\"%s\"," +
                        "\"patientName\":\"%s\",\"doctorName\":\"%s\",\"specialization\":\"%s\"," +
                        "\"diagnosis\":\"%s\",\"prescription\":\"%s\"}",
                a.getId(), a.getPatientId(), a.getDoctorId(), a.getSlotId(),
                a.getAppointmentDatetime(), a.getStatus().name(),
                safe(a.getReason()), safe(a.getPatientName()),
                safe(a.getDoctorName()), safe(a.getSpecialization()),
                safe(a.getDiagnosis()), safe(a.getPrescription()));
    }

    private String serializeReminders(List<Reminder> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Reminder r = list.get(i);
            sb.append(String.format(
                    "{\"id\":\"%d\",\"appointmentId\":\"%d\",\"scheduledSendTime\":\"%s\"," +
                            "\"intervalMinutes\":\"%d\",\"channel\":\"%s\",\"status\":\"%s\"," +
                            "\"enabled\":\"%s\",\"patientName\":\"%s\",\"doctorName\":\"%s\"," +
                            "\"specialization\":\"%s\",\"appointmentDatetime\":\"%s\"}",
                    r.getId(), r.getAppointmentId(), r.getScheduledSendTime(),
                    r.getIntervalMinutes(), r.getChannel(), r.getStatus().name(),
                    r.isEnabled(),                     // "true"/"false"
                    safe(r.getPatientName()), safe(r.getDoctorName()),
                    safe(r.getSpecialization()), r.getAppointmentDatetime()));
        }
        return sb.append("]").toString();
    }

    private String serializePatient(Patient p) {
        return String.format(
                "{\"id\":\"%d\",\"userId\":\"%d\",\"fullName\":\"%s\",\"phone\":\"%s\"," +
                        "\"email\":\"%s\",\"dateOfBirth\":\"%s\",\"policy\":\"%s\"," +
                        "\"allergy\":\"%s\",\"bloodType\":\"%s\"}",
                p.getId(), p.getUserId(), safe(p.getFullName()), safe(p.getPhone()),
                safe(p.getEmail()), p.getDateOfBirth() != null ? p.getDateOfBirth() : "",
                safe(p.getPolicy()), safe(p.getAllergy()), safe(p.getBloodType()));
    }

    private String serializeVisitDetails(VisitDetails v) {
        return String.format(
                "{\"id\":\"%d\",\"appointmentId\":\"%d\",\"complaints\":\"%s\"," +
                        "\"examination\":\"%s\",\"diagnosisCode\":\"%s\",\"diagnosisText\":\"%s\"," +
                        "\"prescription\":\"%s\",\"notes\":\"%s\"}",
                v.getId(), v.getAppointmentId(),
                safe(v.getComplaints()), safe(v.getExamination()),
                safe(v.getDiagnosisCode()), safe(v.getDiagnosisText()),
                safe(v.getPrescription()), safe(v.getNotes()));
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void audit(long userId, String login, String action,
                       String entityType, String entityId, String details, String level) {
        try {
            auditLogDAO.log(new AuditLog(userId, login != null ? login : "system",
                    action, entityType, entityId, details, level));
        } catch (Exception ignored) {}
    }

    private Command.Builder ok(Command cmd) {
        return Command.response(cmd, CommandStatus.OK);
    }

    private Command clientError(Command cmd, String msg) {
        return Command.response(cmd, CommandStatus.CLIENT_ERROR).message(msg).build();
    }

    private Command error(Command cmd, String msg) {
        return Command.response(cmd, CommandStatus.SERVER_ERROR).message(msg).build();
    }
}