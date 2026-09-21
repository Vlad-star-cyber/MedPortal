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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Doctors extends Application {

    private static final String ACCENT = "#3B82F6";
    private static final String ACCENT_DARK = "#1D4ED8";
    private static final String BG_PAGE = "#F5F7FA";
    private static final String CARD_BG = "#FFFFFF";
    private static final String TEXT_PRIMARY = "#1A1A2E";
    private static final String TEXT_SECONDARY = "#64748B";
    private static final String TEXT_MUTED = "#94A3B8";
    private static final String BORDER_COLOR = "#E8EDF2";
    private static final String ORANGE = "#F59E0B";

    private static final String[] AVATAR_COLORS = {
            "#3B82F6","#0891B2","#8B5CF6","#10B981",
            "#F59E0B","#F43F5E","#8B5CF6","#10B981"
    };

    public static class DoctorItem {
        private final long id;
        private final String fullName;
        private final String specialization;
        private final String specGroup;      // пока совпадает со specialization
        private final int experienceYears;
        private final boolean active;
        private final String officeNumber;
        private final String education;
        private final String about;
        private final String avatarColor;
        private double rating = 4.5;
        private int reviews = 0;
        private int price = 0;
        private String nextSlot = "";
        private String nextSlotShort = "";
        private boolean favourite = false;

        public DoctorItem(long id, String fullName, String specialization,
                          int experienceYears, boolean active,
                          String officeNumber, String education, String about) {
            this.id = id;
            this.fullName = fullName;
            this.specialization = specialization;
            this.specGroup = specialization;
            this.experienceYears = experienceYears;
            this.active = active;
            this.officeNumber = officeNumber;
            this.education = education;
            this.about = about;
            this.avatarColor = AVATAR_COLORS[(int)(id % AVATAR_COLORS.length)];
        }

        public long getId() { return id; }
        public String getFullName() { return fullName; }
        public String getSpecialization() { return specialization; }
        public String getSpecGroup() { return specGroup; }
        public int getExperienceYears() { return experienceYears; }
        public boolean isActive() { return active; }
        public String getOfficeNumber() { return officeNumber; }
        public String getEducation() { return education; }
        public String getAbout() { return about; }
        public double getRating() { return rating; }
        public int getReviews() { return reviews; }
        public int getPrice() { return price; }
        public String getNextSlot() { return nextSlot; }
        public String getNextSlotShort() { return nextSlotShort; }
        public boolean isFavourite() { return favourite; }
        public String getAvatarColor() { return avatarColor; }

        public void setRating(double r) { this.rating = r; }
        public void setReviews(int r) { this.reviews = r; }
        public void setPrice(int p) { this.price = p; }
        public void setNextSlot(String s) { this.nextSlot = s; }
        public void setNextSlotShort(String s) { this.nextSlotShort = s; }
        public void setFavourite(boolean f) { this.favourite = f; }
    }

    private String sessionToken;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object sendLock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "doctors-loader");
        t.setDaemon(true);
        return t;
    });

    private final ObservableList<DoctorItem> allDoctors = FXCollections.observableArrayList();

    private String activeGroup = "Все специализации";
    private String sortMode = "rating";
    private String searchQuery = "";
    private FlowPane gridPane;
    private Label countLabel;

    @Override
    public void start(Stage stage) {
        stage.setTitle("MedPortal — Каталог врачей");
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
        loadDoctors();
    }

    private void loadDoctors() {
        executor.execute(() -> {
            try {
                Command cmd = Command.builder(CommandType.GET_ALL_DOCTORS)
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
                    List<DoctorItem> list = parseDoctorList(json);
                    Platform.runLater(() -> {
                        allDoctors.setAll(list);
                        refreshGrid();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<DoctorItem> parseDoctorList(String json) {
        List<DoctorItem> list = new ArrayList<>();
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
            DoctorItem d = parseSingleDoctor(part);
            if (d != null) list.add(d);
        }
        return list;
    }

    private DoctorItem parseSingleDoctor(String jsonObj) {
        try {
            String idStr = extractString(jsonObj, "id");
            if (idStr == null) return null;
            long id = Long.parseLong(idStr);
            String fullName = extractString(jsonObj, "fullName");
            String spec = extractString(jsonObj, "specialization");
            int experience = Integer.parseInt(extractString(jsonObj, "experienceYears"));
            boolean active = Boolean.parseBoolean(extractString(jsonObj, "active"));
            String office = extractString(jsonObj, "officeNumber");
            String education = extractString(jsonObj, "education");
            String about = extractString(jsonObj, "about");

            DoctorItem item = new DoctorItem(id, fullName, spec, experience, active, office, education, about);
            String ratingStr = extractString(jsonObj, "rating");
            if (ratingStr != null) item.setRating(Double.parseDouble(ratingStr));
            String reviewsStr = extractString(jsonObj, "reviews");
            if (reviewsStr != null) item.setReviews(Integer.parseInt(reviewsStr));
            String priceStr = extractString(jsonObj, "price");
            if (priceStr != null) item.setPrice(Integer.parseInt(priceStr));
            String nextSlot = extractString(jsonObj, "nextSlot");
            if (nextSlot != null) item.setNextSlot(nextSlot);

            return item;
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

    public BorderPane buildMainArea() {
        BorderPane bp = new BorderPane();
        bp.setStyle("-fx-background-color:" + BG_PAGE + ";");
        VBox inner = new VBox(20);
        inner.setPadding(new Insets(32, 32, 32, 32));
        inner.getChildren().addAll(
                buildPageTitle(),
                buildSearchRow(),
                buildResultsLabel(),
                buildGrid()
        );
        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:" + BG_PAGE + ";");
        bp.setCenter(scroll);
        return bp;
    }

    private Label buildPageTitle() {
        Label lbl = new Label("Поиск специалиста");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        lbl.setTextFill(Color.web(TEXT_PRIMARY));
        return lbl;
    }

    private HBox buildSearchRow() {
        HBox row = new HBox(12); row.setAlignment(Pos.CENTER_LEFT);
        HBox searchBox = new HBox(8); searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(10, 16, 10, 16));
        searchBox.setPrefWidth(420);
        searchBox.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:10;-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:10;-fx-border-width:1.5;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),8,0,0,2);");
        Label si = new Label("🔍"); si.setFont(Font.font("Segoe UI", 14));
        TextField sf = new TextField(); sf.setPromptText("Поиск врачей..."); sf.setPrefWidth(360);
        sf.setStyle("-fx-background-color:transparent;-fx-border-width:0;-fx-font-size:14px;-fx-font-family:'Segoe UI';-fx-text-fill:" + TEXT_PRIMARY + ";");
        sf.textProperty().addListener((obs, ov, nv) -> { searchQuery = nv.toLowerCase().trim(); refreshGrid(); });
        searchBox.getChildren().addAll(si, sf);

        ComboBox<String> specBox = new ComboBox<>(FXCollections.observableArrayList("Все специализации","Терапия","Стоматология","Хирургия","Офтальмология","Эндокринология","Дерматология"));
        specBox.setValue("Все специализации"); specBox.setPrefWidth(200);
        styleComboBox(specBox);
        specBox.setOnAction(e -> { activeGroup = specBox.getValue(); refreshGrid(); });

        ComboBox<String> sortBox = new ComboBox<>(FXCollections.observableArrayList("Ближайшее время","По рейтингу","По опыту","По цене"));
        sortBox.setValue("Ближайшее время"); sortBox.setPrefWidth(185);
        styleComboBox(sortBox);
        sortBox.setOnAction(e -> {
            sortMode = switch (sortBox.getValue()) {
                case "По рейтингу" -> "rating";
                case "По опыту" -> "experience";
                case "По цене" -> "price";
                default -> "slot";
            };
            refreshGrid();
        });

        row.getChildren().addAll(searchBox, specBox, sortBox);
        return row;
    }

    private void styleComboBox(ComboBox<String> cb) {
        cb.setStyle("-fx-background-color:" + CARD_BG + ";-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:10;-fx-background-radius:10;-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.04),8,0,0,2);");
    }

    private HBox buildResultsLabel() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        countLabel = new Label(); countLabel.setFont(Font.font("Segoe UI", 13)); countLabel.setTextFill(Color.web(TEXT_MUTED));
        updateCount(allDoctors.size());
        bar.getChildren().add(countLabel);
        return bar;
    }

    private void updateCount(int n) {
        if (countLabel != null) countLabel.setText("Найдено специалистов: " + n);
    }

    private FlowPane buildGrid() {
        gridPane = new FlowPane(Orientation.HORIZONTAL, 16, 16);
        gridPane.setAlignment(Pos.TOP_LEFT);
        refreshGrid();
        return gridPane;
    }

    private void refreshGrid() {
        List<DoctorItem> filtered = new ArrayList<>(allDoctors);
        if (!activeGroup.equals("Все специализации")) {
            filtered.removeIf(d -> !d.getSpecGroup().equals(activeGroup));
        }
        if (!searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase();
            filtered.removeIf(d -> !d.getFullName().toLowerCase().contains(q) && !d.getSpecialization().toLowerCase().contains(q));
        }
        filtered.sort((a, b) -> switch (sortMode) {
            case "experience" -> Integer.compare(b.getExperienceYears(), a.getExperienceYears());
            case "price" -> Integer.compare(a.getPrice(), b.getPrice());
            default -> Double.compare(b.getRating(), a.getRating());
        });
        gridPane.getChildren().clear();
        if (filtered.isEmpty()) {
            gridPane.getChildren().add(buildEmptyState());
        } else {
            for (DoctorItem d : filtered) gridPane.getChildren().add(buildDoctorCard(d));
        }
        updateCount(filtered.size());
    }

    private VBox buildDoctorCard(DoctorItem d) {
        VBox card = new VBox(0);
        card.setPrefWidth(430); card.setMaxWidth(430); card.setMinHeight(180);
        card.setCursor(Cursor.HAND);
        card.setStyle(cardStyle(false));

        HBox top = new HBox(16); top.setAlignment(Pos.TOP_LEFT); top.setPadding(new Insets(22, 22, 16, 22));
        StackPane avatar = new StackPane();
        Circle avatarBg = new Circle(26, Color.web(d.getAvatarColor()));
        Label initLbl = new Label(initials(d.getFullName()));
        initLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16)); initLbl.setTextFill(Color.WHITE);
        avatar.getChildren().addAll(avatarBg, initLbl);
        avatar.setMinWidth(52); avatar.setMinHeight(52);

        VBox info = new VBox(3); HBox.setHgrow(info, Priority.ALWAYS);
        Label nameLbl = new Label(d.getFullName());
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15)); nameLbl.setTextFill(Color.web(TEXT_PRIMARY)); nameLbl.setWrapText(true);
        Label specLbl = new Label(d.getSpecialization());
        specLbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13)); specLbl.setTextFill(Color.web(d.getAvatarColor()));
        Label expLbl = new Label("Стаж: " + d.getExperienceYears() + " лет");
        expLbl.setFont(Font.font("Segoe UI", 12)); expLbl.setTextFill(Color.web(TEXT_SECONDARY));
        info.getChildren().addAll(nameLbl, specLbl, expLbl);
        top.getChildren().addAll(avatar, info);

        Separator sep = new Separator(); sep.setPadding(new Insets(0, 22, 0, 22)); sep.setStyle("-fx-background-color:" + BORDER_COLOR + ";");

        HBox ratingRow = new HBox(4); ratingRow.setAlignment(Pos.CENTER_LEFT); ratingRow.setPadding(new Insets(12, 22, 0, 22));
        Label starLbl = new Label("★"); starLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15)); starLbl.setTextFill(Color.web(ORANGE));
        Label ratingVal = new Label(String.format("%.1f", d.getRating()));
        ratingVal.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14)); ratingVal.setTextFill(Color.web(TEXT_PRIMARY));
        Label reviewsLbl = new Label("(" + d.getReviews() + "+ отзывов)");
        reviewsLbl.setFont(Font.font("Segoe UI", 12)); reviewsLbl.setTextFill(Color.web(TEXT_MUTED));
        ratingRow.getChildren().addAll(starLbl, ratingVal, reviewsLbl);

        HBox bottom = new HBox(); bottom.setAlignment(Pos.CENTER_LEFT); bottom.setPadding(new Insets(14, 22, 20, 22));
        Label priceLbl = new Label(d.getPrice() + " BYN");
        priceLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16)); priceLbl.setTextFill(Color.web(TEXT_PRIMARY));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button bookBtn = new Button("Записаться");
        bookBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); bookBtn.setTextFill(Color.WHITE);
        bookBtn.setCursor(Cursor.HAND); bookBtn.setPadding(new Insets(9, 22, 9, 22));
        bookBtn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.28),8,0,0,3);");
        bookBtn.setOnMouseEntered(e -> bookBtn.setStyle("-fx-background-color:" + ACCENT_DARK + ";-fx-background-radius:9;-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.40),10,0,0,4);"));
        bookBtn.setOnMouseExited(e -> bookBtn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.28),8,0,0,3);"));
        bookBtn.setOnAction(e -> { e.consume(); showBookingDialog(d); });
        bottom.getChildren().addAll(priceLbl, sp, bookBtn);

        card.getChildren().addAll(top, sep, ratingRow, bottom);
        card.setOnMouseEntered(e -> card.setStyle(cardStyle(true)));
        card.setOnMouseExited(e -> card.setStyle(cardStyle(false)));
        card.setOnMouseClicked(e -> showDoctorProfile(d));
        return card;
    }

    private String cardStyle(boolean hover) {
        return "-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;"
                + "-fx-border-color:" + (hover ? ACCENT + "44" : BORDER_COLOR) + ";"
                + "-fx-border-radius:14;-fx-border-width:1.5;"
                + "-fx-effect:dropshadow(gaussian," + (hover ? "rgba(59,130,246,0.12),18,0,0,5" : "rgba(0,0,0,0.06),12,0,0,3") + ");";
    }

    private VBox buildEmptyState() {
        VBox box = new VBox(12); box.setAlignment(Pos.CENTER); box.setPadding(new Insets(60));
        box.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:14;-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:14;-fx-border-width:1.5;");
        box.setPrefWidth(600);
        Label icon = new Label("🔍"); icon.setFont(Font.font("Segoe UI", 44));
        Label title = new Label("Специалисты не найдены");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18)); title.setTextFill(Color.web(TEXT_PRIMARY));
        Label sub = new Label("Попробуйте изменить параметры поиска или фильтры.");
        sub.setFont(Font.font("Segoe UI", 13)); sub.setTextFill(Color.web(TEXT_MUTED));
        box.getChildren().addAll(icon, title, sub);
        return box;
    }

    private void showDoctorProfile(DoctorItem d) {
        Dialog<Void> dlg = new Dialog<>(); dlg.setTitle("Профиль врача");
        DialogPane dp = dlg.getDialogPane(); dp.setPrefWidth(480);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().add(ButtonType.CLOSE);
        VBox c = new VBox(18); c.setPadding(new Insets(28));
        HBox hdr = new HBox(18); hdr.setAlignment(Pos.CENTER_LEFT); hdr.setPadding(new Insets(0, 0, 4, 0));
        StackPane avatarBig = new StackPane();
        Circle bigCircle = new Circle(32, Color.web(d.getAvatarColor()));
        Label bigInit = new Label(initials(d.getFullName()));
        bigInit.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20)); bigInit.setTextFill(Color.WHITE);
        avatarBig.getChildren().addAll(bigCircle, bigInit);
        VBox hdrText = new VBox(4);
        Label hn = new Label(d.getFullName()); hn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17)); hn.setTextFill(Color.web(TEXT_PRIMARY));
        Label hs = new Label(d.getSpecialization() + " • Стаж: " + d.getExperienceYears() + " лет");
        hs.setFont(Font.font("Segoe UI", 13)); hs.setTextFill(Color.web(d.getAvatarColor()));
        hdrText.getChildren().addAll(hn, hs);
        hdr.getChildren().addAll(avatarBig, hdrText);
        HBox stats = new HBox(0); stats.setAlignment(Pos.CENTER_LEFT); stats.setPadding(new Insets(14));
        stats.setStyle("-fx-background-color:#F8FAFF;-fx-background-radius:12;-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:12;-fx-border-width:1;");
        stats.getChildren().addAll(
                statBlock("★", String.format("%.1f", d.getRating()), "Рейтинг"), vSep(),
                statBlock("💬", d.getReviews() + "+", "Отзывов"), vSep(),
                statBlock("🏆", d.getExperienceYears() + " лет", "Опыт"), vSep(),
                statBlock("💰", d.getPrice() + " BYN", "Приём"));
        c.getChildren().addAll(hdr, stats,
                infoBlock("О специалисте", d.getAbout()),
                infoBlock("Образование", "🎓 " + d.getEducation()),
                infoBlock("Ближайший приём","📅 " + d.getNextSlot()));
        Button bookBtn = new Button("Записаться к " + d.getFullName().split(" ")[0] + " " + d.getFullName().split(" ")[1]);
        bookBtn.setMaxWidth(Double.MAX_VALUE); bookBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14)); bookBtn.setTextFill(Color.WHITE); bookBtn.setPadding(new Insets(12)); bookBtn.setCursor(Cursor.HAND);
        bookBtn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:10;-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.30),10,0,0,4);");
        bookBtn.setOnAction(e -> { dlg.close(); showBookingDialog(d); });
        c.getChildren().add(bookBtn);
        dp.setContent(c);
        dlg.showAndWait();
    }

    private HBox statBlock(String icon, String val, String label) {
        VBox b = new VBox(3); b.setAlignment(Pos.CENTER); b.setMinWidth(90); b.setMaxWidth(Double.MAX_VALUE);
        Label il = new Label(icon); il.setFont(Font.font("Segoe UI", 16));
        Label vl = new Label(val); vl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); vl.setTextFill(Color.web(TEXT_PRIMARY));
        Label ll = new Label(label); ll.setFont(Font.font("Segoe UI", 10)); ll.setTextFill(Color.web(TEXT_MUTED));
        b.getChildren().addAll(il, vl, ll);
        HBox w = new HBox(b); HBox.setHgrow(b, Priority.ALWAYS);
        return w;
    }
    private Separator vSep() { Separator s = new Separator(Orientation.VERTICAL); s.setStyle("-fx-background-color:" + BORDER_COLOR + ";"); return s; }
    private VBox infoBlock(String title, String text) {
        VBox b = new VBox(4);
        Label t = new Label(title); t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11)); t.setTextFill(Color.web(TEXT_MUTED));
        Label v = new Label(text); v.setFont(Font.font("Segoe UI", 13)); v.setTextFill(Color.web(TEXT_PRIMARY)); v.setWrapText(true);
        b.getChildren().addAll(t, v);
        return b;
    }

    public static class SlotOption {
        private final String time;
        private final long slotId;
        public SlotOption(String time, long slotId) { this.time = time; this.slotId = slotId; }
        public String getTime() { return time; }
        public long getSlotId() { return slotId; }
        @Override public String toString() { return time; }
    }

    private void showBookingDialog(DoctorItem d) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Запись к врачу");
        DialogPane dp = dlg.getDialogPane();
        dp.setPrefWidth(420);
        dp.setStyle("-fx-background-color:" + CARD_BG + ";");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.setText("Записаться");
        okBtn.setStyle("-fx-background-color:" + ACCENT + ";-fx-background-radius:9;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:13px;");

        VBox content = new VBox(14); content.setPadding(new Insets(24));

        HBox mini = new HBox(12); mini.setAlignment(Pos.CENTER_LEFT);
        mini.setPadding(new Insets(12, 14, 12, 14));
        mini.setStyle("-fx-background-color:#F8FAFF;-fx-background-radius:10;-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:10;-fx-border-width:1;");
        StackPane mAv = new StackPane();
        Circle mC = new Circle(20, Color.web(d.getAvatarColor()));
        Label mI = new Label(initials(d.getFullName())); mI.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12)); mI.setTextFill(Color.WHITE);
        mAv.getChildren().addAll(mC, mI);
        VBox mT = new VBox(2);
        Label mn = new Label(d.getFullName()); mn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); mn.setTextFill(Color.web(TEXT_PRIMARY));
        Label ms = new Label(d.getSpecialization() + " • ★ " + d.getRating()); ms.setFont(Font.font("Segoe UI", 11)); ms.setTextFill(Color.web(TEXT_SECONDARY));
        mT.getChildren().addAll(mn, ms); mini.getChildren().addAll(mAv, mT);

        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Выберите дату");
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now().plusDays(1)));
            }
        });

        ComboBox<SlotOption> timeCombo = new ComboBox<>();
        timeCombo.setPromptText("Сначала выберите дату");
        timeCombo.setMaxWidth(Double.MAX_VALUE);
        timeCombo.setDisable(true);

        TextField reasonField = new TextField();
        reasonField.setPromptText("Причина обращения...");
        reasonField.setMaxWidth(Double.MAX_VALUE);

        datePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate == null) {
                timeCombo.setDisable(true);
                timeCombo.setPromptText("Сначала выберите дату");
                return;
            }
            timeCombo.setDisable(false);
            timeCombo.setPromptText("Загрузка...");
            executor.execute(() -> {
                try {
                    Command cmd = Command.builder(CommandType.GET_SLOTS)
                            .token(sessionToken)
                            .param("doctorId", d.getId())
                            .param("date", newDate.toString())
                            .build();
                    synchronized (sendLock) {
                        out.writeObject(cmd); out.flush(); out.reset();
                    }
                    Object resp = in.readObject();
                    if (resp instanceof Command response && response.isOk()) {
                        String slotsStr = response.getParam("slots");
                        List<SlotOption> options = new ArrayList<>();
                        System.out.println("Слоты от сервера: " + slotsStr);
                        if (slotsStr != null && !slotsStr.isBlank()) {
                            String[] parts = slotsStr.split("\\|");
                            for (String part : parts) {
                                String[] sub = part.split(":");
                                if (sub.length >= 2) {                // разрешаем больше двух частей
                                    long slotId = Long.parseLong(sub[0]);
                                    // Время начинается с sub[1], при sub.length > 2 добавляем остаток через ":"
                                    String startTime = sub[1];
                                    if (sub.length > 2) {
                                        startTime += ":" + sub[2];
                                    }
                                    // На случай, если сервер всё же пришлёт диапазон, отрезаем "-"
                                    if (startTime.contains("-")) {
                                        startTime = startTime.split("-")[0];
                                    }
                                    options.add(new SlotOption(startTime, slotId));
                                }
                            }
                        }
                        List<SlotOption> finalOptions = options;
                        Platform.runLater(() -> {
                            timeCombo.getItems().setAll(finalOptions);
                            if (finalOptions.isEmpty()) {
                                timeCombo.setPromptText("Нет свободных слотов");
                                timeCombo.setDisable(true);
                            } else {
                                timeCombo.setValue(finalOptions.get(0));
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        timeCombo.setPromptText("Ошибка сети");
                        timeCombo.setDisable(true);
                    });
                }
            });
        });

        content.getChildren().addAll(mini, new Separator(),
                formField("Дата приёма", datePicker),
                formField("Время", timeCombo),
                formField("Причина обращения", reasonField));

        dp.setContent(content);

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            LocalDate selectedDate = datePicker.getValue();
            SlotOption selectedSlot = timeCombo.getValue();
            if (selectedDate == null || selectedSlot == null) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.WARNING, "Выберите дату и время.");
                alert.showAndWait();
                return;
            }
            event.consume();
            String reason = reasonField.getText().trim();
            LocalTime localTime = LocalTime.parse(selectedSlot.getTime());
            LocalDateTime dateTime = LocalDateTime.of(selectedDate, localTime);
            String datetimeStr = dateTime.toString() + ":00";

            executor.execute(() -> {
                try {
                    Command bookCmd = Command.builder(CommandType.BOOK_APPOINTMENT)
                            .token(sessionToken)
                            .param("doctorId", d.getId())
                            .param("slotId", selectedSlot.getSlotId())
                            .param("reason", reason)
                            .param("datetime", datetimeStr)
                            .build();
                    synchronized (sendLock) {
                        out.writeObject(bookCmd); out.flush(); out.reset();
                    }
                    Object resp = in.readObject();
                    Platform.runLater(() -> {
                        if (resp instanceof Command response && response.isOk()) {
                            Alert info = new Alert(Alert.AlertType.INFORMATION, "Запись успешно создана!");
                            info.showAndWait();
                            dlg.close();
                        } else {
                            String msg = (resp instanceof Command) ? ((Command) resp).getMessage() : "Ошибка";
                            Alert err = new Alert(Alert.AlertType.ERROR, msg);
                            err.showAndWait();
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        Alert err = new Alert(Alert.AlertType.ERROR, "Ошибка сети.");
                        err.showAndWait();
                    });
                }
            });
        });

        dlg.showAndWait();
    }

    private String initials(String name) {
        String[] p = name.split(" ");
        return p.length >= 2 ? "" + p[0].charAt(0) + p[1].charAt(0) : "" + name.charAt(0);
    }

    private VBox formField(String label, Control input) {
        VBox b = new VBox(5);
        Label l = new Label(label); l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12)); l.setTextFill(Color.web(TEXT_SECONDARY));
        input.setMaxWidth(Double.MAX_VALUE);
        input.setStyle("-fx-background-color:#F8FAFF;-fx-border-color:" + BORDER_COLOR + ";-fx-border-radius:8;-fx-background-radius:8;-fx-border-width:1.5;-fx-font-size:13px;-fx-font-family:'Segoe UI';-fx-padding:8 12 8 12;");
        b.getChildren().addAll(l, input);
        return b;
    }

    public static void main(String[] args) { launch(args); }
}