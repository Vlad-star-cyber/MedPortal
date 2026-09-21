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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DoctorMain extends Application {

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

    private static class DoctorAppointmentItem {
        long id;
        long patientId;
        String patientShortName;
        int ageYears;
        String time;
        String goal;
        String status;         // PENDING, CONFIRMED, etc.
        String statusLabel;
        String statusColor;
        String statusBg;
        String actionLabel;
        String actionType;     // "start", "emk", "cancel"
    }

    private static class PatientItem {
        long patientId;
        String fullName;
        String birthDate;
        String phone;
        String lastVisit;
        String policy;
        String allergy;
        String bloodType;
    }

    private static class SlotData {
        long id;
        LocalDate date;
        LocalTime start;
        LocalTime end;
        boolean booked;
        boolean blocked;
    }

    private record HistoryEntry(String date, String visitType, String diagnosis, String prescription) {
    }

    private final List<SlotData> weekSlots = new ArrayList<>();
    private long currentAppointmentId;    // id активной записи для текущего приёма

    private final ObservableList<DoctorAppointmentItem> todayAppointments = FXCollections.observableArrayList();
    private final ObservableList<PatientItem> patientList = FXCollections.observableArrayList();
    private VBox homeContentContainer;
    private Label homeCountPending;
    private Label homeCountCompleted;
    private PatientItem selectedPatient;   // текущий выбранный пациент (для ЭМК/приёма)

    private String sessionToken;
    private long currentUserId;
    private long currentDoctorId;
    private String currentDoctorName = "Врач";
    private String currentDoctorSpecialization = "";

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object sendLock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private TextArea complaintsArea;
    private TextArea examArea;
    private TextField diagnosisField;
    private TextArea prescriptionArea;
    private String currentDoctorOffice = "";

    public void setNetworkStreams(ObjectOutputStream o, ObjectInputStream i, Socket s) {
        this.out = o;
        this.in = i;
    }

    public void setUserSession(String fullName, String token, long userId,
                               String specialization, long doctorId) {
        this.currentDoctorName = fullName;
        this.sessionToken = token;
        this.currentUserId = userId;
        this.currentDoctorSpecialization = specialization;
        this.currentDoctorId = doctorId;
    }

    private void loadTodayAppointments() {
        executor.execute(() -> {
            try {
                String today = LocalDate.now().toString();
                Command cmd = Command.builder(CommandType.GET_ALL_APPOINTMENTS)
                        .token(sessionToken)
                        .param("doctorId", currentDoctorId)
                        .param("date", today)
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<DoctorAppointmentItem> list = parseDoctorAppointments(json);
                    Platform.runLater(() -> {
                        todayAppointments.setAll(list);
                        refreshHomeTable();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<DoctorAppointmentItem> parseDoctorAppointments(String json) {
        List<DoctorAppointmentItem> list = new ArrayList<>();
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
            DoctorAppointmentItem item = parseDoctorAppointment(part);
            if (item != null) list.add(item);
        }
        return list;
    }

    private DoctorAppointmentItem parseDoctorAppointment(String jsonObj) {
        try {
            DoctorAppointmentItem a = new DoctorAppointmentItem();
            a.id = extractLong(jsonObj, "id");
            a.patientId = extractLong(jsonObj, "patientId");
            a.patientShortName = extractString(jsonObj, "patientName");
            a.goal = extractString(jsonObj, "reason");
            String datetime = extractString(jsonObj, "datetime");
            if (datetime != null && datetime.contains("T")) {
                a.time = datetime.split("T")[1].substring(0, 5);
                a.ageYears = 30; // если возраст не приходит, возьмите из профиля пациента отдельным запросом
            }
            a.status = extractString(jsonObj, "status");
            switch (a.status.toUpperCase()) {
                case "PENDING" -> {
                    a.statusLabel = "В ожидании";
                    a.statusColor = ORANGE;
                    a.statusBg = ORANGE_SOFT;
                    a.actionLabel = "Начать приём";
                    a.actionType = "start";
                }
                case "CONFIRMED" -> {
                    a.statusLabel = "Подтверждён";
                    a.statusColor = ACCENT;
                    a.statusBg = BLUE_SOFT;
                    a.actionLabel = "Открыть ЭМК";
                    a.actionType = "emk";
                }
                case "COMPLETED" -> {
                    a.statusLabel = "Завершён";
                    a.statusColor = GREEN;
                    a.statusBg = GREEN_SOFT;
                    a.actionLabel = "Протокол";
                    a.actionType = "emk";
                }
                case "CANCELLED", "MISSED" -> {
                    a.statusLabel = "Неявка";
                    a.statusColor = RED;
                    a.statusBg = RED_SOFT;
                    a.actionLabel = "Отменить";
                    a.actionType = "cancel";
                }
                default -> {
                    a.statusLabel = a.status;
                    a.statusColor = TEXT_MUTED;
                    a.statusBg = GREY_SOFT;
                    a.actionLabel = "Действие";
                    a.actionType = "start";
                }
            }
            if (a.patientShortName == null) a.patientShortName = "Пациент";
            return a;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadPatients() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_ALL_APPOINTMENTS)
                        .token(sessionToken)
                        .param("doctorId", currentDoctorId)
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<PatientItem> items = parsePatientsFromAppointments(json);
                    Platform.runLater(() -> {
                        patientList.setAll(items);
                        if ("patients".equals(activePage)) root.setCenter(buildPatientsPage());
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<PatientItem> parsePatientsFromAppointments(String json) {
        Map<Long, PatientItem> map = new HashMap<>();
        if (json == null || json.isBlank()) return new ArrayList<>();
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        if (json.isBlank()) return new ArrayList<>();
        String[] parts = json.split("},\\s*\\{");
        for (String part : parts) {
            part = part.trim();
            if (!part.startsWith("{")) part = "{" + part;
            if (!part.endsWith("}")) part = part + "}";
            long patientId = extractLong(part, "patientId");
            String patientName = extractString(part, "patientName");
            String datetime = extractString(part, "datetime");
            LocalDate visitDate = null;
            if (datetime != null && datetime.contains("T")) {
                try {
                    visitDate = LocalDate.parse(datetime.split("T")[0]);
                } catch (Exception e) {
                }
            }
            if (patientId > 0 && patientName != null && !map.containsKey(patientId)) {
                PatientItem pi = new PatientItem();
                pi.patientId = patientId;
                pi.fullName = patientName;
                pi.lastVisit = visitDate != null ? visitDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "";
                map.put(patientId, pi);
            } else if (map.containsKey(patientId) && visitDate != null) {
                PatientItem existing = map.get(patientId);
                LocalDate existingDate = existing.lastVisit.isEmpty() ? LocalDate.MIN :
                        LocalDate.parse(existing.lastVisit, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                if (visitDate.isAfter(existingDate)) {
                    existing.lastVisit = visitDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                }
            }
        }
        return new ArrayList<>(map.values());
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

    private long extractLong(String json, String key) {
        String s = extractString(json, key);
        if (s == null) return 0;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String activePage = "home";
    private BorderPane root;
    private VBox sidebar;

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        root.setCenter(buildPage("home"));
        loadTodayAppointments();
        loadPatients();
        Scene scene = new Scene(root, 1200, 760);
        stage.setTitle("MedPortal — Врач");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.show();
        stage.widthProperty().addListener((obs, old, newVal) -> root.layout());
    }

    private VBox buildSidebar() {
        VBox sb = new VBox();
        sb.setPrefWidth(230);
        sb.setMinWidth(230);
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
        nav.getChildren().addAll(
                sideItem("Главная", "home"),
                sideItem("Расписание", "schedule"),
                sideItem("Мои пациенты", "patients"),
                sideItem("ЭМК пациента", "emk"),
                sideItem("Текущий приём", "appointment"),
                sideItem("Настройки", "settings")
        );
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sb.getChildren().addAll(logoRow, sep, nav, spacer, buildProfileBox());
        return sb;
    }

    private HBox sideItem(String label, String pageId) {
        HBox item = new HBox();
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 14, 10, 14));
        item.setCursor(Cursor.HAND);
        item.setMaxWidth(Double.MAX_VALUE);
        boolean active = activePage.equals(pageId);
        String activeStyle = "-fx-background-color:" + BLUE_SOFT + ";-fx-background-radius:8;";
        String normalStyle = "-fx-background-color:transparent;-fx-background-radius:8;";
        item.setStyle(active ? activeStyle : normalStyle);
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", active ? FontWeight.SEMI_BOLD : FontWeight.NORMAL, 14));
        lbl.setTextFill(Color.web(active ? ACCENT : TEXT_SEC));
        item.getChildren().add(lbl);
        item.setOnMouseClicked(e -> navigate(pageId));
        if (!active) {
            String hover = "-fx-background-color:" + GREY_SOFT + ";-fx-background-radius:8;";
            item.setOnMouseEntered(e -> item.setStyle(hover));
            item.setOnMouseExited(e -> item.setStyle(normalStyle));
        }
        return item;
    }

    private HBox buildProfileBox() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color:" + GREY_SOFT + ";-fx-border-color:" + BORDER + ";-fx-border-width:1 0 0 0;");
        StackPane av = new StackPane();
        Circle ac = new Circle(20, Color.web(ACCENT));
        Label ai = new Label(currentDoctorName.substring(0, Math.min(2, currentDoctorName.length())));
        ai.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        ai.setTextFill(Color.WHITE);
        av.getChildren().addAll(ac, ai);
        VBox info = new VBox(1);
        Label name = new Label(currentDoctorName);
        name.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        name.setTextFill(Color.web(TEXT_PRIMARY));
        Label role = new Label(currentDoctorSpecialization.isEmpty() ? "Врач" : currentDoctorSpecialization);
        role.setFont(Font.font("Segoe UI", 11));
        role.setTextFill(Color.web(TEXT_MUTED));
        info.getChildren().addAll(name, role);
        box.getChildren().addAll(av, info);
        return box;
    }

    private void navigate(String pageId) {
        activePage = pageId;
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        Node page = buildPage(pageId);
        root.setCenter(page);
        if ("home".equals(pageId)) loadTodayAppointments();
        if ("patients".equals(pageId)) loadPatients();
        if ("schedule".equals(pageId)) loadSchedule();
    }

    private Node buildPage(String pageId) {
        return switch (pageId) {
            case "home" -> buildHomePage();
            case "schedule" -> buildSchedulePage();
            case "patients" -> buildPatientsPage();
            case "emk" -> buildEmkPage();
            case "appointment" -> buildAppointmentPage();
            case "settings" -> buildSettingsPage();
            default -> buildHomePage();
        };
    }

    private ScrollPane buildSettingsPage() {
        VBox inner = new VBox(24);
        inner.setPadding(new Insets(32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");

        Label title = pageTitle("Настройки профиля");

        VBox card = new VBox(16);
        card.setPadding(new Insets(28));
        card.setStyle("-fx-background-color:" + CARD_BG + ";" +
                "-fx-background-radius:14;" +
                "-fx-border-color:" + BORDER + ";" +
                "-fx-border-radius:14;" +
                "-fx-border-width:1;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");

        TextField nameField = styledField("ФИО");
        nameField.setText(currentDoctorName);

        TextField officeField = styledField("Номер кабинета");
        officeField.setText(currentDoctorOffice);   // о том, как получить currentDoctorOffice, см. ниже

        PasswordField oldPass = new PasswordField();
        oldPass.setPromptText("Текущий пароль (оставьте пустым, если не меняете)");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("Новый пароль (минимум 8 символов)");
        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Подтверждение нового пароля");

        String fieldStyle = "-fx-background-color:" + BG_PAGE + ";" +
                "-fx-border-color:" + BORDER + ";" +
                "-fx-border-radius:9;" +
                "-fx-background-radius:9;" +
                "-fx-border-width:1.5;" +
                "-fx-font-size:13px;" +
                "-fx-font-family:'Segoe UI';" +
                "-fx-padding:8 12 8 12;" +
                "-fx-text-fill:" + TEXT_PRIMARY + ";";
        oldPass.setStyle(fieldStyle);
        newPass.setStyle(fieldStyle);
        confirmPass.setStyle(fieldStyle);
        oldPass.setMaxWidth(Double.MAX_VALUE);
        newPass.setMaxWidth(Double.MAX_VALUE);
        confirmPass.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(16);
        form.getChildren().addAll(
                sectionLabel("Имя"),
                nameField,
                sectionLabel("Кабинет"),
                officeField,
                sectionLabel("Смена пароля"),
                oldPass,
                newPass,
                confirmPass
        );

        card.getChildren().add(form);

        Button saveBtn = filledBtn("Сохранить изменения", ACCENT);
        saveBtn.setOnAction(e -> saveDoctorSettings(nameField, officeField, oldPass, newPass, confirmPass));

        inner.getChildren().addAll(title, card, saveBtn);

        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");
        return scroll;
    }

    private void saveDoctorSettings(TextField nameField, TextField officeField,
                                    PasswordField oldPass, PasswordField newPass, PasswordField confirmPass) {
        String newName = nameField.getText().trim();
        String newOffice = officeField.getText().trim();
        String oldPassword = oldPass.getText();
        String newPassword = newPass.getText();
        String confirm = confirmPass.getText();

        if (newName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Введите имя", "");
            return;
        }
        if (!newPassword.isEmpty()) {
            if (oldPassword.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Введите текущий пароль", "");
                return;
            }
            if (newPassword.length() < 8) {
                showAlert(Alert.AlertType.WARNING, "Пароль должен быть не менее 8 символов", "");
                return;
            }
            if (!newPassword.equals(confirm)) {
                showAlert(Alert.AlertType.WARNING, "Пароли не совпадают", "");
                return;
            }
        }

        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.UPDATE_DOCTOR)
                        .token(sessionToken)
                        .param("doctorId", String.valueOf(currentDoctorId))
                        .param("fullName", newName)
                        .param("officeNumber", newOffice)
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (!(resp instanceof Command response) || !response.isOk()) {
                    String msg = (resp instanceof Command) ? ((Command) resp).getMessage() : "Ошибка";
                    Platform.runLater(() ->
                            showAlert(Alert.AlertType.ERROR, "Ошибка обновления профиля", msg));
                    return;
                }

                if (!newPassword.isEmpty()) {
                    String oldHash = sha256(oldPassword);
                    String newHash = sha256(newPassword);
                    Command passCmd = Command.builder(CommandType.CHANGE_PASSWORD)
                            .token(sessionToken)
                            .param("oldPassword", oldHash)
                            .param("newPassword", newHash)
                            .build();
                    synchronized (sendLock) {
                        out.writeObject(passCmd);
                        out.flush();
                        out.reset();
                    }
                    resp = in.readObject();
                    if (!(resp instanceof Command passResp) || !passResp.isOk()) {
                        String msg = (resp instanceof Command) ? ((Command) resp).getMessage() : "Ошибка";
                        Platform.runLater(() ->
                                showAlert(Alert.AlertType.ERROR, "Ошибка смены пароля", msg));
                        return;
                    }
                }

                Platform.runLater(() -> {
                    currentDoctorName = newName;
                    currentDoctorOffice = newOffice;
                    sidebar = buildSidebar();
                    root.setLeft(sidebar);
                    showAlert(Alert.AlertType.INFORMATION, "Успех", "Профиль обновлён.");
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Ошибка сети", ""));
            }
        });
    }

    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle(
                "-fx-background-color:" + BG_PAGE + ";" +
                        "-fx-border-color:" + BORDER + ";" +
                        "-fx-border-radius:9;" +
                        "-fx-background-radius:9;" +
                        "-fx-border-width:1.5;" +
                        "-fx-font-size:13px;" +
                        "-fx-font-family:'Segoe UI';" +
                        "-fx-padding:8 12 8 12;" +
                        "-fx-text-fill:" + TEXT_PRIMARY + ";"
        );
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        l.setTextFill(Color.web(TEXT_PRIMARY));
        return l;
    }

    private Button filledBtn(String text, String color) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        b.setTextFill(Color.WHITE);
        b.setCursor(Cursor.HAND);
        b.setPadding(new Insets(10, 20, 10, 20));
        b.setStyle(
                "-fx-background-color:" + color + ";" +
                        "-fx-background-radius:10;" +
                        "-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.25),8,0,0,3);"
        );
        b.setOnMouseEntered(e -> b.setOpacity(0.87));
        b.setOnMouseExited(e -> b.setOpacity(1.0));
        return b;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException("SHA-256 недоступен", ex);
        }
    }

    private ScrollPane buildHomePage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");
        Label title = pageTitle("Мои приёмы на " + LocalDate.now().getDayOfMonth() + " " +
                LocalDate.now().getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru")));
        HBox counters = new HBox(12);
        counters.setPadding(new Insets(16, 20, 16, 20));
        counters.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;"
                + "-fx-border-color:" + BORDER + ";-fx-border-radius:12;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),8,0,0,2);");
        homeCountPending = new Label();
        homeCountPending.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        homeCountPending.setPadding(new Insets(5, 14, 5, 14));
        homeCountCompleted = new Label();
        homeCountCompleted.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        homeCountCompleted.setPadding(new Insets(5, 14, 5, 14));
        counters.getChildren().addAll(homeCountPending, homeCountCompleted);

        VBox card = new VBox(0);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");
        Label tableTitle = new Label("Ближайшие пациенты");
        tableTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        tableTitle.setTextFill(Color.web(TEXT_PRIMARY));
        tableTitle.setPadding(new Insets(20, 22, 16, 22));

        HBox header = new HBox();
        header.setPadding(new Insets(10, 22, 10, 22));
        header.setStyle("-fx-background-color:" + GREY_SOFT + ";");
        for (String col : new String[]{"Время", "Пациент", "Цель", "Статус", "Действие"}) {
            Label l = new Label(col);
            l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
            l.setTextFill(Color.web(TEXT_MUTED));
            HBox.setHgrow(l, Priority.ALWAYS);
            l.setMaxWidth(Double.MAX_VALUE);
            header.getChildren().add(l);
        }
        card.getChildren().addAll(tableTitle, header);
        homeContentContainer = new VBox(0);
        card.getChildren().add(homeContentContainer);

        inner.getChildren().addAll(title, counters, card);
        refreshHomeTable();

        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");
        return scroll;
    }

    private void refreshHomeTable() {
        if (homeContentContainer == null) return;
        homeContentContainer.getChildren().clear();
        if (todayAppointments.isEmpty()) {
            Label empty = new Label("На сегодня приёмов нет");
            empty.setFont(Font.font("Segoe UI", 14));
            empty.setTextFill(Color.web(TEXT_MUTED));
            empty.setPadding(new Insets(20, 22, 20, 22));
            homeContentContainer.getChildren().add(empty);
        } else {
            for (DoctorAppointmentItem a : todayAppointments) {
                homeContentContainer.getChildren().add(appointmentRow(a));
            }
        }
        long pending = todayAppointments.stream()
                .filter(a -> a.status.equals("PENDING") || a.status.equals("CONFIRMED")).count();
        long completed = todayAppointments.stream()
                .filter(a -> a.status.equals("COMPLETED")).count();
        if (homeCountPending != null) {
            homeCountPending.setText("Ожидают: " + pending);
            homeCountPending.setStyle("-fx-background-color:" + ORANGE_SOFT + ";-fx-background-radius:20;");
        }
        if (homeCountCompleted != null) {
            homeCountCompleted.setText("Принято: " + completed);
            homeCountCompleted.setStyle("-fx-background-color:" + GREEN_SOFT + ";-fx-background-radius:20;");
        }
    }

    private VBox appointmentRow(DoctorAppointmentItem a) {
        VBox wrapper = new VBox(0);
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(16, 22, 16, 22));
        row.setCursor(Cursor.HAND);
        String hoverStyle = "-fx-background-color:" + GREY_SOFT + "80;";
        row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color:transparent;"));
        Label time = new Label(a.time);
        time.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        time.setTextFill(Color.web(TEXT_PRIMARY));
        time.setMinWidth(100);
        time.setMaxWidth(100);
        row.getChildren().add(time);
        Label patient = new Label(a.patientShortName);
        patient.setFont(Font.font("Segoe UI", 14));
        patient.setTextFill(Color.web(TEXT_PRIMARY));
        HBox.setHgrow(patient, Priority.ALWAYS);
        patient.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(patient);
        Label goal = new Label(a.goal);
        goal.setFont(Font.font("Segoe UI", 14));
        goal.setTextFill(Color.web(TEXT_SEC));
        HBox.setHgrow(goal, Priority.ALWAYS);
        goal.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(goal);
        Label status = new Label(a.statusLabel);
        status.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        status.setTextFill(Color.web(a.statusColor));
        status.setPadding(new Insets(3, 12, 3, 12));
        status.setStyle("-fx-background-color:" + a.statusBg + ";-fx-background-radius:20;");
        HBox statusBox = new HBox(status);
        statusBox.setMinWidth(160);
        statusBox.setMaxWidth(160);
        row.getChildren().add(statusBox);
        Button btn = actionButton(a);
        row.getChildren().add(btn);
        wrapper.getChildren().add(row);
        return wrapper;
    }

    private Button actionButton(DoctorAppointmentItem a) {
        Button btn = new Button(a.actionLabel);
        btn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        btn.setCursor(Cursor.HAND);
        btn.setPadding(new Insets(8, 18, 8, 18));
        switch (a.actionType) {
            case "start" -> {
                btn.setTextFill(Color.WHITE);
                btn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;");
                btn.setOnAction(e -> {
                    if (a.id == 0) {
                        showAlert(Alert.AlertType.WARNING, "Ошибка", "Некорректный идентификатор записи.");
                        return;
                    }
                    currentAppointmentId = a.id;
                    loadPatientAndNavigate(a.patientId);
                });
            }
            case "emk" -> {
                btn.setTextFill(Color.web(ACCENT));
                btn.setStyle("-fx-background-color:" + CARD_BG + ";-fx-border-color:" + ACCENT + ";-fx-border-radius:9;");
                btn.setOnAction(e -> {
                    selectedPatient = findPatientById(a.patientId);
                    if (selectedPatient != null) navigate("emk");
                });
            }
            case "cancel" -> {
                btn.setTextFill(Color.web(RED));
                btn.setStyle("-fx-background-color:" + CARD_BG + ";-fx-border-color:" + RED + ";-fx-border-radius:9;");
                btn.setOnAction(e -> showCancelConfirm(a));
            }
        }
        return btn;
    }

    private PatientItem findPatientById(long patientId) {
        return patientList.stream().filter(p -> p.patientId == patientId).findFirst().orElse(null);
    }

    private ScrollPane buildSchedulePage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");

        Label title = pageTitle("Расписание (Неделя)");

        VBox card = new VBox(0);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");

        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        if (today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) monday = today.plusDays(1);
        List<LocalDate> weekDays = new ArrayList<>();
        for (int i = 0; i < 6; i++) weekDays.add(monday.plusDays(i));

        Label monthLabel = new Label(monday.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"))
                + " " + monday.getYear());
        monthLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        monthLabel.setTextFill(Color.web(TEXT_PRIMARY));
        monthLabel.setPadding(new Insets(18, 22, 0, 22));

        HBox weekNav = new HBox(12);
        weekNav.setAlignment(Pos.CENTER_LEFT);
        weekNav.setPadding(new Insets(0, 22, 14, 22));
        Label weekRange = new Label(monday.format(DateTimeFormatter.ofPattern("dd.MM")) + " – "
                + monday.plusDays(5).format(DateTimeFormatter.ofPattern("dd.MM")) + " " + monday.getYear());
        weekRange.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        weekRange.setTextFill(Color.web(TEXT_SEC));
        weekNav.getChildren().add(weekRange);

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(0, 22, 22, 22));
        grid.setHgap(8);
        grid.setVgap(6);

        for (int d = 0; d < weekDays.size(); d++) {
            LocalDate date = weekDays.get(d);
            String dayText = date.getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, new Locale("ru"))
                    + " " + date.getDayOfMonth();
            Label dayLbl = new Label(dayText);
            dayLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            dayLbl.setTextFill(Color.web(date.equals(today) ? ACCENT : TEXT_SEC));
            dayLbl.setAlignment(Pos.CENTER);
            dayLbl.setMaxWidth(Double.MAX_VALUE);
            dayLbl.setPadding(new Insets(4));
            if (date.equals(today)) {
                dayLbl.setStyle("-fx-background-color:" + BLUE_SOFT + ";-fx-background-radius:8;");
            }
            grid.add(dayLbl, d + 1, 0);
            GridPane.setHgrow(dayLbl, Priority.ALWAYS);
        }

        TreeSet<LocalTime> uniqueTimes = new TreeSet<>();
        for (SlotData s : weekSlots) uniqueTimes.add(s.start);
        if (uniqueTimes.isEmpty()) {
            String[] defaultTimes = {"09:00", "09:30", "10:00", "10:30", "11:00", "11:30",
                    "14:00", "14:30", "15:00", "15:30", "16:00"};
            for (String t : defaultTimes) uniqueTimes.add(LocalTime.parse(t));
        }
        List<LocalTime> timesList = new ArrayList<>(uniqueTimes);

        for (int t = 0; t < timesList.size(); t++) {
            LocalTime time = timesList.get(t);
            Label timeLbl = new Label(time.toString());
            timeLbl.setFont(Font.font("Segoe UI", 11));
            timeLbl.setTextFill(Color.web(TEXT_MUTED));
            timeLbl.setMinWidth(50);
            grid.add(timeLbl, 0, t + 1);

            for (int d = 0; d < weekDays.size(); d++) {
                LocalDate date = weekDays.get(d);
                SlotData slot = null;
                for (SlotData s : weekSlots) {
                    if (s.date.equals(date) && s.start.equals(time)) {
                        slot = s;
                        break;
                    }
                }
                StackPane slotPane = buildSlotPane(slot, date.equals(today));
                grid.add(slotPane, d + 1, t + 1);
                GridPane.setHgrow(slotPane, Priority.ALWAYS);
            }
        }

        card.getChildren().addAll(monthLabel, weekNav, grid);
        inner.getChildren().addAll(title, card);

        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");
        return scroll;
    }

    private StackPane buildSlotPane(SlotData slot, boolean isToday) {
        StackPane pane = new StackPane();
        pane.setMinHeight(34);
        pane.setCursor(Cursor.HAND);

        if (slot == null) {
            Label lbl = new Label("—");
            lbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 10));
            lbl.setTextFill(Color.web(TEXT_MUTED));
            pane.getChildren().add(lbl);
            pane.setStyle("-fx-background-color: " + BG_PAGE + ";-fx-background-radius:6;");
            return pane;
        }

        String text;
        String bgColor;
        String borderColor = BORDER;

        if (slot.blocked) {
            text = "Забл.";
            bgColor = "#F1F5F9";
            borderColor = BORDER;
        } else if (slot.booked) {
            text = "Занят";
            bgColor = BLUE_SOFT;
            borderColor = ACCENT + "33";
        } else {
            text = "Своб.";
            bgColor = isToday ? "#F0FFF4" : CARD_BG;
            borderColor = isToday ? "#6EE7B7" : BORDER;
        }

        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 10));
        if (slot.blocked) lbl.setTextFill(Color.web(TEXT_MUTED));
        else if (slot.booked) lbl.setTextFill(Color.web(ACCENT));
        else lbl.setTextFill(Color.web(GREEN));
        pane.getChildren().add(lbl);

        pane.setStyle("-fx-background-color: " + bgColor + ";-fx-background-radius:6;"
                + "-fx-border-color: " + borderColor + ";-fx-border-radius:6;"
                + "-fx-border-width: 1;");

        if (!slot.booked && !slot.blocked) {
            pane.setOnMouseClicked(e -> {
                System.out.println("Клик по слоту: " + slot.date + " " + slot.start);
            });
            pane.setOnMouseEntered(e -> pane.setStyle("-fx-background-color:" + BLUE_SOFT
                    + ";-fx-background-radius:6;" + "-fx-border-color:" + ACCENT + ";"
                    + "-fx-border-width:1.5;"));
            String finalBorderColor = borderColor;
            pane.setOnMouseExited(e -> pane.setStyle("-fx-background-color:" + bgColor
                    + ";-fx-background-radius:6;" + "-fx-border-color:" + finalBorderColor + ";"
                    + "-fx-border-width:1;"));
        }

        return pane;
    }

    private ScrollPane buildPatientsPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");
        Label title = pageTitle("База пациентов");
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(16, 22, 16, 22));
        searchBox.setStyle("-fx-border-color:" + BORDER + ";-fx-border-width:0 0 1 0;");
        Label si = new Label("🔍");
        TextField sf = new TextField();
        sf.setPromptText("Поиск...");
        sf.setPrefWidth(500);
        HBox.setHgrow(sf, Priority.ALWAYS);
        searchBox.getChildren().addAll(si, sf);
        card.getChildren().add(searchBox);

        HBox hdr = new HBox();
        hdr.setPadding(new Insets(12, 22, 12, 22));
        hdr.setStyle("-fx-background-color:" + GREY_SOFT + ";");
        for (String col : new String[]{"ФИО", "Последний визит", "Телефон", ""}) {
            Label l = new Label(col);
            l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
            l.setTextFill(Color.web(TEXT_MUTED));
            HBox.setHgrow(l, Priority.ALWAYS);
            l.setMaxWidth(Double.MAX_VALUE);
            hdr.getChildren().add(l);
        }
        card.getChildren().add(hdr);

        VBox listContainer = new VBox(0);
        for (PatientItem p : patientList) listContainer.getChildren().add(patientRow(p));
        card.getChildren().add(listContainer);

        inner.getChildren().addAll(title, card);
        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");
        return scroll;
    }

    private HBox patientRow(PatientItem p) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(16, 22, 16, 22));
        row.setCursor(Cursor.HAND);
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:" + GREY_SOFT + "80;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color:transparent;"));
        Label nameLbl = new Label(p.fullName);
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        nameLbl.setTextFill(Color.web(TEXT_PRIMARY));
        HBox.setHgrow(nameLbl, Priority.ALWAYS);
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(nameLbl);
        Label visitLbl = new Label(p.lastVisit);
        visitLbl.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        visitLbl.setTextFill(Color.web(TEXT_SEC));
        HBox.setHgrow(visitLbl, Priority.ALWAYS);
        visitLbl.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(visitLbl);
        Label phoneLbl = new Label(p.phone);
        phoneLbl.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        phoneLbl.setTextFill(Color.web(TEXT_SEC));
        HBox.setHgrow(phoneLbl, Priority.ALWAYS);
        phoneLbl.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(phoneLbl);
        Button cardBtn = new Button("Карта");
        cardBtn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        cardBtn.setTextFill(Color.web(ACCENT));
        cardBtn.setCursor(Cursor.HAND);
        cardBtn.setPadding(new Insets(6, 16, 6, 16));
        cardBtn.setStyle("-fx-background-color:" + CARD_BG + ";-fx-border-color:" + ACCENT + ";"
                + "-fx-border-radius:8;-fx-background-radius:8;-fx-border-width:1.5;");
        cardBtn.setOnAction(e -> {
            selectedPatient = p;
            navigate("emk");
        });
        row.getChildren().add(cardBtn);
        return row;
    }

    private ScrollPane buildEmkPage() {
        VBox inner = new VBox(22);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");
        if (selectedPatient == null) {
            inner.getChildren().add(new Label("Пациент не выбран"));
            return new ScrollPane(inner);
        }

        Label title = pageTitle("Медицинская карта: " + selectedPatient.fullName);

        VBox patientCard = new VBox(6);
        patientCard.setPadding(new Insets(22));
        patientCard.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");

        Label patName = new Label(selectedPatient.fullName);
        patName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        patName.setTextFill(Color.web(TEXT_PRIMARY));

        Label phoneLbl = new Label("Телефон: загрузка…");
        Label policyLbl = new Label("Полис: загрузка…");
        Label allergyLbl = new Label("Аллергия: загрузка…");
        Label bloodLbl = new Label("Группа крови: загрузка…");
        VBox infoBox = new VBox(4, phoneLbl, policyLbl, allergyLbl, bloodLbl);
        patientCard.getChildren().addAll(patName, infoBox);

        VBox historyCard = new VBox(0);
        historyCard.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");
        Label historyTitle = new Label("История посещений");
        historyTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        historyTitle.setTextFill(Color.web(TEXT_PRIMARY));
        historyTitle.setPadding(new Insets(20, 22, 16, 22));
        historyCard.getChildren().add(historyTitle);
        VBox historyList = new VBox(4);
        historyList.setPadding(new Insets(0, 22, 22, 22));
        historyCard.getChildren().add(historyList);

        inner.getChildren().addAll(title, patientCard, historyCard);

        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");

        loadPatientProfile(selectedPatient.patientId, phoneLbl, policyLbl, allergyLbl, bloodLbl);
        loadPatientHistory(selectedPatient.patientId, historyList);

        return scroll;
    }

    private void loadPatientProfile(long patientId, Label phoneLbl, Label policyLbl,
                                    Label allergyLbl, Label bloodLbl) {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_PATIENT_BY_ID)
                        .token(sessionToken)
                        .param("patientId", String.valueOf(patientId))
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    String phone = extractString(json, "phone");
                    String policy = extractString(json, "policy");
                    String allergy = extractString(json, "allergy");
                    String bloodType = extractString(json, "bloodType");
                    Platform.runLater(() -> {
                        phoneLbl.setText("Телефон: " + (phone != null && !phone.isEmpty() ? phone : "—"));
                        policyLbl.setText("Полис: " + (policy != null && !policy.isEmpty() ? policy : "—"));
                        allergyLbl.setText("Аллергия: " + (allergy != null && !allergy.isEmpty() ? allergy : "—"));
                        bloodLbl.setText("Группа крови: " + (bloodType != null && !bloodType.isEmpty() ? bloodType : "—"));
                    });
                }
                System.out.println("Запросили профиль для patientId=" + patientId);
                if (resp instanceof Command response) {
                    System.out.println("Ответ: " + response.getStatus() + " " + response.getParam("json"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void loadPatientHistory(long patientId, VBox listContainer) {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_HISTORY)
                        .token(sessionToken)
                        .param("patientId", String.valueOf(patientId))
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<HistoryEntry> entries = parseHistory(json);
                    Platform.runLater(() -> {
                        listContainer.getChildren().clear();
                        if (entries.isEmpty()) {
                            listContainer.getChildren().add(new Label("Посещений не найдено."));
                        } else {
                            for (HistoryEntry e : entries) {
                                VBox entryBox = new VBox(4);
                                entryBox.setPadding(new Insets(8, 0, 8, 0));
                                Label dateLbl = new Label(e.date);
                                dateLbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
                                dateLbl.setTextFill(Color.web(TEXT_PRIMARY));
                                Label typeLbl = new Label("Приём: " + e.visitType);
                                typeLbl.setFont(Font.font("Segoe UI", 12));
                                typeLbl.setTextFill(Color.web(TEXT_SEC));
                                entryBox.getChildren().addAll(dateLbl, typeLbl);
                                if (!e.diagnosis.equals("—")) {
                                    Label diag = new Label("Диагноз: " + e.diagnosis);
                                    diag.setFont(Font.font("Segoe UI", 12));
                                    diag.setTextFill(Color.web(TEXT_MUTED));
                                    entryBox.getChildren().add(diag);
                                }
                                if (!e.prescription.equals("—")) {
                                    Label presc = new Label("Назначения: " + e.prescription);
                                    presc.setFont(Font.font("Segoe UI", 12));
                                    presc.setTextFill(Color.web(TEXT_MUTED));
                                    entryBox.getChildren().add(presc);
                                }
                                Separator sep = new Separator();
                                sep.setPadding(new Insets(4, 0, 0, 0));
                                listContainer.getChildren().addAll(entryBox, sep);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<HistoryEntry> parseHistory(String json) {
        List<HistoryEntry> list = new ArrayList<>();
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
            String datetime = extractString(part, "datetime");
            String spec = extractString(part, "specialization");
            String doctorName = extractString(part, "doctorName");
            String diagnosis = extractString(part, "diagnosis");
            String prescription = extractString(part, "prescription");
            String date = "";
            if (datetime != null && datetime.contains("T")) {
                date = datetime.split("T")[0];
            }
            if (diagnosis == null) diagnosis = "—";
            if (prescription == null) prescription = "—";
            String visitType = (spec != null ? spec : "") + (doctorName != null ? " (" + doctorName + ")" : "");
            list.add(new HistoryEntry(date, visitType, diagnosis, prescription));
        }
        return list;
    }

    private void loadPatientAndNavigate(long patientId) {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_PATIENT_BY_ID)
                        .token(sessionToken)
                        .param("patientId", String.valueOf(patientId))
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    PatientItem pi = new PatientItem();
                    pi.patientId = patientId;
                    pi.fullName = extractString(json, "fullName");
                    pi.phone = extractString(json, "phone");
                    pi.policy = extractString(json, "policy");
                    pi.allergy = extractString(json, "allergy");
                    pi.bloodType = extractString(json, "bloodType");
                    Platform.runLater(() -> {
                        selectedPatient = pi;
                        navigate("appointment");
                    });
                } else {
                    Platform.runLater(() ->
                            showAlert(Alert.AlertType.WARNING, "Ошибка", "Не удалось загрузить данные пациента."));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Ошибка", "Сетевая ошибка при загрузке пациента."));
            }
        });
    }

    private ScrollPane buildAppointmentPage() {
        VBox inner = new VBox(20);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.setStyle("-fx-background-color:" + BG_PAGE + ";");
        if (selectedPatient == null) {
            inner.getChildren().add(new Label("Сначала выберите пациента"));
            ScrollPane scroll = new ScrollPane(inner);
            scroll.setFitToWidth(true);
            return scroll;
        }
        Label title = pageTitle("Протокол приёма — " + selectedPatient.fullName);
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),12,0,0,3);");
        VBox form = new VBox(0);
        form.setPadding(new Insets(24, 28, 24, 28));
        complaintsArea = new TextArea();
        complaintsArea.setPromptText("Жалобы...");
        complaintsArea.setPrefHeight(100);
        examArea = new TextArea();
        examArea.setPromptText("Осмотр...");
        examArea.setPrefHeight(100);
        diagnosisField = new TextField();
        diagnosisField.setPromptText("Диагноз МКБ-10...");
        prescriptionArea = new TextArea();
        prescriptionArea.setPromptText("Назначения...");
        prescriptionArea.setPrefHeight(100);
        form.getChildren().addAll(formSection("Жалобы"), styledTextArea(complaintsArea),
                formSep(), formSection("Осмотр"), styledTextArea(examArea),
                formSep(), formSection("Диагноз"), styledTextField(diagnosisField),
                formSep(), formSection("Назначения"), styledTextArea(prescriptionArea));
        card.getChildren().add(form);
        HBox btnBar = new HBox(12);
        btnBar.setPadding(new Insets(22, 28, 22, 28));
        btnBar.setAlignment(Pos.CENTER_LEFT);
        btnBar.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:0 0 14 14;"
                + "-fx-border-color:" + BORDER + ";-fx-border-width:1 0 0 0;");
        Button finishBtn = new Button("Завершить приём");
        finishBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        finishBtn.setTextFill(Color.WHITE);
        finishBtn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:10;");
        finishBtn.setOnAction(e -> saveDiagnosis());
        btnBar.getChildren().add(finishBtn);
        card.getChildren().add(btnBar);
        inner.getChildren().addAll(title, card);
        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");
        return scroll;
    }

    private void saveDiagnosis() {
        if (selectedPatient == null || currentAppointmentId == 0) {
            showAlert(Alert.AlertType.WARNING, "Не выбран пациент или запись", "");
            return;
        }
        String complaints = complaintsArea.getText().trim();
        String exam = examArea.getText().trim();
        String diagnosis = diagnosisField.getText().trim();
        String prescription = prescriptionArea.getText().trim();
        if (diagnosis.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Введите диагноз", "");
            return;
        }

        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.SAVE_DIAGNOSIS)
                        .token(sessionToken)
                        .param("appointmentId", String.valueOf(currentAppointmentId))
                        .param("diagnosisCode", diagnosis)
                        .param("diagnosisText", diagnosis)
                        .param("prescription", prescription)
                        .param("complaints", complaints)
                        .param("examination", exam)
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                Platform.runLater(() -> {
                    if (resp instanceof Command response && response.isOk()) {
                        showAlert(Alert.AlertType.INFORMATION, "Приём завершён", "Диагноз сохранён, слот освобождён.");
                        currentAppointmentId = 0;   // сбросить
                        navigate("home");           // вернуться на главную
                    } else {
                        String msg = (resp instanceof Command) ? ((Command) resp).getMessage() : "Ошибка";
                        showAlert(Alert.AlertType.ERROR, "Ошибка", msg);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private Label formSection(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        l.setTextFill(Color.web(TEXT_PRIMARY));
        l.setPadding(new Insets(0, 0, 8, 0));
        return l;
    }

    private Separator formSep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:" + BORDER + ";");
        s.setPadding(new Insets(16, 0, 16, 0));
        return s;
    }

    private TextArea styledTextArea(TextArea ta) {
        ta.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER
                + ";-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;"
                + "-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        ta.setMaxWidth(Double.MAX_VALUE);
        return ta;
    }

    private TextField styledTextField(TextField tf) {
        tf.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER
                + ";-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;"
                + "-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-padding:10 14;"
                + "-fx-text-fill:" + TEXT_PRIMARY + ";");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private Label pageTitle(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        l.setTextFill(Color.web(TEXT_PRIMARY));
        return l;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    private void showCancelConfirm(DoctorAppointmentItem a) {
        showAlert(Alert.AlertType.CONFIRMATION, "Отмена", "Отменить приём?");
    }

    private void loadSchedule() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        if (today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            monday = today.plusDays(1);
        }
        LocalDate saturday = monday.plusDays(5); // Пн–Сб
        final String from = monday.toString();
        final String to = saturday.toString();

        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_DOCTOR_SCHEDULE)
                        .token(sessionToken)
                        .param("doctorId", currentDoctorId)
                        .param("from", from)
                        .param("to", to)
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("slots");
                    System.out.println("Слоты расписания: " + json); // отладка
                    List<SlotData> slots = parseSlots(json);
                    Platform.runLater(() -> {
                        weekSlots.clear();
                        weekSlots.addAll(slots);
                        root.setCenter(buildSchedulePage()); // обновляем страницу
                    });
                } else {
                    System.out.println("Ошибка загрузки расписания");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<SlotData> parseSlots(String json) {
        List<SlotData> list = new ArrayList<>();
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
            SlotData s = new SlotData();
            s.id = Long.parseLong(extractString(part, "id"));
            s.date = LocalDate.parse(extractString(part, "date"));
            s.start = LocalTime.parse(extractString(part, "start"));
            s.end = LocalTime.parse(extractString(part, "end"));
            s.booked = Boolean.parseBoolean(extractString(part, "booked"));
            s.blocked = Boolean.parseBoolean(extractString(part, "blocked"));
            list.add(s);
        }
        return list;
    }
}