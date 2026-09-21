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
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import org.example.common.Command;
import org.example.common.CommandType;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Appointments extends Application {

    private static final String ACCENT = "#3B82F6";
    private static final String BG_PAGE = "#F5F7FA";
    private static final String CARD_BG = "#FFFFFF";
    private static final String TEXT_PRIMARY = "#1A1A2E";
    private static final String TEXT_SECONDARY = "#64748B";
    private static final String TEXT_MUTED = "#94A3B8";
    private static final String BORDER_COLOR = "#E8EDF2";
    private static final String GREEN = "#10B981";
    private static final String RED = "#EF4444";
    private static final String GREY_SOFT = "#F1F5F9";

    private Label tabUpcomingLabel;
    private Label tabPastLabel;
    private Label tabCancelledLabel;

    public static class AppointmentItem {
        private long id;
        private long patientId;
        private long doctorId;
        private long slotId;
        private String datetime;
        private String status;
        private String reason;
        private String patientName;
        private String doctorName;
        private String specialization;
        private String office;

        private String icon;
        private String badgeText;
        private String badgeColor;
        private String accentColor;
        private String cardBg;
        private String date;
        private String time;

        public AppointmentItem() {}

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public long getPatientId() { return patientId; }
        public void setPatientId(long patientId) { this.patientId = patientId; }
        public long getDoctorId() { return doctorId; }
        public void setDoctorId(long doctorId) { this.doctorId = doctorId; }
        public long getSlotId() { return slotId; }
        public void setSlotId(long slotId) { this.slotId = slotId; }
        public String getDatetime() { return datetime; }
        public void setDatetime(String datetime) {
            this.datetime = datetime;
            if (datetime != null && datetime.contains("T")) {
                String[] parts = datetime.split("T");
                this.date = parts[0];
                this.time = parts[1].substring(0, Math.min(5, parts[1].length()));
            }
        }
        public String getStatus() { return status; }
        public void setStatus(String status) {
            this.status = status;
            updateStatusDisplay();
        }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getPatientName() { return patientName; }
        public void setPatientName(String patientName) { this.patientName = patientName; }
        public String getDoctorName() { return doctorName; }
        public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
        public String getSpecialization() { return specialization; }
        public void setSpecialization(String specialization) {
            this.specialization = specialization;
            this.icon = getIconForSpecialization(specialization);
        }
        public String getOffice() { return office; }
        public void setOffice(String office) { this.office = office; }
        public String getIcon() { return icon; }
        public String getBadgeText() { return badgeText; }
        public String getBadgeColor() { return badgeColor; }
        public String getAccentColor() { return accentColor; }
        public String getCardBg() { return cardBg; }
        public String getDate() { return date; }
        public String getTime() { return time; }

        private void updateStatusDisplay() {
            if (status == null) {
                badgeText = ""; badgeColor = "#94A3B8"; accentColor = "#64748B"; cardBg = "#F1F5F9";
                return;
            }
            switch (status.toUpperCase()) {
                case "PENDING":
                    badgeText = "Ожидает"; badgeColor = "#F59E0B"; accentColor = "#F59E0B"; cardBg = "#FEF3C7"; break;
                case "CONFIRMED":
                    badgeText = "Подтверждено"; badgeColor = "#3B82F6"; accentColor = "#3B82F6"; cardBg = "#DBEAFE"; break;
                case "COMPLETED":
                    badgeText = "Завершено"; badgeColor = "#10B981"; accentColor = "#10B981"; cardBg = "#D1FAE5"; break;
                case "CANCELLED":
                    badgeText = "Отменено"; badgeColor = "#EF4444"; accentColor = "#EF4444"; cardBg = "#FEE2E2"; break;
                case "MISSED":
                    badgeText = "Неявка"; badgeColor = "#EF4444"; accentColor = "#EF4444"; cardBg = "#FEE2E2"; break;
                default:
                    badgeText = status; badgeColor = "#94A3B8"; accentColor = "#64748B"; cardBg = "#F1F5F9";
            }
        }

        private String getIconForSpecialization(String spec) {
            if (spec == null) return "🏥";
            return switch (spec) {
                case "Кардиолог" -> "🫀";
                case "Стоматолог" -> "🦷";
                case "Терапевт" -> "🩺";
                case "Невролог" -> "🧠";
                case "Офтальмолог" -> "👁";
                case "Ортопед" -> "🦴";
                case "Эндокринолог" -> "💊";
                case "Пульмонолог" -> "🫁";
                case "Хирург" -> "🩻";
                case "Дерматолог" -> "🧴";
                default -> "🏥";
            };
        }
    }

    private String sessionToken;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object sendLock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "appointments-loader");
        t.setDaemon(true);
        return t;
    });

    private String activeTab = "upcoming";
    private VBox contentArea;
    private Label upcomingCountLabel, pastCountLabel, missedCountLabel, cancelledCountLabel;

    // ─── Динамические данные ─────────────────────────────────────
    private final ObservableList<AppointmentItem> allAppointments = FXCollections.observableArrayList();
    private final ObservableList<AppointmentItem> upcoming = FXCollections.observableArrayList();
    private final ObservableList<AppointmentItem> past = FXCollections.observableArrayList();
    private final ObservableList<AppointmentItem> cancelled = FXCollections.observableArrayList();

    // ─── Фильтры ────────────────────────────────────────────────
    private String searchQuery = "";
    private String filterSpec = "Все специализации";
    private String filterMonth = "Все месяцы";

    @Override
    public void start(Stage stage) {
        stage.setTitle("MedPortal — Мои записи");
        BorderPane root = new BorderPane();
        root.setCenter(buildMainArea());
        stage.setScene(new Scene(root, 1200, 760));
        stage.setMinWidth(900);
        stage.setMinHeight(640);
        stage.show();
    }

    public void setSessionData(String token, ObjectOutputStream out, ObjectInputStream in) {
        this.sessionToken = token;
        this.out = out;
        this.in = in;
        loadAppointments();
    }

    private void loadAppointments() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_MY_APPOINTMENTS)
                        .token(sessionToken)
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response && response.isOk()) {
                    String json = response.getParam("json");
                    List<AppointmentItem> items = parseAppointmentList(json);
                    Platform.runLater(() -> {
                        allAppointments.setAll(items);
                        applyFilters();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<AppointmentItem> parseAppointmentList(String json) {
        List<AppointmentItem> list = new ArrayList<>();
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
            AppointmentItem item = parseSingleAppointment(part);
            if (item != null) list.add(item);
        }
        return list;
    }

    private AppointmentItem parseSingleAppointment(String jsonObj) {
        try {
            AppointmentItem a = new AppointmentItem();
            a.setId(extractLong(jsonObj, "id"));
            a.setPatientId(extractLong(jsonObj, "patientId"));
            a.setDoctorId(extractLong(jsonObj, "doctorId"));
            a.setSlotId(extractLong(jsonObj, "slotId"));
            a.setDatetime(extractString(jsonObj, "datetime"));
            a.setStatus(extractString(jsonObj, "status"));
            a.setReason(extractString(jsonObj, "reason"));
            a.setPatientName(extractString(jsonObj, "patientName"));
            a.setDoctorName(extractString(jsonObj, "doctorName"));
            a.setSpecialization(extractString(jsonObj, "specialization"));
            a.setOffice(extractString(jsonObj, "office"));
            return a;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    public VBox buildMainArea() {
        VBox area = new VBox();
        area.setStyle("-fx-background-color:" + BG_PAGE + ";");

        VBox inner = new VBox(24);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.getChildren().addAll(
                buildTopBar(),
                buildStatsRow(),
                buildTabsRow(),
                buildContentArea()
        );

        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        area.getChildren().add(scroll);
        return area;
    }

    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox tb = new VBox(4);
        Label pt = new Label("Мои записи");
        pt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        pt.setTextFill(Color.web(TEXT_PRIMARY));
        Label ps = new Label("Управление вашими визитами к врачам");
        ps.setFont(Font.font("Segoe UI", 13));
        ps.setTextFill(Color.web(TEXT_MUTED));
        tb.getChildren().addAll(pt, ps);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        return bar;
    }

    private HBox buildStatsRow() {
        HBox row = new HBox(16);

        upcomingCountLabel = new Label(String.valueOf(upcoming.size()));
        upcomingCountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        upcomingCountLabel.setTextFill(Color.web(ACCENT));

        pastCountLabel = new Label(String.valueOf(past.size()));
        pastCountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        pastCountLabel.setTextFill(Color.web(GREEN));

        missedCountLabel = new Label(String.valueOf(
                cancelled.stream().filter(a -> "MISSED".equalsIgnoreCase(a.getStatus())).count()));
        missedCountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        missedCountLabel.setTextFill(Color.web(RED));

        cancelledCountLabel = new Label(String.valueOf(
                cancelled.stream().filter(a -> "CANCELLED".equalsIgnoreCase(a.getStatus())).count()));
        cancelledCountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        cancelledCountLabel.setTextFill(Color.web(TEXT_MUTED));

        row.getChildren().addAll(
                statCard("📅", "Предстоящих", upcomingCountLabel),
                statCard("✅", "Завершённых", pastCountLabel),
                statCard("❌", "Неявок", missedCountLabel),
                statCard("🚫", "Отменённых", cancelledCountLabel)
        );
        for (Node n : row.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        return row;
    }

    private VBox statCard(String icon, String label, Label valueLabel) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(18, 20, 18, 20));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;-fx-border-color:" + BORDER_COLOR
                + ";-fx-border-radius:12;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),10,0,0,2);");
        HBox top = new HBox(); top.setAlignment(Pos.CENTER_LEFT);
        Label il = new Label(icon); il.setFont(Font.font("Segoe UI", 20));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        top.getChildren().addAll(il, sp, valueLabel);
        Label ll = new Label(label);
        ll.setFont(Font.font("Segoe UI", 12));
        ll.setTextFill(Color.web(TEXT_SECONDARY));
        card.getChildren().addAll(top, ll);
        return card;
    }

    private VBox buildTabsRow() {
        VBox wrapper = new VBox(14);
        wrapper.setPadding(new Insets(16, 20, 16, 20));
        wrapper.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),10,0,0,2);");

        HBox tabs = new HBox(6);
        tabs.setAlignment(Pos.CENTER_LEFT);

        Button[] tabBtns = {
                tabBtn("Предстоящие", "upcoming"),
                tabBtn("Прошедшие", "past"),
                tabBtn("Отменённые", "cancelled")
        };
        tabUpcomingLabel  = (Label) tabBtns[0].getGraphic();
        tabPastLabel      = (Label) tabBtns[1].getGraphic();
        tabCancelledLabel = (Label) tabBtns[2].getGraphic();

        for (Button btn : tabBtns) tabs.getChildren().add(btn);

        applyTabStyle(tabBtns[0], true);
        applyTabStyle(tabBtns[1], false);
        applyTabStyle(tabBtns[2], false);

        for (int i = 0; i < tabBtns.length; i++) {
            final String tabId = List.of("upcoming", "past", "cancelled").get(i);
            Button[] all = tabBtns;
            tabBtns[i].setOnAction(e -> {
                activeTab = tabId;
                for (Button b : all) applyTabStyle(b, false);
                applyTabStyle((Button) e.getSource(), true);
                refreshContent();
            });
        }

        Separator divider = new Separator();
        divider.setStyle("-fx-background-color:" + BORDER_COLOR + ";");

        HBox filters = new HBox(12);
        filters.setAlignment(Pos.CENTER_LEFT);

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(9, 14, 9, 14));
        searchBox.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:10;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:10;-fx-border-width:1.5;");
        Label si = new Label("🔍"); si.setFont(Font.font("Segoe UI", 14));
        TextField sf = new TextField();
        sf.setPromptText("Поиск врача или специализации...");
        sf.setPrefWidth(240);
        sf.setStyle("-fx-background-color:transparent;-fx-border-width:0;"
                + "-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sf.textProperty().addListener((obs, ov, nv) -> {
            searchQuery = nv.toLowerCase().trim();
            applyFilters();
        });
        searchBox.getChildren().addAll(si, sf);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        ComboBox<String> specFilter = filterCombo(
                FXCollections.observableArrayList("Все специализации", "Кардиолог", "Стоматолог", "Терапевт",
                        "Невролог", "Офтальмолог", "Ортопед", "Эндокринолог"),
                "Все специализации", 195);
        specFilter.setOnAction(e -> {
            filterSpec = specFilter.getValue();
            applyFilters();
        });

        ComboBox<String> monthFilter = filterCombo(
                FXCollections.observableArrayList("Все месяцы", "Январь", "Февраль", "Март", "Апрель", "Май",
                        "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"),
                "Все месяцы", 155);
        monthFilter.setOnAction(e -> {
            filterMonth = monthFilter.getValue();
            applyFilters();
        });

        filters.getChildren().addAll(searchBox, specFilter, monthFilter);

        wrapper.getChildren().addAll(tabs, divider, filters);
        return wrapper;
    }

    private Button tabBtn(String text, String id) {
        Button btn = new Button();
        btn.setContentDisplay(ContentDisplay.RIGHT);
        btn.setGraphic(new Label("0"));
        ((Label) btn.getGraphic()).setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        btn.setText(text + "  ");
        btn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        btn.setCursor(Cursor.HAND);
        btn.setPadding(new Insets(8, 18, 8, 18));
        btn.setUserData(id);
        return btn;
    }

    private void applyTabStyle(Button btn, boolean active) {
        if (active) {
            btn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;"
                    + "-fx-text-fill:white;"
                    + "-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.25),8,0,0,3);");
            ((Label) btn.getGraphic()).setTextFill(Color.WHITE);
        } else {
            btn.setStyle("-fx-background-color:transparent;-fx-background-radius:9;"
                    + "-fx-text-fill:" + TEXT_SECONDARY + ";");
            ((Label) btn.getGraphic()).setTextFill(Color.web(TEXT_SECONDARY));
            btn.setOnMouseEntered(ev -> {
                if (!btn.getStyle().contains(ACCENT))
                    btn.setStyle("-fx-background-color:" + GREY_SOFT + ";-fx-background-radius:9;"
                            + "-fx-text-fill:" + TEXT_PRIMARY + ";");
            });
            btn.setOnMouseExited(ev -> {
                if (!btn.getStyle().contains(ACCENT))
                    btn.setStyle("-fx-background-color:transparent;-fx-background-radius:9;"
                            + "-fx-text-fill:" + TEXT_SECONDARY + ";");
            });
        }
    }

    private ComboBox<String> filterCombo(ObservableList<String> items, String def, double width) {
        ComboBox<String> cb = new ComboBox<>(items);
        cb.setValue(def);
        cb.setPrefWidth(width);
        cb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER_COLOR + ";"
                + "-fx-border-radius:9;-fx-background-radius:9;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");
        return cb;
    }

    private VBox buildContentArea() {
        contentArea = new VBox(12);
        refreshContent();
        return contentArea;
    }

    private void refreshContent() {
        contentArea.getChildren().clear();
        List<AppointmentItem> data = switch (activeTab) {
            case "upcoming" -> upcoming;
            case "past" -> past;
            default -> cancelled;
        };
        if (data.isEmpty()) {
            contentArea.getChildren().add(buildEmptyState());
        } else {
            for (AppointmentItem a : data) {
                contentArea.getChildren().add(buildAppointmentCard(a));
            }
        }
    }

    private VBox buildAppointmentCard(AppointmentItem a) {
        VBox outer = new VBox(0);
        outer.setCursor(Cursor.HAND);
        outer.setStyle(cardStyle(false, a.getAccentColor()));

        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(18, 22, 18, 18));

        Rectangle stripe = new Rectangle(4, 60);
        stripe.setFill(Color.web(a.getAccentColor()));
        stripe.setArcWidth(4);
        stripe.setArcHeight(4);

        StackPane avatarBox = new StackPane();
        Circle avatarBg = new Circle(24, Color.web(a.getCardBg()));
        Label avatarIcon = new Label(a.getIcon());
        avatarIcon.setFont(Font.font("Segoe UI", 20));
        avatarBox.getChildren().addAll(avatarBg, avatarIcon);

        VBox info = new VBox(4);
        info.setMinWidth(200);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label specLbl = new Label(a.getSpecialization());
        specLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        specLbl.setTextFill(Color.web(TEXT_PRIMARY));
        Label badge = new Label(a.getBadgeText());
        badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        badge.setTextFill(Color.web(a.getBadgeColor()));
        badge.setPadding(new Insets(3, 10, 3, 10));
        badge.setStyle("-fx-background-color:" + a.getBadgeColor() + "22;-fx-background-radius:20;");
        titleRow.getChildren().addAll(specLbl, badge);

        Label docLbl = new Label("Врач: " + a.getDoctorName());
        docLbl.setFont(Font.font("Segoe UI", 13));
        docLbl.setTextFill(Color.web(TEXT_SECONDARY));

        Label reasonLbl = new Label(a.getReason() != null ? a.getReason() : "");
        reasonLbl.setFont(Font.font("Segoe UI", 12));
        reasonLbl.setTextFill(Color.web(TEXT_MUTED));
        info.getChildren().addAll(titleRow, docLbl, reasonLbl);

        VBox dateBlock = new VBox(5);
        dateBlock.setAlignment(Pos.CENTER_LEFT);
        dateBlock.setMinWidth(168);
        Label dateLbl = new Label(a.getDate());
        dateLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        dateLbl.setTextFill(Color.web(a.getAccentColor()));
        Label timeLbl = new Label(a.getTime() + " • " + (a.getOffice() != null ? a.getOffice() : ""));
        timeLbl.setFont(Font.font("Segoe UI", 12));
        timeLbl.setTextFill(Color.web(TEXT_SECONDARY));
        dateBlock.getChildren().addAll(dateLbl, timeLbl);

        VBox actions = new VBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(140);

        if (activeTab.equals("upcoming")) {
            Button detailBtn = filledActionBtn("Подробнее", ACCENT);
            Button cancelBtn = outlineActionBtn("Отменить", RED);
            detailBtn.setOnAction(e -> { e.consume(); showDetailDialog(a); });
            cancelBtn.setOnAction(e -> { e.consume(); cancelAppointment(a); });
            actions.getChildren().addAll(detailBtn, cancelBtn);
        } else if (activeTab.equals("past")) {
            Button rebookBtn = filledActionBtn("Повторить", ACCENT);
            rebookBtn.setOnAction(e -> { e.consume(); });
            actions.getChildren().add(rebookBtn);
        } else {
            Button rebookBtn = filledActionBtn("Записаться снова", ACCENT);
            rebookBtn.setOnAction(e -> { e.consume(); });
            actions.getChildren().add(rebookBtn);
        }

        card.getChildren().addAll(stripe, avatarBox, info, dateBlock, actions);
        outer.getChildren().add(card);

        outer.setOnMouseEntered(e -> outer.setStyle(cardStyle(true, a.getAccentColor())));
        outer.setOnMouseExited(e -> outer.setStyle(cardStyle(false, a.getAccentColor())));
        outer.setOnMouseClicked(e -> showDetailDialog(a));
        return outer;
    }

    private String cardStyle(boolean hover, String accentColor) {
        return "-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + (hover ? accentColor + "44" : BORDER_COLOR) + ";"
                + "-fx-border-radius:14;-fx-border-width:1.5;"
                + "-fx-effect:dropshadow(gaussian," +
                (hover ? "rgba(59,130,246,0.10),16,0,0,4" : "rgba(0,0,0,0.05),10,0,0,2") + ");";
    }

    private Button filledActionBtn(String text, String color) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        b.setTextFill(Color.WHITE);
        b.setCursor(Cursor.HAND);
        b.setPadding(new Insets(7, 16, 7, 16));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color:" + color + ";-fx-background-radius:8;"
                + "-fx-effect:dropshadow(gaussian," + color + "33,6,0,0,2);");
        b.setOnMouseEntered(e -> b.setOpacity(0.85));
        b.setOnMouseExited(e -> b.setOpacity(1.0));
        return b;
    }

    private Button outlineActionBtn(String text, String color) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        b.setTextFill(Color.web(color));
        b.setCursor(Cursor.HAND);
        b.setPadding(new Insets(7, 16, 7, 16));
        b.setMaxWidth(Double.MAX_VALUE);
        String st = "-fx-background-color:" + CARD_BG + ";-fx-border-color:" + color + "44;"
                + "-fx-border-radius:8;-fx-background-radius:8;-fx-border-width:1.5;";
        b.setStyle(st);
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:" + color + "11;-fx-border-color:" + color + "44;"
                + "-fx-border-radius:8;-fx-background-radius:8;-fx-border-width:1.5;"));
        b.setOnMouseExited(e -> b.setStyle(st));
        return b;
    }

    private VBox buildEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60));
        box.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:14;-fx-border-width:1;");
        Label icon = new Label("📋");
        icon.setFont(Font.font("Segoe UI", 44));
        Label title = new Label("Записей не найдено");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(TEXT_PRIMARY));
        Label sub = new Label("В этом разделе пока нет записей.\nНажмите «+ Новая запись», чтобы записаться к врачу.");
        sub.setFont(Font.font("Segoe UI", 13));
        sub.setTextFill(Color.web(TEXT_MUTED));
        sub.setTextAlignment(TextAlignment.CENTER);
        sub.setWrapText(true);
        box.getChildren().addAll(icon, title, sub);
        return box;
    }

    private void applyFilters() {
        List<AppointmentItem> filtered = allAppointments.stream()
                .filter(a -> {
                    if (!searchQuery.isEmpty()) {
                        String doctor = a.getDoctorName() != null ? a.getDoctorName().toLowerCase() : "";
                        String spec = a.getSpecialization() != null ? a.getSpecialization().toLowerCase() : "";
                        if (!doctor.contains(searchQuery) && !spec.contains(searchQuery)) return false;
                    }
                    if (!filterSpec.equals("Все специализации") &&
                            !filterSpec.equals(a.getSpecialization())) return false;

                    if (!filterMonth.equals("Все месяцы")) {
                        String date = a.getDate();
                        if (date == null || date.isEmpty()) return false;
                        try {
                            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
                            String monthName = ld.getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                                    new java.util.Locale("ru"));
                            monthName = monthName.substring(0,1).toUpperCase() + monthName.substring(1);
                            if (!monthName.equals(filterMonth)) return false;
                        } catch (Exception e) {
                            return false;
                        }
                    }
                    return true;
                }).collect(Collectors.toList());

        upcoming.clear();
        past.clear();
        cancelled.clear();

        for (AppointmentItem a : filtered) {
            String status = a.getStatus();
            if ("PENDING".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status)) {
                upcoming.add(a);
            } else if ("COMPLETED".equalsIgnoreCase(status)) {
                past.add(a);
            } else if ("CANCELLED".equalsIgnoreCase(status) || "MISSED".equalsIgnoreCase(status)) {
                cancelled.add(a);
            }
        }

        updateCounts();
        refreshContent();
    }

    private void updateCounts() {
        if (upcomingCountLabel != null) upcomingCountLabel.setText(String.valueOf(upcoming.size()));
        if (pastCountLabel != null) pastCountLabel.setText(String.valueOf(past.size()));
        if (missedCountLabel != null) missedCountLabel.setText(String.valueOf(
                cancelled.stream().filter(a -> "MISSED".equalsIgnoreCase(a.getStatus())).count()));
        if (cancelledCountLabel != null) cancelledCountLabel.setText(String.valueOf(
                cancelled.stream().filter(a -> "CANCELLED".equalsIgnoreCase(a.getStatus())).count()));

        if (tabUpcomingLabel != null)
            tabUpcomingLabel.setText(String.valueOf(upcoming.size()));
        if (tabPastLabel != null)
            tabPastLabel.setText(String.valueOf(past.size()));
        if (tabCancelledLabel != null)
            tabCancelledLabel.setText(String.valueOf(cancelled.size()));
    }

    private void cancelAppointment(AppointmentItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Отмена записи");
        confirm.setHeaderText("Вы действительно хотите отменить запись?");
        String details = item.getSpecialization() + " — " + item.getDoctorName() + "\n" +
                item.getDate() + " в " + item.getTime();
        confirm.setContentText(details);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                performCancel(item);
            }
        });
    }

    private void performCancel(AppointmentItem item) {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.CANCEL_APPOINTMENT)
                        .token(sessionToken)
                        .param("appointmentId", item.getId())
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd);
                    out.flush();
                    out.reset();
                }
                Object resp = in.readObject();
                if (resp instanceof Command response) {
                    if (response.isOk()) {
                        Platform.runLater(() -> {
                            allAppointments.remove(item);
                            applyFilters();
                        });
                    } else {
                        String msg = response.getMessage();
                        Platform.runLater(() -> {
                            Alert err = new Alert(Alert.AlertType.ERROR,
                                    "Не удалось отменить запись: " + (msg != null ? msg : "неизвестная ошибка"));
                            err.showAndWait();
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Ошибка связи с сервером.");
                    err.showAndWait();
                });
            }
        });
    }

    private void showDetailDialog(AppointmentItem a) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Детали записи");
        DialogPane dp = dlg.getDialogPane();
        dp.setPrefWidth(460);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(16);
        content.setPadding(new Insets(24));

        HBox hdr = new HBox(14);
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(0, 0, 4, 0));
        StackPane ico = new StackPane();
        Circle ic = new Circle(26, Color.web(a.getCardBg()));
        Label il = new Label(a.getIcon());
        il.setFont(Font.font("Segoe UI", 22));
        ico.getChildren().addAll(ic, il);
        VBox hdrText = new VBox(3);
        Label ht = new Label(a.getSpecialization());
        ht.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        ht.setTextFill(Color.web(TEXT_PRIMARY));
        Label hs = new Label(a.getDoctorName());
        hs.setFont(Font.font("Segoe UI", 13));
        hs.setTextFill(Color.web(a.getAccentColor()));
        hdrText.getChildren().addAll(ht, hs);
        hdr.getChildren().addAll(ico, hdrText);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:" + BORDER_COLOR + ";");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 4, 0));
        addRow(grid, 0, "📅 Дата", a.getDate());
        addRow(grid, 1, "🕐 Время", a.getTime());
        addRow(grid, 2, "🚪 Кабинет", a.getOffice() != null ? a.getOffice() : "—");
        addRow(grid, 3, "📋 Причина", a.getReason() != null ? a.getReason() : "—");
        addRow(grid, 4, "🔖 Статус", a.getBadgeText());

        content.getChildren().addAll(hdr, sep, grid);
        dp.setContent(content);
        dlg.showAndWait();
    }

    private void addRow(GridPane g, int row, String key, String val) {
        Label k = new Label(key);
        k.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        k.setTextFill(Color.web(TEXT_MUTED));
        k.setMinWidth(110);
        Label v = new Label(val);
        v.setFont(Font.font("Segoe UI", 13));
        v.setTextFill(Color.web(TEXT_PRIMARY));
        v.setWrapText(true);
        g.add(k, 0, row);
        g.add(v, 1, row);
    }

    private void showNewAppointmentDialog() {
        // Заглушка, как в исходном классе
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Новая запись к врачу");
        DialogPane dp = dlg.getDialogPane();
        dp.setPrefWidth(440);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dp.lookupButton(ButtonType.OK);
        ok.setText("Записаться");
        ok.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;");

        VBox content = new VBox(14);
        content.setPadding(new Insets(24));
        Label title = new Label("Новая запись к врачу");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(TEXT_PRIMARY));
        content.getChildren().addAll(
                title, new Separator(),
                formField("Специализация", new ComboBox<>(FXCollections.observableArrayList(
                        "Кардиолог","Стоматолог","Терапевт","Невролог","Офтальмолог"))),
                formField("Врач", new ComboBox<>(FXCollections.observableArrayList(
                        "Смирнов А.В.","Козлова М.И.","Иванова С.Л.","Петров К.Н."))),
                formField("Дата", makeTextField("дд.мм.гггг")),
                formField("Время", new ComboBox<>(FXCollections.observableArrayList(
                        "09:00","09:30","10:00","10:30","11:00","14:00","14:30","15:00"))),
                formField("Причина обращения", makeTextField("Опишите жалобы..."))
        );
        dp.setContent(content);
        dlg.showAndWait();
    }

    private VBox formField(String label, Control input) {
        VBox b = new VBox(5);
        Label l = new Label(label);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        l.setTextFill(Color.web(TEXT_SECONDARY));
        input.setMaxWidth(Double.MAX_VALUE);
        input.setStyle("-fx-background-color:#F8FAFF;-fx-border-color:" + BORDER_COLOR + ";"
                + "-fx-border-radius:8;-fx-background-radius:8;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-padding:8 12 8 12;");
        b.getChildren().addAll(l, input);
        return b;
    }

    private TextField makeTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        return tf;
    }

    public static void main(String[] args) { launch(args); }
}