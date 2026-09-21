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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Reminders extends Application {

    private static final String ACCENT = "#3B82F6";
    private static final String BG_PAGE = "#F5F7FA";
    private static final String CARD_BG = "#FFFFFF";
    private static final String TEXT_PRIMARY = "#1A1A2E";
    private static final String TEXT_SECONDARY = "#64748B";
    private static final String TEXT_MUTED = "#94A3B8";
    private static final String BORDER_COLOR = "#E8EDF2";
    private static final String GREEN = "#10B981";
    private static final String GREEN_SOFT = "#D1FAE5";
    private static final String ORANGE = "#F59E0B";
    private static final String ORANGE_SOFT = "#FEF3C7";
    private static final String RED = "#EF4444";
    private static final String RED_SOFT = "#FEE2E2";
    private static final String BLUE_SOFT = "#DBEAFE";
    private static final String GREY_SOFT = "#F1F5F9";

    private String sessionToken;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object sendLock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "reminders-loader");
        t.setDaemon(true);
        return t;
    });

    public record Reminder(
            String id,
            String icon, String spec, String doctor,
            String appointDate, String appointTime,
            String remindTime, String remindLabel,
            String channel,
            String status,
            String statusLabel,
            String accentColor, String softColor,
            boolean enabled
    ) {}

    private final ObservableList<Reminder> allReminders = FXCollections.observableArrayList();

    private String activeTab = "upcoming";
    private String searchQuery = "";
    private VBox listPane;
    private Label countLabel;
    private Label sumScheduled, sumSent, sumCancelled, sumEnabled;

    @Override
    public void start(Stage stage) {
        stage.setTitle("MedPortal — Напоминания");
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
        loadReminders();
    }

    private void loadReminders() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_REMINDERS)
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
                    List<Reminder> parsed = parseReminderList(json);
                    Platform.runLater(() -> {
                        allReminders.setAll(parsed);
                        refreshList();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<Reminder> parseReminderList(String json) {
        List<Reminder> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        String[] parts = json.split("},\\s*\\{");
        for (String part : parts) {
            part = part.trim();
            if (!part.startsWith("{")) part = "{" + part;
            if (!part.endsWith("}")) part = part + "}";
            Reminder r = parseSingleReminder(part);
            if (r != null) list.add(r);
        }
        return list;
    }

    private Reminder parseSingleReminder(String jsonObj) {
        try {
            String id = nvl(extractString(jsonObj, "id"), "0");
            String spec = nvl(extractString(jsonObj, "specialization"), "—");
            String doctor = nvl(extractString(jsonObj, "doctorName"), "—");
            String channel = nvl(extractString(jsonObj, "channel"), "—");
            String status = nvl(extractString(jsonObj, "status"), "SCHEDULED");
            boolean enabled = Boolean.parseBoolean(nvl(extractString(jsonObj, "enabled"), "true"));
            String scheduledSendTime = nvl(extractString(jsonObj, "scheduledSendTime"), "");
            String appointmentDatetime = nvl(extractString(jsonObj, "appointmentDatetime"), "");

            String appointDate = "", appointTime = "";
            if (!appointmentDatetime.isEmpty() && appointmentDatetime.contains("T")) {
                String[] dtParts = appointmentDatetime.split("T");
                appointDate = dtParts[0];
                appointTime = dtParts[1].length() >= 5 ? dtParts[1].substring(0, 5) : dtParts[1];
            }

            String remindTime = scheduledSendTime.replace("T", " ");

            String intervalStr = nvl(extractString(jsonObj, "intervalMinutes"), "0");
            int intervalMinutes = Integer.parseInt(intervalStr);
            String remindLabel = intervalMinutesToString(intervalMinutes);

            String statusLabel = switch (status.toUpperCase()) {
                case "SCHEDULED" -> "Запланировано";
                case "SENT" -> "Отправлено";
                case "CANCELLED" -> "Отменено";
                default -> status;
            };
            String accentColor = switch (status.toUpperCase()) {
                case "SCHEDULED" -> ACCENT;
                case "SENT" -> GREEN;
                case "CANCELLED" -> RED;
                default -> TEXT_MUTED;
            };
            String softColor = switch (status.toUpperCase()) {
                case "SCHEDULED" -> BLUE_SOFT;
                case "SENT" -> GREEN_SOFT;
                case "CANCELLED" -> RED_SOFT;
                default -> GREY_SOFT;
            };

            String icon = getIconForSpecialization(spec);

            return new Reminder(id, icon, spec, doctor,
                    appointDate, appointTime,
                    remindTime, remindLabel,
                    channel, status, statusLabel,
                    accentColor, softColor, enabled);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String nvl(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a=new Alert(type); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private String intervalMinutesToString(int minutes) {
        if (minutes == 1440) return "За 24 часа";
        if (minutes == 120) return "За 2 часа";
        if (minutes == 30) return "За 30 минут";
        if (minutes == 60) return "За 1 час";
        if (minutes == 10080) return "За 1 неделю";
        return "За " + minutes + " мин";
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

    public VBox buildMainArea() {
        VBox area = new VBox();
        area.setStyle("-fx-background-color:" + BG_PAGE + ";");

        VBox inner = new VBox(24);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.getChildren().addAll(
                buildTopBar(),
                buildSummaryRow(),
                buildNotificationSettingsCard(),
                buildTabsAndSearch(),
                buildResultsBar(),
                buildListPane()
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
        Label pt = new Label("Напоминания");
        pt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        pt.setTextFill(Color.web(TEXT_PRIMARY));
        Label ps = new Label("Управление уведомлениями о предстоящих визитах");
        ps.setFont(Font.font("Segoe UI", 13));
        ps.setTextFill(Color.web(TEXT_MUTED));
        tb.getChildren().addAll(pt, ps);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        bar.getChildren().addAll(tb, sp);
        return bar;
    }

    private HBox buildSummaryRow() {
        long scheduled = allReminders.stream().filter(r -> "SCHEDULED".equalsIgnoreCase(r.status())).count();
        long sent = allReminders.stream().filter(r -> "SENT".equalsIgnoreCase(r.status())).count();
        long cancelled = allReminders.stream().filter(r -> "CANCELLED".equalsIgnoreCase(r.status())).count();
        long enabled = allReminders.stream().filter(r -> r.enabled() && "SCHEDULED".equalsIgnoreCase(r.status())).count();

        sumScheduled = new Label(String.valueOf(scheduled));
        sumSent = new Label(String.valueOf(sent));
        sumCancelled = new Label(String.valueOf(cancelled));
        sumEnabled = new Label(String.valueOf(enabled));

        HBox row = new HBox(16);
        row.getChildren().addAll(
                summaryCard("🔔","Запланировано", sumScheduled, "активных напоминаний", ACCENT),
                summaryCard("✅","Отправлено", sumSent, "успешно доставлено", GREEN),
                summaryCard("🚫","Отменено", sumCancelled, "не будет отправлено", RED),
                summaryCard("🟢","Включено", sumEnabled, "с уведомлением", TEXT_SECONDARY)
        );
        for (var n : row.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        return row;
    }

    private VBox summaryCard(String icon, String label, Label value, String sub, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(18, 20, 18, 20));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:12;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),10,0,0,2);");
        HBox top = new HBox(); top.setAlignment(Pos.CENTER_LEFT);
        Label il = new Label(icon); il.setFont(Font.font("Segoe UI", 20));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        top.getChildren().addAll(il, sp, value); // value уже готовый Label
        Label ll = new Label(label);
        ll.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        ll.setTextFill(Color.web(TEXT_SECONDARY));
        Label sl = new Label(sub);
        sl.setFont(Font.font("Segoe UI", 10));
        sl.setTextFill(Color.web(TEXT_MUTED));
        card.getChildren().addAll(top, ll, sl);
        return card;
    }

    private void updateSummaryRow() {
        if (sumScheduled == null) return;
        long scheduled = allReminders.stream().filter(r -> "SCHEDULED".equalsIgnoreCase(r.status())).count();
        long sent = allReminders.stream().filter(r -> "SENT".equalsIgnoreCase(r.status())).count();
        long cancelled = allReminders.stream().filter(r -> "CANCELLED".equalsIgnoreCase(r.status())).count();
        long enabled = allReminders.stream().filter(r -> r.enabled() && "SCHEDULED".equalsIgnoreCase(r.status())).count();
        sumScheduled.setText(String.valueOf(scheduled));
        sumSent.setText(String.valueOf(sent));
        sumCancelled.setText(String.valueOf(cancelled));
        sumEnabled.setText(String.valueOf(enabled));
    }

    private HBox buildNotificationSettingsCard() {
        HBox card = new HBox(0);
        card.setPadding(new Insets(18, 22, 18, 22));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),10,0,0,2);");
        StackPane ico = new StackPane();
        Circle icBg = new Circle(22, Color.web(BLUE_SOFT));
        Label icLbl = new Label("🔔"); icLbl.setFont(Font.font("Segoe UI", 18));
        ico.getChildren().addAll(icBg, icLbl);
        VBox textPart = new VBox(3);
        textPart.setPadding(new Insets(0, 0, 0, 16));
        HBox.setHgrow(textPart, Priority.ALWAYS);
        Label title = new Label("Настройки уведомлений");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        title.setTextFill(Color.web(TEXT_PRIMARY));
        Label sub = new Label("Настройте каналы и время отправки напоминаний для всех записей");
        sub.setFont(Font.font("Segoe UI", 12)); sub.setTextFill(Color.web(TEXT_MUTED));
        textPart.getChildren().addAll(title, sub);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        VBox channelsBlock = new VBox(5); channelsBlock.setAlignment(Pos.CENTER_RIGHT);
        channelsBlock.setPadding(new Insets(0, 24, 0, 0));
        Label chLbl = new Label("Канал");
        chLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); chLbl.setTextFill(Color.web(TEXT_MUTED));
        HBox chRow = new HBox(8); chRow.setAlignment(Pos.CENTER_RIGHT);
        chRow.getChildren().addAll(channelChip("📧 Email", true), channelChip("📱 Push", false), channelChip("💬 SMS", false));
        channelsBlock.getChildren().addAll(chLbl, chRow);
        VBox intervalsBlock = new VBox(5); intervalsBlock.setAlignment(Pos.CENTER_RIGHT);
        Label intLbl = new Label("Интервал");
        intLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); intLbl.setTextFill(Color.web(TEXT_MUTED));
        HBox intRow = new HBox(8); intRow.setAlignment(Pos.CENTER_RIGHT);
        intRow.getChildren().addAll(intervalChip("24 ч", true), intervalChip("2 ч", true), intervalChip("30 мин", false));
        intervalsBlock.getChildren().addAll(intLbl, intRow);
        card.getChildren().addAll(ico, textPart, sp, channelsBlock, intervalsBlock);
        return card;
    }

    private Button channelChip(String text, boolean on) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        btn.setCursor(Cursor.HAND);
        btn.setPadding(new Insets(5, 12, 5, 12));
        String activeStyle = "-fx-background-color:" + ACCENT + ";-fx-background-radius:20;-fx-text-fill:white;"
                + "-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.20),6,0,0,2);";
        String inactiveStyle = "-fx-background-color:" + GREY_SOFT + ";-fx-background-radius:20;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:20;-fx-border-width:1.5;"
                + "-fx-text-fill:" + TEXT_MUTED + ";";
        btn.setStyle(on ? activeStyle : inactiveStyle);
        btn.setOnMouseClicked(e -> {
            boolean cur = btn.getStyle().contains("-fx-background-color:" + ACCENT);
            btn.setStyle(!cur ? activeStyle : inactiveStyle);
        });
        return btn;
    }

    private Label intervalChip(String text, boolean on) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        l.setPadding(new Insets(5, 12, 5, 12));
        l.setStyle(on
                ? "-fx-background-color:" + BLUE_SOFT + ";-fx-background-radius:20;"
                + "-fx-border-color:" + ACCENT + "44;-fx-border-radius:20;-fx-border-width:1.5;"
                + "-fx-text-fill:" + ACCENT + ";"
                : "-fx-background-color:" + GREY_SOFT + ";-fx-background-radius:20;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:20;-fx-border-width:1.5;"
                + "-fx-text-fill:" + TEXT_MUTED + ";");
        return l;
    }

    private VBox buildTabsAndSearch() {
        VBox wrapper = new VBox(14);
        wrapper.setPadding(new Insets(16, 20, 16, 20));
        wrapper.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:14;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),10,0,0,2);");

        HBox tabs = new HBox(6); tabs.setAlignment(Pos.CENTER_LEFT);
        long sch = allReminders.stream().filter(r -> "SCHEDULED".equalsIgnoreCase(r.status())).count();
        long snt = allReminders.stream().filter(r -> "SENT".equalsIgnoreCase(r.status())).count();
        long can = allReminders.stream().filter(r -> "CANCELLED".equalsIgnoreCase(r.status())).count();

        Button[] btns = {
                tabBtn("Запланированные", "upcoming", (int) sch),
                tabBtn("Отправленные", "sent", (int) snt),
                tabBtn("Отменённые", "cancelled", (int) can)
        };
        tabs.getChildren().addAll(btns);
        applyTabStyle(btns[0], true);
        applyTabStyle(btns[1], false);
        applyTabStyle(btns[2], false);

        List<String> tabIds = List.of("upcoming","sent","cancelled");
        for (int i = 0; i < btns.length; i++) {
            final String tid = tabIds.get(i);
            final Button[] all = btns;
            btns[i].setOnAction(e -> {
                activeTab = tid;
                for (Button b : all) applyTabStyle(b, false);
                applyTabStyle((Button) e.getSource(), true);
                refreshList();
            });
        }

        Separator divider = new Separator();
        divider.setStyle("-fx-background-color:" + BORDER_COLOR + ";");

        HBox searchRow = new HBox(12); searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(9, 14, 9, 14));
        searchBox.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-background-radius:10;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:10;-fx-border-width:1.5;");
        Label si = new Label("🔍"); si.setFont(Font.font("Segoe UI", 14));
        TextField sf = new TextField();
        sf.setPromptText("Поиск по врачу или специализации...");
        sf.setPrefWidth(280);
        sf.setStyle("-fx-background-color:transparent;-fx-border-width:0;"
                + "-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sf.textProperty().addListener((obs, ov, nv) -> { searchQuery = nv.toLowerCase(); refreshList(); });
        searchBox.getChildren().addAll(si, sf);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        ComboBox<String> channelCb = new ComboBox<>(FXCollections.observableArrayList("Все каналы","Email","Push","SMS"));
        channelCb.setValue("Все каналы"); channelCb.setPrefWidth(150);
        channelCb.setStyle("-fx-background-color:" + BG_PAGE + ";-fx-border-color:" + BORDER_COLOR + ";"
                + "-fx-border-radius:9;-fx-background-radius:9;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';");
        CheckBox onlyActive = new CheckBox("Только включённые");
        onlyActive.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        onlyActive.setTextFill(Color.web(TEXT_SECONDARY));
        onlyActive.setCursor(Cursor.HAND);
        searchRow.getChildren().addAll(searchBox, channelCb, onlyActive);
        wrapper.getChildren().addAll(tabs, divider, searchRow);
        return wrapper;
    }

    private Button tabBtn(String text, String id, int count) {
        Button btn = new Button(text + " " + count);
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
        } else {
            btn.setStyle("-fx-background-color:transparent;-fx-background-radius:9;"
                    + "-fx-text-fill:" + TEXT_SECONDARY + ";");
            btn.setOnMouseEntered(ev -> { if (!btn.getStyle().contains(ACCENT))
                btn.setStyle("-fx-background-color:" + GREY_SOFT + ";-fx-background-radius:9;-fx-text-fill:" + TEXT_PRIMARY + ";"); });
            btn.setOnMouseExited(ev -> { if (!btn.getStyle().contains(ACCENT))
                btn.setStyle("-fx-background-color:transparent;-fx-background-radius:9;-fx-text-fill:" + TEXT_SECONDARY + ";"); });
        }
    }

    private HBox buildResultsBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        countLabel = new Label();
        countLabel.setFont(Font.font("Segoe UI", 13));
        countLabel.setTextFill(Color.web(TEXT_MUTED));
        updateCount(allReminders.size());
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label hint = new Label("💡 Нажмите на карточку для управления напоминанием");
        hint.setFont(Font.font("Segoe UI", 12)); hint.setTextFill(Color.web(TEXT_MUTED));
        bar.getChildren().addAll(countLabel, sp, hint);
        return bar;
    }

    private void updateCount(int n) {
        if (countLabel == null) return;
        String noun = (n % 10 == 1) ? "напоминание" : (n % 10 >= 2 && n % 10 <= 4) ? "напоминания" : "напоминаний";
        countLabel.setText("Найдено: " + n + " " + noun);
    }

    private VBox buildListPane() {
        listPane = new VBox(12);
        refreshList();
        return listPane;
    }

    private void refreshList() {
        if (listPane == null) return;
        String statusFilter = switch (activeTab) {
            case "sent" -> "SENT";
            case "cancelled" -> "CANCELLED";
            default -> "SCHEDULED";
        };
        List<Reminder> filtered = new ArrayList<>();
        for (Reminder r : allReminders) {
            if (!r.status().equalsIgnoreCase(statusFilter)) continue;
            if (!searchQuery.isEmpty()
                    && !r.doctor().toLowerCase().contains(searchQuery)
                    && !r.spec().toLowerCase().contains(searchQuery)) continue;
            filtered.add(r);
        }
        listPane.getChildren().clear();
        updateCount(filtered.size());
        if (filtered.isEmpty()) { listPane.getChildren().add(buildEmptyState()); return; }

        String prevGroup = "";
        for (Reminder r : filtered) {
            String group = r.spec() + r.doctor() + r.appointDate();
            if (!group.equals(prevGroup)) {
                listPane.getChildren().add(buildGroupHeader(r));
                prevGroup = group;
            }
            listPane.getChildren().add(buildReminderCard(r));
        }
        updateSummaryRow();
    }

    private HBox buildGroupHeader(Reminder r) {
        HBox row = new HBox(12); row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 0, 6, 0));
        StackPane ib = new StackPane();
        Circle ic = new Circle(14, Color.web(r.softColor()));
        Label il = new Label(r.icon()); il.setFont(Font.font("Segoe UI", 12));
        ib.getChildren().addAll(ic, il);
        Label specLbl = new Label(r.spec() + " — " + r.doctor());
        specLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        specLbl.setTextFill(Color.web(TEXT_PRIMARY));
        Label dateLbl = new Label("📅 " + r.appointDate() + " в " + r.appointTime());
        dateLbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        dateLbl.setTextFill(Color.web(r.accentColor()));
        dateLbl.setPadding(new Insets(3, 10, 3, 10));
        dateLbl.setStyle("-fx-background-color:" + r.softColor() + ";-fx-background-radius:20;");
        row.getChildren().addAll(ib, specLbl, dateLbl);
        return row;
    }

    private HBox buildReminderCard(Reminder r) {
        HBox outer = new HBox(14); outer.setAlignment(Pos.CENTER_LEFT);
        outer.setPadding(new Insets(0, 0, 0, 24));
        StackPane bellBox = new StackPane();
        bellBox.setMinWidth(44); bellBox.setMinHeight(44);
        Circle bellBg = new Circle(20, Color.web(BLUE_SOFT));
        Label bellLbl = new Label("🔔"); bellLbl.setFont(Font.font("Segoe UI", 16));
        bellBox.getChildren().addAll(bellBg, bellLbl);

        VBox card = new VBox(0); card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setCursor(Cursor.HAND);

        boolean isScheduled = "SCHEDULED".equalsIgnoreCase(r.status());
        boolean isSent = "SENT".equalsIgnoreCase(r.status());
        String cardStyle = "-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;"
                + "-fx-border-color:" + (isScheduled ? r.accentColor() + "33" : BORDER_COLOR) + ";"
                + "-fx-border-radius:12;-fx-border-width:1.5;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),10,0,0,2);";
        card.setStyle(cardStyle);

        HBox inner = new HBox(14); inner.setAlignment(Pos.CENTER_LEFT);
        inner.setPadding(new Insets(14, 18, 14, 14));
        Rectangle stripe = new Rectangle(4, 48);
        stripe.setFill(Color.web(isScheduled ? r.accentColor() : isSent ? GREEN : RED));
        stripe.setArcWidth(4); stripe.setArcHeight(4);

        VBox info = new VBox(4); info.setMinWidth(220); HBox.setHgrow(info, Priority.ALWAYS);
        HBox timeRow = new HBox(8); timeRow.setAlignment(Pos.CENTER_LEFT);
        Label timeLbl = new Label("🕐 " + r.remindTime());
        timeLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        timeLbl.setTextFill(Color.web(isScheduled ? r.accentColor() : isSent ? GREEN : RED));
        Label intervalBadge = new Label(r.remindLabel());
        intervalBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        intervalBadge.setTextFill(Color.web(isScheduled ? r.accentColor() : isSent ? GREEN : RED));
        intervalBadge.setPadding(new Insets(2, 8, 2, 8));
        intervalBadge.setStyle("-fx-background-color:" + (isScheduled ? r.softColor() : isSent ? GREEN_SOFT : RED_SOFT) + ";-fx-background-radius:20;");
        timeRow.getChildren().addAll(timeLbl, intervalBadge);
        String chIcon = r.channel().equalsIgnoreCase("EMAIL") ? "📧" : r.channel().equalsIgnoreCase("PUSH") ? "📱" : "💬";
        Label chLbl = new Label(chIcon + " " + r.channel());
        chLbl.setFont(Font.font("Segoe UI", 11)); chLbl.setTextFill(Color.web(TEXT_SECONDARY));
        info.getChildren().addAll(timeRow, chLbl);

        VBox rightBlock = new VBox(8); rightBlock.setAlignment(Pos.CENTER_RIGHT);
        String stIcon = isScheduled ? "⏳" : isSent ? "✓" : "✗";
        String stColor = isScheduled ? ORANGE : isSent ? GREEN : RED;
        String stBg = isScheduled ? ORANGE_SOFT : isSent ? GREEN_SOFT : RED_SOFT;
        Label statusBadge = new Label(stIcon + " " + r.statusLabel());
        statusBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        statusBadge.setTextFill(Color.web(stColor));
        statusBadge.setPadding(new Insets(4, 12, 4, 12));
        statusBadge.setStyle("-fx-background-color:" + stBg + ";-fx-background-radius:20;");

        if (isScheduled) {
            HBox toggleRow = new HBox(8); toggleRow.setAlignment(Pos.CENTER_RIGHT);
            Label toggleLbl = new Label(r.enabled() ? "Вкл" : "Выкл");
            toggleLbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
            toggleLbl.setTextFill(Color.web(r.enabled() ? GREEN : TEXT_MUTED));
            Button toggleBtn = new Button(r.enabled() ? "●" : "○");
            toggleBtn.setFont(Font.font("Segoe UI", 18));
            toggleBtn.setTextFill(Color.web(r.enabled() ? GREEN : TEXT_MUTED));
            toggleBtn.setCursor(Cursor.HAND);
            toggleBtn.setPadding(Insets.EMPTY);
            toggleBtn.setStyle("-fx-background-color:transparent;-fx-border-width:0;");
            toggleRow.getChildren().addAll(toggleLbl, toggleBtn);
            rightBlock.getChildren().addAll(statusBadge, toggleRow);
        } else {
            rightBlock.getChildren().add(statusBadge);
        }

        inner.getChildren().addAll(stripe, info, rightBlock);

        if (isScheduled) {
            HBox actions = new HBox(8); actions.setAlignment(Pos.CENTER_RIGHT);
            actions.setPadding(new Insets(0, 18, 12, 14));
            actions.setStyle("-fx-background-color:#FAFBFD;-fx-background-radius:0 0 11 11;"
                    + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-width:1 0 0 0;");
            Button editBtn = outlineSmBtn("✏️ Изменить", ACCENT);
            editBtn.setOnAction(e -> { e.consume(); showEditDialog(r); });
            Button cancelBtn = outlineSmBtn("✕ Отменить", RED);
            cancelBtn.setOnAction(e -> { e.consume(); showCancelConfirm(r); });
            Region actSp = new Region(); HBox.setHgrow(actSp, Priority.ALWAYS);
            actions.getChildren().addAll(actSp, editBtn, cancelBtn);
            card.getChildren().addAll(inner, actions);
        } else {
            card.getChildren().add(inner);
        }

        String hoverStyle = "-fx-background-color:" + CARD_BG + ";-fx-background-radius:12;"
                + "-fx-border-color:" + ACCENT + "33;-fx-border-radius:12;-fx-border-width:1.5;"
                + "-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.10),14,0,0,4);";
        card.setOnMouseEntered(e -> card.setStyle(hoverStyle));
        card.setOnMouseExited(e -> card.setStyle(cardStyle));
        card.setOnMouseClicked(e -> showDetailDialog(r));

        outer.getChildren().addAll(bellBox, card);
        return outer;
    }

    private VBox buildEmptyState() {
        VBox box = new VBox(12); box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60));
        box.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:14;-fx-border-width:1;");
        Label icon = new Label("🔔"); icon.setFont(Font.font("Segoe UI", 44));
        Label title = new Label("Напоминаний не найдено");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18)); title.setTextFill(Color.web(TEXT_PRIMARY));
        Label sub = new Label("Здесь появятся напоминания о ваших предстоящих визитах.");
        sub.setFont(Font.font("Segoe UI", 13)); sub.setTextFill(Color.web(TEXT_MUTED));
        box.getChildren().addAll(icon, title, sub);
        return box;
    }

    private void showDetailDialog(Reminder r) {
        Dialog<Void> dlg = new Dialog<>(); dlg.setTitle("Детали напоминания");
        DialogPane dp = dlg.getDialogPane(); dp.setPrefWidth(460);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().add(ButtonType.CLOSE);
        VBox c = new VBox(16); c.setPadding(new Insets(26));
        HBox hdr = new HBox(14); hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(0, 0, 4, 0));
        StackPane ico = new StackPane();
        Circle icBg = new Circle(26, Color.web(BLUE_SOFT));
        Label icLbl = new Label("🔔"); icLbl.setFont(Font.font("Segoe UI", 22));
        ico.getChildren().addAll(icBg, icLbl);
        VBox ht = new VBox(3);
        Label hn = new Label("Напоминание · " + r.spec());
        hn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17)); hn.setTextFill(Color.web(TEXT_PRIMARY));
        Label hs = new Label(r.doctor() + " • " + r.appointDate());
        hs.setFont(Font.font("Segoe UI", 13)); hs.setTextFill(Color.web(r.accentColor()));
        ht.getChildren().addAll(hn, hs);
        hdr.getChildren().addAll(ico, ht);
        Separator sep = new Separator(); sep.setStyle("-fx-background-color:" + BORDER_COLOR + ";");
        GridPane grid = new GridPane(); grid.setHgap(20); grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 4, 0));
        addDRow(grid, 0, "📅 Дата визита", r.appointDate() + " в " + r.appointTime());
        addDRow(grid, 1, "🕐 Отправить в", r.remindTime());
        addDRow(grid, 2, "⏰ Интервал", r.remindLabel());
        addDRow(grid, 3, "📧 Канал", r.channel());
        addDRow(grid, 4, "🔖 Статус", r.statusLabel());
        addDRow(grid, 5, "🟢 Включено", r.enabled() ? "Да" : "Нет");
        c.getChildren().addAll(hdr, sep, grid);
        dp.setContent(c); dlg.showAndWait();
    }

    private void addDRow(GridPane g, int row, String key, String val) {
        Label k = new Label(key); k.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        k.setTextFill(Color.web(TEXT_MUTED)); k.setMinWidth(130);
        Label v = new Label(val); v.setFont(Font.font("Segoe UI", 13));
        v.setTextFill(Color.web(TEXT_PRIMARY));
        g.add(k, 0, row); g.add(v, 1, row);
    }

    private void showAddReminderDialog() {
        Dialog<Void> dlg = new Dialog<>(); dlg.setTitle("Новое напоминание");
        DialogPane dp = dlg.getDialogPane(); dp.setPrefWidth(420);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) dp.lookupButton(ButtonType.OK); ok.setText("Сохранить");
        ok.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;");
        VBox c = new VBox(14); c.setPadding(new Insets(24));
        Label t = new Label("Новое напоминание");
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17)); t.setTextFill(Color.web(TEXT_PRIMARY));
        ComboBox<String> apptCb = new ComboBox<>(FXCollections.observableArrayList(
                "Кардиолог — Смирнов А.В. (5 мая 10:30)",
                "Стоматолог — Козлова М.И. (8 мая 14:00)",
                "Невролог — Петров К.Н. (15 мая 09:00)"));
        ComboBox<String> intervalCb = new ComboBox<>(FXCollections.observableArrayList(
                "За 24 часа","За 2 часа","За 30 минут","За 1 неделю"));
        intervalCb.setValue("За 24 часа");
        ComboBox<String> channelCb2 = new ComboBox<>(FXCollections.observableArrayList("Email","Push","SMS"));
        channelCb2.setValue("Email");
        c.getChildren().addAll(t, new Separator(),
                formField("Запись к врачу", apptCb),
                formField("Уведомить за", intervalCb),
                formField("Канал", channelCb2));
        dp.setContent(c); dlg.showAndWait();
    }

    private void showEditDialog(Reminder r) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Изменить напоминание");
        DialogPane dp = dlg.getDialogPane();
        dp.setPrefWidth(400);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.setText("Сохранить");
        okBtn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;");

        VBox c = new VBox(14); c.setPadding(new Insets(24));
        HBox mini = new HBox(10); mini.setAlignment(Pos.CENTER_LEFT);
        mini.setPadding(new Insets(12, 14, 12, 14));
        mini.setStyle("-fx-background-color:#F8FAFF;-fx-background-radius:10;-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:10;-fx-border-width:1;");
        StackPane mAv = new StackPane();
        Circle mC = new Circle(18, Color.web(r.softColor()));
        Label mI = new Label(r.icon()); mI.setFont(Font.font("Segoe UI", 15));
        mAv.getChildren().addAll(mC, mI);
        VBox mt = new VBox(2);
        Label mn = new Label(r.spec() + " — " + r.doctor());
        mn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); mn.setTextFill(Color.web(TEXT_PRIMARY));
        Label md = new Label(r.appointDate() + " в " + r.appointTime());
        md.setFont(Font.font("Segoe UI", 11)); md.setTextFill(Color.web(TEXT_SECONDARY));
        mt.getChildren().addAll(mn, md); mini.getChildren().addAll(mAv, mt);

        ComboBox<String> intCb = new ComboBox<>(FXCollections.observableArrayList(
                "За 24 часа","За 2 часа","За 30 минут","За 1 неделю"));
        intCb.setValue(r.remindLabel());
        ComboBox<String> chCb = new ComboBox<>(FXCollections.observableArrayList("Email","SMS"));
        chCb.setValue(r.channel());

        c.getChildren().addAll(mini, new Separator(),
                formField("Уведомить за", intCb),
                formField("Канал", chCb));
        dp.setContent(c);

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            String selectedLabel = intCb.getValue();
            int intervalMinutes = switch (selectedLabel) {
                case "За 24 часа" -> 1440;
                case "За 2 часа" -> 120;
                case "За 30 минут" -> 30;
                case "За 1 неделю" -> 10080;
                default -> 1440;
            };
            String channel = chCb.getValue();
            sendUpdateReminder(r.id(), intervalMinutes, channel, dlg);
        });

        dlg.showAndWait();
    }

    private void sendUpdateReminder(String reminderId, int intervalMinutes, String channel, Dialog<Void> dlg) {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.UPDATE_REMINDER)
                        .token(sessionToken)
                        .param("reminderId", reminderId)
                        .param("intervalMinutes", intervalMinutes)
                        .param("channel", channel)
                        .build();
                synchronized (sendLock) {
                    out.writeObject(cmd); out.flush(); out.reset();
                }
                Object resp = in.readObject();
                Platform.runLater(() -> {
                    if (resp instanceof Command response && response.isOk()) {
                        loadReminders();
                        dlg.close();
                    } else {
                        String msg = (resp instanceof Command) ? ((Command) resp).getMessage() : "Ошибка";
                        showAlert(Alert.AlertType.ERROR, "Не удалось обновить напоминание", msg);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Ошибка сети", ""));
            }
        });
    }

    private void showCancelConfirm(Reminder r) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Отмена напоминания");
        alert.setHeaderText("Отменить это напоминание?");
        alert.setContentText(
                "Напоминание «" + r.remindLabel() + "» для визита к " +
                        r.spec() + " будет отменено и не будет отправлено."
        );
        alert.showAndWait().ifPresent(btnType -> {
            if (btnType == ButtonType.OK) {
                executor.execute(() -> {
                    try {
                        Command cmd = Command.builder(CommandType.CANCEL_REMINDER)
                                .token(sessionToken)
                                .param("reminderId", r.id())
                                .build();
                        synchronized (sendLock) {
                            out.writeObject(cmd); out.flush(); out.reset();
                        }
                        Object resp = in.readObject();
                        Platform.runLater(() -> {
                            if (resp instanceof Command response && response.isOk()) {
                                loadReminders();
                            } else {
                                String msg = (resp instanceof Command) ?
                                        ((Command) resp).getMessage() : "Ошибка";
                                showAlert(Alert.AlertType.ERROR, "Не удалось отменить напоминание", msg);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        Platform.runLater(() ->
                                showAlert(Alert.AlertType.ERROR, "Ошибка сети", ""));
                    }
                });
            }
        });
    }

    private Button outlineSmBtn(String text, String color) {
        Button b = new Button(text); b.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        b.setTextFill(Color.web(color)); b.setCursor(Cursor.HAND); b.setPadding(new Insets(7, 16, 7, 16));
        String st = "-fx-background-color:" + CARD_BG + ";-fx-border-color:" + color + "44;"
                + "-fx-border-radius:8;-fx-background-radius:8;-fx-border-width:1.5;";
        b.setStyle(st);
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:" + color + "11;"
                + "-fx-border-color:" + color + "44;-fx-border-radius:8;-fx-background-radius:8;-fx-border-width:1.5;"));
        b.setOnMouseExited(e -> b.setStyle(st));
        return b;
    }

    private VBox formField(String label, Control input) {
        VBox b = new VBox(5);
        Label l = new Label(label); l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        l.setTextFill(Color.web(TEXT_SECONDARY));
        input.setMaxWidth(Double.MAX_VALUE);
        input.setStyle("-fx-background-color:#F8FAFF;-fx-border-color:" + BORDER_COLOR + ";"
                + "-fx-border-radius:8;-fx-background-radius:8;"
                + "-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-padding:8 12 8 12;");
        b.getChildren().addAll(l, input); return b;
    }

    public static void main(String[] args) { launch(args); }
}