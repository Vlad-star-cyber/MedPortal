package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import org.example.common.Command;
import org.example.common.CommandType;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javafx.stage.FileChooser;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class AdminMain extends Application {

    private static final String ACCENT = "#3B82F6";
    private static final String BG_PAGE = "#F5F7FA";
    private static final String CARD_BG = "#FFFFFF";
    private static final String TEXT_PRIMARY = "#1A1A2E";
    private static final String TEXT_SEC = "#64748B";
    private static final String TEXT_MUTED = "#94A3B8";
    private static final String BORDER = "#E8EDF2";
    private static final String GREEN = "#10B981";
    private static final String GREEN_SOFT = "#D1FAE5";
    private static final String ORANGE = "#F59E0B";
    private static final String ORANGE_SOFT = "#FEF3C7";
    private static final String RED = "#EF4444";
    private static final String RED_SOFT = "#FEE2E2";
    private static final String BLUE_SOFT = "#DBEAFE";
    private static final String GREY_SOFT = "#F1F5F9";
    private static final String PURPLE = "#8B5CF6";
    private static final String PURPLE_SOFT = "#EDE9FE";

    private TextField logSearchField;
    private ComboBox<String> logLevelFilter;
    private VBox logsTableContainer;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Socket socket;
    private final Object sendLock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "admin-loader");
        t.setDaemon(true);
        return t;
    });

    public void setNetworkStreams(ObjectOutputStream out, ObjectInputStream in, Socket socket) {
        this.out = out;
        this.in = in;
        this.socket = socket;
    }

    private String currentAdminName = "Администратор";
    private String sessionToken;
    private long currentUserId;

    private String activePage = "appointments";
    private BorderPane root;
    private VBox sidebar;
    private final Map<String, HBox> navItems = new HashMap<>();

    private ComboBox<String> userRoleFilter;
    private ComboBox<String> userStatusFilter;
    private TextField userSearchField;
    private VBox usersTableContainer;

    private TextField appointmentSearchField;
    private ComboBox<String> appointmentStatusFilter;
    private ComboBox<String> appointmentSpecFilter;
    private VBox appointmentsTableContainer;

    private final ObservableList<DoctorItem> manualDoctors = FXCollections.observableArrayList();
    private ComboBox<DoctorItem> docCombo;
    private DatePicker manualDatePicker;
    private ComboBox<Doctors.SlotOption> manualTimeCombo;
    private TextField manualPatientField;

    private static class DoctorItem {
        long id;
        String fullName;
        String spec;

        DoctorItem(long id, String fullName, String spec) {
            this.id = id;
            this.fullName = fullName;
            this.spec = spec;
        }

        @Override
        public String toString() {
            String[] parts = fullName.split(" ");
            String shortName = parts.length >= 2
                    ? parts[0] + " " + parts[1].charAt(0) + "." +
                    (parts.length > 2 ? parts[2].charAt(0) + "." : "")
                    : fullName;
            return shortName + " (" + spec + ")";
        }
    }

    private ComboBox<DoctorItem> doctorCombo;
    private DoctorItem selectedDoctor;
    private Button newPatientBtn;

    private static class AppointmentRow {
        String date, time, patient, doctor, spec, status;
        String statusColor, statusBg;
    }

    private static class UserRow {
        long userId;
        String name, login, role, status;
        String statusColor, statusBg, created;
    }

    private static class DoctorRow {
        String name, spec, office, education, experience;
        boolean active;
    }

    private static class ReminderRow {
        String patient, doctor, visitDate, sendTime, channel, status;
        String statusColor, statusBg;
    }

    private static class LogRow {
        String time, user, action, entity, details, level;
        String levelColor, levelBg;
    }

    private static class PatientOption {
        long patientId;
        String fullName;

        PatientOption(long patientId, String fullName) {
            this.patientId = patientId;
            this.fullName = fullName;
        }

        @Override
        public String toString() {
            return fullName;
        }
    }

    private final ObservableList<PatientOption> manualPatients = FXCollections.observableArrayList();
    private ComboBox<PatientOption> patientCombo;

    private final ObservableList<AppointmentRow> allAppointments = FXCollections.observableArrayList();
    private final ObservableList<UserRow> allUsers = FXCollections.observableArrayList();
    private final ObservableList<DoctorRow> allDoctors = FXCollections.observableArrayList();
    private final ObservableList<ReminderRow> allReminders = FXCollections.observableArrayList();
    private final ObservableList<LogRow> allLogs = FXCollections.observableArrayList();
    private final ObservableList<DoctorItem> doctorList = FXCollections.observableArrayList();

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        root.setCenter(buildPage(activePage));

        Scene scene = new Scene(root, 1260, 780);
        stage.setTitle("MedPortal — Администратор");
        stage.setScene(scene);
        stage.setMinWidth(1050);
        stage.setMinHeight(640);
        stage.show();

        if (sessionToken != null && out != null) {
            loadAllData();
            loadDoctorsForAdmin();
            loadPatientsForManual();
        }
    }

    public void setUserSession(String fullName, String token, long userId) {
        this.currentAdminName = fullName;
        this.sessionToken = token;
        this.currentUserId = userId;
    }

    private void loadPatientsForManual() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.SEARCH_PATIENTS)
                        .token(sessionToken)
                        .param("query", "")
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<PatientOption> list = new ArrayList<>();
                    if (json != null && !json.isBlank()) {
                        json = json.trim();
                        if (json.startsWith("[")) json = json.substring(1);
                        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
                        if (!json.isBlank()) {
                            String[] parts = json.split("},\\s*\\{");
                            for (String part : parts) {
                                part = part.trim();
                                if (!part.startsWith("{")) part = "{" + part;
                                if (!part.endsWith("}")) part = part + "}";
                                long id = Long.parseLong(extractString(part, "id"));
                                String name = extractString(part, "fullName");
                                if (name != null) list.add(new PatientOption(id, name));
                            }
                        }
                    }
                    Platform.runLater(() -> {
                        manualPatients.setAll(list);
                        if (patientCombo != null) {
                            patientCombo.setItems(manualPatients);
                            if (!manualPatients.isEmpty()) patientCombo.setValue(manualPatients.get(0));
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void loadDoctorsForAdmin() {
        if (!manualDoctors.isEmpty()) return;
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_ALL_DOCTORS)
                        .token(sessionToken).build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<DoctorItem> list = new ArrayList<>();
                    if (json != null && !json.isBlank()) {
                        json = json.trim();
                        if (json.startsWith("[")) json = json.substring(1);
                        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
                        String[] parts = json.split("},\\s*\\{");
                        for (String part : parts) {
                            part = part.trim();
                            if (!part.startsWith("{")) part = "{" + part;
                            if (!part.endsWith("}")) part = part + "}";
                            long id = Long.parseLong(extractString(part, "id"));
                            String name = extractString(part, "fullName");
                            String spec = extractString(part, "specialization");
                            if (name != null && spec != null) list.add(new DoctorItem(id, name, spec));
                        }
                    }
                    Platform.runLater(() -> {
                        manualDoctors.setAll(list);
                        if (docCombo != null) {
                            docCombo.setItems(manualDoctors);
                            if (!manualDoctors.isEmpty()) docCombo.setValue(manualDoctors.get(0));
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void loadManualSlots(LocalDate date, long doctorId) {
        manualTimeCombo.setPromptText("Загрузка…");
        manualTimeCombo.setDisable(true);
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_SLOTS)
                        .token(sessionToken)
                        .param("doctorId", String.valueOf(doctorId))
                        .param("date", date.toString())
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String slotsStr = response.getParam("slots");
                    List<Doctors.SlotOption> options = new ArrayList<>();
                    if (slotsStr != null && !slotsStr.isBlank()) {
                        String[] parts = slotsStr.split("\\|");
                        for (String part : parts) {
                            String[] sub = part.split(":");
                            if (sub.length >= 2) {
                                long slotId = Long.parseLong(sub[0]);
                                String time = sub[1];               // часы
                                if (sub.length >= 3) time += ":" + sub[2]; // минуты
                                if (time.contains("-")) time = time.split("-")[0]; // отрезаем "-конец"
                                options.add(new Doctors.SlotOption(time, slotId));
                            }
                        }
                    }
                    List<Doctors.SlotOption> finalOptions = options;
                    Platform.runLater(() -> {
                        manualTimeCombo.getItems().setAll(finalOptions);
                        manualTimeCombo.setDisable(false);
                        if (!finalOptions.isEmpty()) manualTimeCombo.setValue(finalOptions.get(0));
                        else manualTimeCombo.setPromptText("Нет свободных слотов");
                    });
                } else {
                    Platform.runLater(() -> manualTimeCombo.setDisable(false));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> manualTimeCombo.setDisable(false));
            }
        });
    }

    private void bookManualAppointment(long patientId, String reason, DoctorItem doc) {
        Doctors.SlotOption selectedSlot = manualTimeCombo.getValue();
        if (selectedSlot == null) {
            Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Не выбран слот", ""));
            return;
        }

        LocalDate date = manualDatePicker.getValue();
        String timeStr = selectedSlot.getTime();
        LocalTime time = LocalTime.parse(timeStr);
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        String datetimeStr = dateTime.toString() + ":00";   // секунды всегда 00

        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.BOOK_APPOINTMENT)
                        .token(sessionToken)
                        .param("doctorId", String.valueOf(doc.id))
                        .param("patientId", String.valueOf(patientId))
                        .param("slotId", String.valueOf(selectedSlot.getSlotId()))
                        .param("reason", reason)
                        .param("datetime", datetimeStr)
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                Platform.runLater(() -> {
                    if (resp instanceof Command response && response.isOk()) {
                        showAlert(Alert.AlertType.INFORMATION, "Запись создана",
                                "Пациент успешно записан!\nНапоминания созданы автоматически.");
                        loadManualSlots(manualDatePicker.getValue(), doc.id);
                    } else {
                        String msg = (resp instanceof Command) ? ((Command) resp).getMessage() : "Ошибка";
                        showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось создать запись: " + msg);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private VBox buildSidebar() {
        VBox sb = new VBox();
        sb.setPrefWidth(240);
        sb.setMinWidth(240);
        sb.setStyle("-fx-background-color:" + CARD_BG + ";-fx-border-color:" + BORDER + ";-fx-border-width:0 1 0 0;");

        HBox logoRow = new HBox();
        logoRow.setPadding(new Insets(24, 20, 28, 24));
        logoRow.setAlignment(Pos.CENTER_LEFT);
        Label logo = new Label("+ MedPortal");
        logo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        logo.setTextFill(Color.web(ACCENT));
        logoRow.getChildren().add(logo);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:" + BORDER + ";");

        VBox nav = new VBox(2);
        nav.setPadding(new Insets(16, 12, 16, 12));
        nav.getChildren().add(sideGroupLabel("ЗАПИСИ"));
        addNavItem(nav, "📅  Все записи", "appointments");
        addNavItem(nav, "➕  Ручная запись", "manual");
        addNavItem(nav, "🔔  Напоминания", "reminders");

        nav.getChildren().add(sideGroupLabel("ОТЧЁТНОСТЬ"));
        addNavItem(nav, "📊  Отчёты", "reports");

        nav.getChildren().add(sideGroupLabel("АДМИНИСТРИРОВАНИЕ"));
        addNavItem(nav, "👤  Пользователи", "users");
        addNavItem(nav, "👨‍⚕️  Справочник врачей", "doctors_dir");
        addNavItem(nav, "📋  Системные журналы", "logs");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        HBox profile = new HBox(10);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.setPadding(new Insets(14));
        profile.setStyle("-fx-background-color:" + GREY_SOFT + ";-fx-border-color:" + BORDER + ";-fx-border-width:1 0 0 0;");
        StackPane av = new StackPane();
        Circle ac = new Circle(20, Color.web(PURPLE));
        Label ai = new Label("АД");
        ai.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        ai.setTextFill(Color.WHITE);
        av.getChildren().addAll(ac, ai);
        VBox info = new VBox(1);
        Label nm = new Label(currentAdminName);
        nm.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        nm.setTextFill(Color.web(TEXT_PRIMARY));
        Label rl = new Label("admin@clinic.ru");
        rl.setFont(Font.font("Segoe UI", 11));
        rl.setTextFill(Color.web(TEXT_MUTED));
        info.getChildren().addAll(nm, rl);
        profile.getChildren().addAll(av, info);

        sb.getChildren().addAll(logoRow, sep, nav, spacer, profile);
        setActiveNavItem(activePage);
        return sb;
    }

    private void addNavItem(VBox nav, String label, String pageId) {
        HBox item = new HBox();
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 14, 10, 14));
        item.setCursor(Cursor.HAND);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setUserData(pageId);
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
        lbl.setTextFill(Color.web(TEXT_SEC));
        item.getChildren().add(lbl);
        String normalStyle = "-fx-background-color:transparent;-fx-background-radius:8;";
        String hoverStyle = "-fx-background-color:" + GREY_SOFT + ";-fx-background-radius:8;";
        item.setStyle(normalStyle);
        item.setOnMouseEntered(e -> item.setStyle(hoverStyle));
        item.setOnMouseExited(e -> {
            if (!pageId.equals(activePage)) item.setStyle(normalStyle);
        });
        item.setOnMouseClicked(e -> navigate(pageId));
        nav.getChildren().add(item);
        navItems.put(pageId, item);
    }

    private Label sideGroupLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(TEXT_MUTED));
        l.setPadding(new Insets(14, 14, 4, 14));
        return l;
    }

    private void setActiveNavItem(String pageId) {
        for (Map.Entry<String, HBox> entry : navItems.entrySet()) {
            HBox item = entry.getValue();
            boolean isActive = entry.getKey().equals(pageId);
            String style = isActive ? "-fx-background-color:" + BLUE_SOFT + ";-fx-background-radius:8;"
                    : "-fx-background-color:transparent;-fx-background-radius:8;";
            item.setStyle(style);
            Label lbl = (Label) item.getChildren().get(0);
            lbl.setFont(Font.font("Segoe UI", isActive ? FontWeight.SEMI_BOLD : FontWeight.NORMAL, 13));
            lbl.setTextFill(Color.web(isActive ? ACCENT : TEXT_SEC));
        }
    }

    private void navigate(String pageId) {
        if (pageId.equals(activePage)) return;
        activePage = pageId;
        setActiveNavItem(pageId);
        root.setCenter(buildPage(pageId));
    }

    private Node buildPage(String id) {
        return switch (id) {
            case "manual" -> buildManualPage();
            case "reminders" -> buildRemindersPage();
            case "reports" -> buildReportsPage();
            case "users" -> buildUsersPage();
            case "doctors_dir" -> buildDoctorsDirPage();
            case "logs" -> buildLogsPage();
            default -> buildAppointmentsPage();
        };
    }

    private void loadAllData() {
        loadAppointments();
        loadUsers();
        loadDoctors();
        loadReminders();
        loadAuditLogs();
    }

    private void loadAppointments() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_ALL_APPOINTMENTS).token(sessionToken).build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<AppointmentRow> parsed = parseAppointments(json);
                    Platform.runLater(() -> {
                        allAppointments.setAll(parsed);
                        if ("appointments".equals(activePage)) {
                            root.setCenter(buildAppointmentsPage());
                        }
                        applyAppointmentFilters();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<AppointmentRow> parseAppointments(String json) {
        List<AppointmentRow> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        if (json.isBlank()) return list;
        String[] parts = json.split("},\\s*\\{");
        for (String part : parts) {
            part = part.trim();
            if (!part.startsWith("{")) part = "{" + part;
            if (!part.endsWith("}")) part = part + "}";
            AppointmentRow row = new AppointmentRow();
            String datetime = extractString(part, "datetime");
            if (datetime != null && datetime.contains("T")) {
                String[] dt = datetime.split("T");
                row.date = dt[0];
                row.time = dt[1].substring(0, Math.min(5, dt[1].length()));
            }
            row.patient = extractString(part, "patientName");
            row.doctor = extractString(part, "doctorName");
            row.spec = extractString(part, "specialization");
            String status = extractString(part, "status");
            switch (status != null ? status.toUpperCase() : "") {
                case "PENDING" -> {
                    row.status = "Ожидает";
                    row.statusColor = ORANGE;
                    row.statusBg = ORANGE_SOFT;
                }
                case "CONFIRMED" -> {
                    row.status = "Подтверждено";
                    row.statusColor = ACCENT;
                    row.statusBg = BLUE_SOFT;
                }
                case "COMPLETED" -> {
                    row.status = "Завершено";
                    row.statusColor = GREEN;
                    row.statusBg = GREEN_SOFT;
                }
                case "CANCELLED" -> {
                    row.status = "Отменено";
                    row.statusColor = RED;
                    row.statusBg = RED_SOFT;
                }
                case "MISSED" -> {
                    row.status = "Неявка";
                    row.statusColor = RED;
                    row.statusBg = RED_SOFT;
                }
                default -> {
                    row.status = status != null ? status : "—";
                    row.statusColor = TEXT_MUTED;
                    row.statusBg = GREY_SOFT;
                }
            }
            list.add(row);
        }
        return list;
    }

    private void loadUsers() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_ALL_USERS).token(sessionToken).build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<UserRow> parsed = parseUsers(json);
                    Platform.runLater(() -> {
                        allUsers.setAll(parsed);
                        if ("users".equals(activePage)) {
                            root.setCenter(buildUsersPage());
                        }
                        applyUserFilters();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<UserRow> parseUsers(String json) {
        List<UserRow> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        if (json.isBlank()) return list;
        String[] parts = json.split("},\\s*\\{");
        for (String part : parts) {
            part = part.trim();
            if (!part.startsWith("{")) part = "{" + part;
            if (!part.endsWith("}")) part = part + "}";
            UserRow u = new UserRow();
            u.userId = Long.parseLong(extractString(part, "id"));
            u.name = extractString(part, "login");
            u.login = extractString(part, "login");
            String serverRole = extractString(part, "role");
            u.role = switch (serverRole != null ? serverRole.toUpperCase() : "") {
                case "PATIENT" -> "Пациент";
                case "DOCTOR" -> "Врач";
                case "ADMIN" -> "Администратор";
                default -> serverRole != null ? serverRole : "—";
            };
            u.status = Boolean.parseBoolean(extractString(part, "blocked")) ? "Заблокирован" : "Активен";
            u.statusColor = u.status.equals("Активен") ? GREEN : RED;
            u.statusBg = u.status.equals("Активен") ? GREEN_SOFT : RED_SOFT;
            u.created = "";
            list.add(u);
        }
        return list;
    }

    private void toggleBlockUser(UserRow u) {
        if ("Администратор".equals(u.role) || u.userId == currentUserId) {
            showAlert(Alert.AlertType.WARNING, "Действие запрещено",
                    "Вы не можете заблокировать или разблокировать администратора");
            return;
        }
        boolean currentlyBlocked = "Заблокирован".equals(u.status);
        String action = currentlyBlocked ? "разблокировать" : "заблокировать";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Вы уверены, что хотите " + action + " пользователя " + u.name + "?");
        confirm.showAndWait().ifPresent(btnType -> {
            if (btnType == ButtonType.OK) {
                CommandType cmdType = currentlyBlocked ? CommandType.UNBLOCK_USER : CommandType.BLOCK_USER;
                executor.execute(() -> {
                    try {
                        Command cmd = Command.builder(cmdType)
                                .token(sessionToken)
                                .param("userId", String.valueOf(u.userId))
                                .build();
                        synchronized (sendLock) {
                            out.writeObject(cmd);
                            out.flush();
                            out.reset();
                        }
                        Object resp = in.readObject();
                        Platform.runLater(() -> {
                            if (resp instanceof Command response && response.isOk()) {
                                showAlert(Alert.AlertType.INFORMATION, "Успех", "Пользователь " + action + ".");
                                loadUsers();
                            } else {
                                showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось " + action + " пользователя.");
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    private void deleteUser(UserRow u) {
        if ("Администратор".equals(u.role) || u.userId == currentUserId) {
            showAlert(Alert.AlertType.WARNING, "Действие запрещено",
                    "Вы не можете удалить администратора, включая самого себя.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Вы уверены, что хотите удалить пользователя " + u.name + "?");
        confirm.showAndWait().ifPresent(btnType -> {
            if (btnType == ButtonType.OK) {
                executor.execute(() -> {
                    try {
                        Command cmd = Command.builder(CommandType.DELETE_USER)
                                .token(sessionToken)
                                .param("userId", String.valueOf(u.userId))
                                .build();
                        synchronized (sendLock) {
                            out.writeObject(cmd);
                            out.flush();
                            out.reset();
                        }
                        Object resp = in.readObject();
                        Platform.runLater(() -> {
                            if (resp instanceof Command response && response.isOk()) {
                                showAlert(Alert.AlertType.INFORMATION, "Успех", "Пользователь удалён.");
                                loadUsers();
                            } else {
                                showAlert(Alert.AlertType.ERROR, "Ошибка", "Не удалось удалить пользователя.");
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    private void loadDoctors() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_ALL_DOCTORS).token(sessionToken).build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<DoctorRow> parsed = parseDoctors(json);
                    Platform.runLater(() -> {
                        allDoctors.setAll(parsed);
                        if ("doctors_dir".equals(activePage)) root.setCenter(buildDoctorsDirPage());
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<DoctorRow> parseDoctors(String json) {
        List<DoctorRow> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        if (json.isBlank()) return list;
        String[] parts = json.split("},\\s*\\{");
        for (String part : parts) {
            part = part.trim();
            if (!part.startsWith("{")) part = "{" + part;
            if (!part.endsWith("}")) part = part + "}";
            DoctorRow d = new DoctorRow();
            d.name = extractString(part, "fullName");
            d.spec = extractString(part, "specialization");
            d.office = extractString(part, "officeNumber");
            d.education = extractString(part, "education");
            d.experience = extractString(part, "experienceYears") + " лет";
            d.active = Boolean.parseBoolean(extractString(part, "active"));
            list.add(d);
        }
        return list;
    }

    private void loadReminders() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_REMINDERS).token(sessionToken).build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<ReminderRow> parsed = parseReminders(json);
                    Platform.runLater(() -> {
                        allReminders.setAll(parsed);
                        if ("reminders".equals(activePage)) root.setCenter(buildRemindersPage());
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<ReminderRow> parseReminders(String json) {
        List<ReminderRow> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        if (json.isBlank()) return list;
        String[] parts = json.split("},\\s*\\{");
        for (String part : parts) {
            part = part.trim();
            if (!part.startsWith("{")) part = "{" + part;
            if (!part.endsWith("}")) part = part + "}";
            ReminderRow r = new ReminderRow();
            r.patient = extractString(part, "patientName");
            r.doctor = extractString(part, "doctorName");
            r.visitDate = extractString(part, "appointmentDatetime");
            r.sendTime = extractString(part, "scheduledSendTime");
            r.channel = extractString(part, "channel");
            String st = extractString(part, "status");
            switch (st != null ? st.toUpperCase() : "") {
                case "SENT" -> {
                    r.status = "Отправлено";
                    r.statusColor = GREEN;
                    r.statusBg = GREEN_SOFT;
                }
                case "SCHEDULED" -> {
                    r.status = "Запланировано";
                    r.statusColor = ACCENT;
                    r.statusBg = BLUE_SOFT;
                }
                case "CANCELLED" -> {
                    r.status = "Отменено";
                    r.statusColor = RED;
                    r.statusBg = RED_SOFT;
                }
                default -> {
                    r.status = st != null ? st : "—";
                    r.statusColor = TEXT_MUTED;
                    r.statusBg = GREY_SOFT;
                }
            }
            list.add(r);
        }
        return list;
    }

    private void loadAuditLogs() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_AUDIT_LOG).token(sessionToken).build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<LogRow> parsed = parseLogs(json);
                    Platform.runLater(() -> {
                        allLogs.setAll(parsed);
                        if ("logs".equals(activePage)) {
                            root.setCenter(buildLogsPage());
                        }
                        applyLogFilters();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<LogRow> parseLogs(String json) {
        List<LogRow> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        if (json.isBlank()) return list;
        String[] parts = json.split("},\\s*\\{");
        for (String part : parts) {
            part = part.trim();
            if (!part.startsWith("{")) part = "{" + part;
            if (!part.endsWith("}")) part = part + "}";
            LogRow l = new LogRow();
            l.time = extractString(part, "timestamp");
            l.user = extractString(part, "userLogin");
            l.action = extractString(part, "action");
            l.entity = extractString(part, "entityType");
            l.details = extractString(part, "details");
            l.level = extractString(part, "level");
            switch (l.level != null ? l.level.toUpperCase() : "") {
                case "INFO" -> {
                    l.levelColor = GREEN;
                    l.levelBg = GREEN_SOFT;
                }
                case "WARN" -> {
                    l.levelColor = ORANGE;
                    l.levelBg = ORANGE_SOFT;
                }
                case "ERROR" -> {
                    l.levelColor = RED;
                    l.levelBg = RED_SOFT;
                }
                default -> {
                    l.levelColor = TEXT_MUTED;
                    l.levelBg = GREY_SOFT;
                }
            }
            list.add(l);
        }
        return list;
    }

    private ScrollPane buildAppointmentsPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");

        HBox hdr = new HBox();
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label title = pageTitle("Управление записями");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        hdr.getChildren().addAll(title, sp);

        long total = allAppointments.size();
        long completed = allAppointments.stream().filter(a -> "Завершено".equals(a.status)).count();
        long pending = allAppointments.stream().filter(a -> "Ожидает".equals(a.status) || "Запланировано".equals(a.status)).count();
        long cancelled = allAppointments.stream().filter(a -> "Отменено".equals(a.status) || "Неявка".equals(a.status)).count();

        HBox stats = new HBox(16);
        stats.getChildren().addAll(
                statCard("📅", "Всего", String.valueOf(total), ACCENT),
                statCard("✅", "Завершено", String.valueOf(completed), GREEN),
                statCard("⏳", "Ожидает", String.valueOf(pending), ORANGE),
                statCard("❌", "Отменено/неявка", String.valueOf(cancelled), RED)
        );
        for (var n : stats.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        HBox filters = new HBox(12);
        filters.setPadding(new Insets(14, 20, 14, 20));
        filters.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;-fx-border-color:" + BORDER + ";-fx-border-radius:12;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),8,0,0,2);");

        HBox sb = new HBox(8);
        sb.setAlignment(Pos.CENTER_LEFT);
        sb.setPadding(new Insets(8, 14, 8, 14));
        sb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:9;-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-border-width:1.5;");
        Label si = new Label("🔍");
        appointmentSearchField = new TextField();
        appointmentSearchField.setPromptText("Поиск по пациенту, врачу...");
        appointmentSearchField.setPrefWidth(280);
        appointmentSearchField.setStyle("-fx-background-color:transparent;-fx-border-width:0;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sb.getChildren().addAll(si, appointmentSearchField);
        HBox.setHgrow(sb, Priority.ALWAYS);

        appointmentStatusFilter = new ComboBox<>(FXCollections.observableArrayList(
                "Все статусы", "Подтверждено", "Ожидает", "Завершено", "Отменено", "Неявка"));
        appointmentStatusFilter.setValue("Все статусы");
        appointmentStatusFilter.setPrefWidth(180);
        appointmentStatusFilter.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");

        appointmentSpecFilter = new ComboBox<>(FXCollections.observableArrayList(
                "Все специализации", "Кардиолог", "Стоматолог", "Терапевт", "Невролог", "Офтальмолог"));
        appointmentSpecFilter.setValue("Все специализации");
        appointmentSpecFilter.setPrefWidth(180);
        appointmentSpecFilter.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");

        filters.getChildren().addAll(sb, appointmentStatusFilter, appointmentSpecFilter);

        appointmentSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyAppointmentFilters());
        appointmentStatusFilter.setOnAction(e -> applyAppointmentFilters());
        appointmentSpecFilter.setOnAction(e -> applyAppointmentFilters());

        VBox table = tableCard(
                new String[]{"Дата", "Время", "Пациент", "Врач", "Специализация", "Статус", ""},
                new double[]{12, 8, 20, 18, 16, 14, 12}   // веса для адаптивности
        );
        appointmentsTableContainer = new VBox(0);
        table.getChildren().add(appointmentsTableContainer);

        inner.getChildren().addAll(hdr, stats, filters, table);
        return scroll(inner);
    }

    private void applyAppointmentFilters() {
        if (appointmentsTableContainer == null) return;

        String search = appointmentSearchField.getText() != null ?
                appointmentSearchField.getText().toLowerCase().trim() : "";
        String statusFilter = appointmentStatusFilter.getValue();
        String specFilter = appointmentSpecFilter.getValue();

        List<AppointmentRow> filtered = allAppointments.stream()
                .filter(a -> {
                    if (!search.isEmpty()) {
                        String patient = a.patient != null ? a.patient.toLowerCase() : "";
                        String doctor = a.doctor != null ? a.doctor.toLowerCase() : "";
                        if (!patient.contains(search) && !doctor.contains(search)) return false;
                    }
                    if (!"Все статусы".equals(statusFilter) && !statusFilter.equals(a.status)) return false;
                    if (!"Все специализации".equals(specFilter) && !specFilter.equals(a.spec)) return false;
                    return true;
                }).collect(Collectors.toList());

        appointmentsTableContainer.getChildren().clear();
        for (AppointmentRow a : filtered) {
            appointmentsTableContainer.getChildren().add(appointmentRow(a));
        }
    }

    private VBox appointmentRow(AppointmentRow a) {
        VBox wrap = new VBox(0);
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 22, 14, 22));
        row.setCursor(Cursor.HAND);
        hoverRow(row);
        row.getChildren().add(adaptiveCell(a.date, 12, FontWeight.NORMAL, TEXT_SEC));
        row.getChildren().add(adaptiveCell(a.time, 8, FontWeight.BOLD, TEXT_PRIMARY));
        row.getChildren().add(adaptiveCell(a.patient, 20, FontWeight.SEMI_BOLD, TEXT_PRIMARY));
        row.getChildren().add(adaptiveCell(a.doctor, 18, FontWeight.NORMAL, TEXT_SEC));
        row.getChildren().add(adaptiveCell(a.spec, 16, FontWeight.NORMAL, TEXT_MUTED));
        Label badge = badge(a.status, a.statusColor, a.statusBg);
        HBox badgeBox = new HBox(badge);
        badgeBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(badgeBox, Priority.ALWAYS);
        badgeBox.setMaxWidth(Double.MAX_VALUE);
        badgeBox.setMinWidth(0);
        row.getChildren().add(badgeBox);
        HBox acts = new HBox(6);
        Button edit = smallBtn("✎", ACCENT);
        Button cancel = smallBtn("✕", RED);
        edit.setOnAction(e -> showEditAppointmentDialog(a));
        cancel.setOnAction(e -> confirmCancel("запись " + a.patient + " — " + a.date + " " + a.time));
        acts.getChildren().addAll(edit, cancel);
        row.getChildren().add(acts);
        wrap.getChildren().add(row);
        Separator s = new Separator();
        s.setStyle("-fx-background-color:" + BORDER + ";");
        wrap.getChildren().add(s);
        return wrap;
    }

    private ScrollPane buildManualPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");
        Label title = pageTitle("Ручная запись пациента");

        VBox form = new VBox(0);
        form.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");
        VBox formContent = new VBox(20);
        formContent.setPadding(new Insets(28));

        Label secPat = sectionLabel("Пациент");
        patientCombo = new ComboBox<>(manualPatients);
        patientCombo.setPromptText("Выберите пациента");
        patientCombo.setPrefWidth(400);
        patientCombo.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");
        if (!manualPatients.isEmpty()) patientCombo.setValue(manualPatients.get(0));
        formContent.getChildren().addAll(secPat, patientCombo, hSep());

        Label secDoc = sectionLabel("Врач");
        docCombo = new ComboBox<>(manualDoctors);
        docCombo.setPromptText("Выберите врача");
        docCombo.setPrefWidth(400);
        if (!manualDoctors.isEmpty()) docCombo.setValue(manualDoctors.get(0));
        docCombo.setOnAction(e -> {
            DoctorItem doc = docCombo.getValue();
            if (doc != null && manualDatePicker.getValue() != null) {
                loadManualSlots(manualDatePicker.getValue(), doc.id);
            }
        });
        formContent.getChildren().addAll(secDoc, docCombo, hSep());

        Label secDate = sectionLabel("Дата и время");
        HBox dateRow = new HBox(16);
        manualDatePicker = new DatePicker();
        manualDatePicker.setPromptText("Выберите дату");
        manualDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now().plusDays(1)));
            }
        });
        manualDatePicker.setPrefWidth(200);
        manualTimeCombo = new ComboBox<>();
        manualTimeCombo.setPromptText("Сначала выберите врача и дату");
        manualTimeCombo.setPrefWidth(150);
        manualTimeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Doctors.SlotOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getTime());
            }
        });
        manualTimeCombo.setCellFactory(p -> new ListCell<>() {
            @Override
            protected void updateItem(Doctors.SlotOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getTime());
            }
        });
        manualTimeCombo.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");
        manualDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            DoctorItem doc = docCombo.getValue();
            if (newDate != null && doc != null) {
                loadManualSlots(newDate, doc.id);
            }
        });
        dateRow.getChildren().addAll(manualDatePicker, manualTimeCombo);
        formContent.getChildren().addAll(secDate, dateRow, hSep());

        Label secReason = sectionLabel("Причина обращения");
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Укажите цель визита...");
        reasonArea.setPrefHeight(80);
        reasonArea.setWrapText(true);
        styleTextArea(reasonArea);
        formContent.getChildren().addAll(secReason, reasonArea);

        form.getChildren().add(formContent);

        HBox btnBar = new HBox(12);
        btnBar.setPadding(new Insets(20, 28, 22, 28));
        btnBar.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:0 0 14 14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-width:1 0 0 0;");
        Button saveBtn = filledBtn("Записать пациента", ACCENT);
        saveBtn.setOnAction(e -> {
            PatientOption selectedPat = patientCombo.getValue();
            if (selectedPat == null) {
                showAlert(Alert.AlertType.WARNING, "Выберите пациента", "");
                return;
            }
            DoctorItem doc = docCombo.getValue();
            if (doc == null) {
                showAlert(Alert.AlertType.WARNING, "Выберите врача", "");
                return;
            }
            if (manualDatePicker.getValue() == null) {
                showAlert(Alert.AlertType.WARNING, "Выберите дату", "");
                return;
            }
            if (manualTimeCombo.getValue() == null) {
                showAlert(Alert.AlertType.WARNING, "Выберите время", "");
                return;
            }
            String reason = reasonArea.getText().trim();
            bookManualAppointment(selectedPat.patientId, reason, doc);
        });
        Button clearBtn = outlineBtn("Очистить форму");
        clearBtn.setOnAction(e -> {
            if (patientCombo != null) {
                patientCombo.setValue(manualPatients.isEmpty() ? null : manualPatients.get(0));
            }
            if (docCombo != null) {
                docCombo.setValue(manualDoctors.isEmpty() ? null : manualDoctors.get(0));
            }
            manualDatePicker.setValue(null);
            manualTimeCombo.setItems(FXCollections.observableArrayList());
            manualTimeCombo.setPromptText("Сначала выберите врача и дату");
            reasonArea.clear();
        });
        btnBar.getChildren().addAll(saveBtn, clearBtn);
        form.getChildren().add(btnBar);

        inner.getChildren().addAll(title, form);
        return scroll(inner);
    }

    private ScrollPane buildRemindersPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");

        HBox hdr = new HBox();
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label title = pageTitle("Управление напоминаниями");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        hdr.getChildren().addAll(title, sp);

        HBox stats = new HBox(16);
        long sent = allReminders.stream().filter(r -> "Отправлено".equals(r.status)).count();
        long planned = allReminders.stream().filter(r -> "Запланировано".equals(r.status)).count();
        long cancelled = allReminders.stream().filter(r -> "Отменено".equals(r.status)).count();
        stats.getChildren().addAll(
                statCard("✅", "Отправлено", String.valueOf(sent), GREEN),
                statCard("🔔", "Запланировано", String.valueOf(planned), ACCENT),
                statCard("🚫", "Отменено", String.valueOf(cancelled), RED)
        );
        for (var n : stats.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        HBox filters = filterRow("Поиск по пациенту...",
                List.of("Все статусы", "Отправлено", "Запланировано", "Отменено"), List.of(), null);

        VBox table = tableCard(
                new String[]{"Пациент", "Врач", "Дата визита", "Время отправки", "Канал", "Статус", ""},
                new double[]{180, 160, 160, 160, 100, 140, 100}
        );
        for (ReminderRow r : allReminders) {
            VBox wrap = new VBox(0);
            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(13, 22, 13, 22));
            row.setCursor(Cursor.HAND);
            hoverRow(row);
            row.getChildren().add(adaptiveCell(r.patient, 18, FontWeight.SEMI_BOLD, TEXT_PRIMARY));
            row.getChildren().add(adaptiveCell(r.doctor, 16, FontWeight.NORMAL, TEXT_SEC));
            row.getChildren().add(adaptiveCell(r.visitDate, 16, FontWeight.NORMAL, TEXT_SEC));
            row.getChildren().add(adaptiveCell(r.sendTime, 16, FontWeight.NORMAL, TEXT_SEC));
            row.getChildren().add(adaptiveCell(r.channel, 10, FontWeight.NORMAL, TEXT_MUTED));
            HBox bBox = new HBox(badge(r.status, r.statusColor, r.statusBg));
            bBox.setMinWidth(140);
            bBox.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(bBox);
            Button sendBtn = smallBtn("▶", ACCENT);
            sendBtn.setOnAction(e -> showAlert(Alert.AlertType.INFORMATION, "Напоминание отправлено",
                    "Напоминание для " + r.patient + " отправлено на " + r.channel + "."));
            row.getChildren().add(sendBtn);
            wrap.getChildren().add(row);
            Separator sep2 = new Separator();
            sep2.setStyle("-fx-background-color:" + BORDER + ";");
            wrap.getChildren().add(sep2);
            table.getChildren().add(wrap);
        }

        inner.getChildren().addAll(hdr, stats, filters, table);
        return scroll(inner);
    }

    private ScrollPane buildReportsPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");
        Label title = pageTitle("Формирование отчётов");

        HBox row1 = new HBox(16);
        HBox row2 = new HBox(16);
        row1.getChildren().addAll(
                reportCard("📅", "Нагрузка по врачам", "Количество приёмов по каждому врачу за период", ACCENT, BLUE_SOFT),
                reportCard("📊", "Статистика явки", "Процент неявок по специализациям и датам", GREEN, GREEN_SOFT)
        );
        row2.getChildren().addAll(
                reportCard("🔔", "Эффективность напоминаний", "Процент явки с напоминаниями и без", ORANGE, ORANGE_SOFT),
                reportCard("👥", "Активность пациентов", "Частота обращений, новые vs повторные пациенты", PURPLE, PURPLE_SOFT)
        );
        for (var n : row1.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        for (var n : row2.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        Label sumTitle = new Label("Сводка за сегодня — 7 Мая 2026");
        sumTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        sumTitle.setTextFill(Color.web(TEXT_PRIMARY));
        sumTitle.setPadding(new Insets(20, 22, 14, 22));

        GridPane sumGrid = new GridPane();
        sumGrid.setPadding(new Insets(0, 22, 20, 22));
        sumGrid.setHgap(60);
        sumGrid.setVgap(12);
        Object[][] sumData = {
                {"Всего записей сегодня", "16"}, {"Завершено приёмов", "9"}, {"Неявки", "2"},
                {"Отмены пациентами", "1"}, {"Отправлено напоминаний", "14"}, {"Новых пациентов", "3"}
        };
        for (int i = 0; i < sumData.length; i++) {
            Label k = new Label((String) sumData[i][0]);
            k.setFont(Font.font("Segoe UI", 13));
            k.setTextFill(Color.web(TEXT_SEC));
            k.setMinWidth(220);
            Label v = new Label((String) sumData[i][1]);
            v.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
            v.setTextFill(Color.web(TEXT_PRIMARY));
            sumGrid.add(k, (i % 2) * 2, i / 2);
            sumGrid.add(v, (i % 2) * 2 + 1, i / 2);
        }

        inner.getChildren().addAll(title, row1, row2);
        return scroll(inner);
    }

    private VBox reportCard(String icon, String name, String desc, String color, String bg) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(22));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setCursor(Cursor.HAND);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");

        StackPane ib = new StackPane();
        ib.setMaxWidth(44);
        ib.setMaxHeight(44);
        Circle ic = new Circle(22, Color.web(bg));
        Label il = new Label(icon);
        il.setFont(Font.font("Segoe UI", 20));
        ib.getChildren().addAll(ic, il);

        Label nameLbl = new Label(name);
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        nameLbl.setTextFill(Color.web(TEXT_PRIMARY));
        Label descLbl = new Label(desc);
        descLbl.setFont(Font.font("Segoe UI", 12));
        descLbl.setTextFill(Color.web(TEXT_MUTED));
        descLbl.setWrapText(true);

        HBox btnRow = new HBox(8);
        Button viewBtn = filledBtn("Сформировать", color);
        viewBtn.setOnAction(e -> showReportDialog(name));
        btnRow.getChildren().addAll(viewBtn);

        card.getChildren().addAll(ib, nameLbl, descLbl, btnRow);
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:14;-fx-border-color:" + color + "44;-fx-border-radius:14;-fx-border-width:1.5;-fx-effect:dropshadow(gaussian," + color + "22,16,0,0,4);"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);"));
        return card;
    }

    private ScrollPane buildUsersPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");

        HBox hdr = new HBox();
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label title = pageTitle("Управление пользователями");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        hdr.getChildren().addAll(title, sp);

        long patients = allUsers.stream().filter(u -> "Пациент".equals(u.role)).count();
        long docs = allUsers.stream().filter(u -> "Врач".equals(u.role)).count();
        long blocked = allUsers.stream().filter(u -> "Заблокирован".equals(u.status)).count();

        HBox stats = new HBox(16);
        stats.getChildren().addAll(
                statCard("👤", "Пациентов", String.valueOf(patients), ACCENT),
                statCard("👨‍⚕️", "Врачей", String.valueOf(docs), GREEN),
                statCard("🚫", "Заблокировано", String.valueOf(blocked), RED)
        );
        for (var n : stats.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        HBox filters = new HBox(12);
        filters.setPadding(new Insets(14, 20, 14, 20));
        filters.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;-fx-border-color:" + BORDER + ";-fx-border-radius:12;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),8,0,0,2);");

        HBox sb = new HBox(8);
        sb.setAlignment(Pos.CENTER_LEFT);
        sb.setPadding(new Insets(8, 14, 8, 14));
        sb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:9;-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-border-width:1.5;");
        Label si = new Label("🔍");
        userSearchField = new TextField();
        userSearchField.setPromptText("Поиск по имени или email...");
        userSearchField.setPrefWidth(280);
        userSearchField.setStyle("-fx-background-color:transparent;-fx-border-width:0;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sb.getChildren().addAll(si, userSearchField);
        HBox.setHgrow(sb, Priority.ALWAYS);

        userRoleFilter = new ComboBox<>(FXCollections.observableArrayList("Все роли", "Пациент", "Врач", "Администратор"));
        userRoleFilter.setValue("Все роли");
        userRoleFilter.setPrefWidth(180);
        userRoleFilter.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");

        userStatusFilter = new ComboBox<>(FXCollections.observableArrayList("Все статусы", "Активен", "Заблокирован"));
        userStatusFilter.setValue("Все статусы");
        userStatusFilter.setPrefWidth(180);
        userStatusFilter.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");

        filters.getChildren().addAll(sb, userRoleFilter, userStatusFilter);

        userSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyUserFilters());
        userRoleFilter.setOnAction(e -> applyUserFilters());
        userStatusFilter.setOnAction(e -> applyUserFilters());

        VBox table = tableCard(
                new String[]{"Имя", "Логин (email)", "Роль", "Статус", "Дата создания", ""},
                new double[]{220, 200, 140, 120, 150, 120}
        );
        usersTableContainer = new VBox(0);
        table.getChildren().add(usersTableContainer);

        inner.getChildren().addAll(hdr, stats, filters, table);
        return scroll(inner);
    }

    private void applyUserFilters() {
        if (usersTableContainer == null) return;

        String search = userSearchField.getText() != null ? userSearchField.getText().toLowerCase().trim() : "";
        String roleFilter = userRoleFilter.getValue();
        String statusFilter = userStatusFilter.getValue();

        List<UserRow> filtered = allUsers.stream()
                .filter(u -> {
                    if (!search.isEmpty()) {
                        String name = u.name != null ? u.name.toLowerCase() : "";
                        String login = u.login != null ? u.login.toLowerCase() : "";
                        if (!name.contains(search) && !login.contains(search)) return false;
                    }
                    if (!"Все роли".equals(roleFilter) && !roleFilter.equals(u.role)) return false;
                    if (!"Все статусы".equals(statusFilter) && !statusFilter.equals(u.status)) return false;
                    return true;
                }).collect(Collectors.toList());

        usersTableContainer.getChildren().clear();
        for (UserRow u : filtered) {
            usersTableContainer.getChildren().add(createUserRow(u));
        }
    }

    private VBox createUserRow(UserRow u) {
        VBox wrap = new VBox(0);
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(13, 22, 13, 22));
        row.setCursor(Cursor.HAND);
        hoverRow(row);
        HBox nameCell = new HBox(10);
        nameCell.setAlignment(Pos.CENTER_LEFT);
        nameCell.setMinWidth(220);
        StackPane uAv = new StackPane();
        Circle uC = new Circle(15, Color.web(roleColor(u.role)));
        Label uI = new Label(String.valueOf(u.name.charAt(0)));
        uI.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        uI.setTextFill(Color.WHITE);
        uAv.getChildren().addAll(uC, uI);
        Label nameLbl = new Label(u.name);
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        nameLbl.setTextFill(Color.web(TEXT_PRIMARY));
        nameCell.getChildren().addAll(uAv, nameLbl);
        row.getChildren().add(nameCell);
        row.getChildren().add(adaptiveCell(u.login, 20, FontWeight.NORMAL, TEXT_SEC));
        HBox roleBox = new HBox(badge(u.role, roleColor(u.role), roleBg(u.role)));
        roleBox.setMinWidth(140);
        roleBox.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(roleBox);
        HBox stBox = new HBox(badge(u.status, u.statusColor, u.statusBg));
        stBox.setMinWidth(120);
        stBox.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(stBox);
        row.getChildren().add(adaptiveCell(u.created, 15, FontWeight.NORMAL, TEXT_MUTED));
        HBox acts = new HBox(6);
        Button blockBtn = smallBtn(u.status.equals("Активен") ? "🔒" : "🔓", RED);
        Button delBtn = smallBtn("✕", RED);
        blockBtn.setOnAction(e -> toggleBlockUser(u));
        delBtn.setOnAction(e -> deleteUser(u));
        acts.getChildren().addAll(blockBtn, delBtn);
        row.getChildren().add(acts);
        wrap.getChildren().add(row);
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-background-color:" + BORDER + ";");
        wrap.getChildren().add(sep2);
        return wrap;
    }

    private ScrollPane buildDoctorsDirPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");

        HBox hdr = new HBox();
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label title = pageTitle("Справочник врачей");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        hdr.getChildren().addAll(title, sp);

        HBox filters = filterRow("Поиск по имени или специализации...",
                List.of("Все специализации", "Кардиолог", "Стоматолог", "Терапевт", "Невролог", "Офтальмолог"),
                List.of("Все", "Активен", "Неактивен"), null);

        VBox table = tableCard(
                new String[]{"Врач", "Специализация", "Кабинет", "Образование", "Опыт", "Статус", ""},
                new double[]{220, 140, 90, 200, 90, 120, 120}
        );
        for (DoctorRow d : allDoctors) {
            VBox wrap = new VBox(0);
            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(14, 22, 14, 22));
            row.setCursor(Cursor.HAND);
            hoverRow(row);
            HBox nameCell = new HBox(10);
            nameCell.setAlignment(Pos.CENTER_LEFT);
            nameCell.setMinWidth(220);
            StackPane dAv = new StackPane();
            Circle dC = new Circle(16, Color.web(ACCENT));
            Label dI = new Label(String.valueOf(d.name.charAt(0)));
            dI.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            dI.setTextFill(Color.WHITE);
            dAv.getChildren().addAll(dC, dI);
            Label dName = new Label(d.name);
            dName.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
            dName.setTextFill(Color.web(TEXT_PRIMARY));
            dName.setWrapText(true);
            nameCell.getChildren().addAll(dAv, dName);
            row.getChildren().add(nameCell);
            row.getChildren().add(adaptiveCell(d.spec, 14, FontWeight.NORMAL, TEXT_SEC));
            row.getChildren().add(adaptiveCell("Каб. " + d.office, 9, FontWeight.NORMAL, TEXT_MUTED));
            row.getChildren().add(adaptiveCell(d.education, 20, FontWeight.NORMAL, TEXT_SEC));
            row.getChildren().add(adaptiveCell(d.experience, 9, FontWeight.NORMAL, TEXT_MUTED));
            String stColor = d.active ? GREEN : RED;
            String stBg = d.active ? GREEN_SOFT : RED_SOFT;
            String stLabel = d.active ? "Активен" : "Неактивен";
            HBox stBox = new HBox(badge(stLabel, stColor, stBg));
            stBox.setMinWidth(120);
            stBox.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(stBox);
            HBox acts = new HBox(6);
            Button editBtn = smallBtn("✎", ACCENT);
            Button togBtn = smallBtn(d.active ? "⏸" : "▶", d.active ? RED : GREEN);
            editBtn.setOnAction(e -> showEditDoctorDialog(d));
            togBtn.setOnAction(e -> showAlert(Alert.AlertType.INFORMATION, "Статус изменён", "Врач " + d.name + (d.active ? " деактивирован." : " активирован.")));
            acts.getChildren().addAll(editBtn, togBtn);
            row.getChildren().add(acts);
            wrap.getChildren().add(row);
            Separator sep2 = new Separator();
            sep2.setStyle("-fx-background-color:" + BORDER + ";");
            wrap.getChildren().add(sep2);
            table.getChildren().add(wrap);
        }

        inner.getChildren().addAll(hdr, filters, table);
        return scroll(inner);
    }

    private ScrollPane buildLogsPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");

        HBox hdr = new HBox();
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label title = pageTitle("Системные журналы");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button refreshBtn = filledBtn("↻  Обновить", ACCENT);
        refreshBtn.setOnAction(e -> loadAuditLogs());  // повторный запрос
        HBox btns = new HBox(10, refreshBtn);
        hdr.getChildren().addAll(title, sp, btns);

        // Статистика (динамическая)
        long info = allLogs.stream().filter(l -> "INFO".equals(l.level)).count();
        long warn = allLogs.stream().filter(l -> "WARN".equals(l.level)).count();
        long error = allLogs.stream().filter(l -> "ERROR".equals(l.level)).count();

        HBox stats = new HBox(16);
        stats.getChildren().addAll(
                statCard("📋", "Всего событий", String.valueOf(allLogs.size()), ACCENT),
                statCard("ℹ", "INFO", String.valueOf(info), GREEN),
                statCard("⚠", "WARN", String.valueOf(warn), ORANGE),
                statCard("❌", "ERROR", String.valueOf(error), RED)
        );
        for (var n : stats.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // Фильтры
        HBox filters = new HBox(12);
        filters.setPadding(new Insets(14, 20, 14, 20));
        filters.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;-fx-border-color:" + BORDER + ";"
                + "-fx-border-radius:12;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),8,0,0,2);");

        // Поле поиска
        HBox sb = new HBox(8);
        sb.setAlignment(Pos.CENTER_LEFT);
        sb.setPadding(new Insets(8, 14, 8, 14));
        sb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:9;-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-border-width:1.5;");
        Label si = new Label("🔍");
        logSearchField = new TextField();
        logSearchField.setPromptText("Поиск по пользователю, действию...");
        logSearchField.setPrefWidth(280);
        logSearchField.setStyle("-fx-background-color:transparent;-fx-border-width:0;-fx-font-size:13px;"
                + "-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sb.getChildren().addAll(si, logSearchField);
        HBox.setHgrow(sb, Priority.ALWAYS);

        // Фильтр по уровню
        logLevelFilter = new ComboBox<>(FXCollections.observableArrayList("Все уровни", "INFO", "WARN", "ERROR"));
        logLevelFilter.setValue("Все уровни");
        logLevelFilter.setPrefWidth(180);
        logLevelFilter.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;"
                + "-fx-background-radius:9;-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");

        filters.getChildren().addAll(sb, logLevelFilter);

        // Слушатели фильтров
        logSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyLogFilters());
        logLevelFilter.setOnAction(e -> applyLogFilters());

        // Таблица (динамическая часть)
        VBox table = tableCard(
                new String[]{"Время", "Пользователь", "Действие", "Объект", "Детали", "Уровень"},
                new double[]{150, 160, 160, 130, 260, 90}
        );
        logsTableContainer = new VBox(0);
        table.getChildren().add(logsTableContainer);

        inner.getChildren().addAll(hdr, stats, filters, table);
        return scroll(inner);
    }

    private void applyLogFilters() {
        if (logsTableContainer == null) return;

        String search = logSearchField.getText() != null ? logSearchField.getText().toLowerCase().trim() : "";
        String levelFilter = logLevelFilter.getValue();

        List<LogRow> filtered = allLogs.stream()
                .filter(l -> {
                    if (!search.isEmpty()) {
                        String user = l.user != null ? l.user.toLowerCase() : "";
                        String action = l.action != null ? l.action.toLowerCase() : "";
                        String details = l.details != null ? l.details.toLowerCase() : "";
                        if (!user.contains(search) && !action.contains(search) && !details.contains(search))
                            return false;
                    }
                    if (!"Все уровни".equals(levelFilter) && !levelFilter.equals(l.level))
                        return false;
                    return true;
                }).collect(Collectors.toList());

        logsTableContainer.getChildren().clear();
        for (LogRow l : filtered) {
            logsTableContainer.getChildren().add(createLogRow(l));
        }
    }

    private VBox createLogRow(LogRow l) {
        VBox wrap = new VBox(0);
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 22, 12, 22));
        row.setCursor(Cursor.HAND);
        hoverRow(row);
        row.getChildren().add(adaptiveCell(l.time, 15, FontWeight.NORMAL, TEXT_MUTED));
        row.getChildren().add(adaptiveCell(l.user, 16, FontWeight.SEMI_BOLD, TEXT_PRIMARY));
        Label actLbl = new Label(l.action);
        actLbl.setFont(Font.font("Courier New", FontWeight.BOLD, 11));
        actLbl.setTextFill(Color.web(ACCENT));
        actLbl.setPadding(new Insets(2, 8, 2, 8));
        actLbl.setStyle("-fx-background-color:" + BLUE_SOFT + ";-fx-background-radius:6;");
        HBox actBox = new HBox(actLbl);
        actBox.setMinWidth(160);
        actBox.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(actBox);
        row.getChildren().add(adaptiveCell(l.entity, 13, FontWeight.NORMAL, TEXT_SEC));
        row.getChildren().add(adaptiveCell(l.details, 26, FontWeight.NORMAL, TEXT_SEC));
        HBox lvBox = new HBox(badge(l.level, l.levelColor, l.levelBg));
        lvBox.setMinWidth(90);
        lvBox.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(lvBox);
        wrap.getChildren().add(row);
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-background-color:" + BORDER + ";");
        wrap.getChildren().add(sep2);
        return wrap;
    }

    private void showEditAppointmentDialog(AppointmentRow a) {
        Dialog<Void> dlg = dialog("Изменить запись", 440);
        VBox c = new VBox(14);
        c.setPadding(new Insets(24));
        c.getChildren().addAll(
                dlgHeader("Запись: " + a.patient + " — " + a.date + " " + a.time),
                new Separator(),
                formFieldDlg("Статус", styledCombo(FXCollections.observableArrayList("Подтверждено", "Ожидает", "Завершено", "Отменено", "Неявка"), a.status)),
                formFieldDlg("Дата", styledField(a.date)),
                formFieldDlg("Время", styledCombo(FXCollections.observableArrayList("09:00", "09:30", "10:00", "10:30", "11:00", "14:00", "14:30"), a.time)),
                formFieldDlg("Примечание", styledField("Комментарий..."))
        );
        dlg.getDialogPane().setContent(c);
        dlg.showAndWait();
    }

    private void showAddUserDialog() {
        Dialog<Void> dlg = dialog("Новый пользователь", 440);
        VBox c = new VBox(14);
        c.setPadding(new Insets(24));
        c.getChildren().addAll(
                dlgHeader("Создать учётную запись"),
                new Separator(),
                formFieldDlg("ФИО", styledField("Фамилия Имя Отчество")),
                formFieldDlg("Email", styledField("example@mail.ru")),
                formFieldDlg("Роль", styledCombo(FXCollections.observableArrayList("Пациент", "Врач", "Регистратор", "Администратор"), "Пациент")),
                formFieldDlg("Пароль", styledField("Временный пароль...")),
                formFieldDlg("Дата рождения", styledField("дд.мм.гггг"))
        );
        dlg.getDialogPane().setContent(c);
        dlg.showAndWait();
    }

    private void showEditUserDialog(UserRow u) {
        Dialog<Void> dlg = dialog("Редактировать пользователя", 440);
        VBox c = new VBox(14);
        c.setPadding(new Insets(24));
        c.getChildren().addAll(
                dlgHeader(u.name),
                new Separator(),
                formFieldDlg("ФИО", styledField(u.name)),
                formFieldDlg("Email", styledField(u.login)),
                formFieldDlg("Роль", styledCombo(FXCollections.observableArrayList("Пациент", "Врач", "Регистратор", "Администратор"), u.role)),
                formFieldDlg("Статус", styledCombo(FXCollections.observableArrayList("Активен", "Заблокирован"), u.status))
        );
        dlg.getDialogPane().setContent(c);
        dlg.showAndWait();
    }

    private void showAddDoctorDialog() {
        Dialog<Void> dlg = dialog("Новый врач", 460);
        VBox c = new VBox(14);
        c.setPadding(new Insets(24));
        c.getChildren().addAll(
                dlgHeader("Добавить врача в справочник"),
                new Separator(),
                formFieldDlg("ФИО", styledField("Фамилия Имя Отчество")),
                formFieldDlg("Специализация", styledCombo(FXCollections.observableArrayList("Кардиолог", "Стоматолог", "Терапевт", "Невролог", "Офтальмолог", "Ортопед"), "Кардиолог")),
                formFieldDlg("Номер кабинета", styledField("Например: 204")),
                formFieldDlg("Образование", styledField("Название учебного заведения...")),
                formFieldDlg("Опыт работы", styledField("Например: 10 лет")),
                formFieldDlg("Учётная запись", styledCombo(FXCollections.observableArrayList("Создать новую", "Привязать существующую"), "Создать новую"))
        );
        dlg.getDialogPane().setContent(c);
        dlg.showAndWait();
    }

    private void showEditDoctorDialog(DoctorRow d) {
        Dialog<Void> dlg = dialog("Редактировать врача", 460);
        VBox c = new VBox(14);
        c.setPadding(new Insets(24));
        c.getChildren().addAll(
                dlgHeader(d.name),
                new Separator(),
                formFieldDlg("Специализация", styledField(d.spec)),
                formFieldDlg("Кабинет", styledField(d.office)),
                formFieldDlg("Образование", styledField(d.education)),
                formFieldDlg("Опыт", styledField(d.experience))
        );
        dlg.getDialogPane().setContent(c);
        dlg.showAndWait();
    }

    private void showNewPatientDialog() {
        Dialog<Void> dlg = dialog("Новый пациент", 440);
        VBox c = new VBox(14);
        c.setPadding(new Insets(24));
        c.getChildren().addAll(
                dlgHeader("Создать карту пациента"),
                new Separator(),
                formFieldDlg("ФИО", styledField("Фамилия Имя Отчество")),
                formFieldDlg("Дата рождения", styledField("дд.мм.гггг")),
                formFieldDlg("Телефон", styledField("+7 (___) ___-__-__")),
                formFieldDlg("Email", styledField("example@mail.ru")),
                formFieldDlg("Полис ОМС", styledField("0000 0000 0000"))
        );
        dlg.getDialogPane().setContent(c);
        dlg.showAndWait();
    }

    private void showReminderSettingsDialog() {
        Dialog<Void> dlg = dialog("Настройки отправки напоминаний", 420);
        VBox c = new VBox(14);
        c.setPadding(new Insets(24));
        c.getChildren().addAll(
                dlgHeader("Глобальные настройки"),
                new Separator(),
                formFieldDlg("Канал по умолчанию", styledCombo(FXCollections.observableArrayList("Email", "SMS", "Push"), "Email")),
                formFieldDlg("Отправлять за", styledCombo(FXCollections.observableArrayList("24 часа", "2 часа", "1 час", "30 минут"), "24 часа")),
                formFieldDlg("Второе напоминание", styledCombo(FXCollections.observableArrayList("Нет", "2 часа", "1 час", "30 минут"), "2 часа")),
                formFieldDlg("Тихие часы с", styledField("22:00")),
                formFieldDlg("Тихие часы до", styledField("08:00"))
        );
        dlg.getDialogPane().setContent(c);
        dlg.showAndWait();
    }

    private void showReportDialog(String reportName) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Сформировать отчёт");
        DialogPane dp = dlg.getDialogPane();
        dp.setPrefWidth(440);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.setText("Сформировать");
        okBtn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;");

        VBox content = new VBox(14);
        content.setPadding(new Insets(24));
        content.getChildren().add(dlgHeader(reportName));
        content.getChildren().add(new Separator());

        DatePicker fromDate = new DatePicker();
        fromDate.setPromptText("Выберите дату");
        fromDate.setMaxWidth(Double.MAX_VALUE);
        DatePicker toDate = new DatePicker();
        toDate.setPromptText("Выберите дату");
        toDate.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> formatCb = new ComboBox<>(
                FXCollections.observableArrayList("CSV"));
        formatCb.setValue("CSV");
        formatCb.setMaxWidth(Double.MAX_VALUE);
        formatCb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";"
                + "-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;"
                + "-fx-font-size:13px;-fx-font-family:'Segoe UI';");

        ComboBox<String> detailCb = new ComboBox<>(
                FXCollections.observableArrayList("По дням", "По неделям", "По месяцам"));
        detailCb.setValue("По дням");
        detailCb.setMaxWidth(Double.MAX_VALUE);
        detailCb.setStyle(formatCb.getStyle());

        content.getChildren().addAll(
                formFieldDlg("Период с", fromDate),
                formFieldDlg("Период по", toDate),
                formFieldDlg("Формат", formatCb),
                formFieldDlg("Детализация", detailCb)
        );

        dp.setContent(content);

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            LocalDate start = fromDate.getValue();
            LocalDate end = toDate.getValue();
            if (start == null || end == null) {
                event.consume();
                showAlert(Alert.AlertType.WARNING, "Даты не выбраны",
                        "Укажите начальную и конечную дату периода.");
                return;
            }
            if (end.isBefore(start)) {
                event.consume();
                showAlert(Alert.AlertType.WARNING, "Ошибка периода",
                        "Дата окончания не может быть раньше даты начала.");
                return;
            }
            String format = formatCb.getValue();
            String detail = detailCb.getValue();
            generateReport(reportName, start, end, format, detail);
        });

        dlg.showAndWait();
    }

    private void confirmCancel(String target) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Подтверждение");
        a.setHeaderText("Действие с: " + target);
        a.setContentText("Вы уверены? Это действие нельзя отменить.");
        a.showAndWait();
    }

    private ScrollPane scroll(VBox inner) {
        ScrollPane sc = new ScrollPane(inner);
        sc.setFitToWidth(true);
        sc.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sc.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");
        return sc;
    }

    private Label pageTitle(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        l.setTextFill(Color.web(TEXT_PRIMARY));
        return l;
    }

    private VBox statCard(String icon, String label, String val, String color) {
        VBox c = new VBox(8);
        c.setPadding(new Insets(18, 20, 18, 20));
        c.setMaxWidth(Double.MAX_VALUE);
        c.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;-fx-border-color:" + BORDER + ";-fx-border-radius:12;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),8,0,0,2);");
        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);
        Label il = new Label(icon);
        il.setFont(Font.font("Segoe UI", 20));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label vl = new Label(val);
        vl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        vl.setTextFill(Color.web(color));
        top.getChildren().addAll(il, sp, vl);
        Label ll = new Label(label);
        ll.setFont(Font.font("Segoe UI", 11));
        ll.setTextFill(Color.web(TEXT_SEC));
        c.getChildren().addAll(top, ll);
        return c;
    }

    private HBox filterRow(String searchPrompt, List<String> filter1Items, List<String> filter2Items, String f2def) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 20, 14, 20));
        row.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;-fx-border-color:" + BORDER + ";-fx-border-radius:12;-fx-border-width:1;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),8,0,0,2);");

        HBox sb = new HBox(8);
        sb.setAlignment(Pos.CENTER_LEFT);
        sb.setPadding(new Insets(8, 14, 8, 14));
        sb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:9;-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-border-width:1.5;");
        Label si = new Label("🔍");
        TextField sf = new TextField();
        sf.setPromptText(searchPrompt);
        sf.setPrefWidth(280);
        sf.setStyle("-fx-background-color:transparent;-fx-border-width:0;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sb.getChildren().addAll(si, sf);
        HBox.setHgrow(sb, Priority.ALWAYS);
        row.getChildren().add(sb);

        if (!filter1Items.isEmpty())
            row.getChildren().add(styledCombo(FXCollections.observableArrayList(filter1Items), filter1Items.get(0)));
        if (!filter2Items.isEmpty())
            row.getChildren().add(styledCombo(FXCollections.observableArrayList(filter2Items), f2def != null ? f2def : filter2Items.get(0)));
        return row;
    }

    private VBox tableCard(String[] cols, double[] weights) {
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");
        HBox hdr = new HBox();
        hdr.setPadding(new Insets(11, 22, 11, 22));
        hdr.setStyle("-fx-background-color:" + GREY_SOFT + ";-fx-background-radius:14 14 0 0;");
        for (int i = 0; i < cols.length; i++) {
            Label l = new Label(cols[i]);
            l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
            l.setTextFill(Color.web(TEXT_MUTED));
            HBox.setHgrow(l, Priority.ALWAYS);
            l.setMaxWidth(Double.MAX_VALUE);
            l.setMinWidth(0);
            hdr.getChildren().add(l);
        }
        card.getChildren().add(hdr);
        return card;
    }

    private Label adaptiveCell(String text, double weight, FontWeight fw, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", fw, 13));
        l.setTextFill(Color.web(color));
        l.setWrapText(false);
        HBox.setHgrow(l, Priority.ALWAYS);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMinWidth(0);
        return l;
    }

    private void generateReport(String reportName, LocalDate from, LocalDate to,
                                String format, String detail) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить отчёт");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файлы (*.csv)", "*.csv"));
        fileChooser.setInitialFileName(
                "Отчёт_" + reportName.replaceAll("\\s+", "_") + "_" + from + "_" + to + ".csv");

        File file = fileChooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(0xEF);
            fos.write(0xBB);
            fos.write(0xBF);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));

            writer.println("Отчёт: " + reportName);
            writer.println("Период: " + from + " – " + to);
            writer.println("Детализация: " + detail);
            writer.println();

            if (reportName.contains("Нагрузка по врачам")) {
                writer.println("Врач;Количество приёмов");
                Map<String, Long> docCount = new HashMap<>();
                for (AppointmentRow a : allAppointments) {
                    docCount.merge(a.doctor, 1L, Long::sum);
                }
                for (Map.Entry<String, Long> e : docCount.entrySet()) {
                    writer.println(e.getKey() + ";" + e.getValue());
                }
            } else if (reportName.contains("Статистика явки")) {
                writer.println("Специализация;Неявки");
                Map<String, Long> specMissed = new HashMap<>();
                for (AppointmentRow a : allAppointments) {
                    if ("Неявка".equals(a.status)) {
                        specMissed.merge(a.spec, 1L, Long::sum);
                    }
                }
                for (Map.Entry<String, Long> e : specMissed.entrySet()) {
                    writer.println(e.getKey() + ";" + e.getValue());
                }
            } else if (reportName.contains("Эффективность напоминаний")) {
                writer.println("Всего напоминаний;" + allReminders.size());
                writer.println("Отправлено;" + allReminders.stream().filter(r -> "Отправлено".equals(r.status)).count());
                writer.println("Запланировано;" + allReminders.stream().filter(r -> "Запланировано".equals(r.status)).count());
            } else if (reportName.contains("Активность пациентов")) {
                writer.println("Пациент;Количество визитов");
                Map<String, Long> patientVisits = new HashMap<>();
                for (AppointmentRow a : allAppointments) {
                    patientVisits.merge(a.patient, 1L, Long::sum);
                }
                for (Map.Entry<String, Long> e : patientVisits.entrySet()) {
                    writer.println(e.getKey() + ";" + e.getValue());
                }
            } else {
                writer.println("Для этого типа отчёта пока нет предустановленных данных.");
            }

            writer.flush();
            showAlert(Alert.AlertType.INFORMATION, "Отчёт сохранён",
                    "Файл сохранён: " + file.getAbsolutePath());
        } catch (IOException ex) {
            showAlert(Alert.AlertType.ERROR, "Ошибка",
                    "Не удалось сохранить файл: " + ex.getMessage());
        }
    }

    private Label badge(String text, String color, String bg) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        l.setTextFill(Color.web(color));
        l.setPadding(new Insets(3, 10, 3, 10));
        l.setStyle("-fx-background-color:" + bg + ";-fx-background-radius:20;");
        return l;
    }

    private Button filledBtn(String text, String color) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        b.setTextFill(Color.WHITE);
        b.setCursor(Cursor.HAND);
        b.setPadding(new Insets(10, 20, 10, 20));
        b.setStyle("-fx-background-color:" + color + ";-fx-background-radius:10;-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.25),8,0,0,3);");
        b.setOnMouseEntered(e -> b.setOpacity(0.87));
        b.setOnMouseExited(e -> b.setOpacity(1.0));
        return b;
    }

    private Button outlineBtn(String text) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        b.setTextFill(Color.web(ACCENT));
        b.setCursor(Cursor.HAND);
        b.setPadding(new Insets(10, 18, 10, 18));
        String st = "-fx-background-color:" + CARD_BG + ";-fx-border-color:" + ACCENT + ";-fx-border-radius:10;-fx-background-radius:10;-fx-border-width:1.5;";
        b.setStyle(st);
        b.setOnMouseEntered(e -> b.setStyle(st + "-fx-background-color:" + BLUE_SOFT + ";"));
        b.setOnMouseExited(e -> b.setStyle(st));
        return b;
    }

    private Button smallBtn(String text, String color) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        b.setTextFill(Color.web(color));
        b.setCursor(Cursor.HAND);
        b.setPadding(new Insets(5, 10, 5, 10));
        String st = "-fx-background-color:transparent;-fx-border-color:" + color + "44;-fx-border-radius:7;-fx-background-radius:7;-fx-border-width:1.5;";
        b.setStyle(st);
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:" + color + "11;-fx-border-color:" + color + "44;-fx-border-radius:7;-fx-background-radius:7;-fx-border-width:1.5;"));
        b.setOnMouseExited(e -> b.setStyle(st));
        return b;
    }

    private HBox searchField(String prompt) {
        HBox sb = new HBox(8);
        sb.setAlignment(Pos.CENTER_LEFT);
        sb.setPadding(new Insets(9, 14, 9, 14));
        sb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:10;-fx-border-color:" + BORDER + ";-fx-border-radius:10;-fx-border-width:1.5;");
        Label si = new Label("🔍");
        TextField sf = new TextField();
        sf.setPromptText(prompt);
        sf.setPrefWidth(300);
        sf.setStyle("-fx-background-color:transparent;-fx-border-width:0;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sb.getChildren().addAll(si, sf);
        return sb;
    }

    private ComboBox<String> styledCombo(javafx.collections.ObservableList<String> items, String def) {
        ComboBox<String> cb = new ComboBox<>(items);
        cb.setValue(def);
        cb.setPrefWidth(180);
        cb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");
        return cb;
    }

    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-padding:8 12 8 12;-fx-text-fill:" + TEXT_PRIMARY + ";");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private void styleTextArea(TextArea ta) {
        ta.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER + ";-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        ta.setMaxWidth(Double.MAX_VALUE);
    }

    private Label sectionLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        l.setTextFill(Color.web(TEXT_PRIMARY));
        return l;
    }

    private Separator hSep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:" + BORDER + ";");
        s.setPadding(new Insets(4, 0, 4, 0));
        return s;
    }

    private void hoverRow(HBox row) {
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:" + GREY_SOFT + "80;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color:transparent;"));
    }

    private Dialog<Void> dialog(String title, double width) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(title);
        DialogPane dp = dlg.getDialogPane();
        dp.setPrefWidth(width);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dp.lookupButton(ButtonType.OK);
        ok.setText("Сохранить");
        ok.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;");
        return dlg;
    }

    private Label dlgHeader(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        l.setTextFill(Color.web(TEXT_PRIMARY));
        return l;
    }

    private VBox formFieldDlg(String label, Control input) {
        VBox b = new VBox(5);
        Label l = new Label(label);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        l.setTextFill(Color.web(TEXT_SEC));
        input.setMaxWidth(Double.MAX_VALUE);
        b.getChildren().addAll(l, input);
        return b;
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String roleColor(String role) {
        return switch (role) {
            case "Врач" -> GREEN;
            case "Регистратор" -> ORANGE;
            case "Администратор" -> PURPLE;
            default -> ACCENT;
        };
    }

    private String roleBg(String role) {
        return switch (role) {
            case "Врач" -> GREEN_SOFT;
            case "Регистратор" -> ORANGE_SOFT;
            case "Администратор" -> PURPLE_SOFT;
            default -> BLUE_SOFT;
        };
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int pos = json.indexOf(search);
        if (pos < 0) return null;
        pos += search.length();
        while (pos < json.length() && (json.charAt(pos) == ' ' || json.charAt(pos) == ':')) pos++;
        if (pos >= json.length() || json.charAt(pos) != '"') return null;
        int start = pos + 1;
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    public static void main(String[] args) {
        launch(args);
    }
}