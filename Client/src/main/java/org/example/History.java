package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Cursor;
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

public class History extends Application {

    private static final String ACCENT = "#3B82F6";
    private static final String BG_PAGE = "#F5F7FA";
    private static final String CARD_BG = "#FFFFFF";
    private static final String TEXT_PRIMARY = "#1A1A2E";
    private static final String TEXT_SECONDARY= "#64748B";
    private static final String TEXT_MUTED = "#94A3B8";
    private static final String BORDER_COLOR = "#E8EDF2";
    private static final String GREEN = "#10B981";
    private static final String GREEN_SOFT = "#D1FAE5";
    private static final String ORANGE = "#F59E0B";
    private static final String RED = "#EF4444";
    private static final String RED_SOFT = "#FEE2E2";
    private static final String BLUE_SOFT = "#DBEAFE";
    private static final String GREY_SOFT = "#F1F5F9";
    private static final String PURPLE = "#8B5CF6";

    public record HistoryRecord(
            String icon, String spec, String doctor,
            String date, String time, String office,
            String status, String statusLabel,
            String diagnosis, String prescription,
            String accentColor, String softColor,
            int year, String month
    ) {}

    private String sessionToken;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object sendLock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "history-loader");
        t.setDaemon(true);
        return t;
    });

    private String searchQuery = "";
    private String filterYear = "Все годы";
    private String filterMonth = "Все месяцы";
    private String filterStatus = "Все статусы";
    private String filterSpec = "Все специализации";

    private final ObservableList<HistoryRecord> allRecords = FXCollections.observableArrayList();
    private VBox timelinePane;
    private Label countLabel;

    private Label summaryTotal, summaryCompleted, summaryMissed, summarySpec, summaryLastVisit;

    @Override
    public void start(Stage stage) {
        stage.setTitle("MedPortal — История");
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
        loadHistory();
    }

    private void loadHistory() {
        executor.submit(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_HISTORY)
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
                    List<HistoryRecord> records = parseHistoryJson(json);
                    Platform.runLater(() -> {
                        allRecords.setAll(records);
                        refreshTimeline();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<HistoryRecord> parseHistoryJson(String json) {
        List<HistoryRecord> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;

        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        String[] parts = json.split("},\\s*\\{");
        for (String part : parts) {
            part = part.trim();
            if (!part.startsWith("{")) part = "{" + part;
            if (!part.endsWith("}")) part = part + "}";
            HistoryRecord hr = parseSingleRecord(part);
            if (hr != null) list.add(hr);
        }
        return list;
    }

    private HistoryRecord parseSingleRecord(String jsonObj) {
        try {
            long id = extractLong(jsonObj, "id");
            String datetime = extractString(jsonObj, "datetime");
            String status = extractString(jsonObj, "status");
            String reason = extractString(jsonObj, "reason");
            String doctorName = extractString(jsonObj, "doctorName");
            String specialization = extractString(jsonObj, "specialization");
            String diagnosis = extractString(jsonObj, "diagnosis");
            String prescription = extractString(jsonObj, "prescription");

            if (diagnosis == null) diagnosis = "—";
            if (prescription == null) prescription = "—";

            String date = "", time = "";
            if (datetime != null && datetime.contains("T")) {
                String[] dtParts = datetime.split("T");
                date = dtParts[0];
                time = dtParts[1].length() >= 5 ? dtParts[1].substring(0, 5) : dtParts[1];
            }
            String icon = getIconForSpecialization(specialization);
            boolean completed = "COMPLETED".equalsIgnoreCase(status);
            String statusLabel = completed ? "Завершён" : "Неявка";
            String accentColor = completed ? GREEN : RED;
            String softColor = completed ? GREEN_SOFT : RED_SOFT;
            String office = extractString(jsonObj, "office");
            if (office == null || office.isBlank()) office = "—";

            int year = 0;
            String month = "";
            if (!date.isEmpty()) {
                try {
                    LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
                    year = ld.getYear();
                    month = ld.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("ru"));
                    month = month.substring(0,1).toUpperCase() + month.substring(1);
                } catch (Exception e) {
                    year = 2024;
                }
            }

            return new HistoryRecord(icon, specialization, doctorName,
                    date, time, office,
                    status, statusLabel,
                    diagnosis, prescription,
                    accentColor, softColor,
                    year, month);
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

    public VBox buildMainArea() {
        VBox area = new VBox();
        area.setStyle("-fx-background-color:" + BG_PAGE + ";");

        VBox inner = new VBox(24);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.getChildren().addAll(
                buildTopBar(),
                buildSummaryCards(),
                buildFiltersPanel(),
                buildResultsBar(),
                buildTimeline()
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
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        VBox tb = new VBox(4);
        Label pt = new Label("История визитов");
        pt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        pt.setTextFill(Color.web(TEXT_PRIMARY));
        Label ps = new Label("Полная хронология ваших обращений к врачам");
        ps.setFont(Font.font("Segoe UI", 13));
        ps.setTextFill(Color.web(TEXT_MUTED));
        tb.getChildren().addAll(pt, ps);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        bar.getChildren().addAll(tb, sp);
        return bar;
    }

    private HBox buildSummaryCards() {
        long completed = allRecords.stream().filter(r -> "COMPLETED".equalsIgnoreCase(r.status())).count();
        long missed = allRecords.stream().filter(r -> "MISSED".equalsIgnoreCase(r.status())).count();

        HBox row = new HBox(16);
        summaryTotal = new Label(String.valueOf(allRecords.size()));
        summaryCompleted = new Label(String.valueOf(completed));
        summaryMissed = new Label(String.valueOf(missed));
        summarySpec = new Label(String.valueOf(
                allRecords.stream().map(HistoryRecord::spec).distinct().count()));
        summaryLastVisit = new Label(allRecords.isEmpty() ? "—" : allRecords.get(0).date());

        row.getChildren().addAll(
                summaryCard("📋","Всего визитов", summaryTotal, "за всё время", ACCENT),
                summaryCard("✅","Успешных приёмов", summaryCompleted, "завершённых", GREEN),
                summaryCard("❌","Пропущено", summaryMissed, "неявок", RED),
                summaryCard("🩺","Специализаций", summarySpec, "разных врачей", PURPLE),
                summaryCard("📅","Последний визит", summaryLastVisit, "", ORANGE)  // sub-текст оставлен пустым для простоты
        );
        for (var n : row.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        return row;
    }

    private VBox summaryCard(String icon, String label, Label valueLabel, String sub, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(18, 20, 18, 20));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:12;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),10,0,0,2);");
        HBox top = new HBox(); top.setAlignment(Pos.CENTER_LEFT);
        Label il = new Label(icon); il.setFont(Font.font("Segoe UI", 20));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        valueLabel.setTextFill(Color.web(color));
        top.getChildren().addAll(il, sp, valueLabel);
        Label ll = new Label(label);
        ll.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        ll.setTextFill(Color.web(TEXT_SECONDARY));
        Label sl = new Label(sub);
        sl.setFont(Font.font("Segoe UI", 10));
        sl.setTextFill(Color.web(TEXT_MUTED));
        card.getChildren().addAll(top, ll, sl);
        return card;
    }

    private void updateSummaryCards() {
        if (summaryTotal == null) return;
        long completed = allRecords.stream().filter(r -> "COMPLETED".equalsIgnoreCase(r.status())).count();
        long missed = allRecords.stream().filter(r -> "MISSED".equalsIgnoreCase(r.status())).count();
        summaryTotal.setText(String.valueOf(allRecords.size()));
        summaryCompleted.setText(String.valueOf(completed));
        summaryMissed.setText(String.valueOf(missed));
        summarySpec.setText(String.valueOf(
                allRecords.stream().map(HistoryRecord::spec).distinct().count()));
        summaryLastVisit.setText(allRecords.isEmpty() ? "—" : allRecords.get(0).date());
    }

    private VBox buildFiltersPanel() {
        VBox panel = new VBox(14);
        panel.setPadding(new Insets(16, 20, 16, 20));
        panel.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),10,0,0,2);");
        HBox row = new HBox(12); row.setAlignment(Pos.CENTER_LEFT);

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(9, 14, 9, 14));
        searchBox.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:10;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:10;-fx-border-width:1.5;");
        Label si = new Label("🔍"); si.setFont(Font.font("Segoe UI", 14));
        TextField sf = new TextField();
        sf.setPromptText("Поиск по врачу, диагнозу...");
        sf.setPrefWidth(220);
        sf.setStyle("-fx-background-color:transparent;-fx-border-width:0;"
                + "-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sf.textProperty().addListener((obs, ov, nv) -> { searchQuery = nv.toLowerCase(); refreshTimeline(); });
        searchBox.getChildren().addAll(si, sf);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        ComboBox<String> yearCb = filterCombo(FXCollections.observableArrayList("Все годы","2025","2024"), "Все годы", 120);
        yearCb.setOnAction(e -> { filterYear = yearCb.getValue(); refreshTimeline(); });
        ComboBox<String> monthCb = filterCombo(FXCollections.observableArrayList("Все месяцы",
                "Январь","Февраль","Март","Апрель","Май","Август","Октябрь","Ноябрь","Декабрь"), "Все месяцы", 150);
        monthCb.setOnAction(e -> { filterMonth = monthCb.getValue(); refreshTimeline(); });
        ComboBox<String> statusCb = filterCombo(FXCollections.observableArrayList("Все статусы","Завершён","Неявка"), "Все статусы", 150);
        statusCb.setOnAction(e -> { filterStatus = statusCb.getValue(); refreshTimeline(); });
        ComboBox<String> specCb = filterCombo(FXCollections.observableArrayList("Все специализации",
                "Кардиолог","Стоматолог","Терапевт","Невролог","Офтальмолог","Ортопед","Эндокринолог","Пульмонолог"), "Все специализации", 195);
        specCb.setOnAction(e -> { filterSpec = specCb.getValue(); refreshTimeline(); });

        Button resetBtn = new Button("✕ Сбросить");
        resetBtn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        resetBtn.setTextFill(Color.web(RED));
        resetBtn.setCursor(Cursor.HAND);
        resetBtn.setPadding(new Insets(8, 14, 8, 14));
        resetBtn.setStyle("-fx-background-color:" + RED_SOFT + ";-fx-background-radius:9;"
                + "-fx-border-color:" + RED + "33;-fx-border-radius:9;-fx-border-width:1.5;");
        resetBtn.setOnAction(e -> {
            yearCb.setValue("Все годы"); filterYear = "Все годы";
            monthCb.setValue("Все месяцы"); filterMonth = "Все месяцы";
            statusCb.setValue("Все статусы"); filterStatus = "Все статусы";
            specCb.setValue("Все специализации"); filterSpec = "Все специализации";
            sf.clear(); searchQuery = "";
            refreshTimeline();
        });

        row.getChildren().addAll(searchBox, yearCb, monthCb, statusCb, specCb, resetBtn);
        panel.getChildren().add(row);
        return panel;
    }

    private ComboBox<String> filterCombo(ObservableList<String> items, String def, double width) {
        ComboBox<String> cb = new ComboBox<>(items);
        cb.setValue(def); cb.setPrefWidth(width);
        cb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER_COLOR + ";"
                + "-fx-border-radius:9;-fx-background-radius:9;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");
        return cb;
    }

    private HBox buildResultsBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        countLabel = new Label();
        countLabel.setFont(Font.font("Segoe UI", 13));
        countLabel.setTextFill(Color.web(TEXT_MUTED));
        updateCount(allRecords.size());
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label hint = new Label("💡 Нажмите на запись, чтобы увидеть диагноз и назначения");
        hint.setFont(Font.font("Segoe UI", 12));
        hint.setTextFill(Color.web(TEXT_MUTED));
        bar.getChildren().addAll(countLabel, sp, hint);
        return bar;
    }

    private void updateCount(int n) {
        String noun = (n % 10 == 1) ? "запись" : (n % 10 >= 2 && n % 10 <= 4) ? "записи" : "записей";
        if (countLabel != null) countLabel.setText("Найдено: " + n + " " + noun);
    }

    private VBox buildTimeline() {
        timelinePane = new VBox(0);
        refreshTimeline();
        return timelinePane;
    }

    private void refreshTimeline() {
        List<HistoryRecord> filtered = new ArrayList<>();
        for (HistoryRecord r : allRecords) {
            if (!filterYear.equals("Все годы") && r.year() != Integer.parseInt(filterYear)) continue;
            if (!filterMonth.equals("Все месяцы") && !r.month().equals(filterMonth)) continue;
            if (!filterStatus.equals("Все статусы") && !r.statusLabel().equals(filterStatus)) continue;
            if (!filterSpec.equals("Все специализации") && !r.spec().equals(filterSpec)) continue;
            if (!searchQuery.isEmpty()
                    && !r.doctor().toLowerCase().contains(searchQuery)
                    && !r.spec().toLowerCase().contains(searchQuery)
                    && !r.diagnosis().toLowerCase().contains(searchQuery)) continue;
            filtered.add(r);
        }

        timelinePane.getChildren().clear();
        updateCount(filtered.size());

        if (filtered.isEmpty()) {
            timelinePane.getChildren().add(buildEmptyState());
            return;
        }

        int prevYear = -1;
        String prevMonth = "";
        for (HistoryRecord r : filtered) {
            if (r.year() != prevYear) {
                timelinePane.getChildren().add(buildYearDivider(r.year()));
                prevYear = r.year(); prevMonth = "";
            }
            if (!r.month().equals(prevMonth)) {
                timelinePane.getChildren().add(buildMonthLabel(r.month(), r.year()));
                prevMonth = r.month();
            }
            timelinePane.getChildren().add(buildTimelineRow(r));
        }
        updateSummaryCards();
    }

    private HBox buildYearDivider(int year) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(22, 0, 10, 0));
        Label yearLbl = new Label(String.valueOf(year));
        yearLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        yearLbl.setTextFill(Color.web(TEXT_PRIMARY));
        yearLbl.setPadding(new Insets(4, 14, 4, 14));
        yearLbl.setStyle("-fx-background-color:" + CARD_BG + ";"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:20;-fx-border-width:1.5;"
                + "-fx-background-radius:20;");
        Line line = new Line();
        line.setStroke(Color.web(BORDER_COLOR));
        line.setStrokeWidth(1.5);
        line.endXProperty().bind(row.widthProperty().subtract(100));
        row.getChildren().addAll(yearLbl, line);
        return row;
    }

    private HBox buildMonthLabel(String month, int year) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 0, 6, 52));
        Label dot = new Label("▸");
        dot.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        dot.setTextFill(Color.web(TEXT_MUTED));
        Label lbl = new Label(month + " " + year);
        lbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        lbl.setTextFill(Color.web(TEXT_SECONDARY));
        row.getChildren().addAll(dot, lbl);
        return row;
    }

    private HBox buildTimelineRow(HistoryRecord r) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(0, 0, 14, 0));

        VBox lineCol = new VBox();
        lineCol.setAlignment(Pos.TOP_CENTER);
        lineCol.setMinWidth(52); lineCol.setMaxWidth(52);
        StackPane dotStack = new StackPane();
        Circle outerDot = new Circle(10, Color.web(r.softColor()));
        Circle innerDot = new Circle(5, Color.web(r.accentColor()));
        dotStack.getChildren().addAll(outerDot, innerDot);
        dotStack.setPadding(new Insets(12, 0, 0, 0));
        Rectangle vLine = new Rectangle(1.5, 62);
        vLine.setFill(Color.web(BORDER_COLOR));
        lineCol.getChildren().addAll(dotStack, vLine);

        VBox card = new VBox(0);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setCursor(Cursor.HAND);
        card.setStyle(cardStyle(false, r));
        VBox.setMargin(card, new Insets(4, 0, 0, 0));

        HBox top = new HBox(14);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(16, 20, 14, 16));
        Rectangle stripe = new Rectangle(4, 54);
        stripe.setFill(Color.web(r.accentColor()));
        stripe.setArcWidth(4); stripe.setArcHeight(4);

        StackPane avatar = new StackPane();
        Circle avatarBg = new Circle(22, Color.web(r.softColor()));
        Label avatarIcon = new Label(r.icon());
        avatarIcon.setFont(Font.font("Segoe UI", 18));
        avatar.getChildren().addAll(avatarBg, avatarIcon);

        VBox info = new VBox(3); info.setMinWidth(200); HBox.setHgrow(info, Priority.ALWAYS);
        Label specLbl = new Label(r.spec());
        specLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        specLbl.setTextFill(Color.web(TEXT_PRIMARY));
        Label docLbl = new Label("Врач: " + r.doctor());
        docLbl.setFont(Font.font("Segoe UI", 12));
        docLbl.setTextFill(Color.web(TEXT_SECONDARY));
        info.getChildren().addAll(specLbl, docLbl);

        VBox dateSide = new VBox(5); dateSide.setAlignment(Pos.CENTER_RIGHT);
        Label dateLbl = new Label(r.date() + " • " + r.time());
        dateLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        dateLbl.setTextFill(Color.web(r.accentColor()));
        boolean done = "COMPLETED".equalsIgnoreCase(r.status());
        Label statusBadge = new Label((done ? "✓ " : "✗ ") + r.statusLabel());
        statusBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        statusBadge.setTextFill(Color.web(done ? GREEN : RED));
        statusBadge.setPadding(new Insets(3, 12, 3, 12));
        statusBadge.setStyle("-fx-background-color:" + (done ? GREEN_SOFT : RED_SOFT) + ";"
                + "-fx-background-radius:20;");
        Label officeLbl = new Label(r.office());
        officeLbl.setFont(Font.font("Segoe UI", 11));
        officeLbl.setTextFill(Color.web(TEXT_MUTED));
        dateSide.getChildren().addAll(dateLbl, statusBadge, officeLbl);
        top.getChildren().addAll(stripe, avatar, info, dateSide);

        if (done) {
            Separator div = new Separator();
            div.setStyle("-fx-background-color:" + BORDER_COLOR + ";");
            HBox bottom = new HBox(0);
            bottom.setPadding(new Insets(12, 20, 14, 16));
            bottom.setStyle("-fx-background-color:#FAFBFD;-fx-background-radius:0 0 13 13;");
            VBox diagBlock = new VBox(3); diagBlock.setMinWidth(260);
            Label diagTitle = new Label("🩺 Диагноз");
            diagTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            diagTitle.setTextFill(Color.web(TEXT_MUTED));
            Label diagText = new Label(r.diagnosis());
            diagText.setFont(Font.font("Segoe UI", 12));
            diagText.setTextFill(Color.web(TEXT_PRIMARY));
            diagText.setWrapText(true);
            diagBlock.getChildren().addAll(diagTitle, diagText);
            Rectangle vd = new Rectangle(1, 36);
            vd.setFill(Color.web(BORDER_COLOR));
            HBox.setMargin(vd, new Insets(2, 18, 0, 18));
            VBox prescBlock = new VBox(3); HBox.setHgrow(prescBlock, Priority.ALWAYS);
            Label prescTitle = new Label("💊 Назначения");
            prescTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            prescTitle.setTextFill(Color.web(TEXT_MUTED));
            Label prescText = new Label(r.prescription());
            prescText.setFont(Font.font("Segoe UI", 12));
            prescText.setTextFill(Color.web(TEXT_PRIMARY));
            prescText.setWrapText(true);
            prescBlock.getChildren().addAll(prescTitle, prescText);
            Region rightSp = new Region(); HBox.setHgrow(rightSp, Priority.ALWAYS);
            Button rebookBtn = new Button("↩ Повторить");
            rebookBtn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
            rebookBtn.setTextFill(Color.web(ACCENT));
            rebookBtn.setCursor(Cursor.HAND);
            rebookBtn.setPadding(new Insets(7, 16, 7, 16));
            String rbStyle = "-fx-background-color:" + CARD_BG + ";-fx-border-color:" + ACCENT + "44;"
                    + "-fx-border-radius:9;-fx-background-radius:9;-fx-border-width:1.5;";
            rebookBtn.setStyle(rbStyle);
            rebookBtn.setOnMouseEntered(e -> rebookBtn.setStyle("-fx-background-color:" + BLUE_SOFT + ";"
                    + "-fx-border-color:" + ACCENT + "44;-fx-border-radius:9;-fx-background-radius:9;"
                    + "-fx-border-width:1.5;"));
            rebookBtn.setOnMouseExited(e -> rebookBtn.setStyle(rbStyle));
            rebookBtn.setOnAction(e -> { e.consume(); showRebookDialog(r); });
            bottom.getChildren().addAll(diagBlock, vd, prescBlock, rightSp, rebookBtn);
            card.getChildren().addAll(top, div, bottom);
        } else {
            HBox bottom = new HBox();
            bottom.setPadding(new Insets(10, 20, 12, 16));
            bottom.setStyle("-fx-background-color:" + RED_SOFT + "55;-fx-background-radius:0 0 13 13;");
            Label missTxt = new Label("⚠️ Пациент не явился на приём. Запись отмечена как неявка.");
            missTxt.setFont(Font.font("Segoe UI", 12));
            missTxt.setTextFill(Color.web(RED));
            bottom.getChildren().add(missTxt);
            card.getChildren().addAll(top, bottom);
        }

        card.setOnMouseEntered(e -> card.setStyle(cardStyle(true, r)));
        card.setOnMouseExited(e -> card.setStyle(cardStyle(false, r)));
        card.setOnMouseClicked(e -> showDetailDialog(r));
        row.getChildren().addAll(lineCol, card);
        return row;
    }

    private String cardStyle(boolean hover, HistoryRecord r) {
        return "-fx-background-color:" + CARD_BG + ";-fx-background-radius:13;"
                + "-fx-border-color:" + (hover ? r.accentColor() + "44" : BORDER_COLOR) + ";"
                + "-fx-border-radius:13;-fx-border-width:1.5;"
                + "-fx-effect:dropshadow(gaussian," +
                (hover ? "rgba(59,130,246,0.10),14,0,0,4" : "rgba(0,0,0,0.05),10,0,0,2") + ");";
    }

    private VBox buildEmptyState() {
        VBox box = new VBox(12); box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60));
        box.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:14;-fx-border-width:1;");
        Label icon = new Label("🔍"); icon.setFont(Font.font("Segoe UI", 44));
        Label title = new Label("Записей не найдено");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(TEXT_PRIMARY));
        Label sub = new Label("Попробуйте изменить фильтры поиска.");
        sub.setFont(Font.font("Segoe UI", 13));
        sub.setTextFill(Color.web(TEXT_MUTED));
        box.getChildren().addAll(icon, title, sub);
        return box;
    }

    private void showDetailDialog(HistoryRecord r) {
        Dialog<Void> dlg = new Dialog<>(); dlg.setTitle("Детали визита");
        DialogPane dp = dlg.getDialogPane(); dp.setPrefWidth(500);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().add(ButtonType.CLOSE);
        VBox c = new VBox(18); c.setPadding(new Insets(28));
        HBox hdr = new HBox(16); hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(0, 0, 4, 0));
        StackPane ico = new StackPane();
        Circle icBg = new Circle(28, Color.web(r.softColor()));
        Label icLbl = new Label(r.icon()); icLbl.setFont(Font.font("Segoe UI", 24));
        ico.getChildren().addAll(icBg, icLbl);
        VBox hdrText = new VBox(3);
        Label hn = new Label(r.spec());
        hn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        hn.setTextFill(Color.web(TEXT_PRIMARY));
        Label hs = new Label(r.doctor() + " • " + r.date() + ", " + r.time());
        hs.setFont(Font.font("Segoe UI", 13));
        hs.setTextFill(Color.web(r.accentColor()));
        hdrText.getChildren().addAll(hn, hs);
        hdr.getChildren().addAll(ico, hdrText);
        Separator sep = new Separator(); sep.setStyle("-fx-background-color:" + BORDER_COLOR + ";");
        GridPane grid = new GridPane(); grid.setHgap(20); grid.setVgap(12);
        grid.setPadding(new Insets(4, 0, 4, 0));
        addDetailRow(grid, 0, "📅 Дата приёма", r.date() + " в " + r.time());
        addDetailRow(grid, 1, "🚪 Кабинет", r.office());
        addDetailRow(grid, 2, "🔖 Статус", r.statusLabel());
        addDetailRow(grid, 3, "🩺 Диагноз", r.diagnosis());
        addDetailRow(grid, 4, "💊 Назначения", r.prescription());
        c.getChildren().addAll(hdr, sep, grid);
        if ("COMPLETED".equalsIgnoreCase(r.status())) {
            Button rebookBtn = new Button("↩ Записаться повторно");
            rebookBtn.setMaxWidth(Double.MAX_VALUE);
            rebookBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            rebookBtn.setTextFill(Color.WHITE);
            rebookBtn.setPadding(new Insets(12)); rebookBtn.setCursor(Cursor.HAND);
            rebookBtn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:10;"
                    + "-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.28),10,0,0,4);");
            rebookBtn.setOnAction(e -> { dlg.close(); showRebookDialog(r); });
            c.getChildren().add(rebookBtn);
        }
        dp.setContent(c); dlg.showAndWait();
    }

    private void addDetailRow(GridPane g, int row, String key, String val) {
        Label k = new Label(key);
        k.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        k.setTextFill(Color.web(TEXT_MUTED)); k.setMinWidth(130);
        Label v = new Label(val);
        v.setFont(Font.font("Segoe UI", 13));
        v.setTextFill(Color.web(TEXT_PRIMARY)); v.setWrapText(true); v.setMaxWidth(280);
        g.add(k, 0, row); g.add(v, 1, row);
    }

    private void showRebookDialog(HistoryRecord r) {
        Dialog<Void> dlg = new Dialog<>(); dlg.setTitle("Повторная запись");
        DialogPane dp = dlg.getDialogPane(); dp.setPrefWidth(420);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dp.lookupButton(ButtonType.OK); ok.setText("Записаться");
        ok.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;"
                + "-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;");
        VBox c = new VBox(14); c.setPadding(new Insets(24));
        HBox mini = new HBox(12); mini.setAlignment(Pos.CENTER_LEFT);
        mini.setPadding(new Insets(12, 14, 12, 14));
        mini.setStyle("-fx-background-color:#F8FAFF;-fx-background-radius:10;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:10;-fx-border-width:1;");
        StackPane mAv = new StackPane();
        Circle mC = new Circle(20, Color.web(r.softColor()));
        Label mI = new Label(r.icon()); mI.setFont(Font.font("Segoe UI", 16));
        mAv.getChildren().addAll(mC, mI);
        VBox mT = new VBox(2);
        Label mn = new Label(r.doctor()); mn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        mn.setTextFill(Color.web(TEXT_PRIMARY));
        Label ms = new Label(r.spec() + " • последний визит: " + r.date());
        ms.setFont(Font.font("Segoe UI", 11)); ms.setTextFill(Color.web(TEXT_SECONDARY));
        mT.getChildren().addAll(mn, ms); mini.getChildren().addAll(mAv, mT);
        ComboBox<String> times = new ComboBox<>(FXCollections.observableArrayList(
                "09:00","09:30","10:00","10:30","11:00","14:00","14:30","15:00"));
        times.setValue("09:00");
        c.getChildren().addAll(mini, new Separator(),
                formField("Дата приёма", makeTextField("дд.мм.гггг")),
                formField("Время", times),
                formField("Причина обращения", makeTextField("Повторный приём, " + r.diagnosis())));
        dp.setContent(c); dlg.showAndWait();
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
        b.getChildren().addAll(l, input); return b;
    }

    private TextField makeTextField(String prompt) {
        TextField tf = new TextField(); tf.setPromptText(prompt); return tf;
    }

    public static void main(String[] args) { launch(args); }
}