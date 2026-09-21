package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;

import org.example.common.Command;
import org.example.common.CommandType;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Login extends Application {

    private static final String BG_COLOR = "#FFFFFF";
    private static final String ACCENT_BLUE = "#3B82F6";
    private static final String BTN_HOVER = "#2563EB";
    private static final String BTN_DISABLED = "#93C5FD";
    private static final String TITLE_COLOR = "#1A1A2E";
    private static final String SUBTITLE_COLOR = "#6B7280";
    private static final String LABEL_COLOR = "#374151";
    private static final String FIELD_BORDER = "#D1D5DB";
    private static final String FIELD_FOCUS = "#3B82F6";
    private static final String FIELD_ERROR = "#EF4444";
    private static final String FIELD_BG = "#FFFFFF";
    private static final String HINT_COLOR = "#6B7280";
    private static final String LINK_COLOR = "#3B82F6";
    private static final String BTN_TEXT = "#FFFFFF";

    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 5555;

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Socket socket;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "network-thread");
        t.setDaemon(true);
        return t;
    });

    private final Object sendLock = new Object();

    private TextField emailField;
    private PasswordField passField;
    private Label emailError;
    private Label passError;
    private Button loginBtn;
    private Text registerLink;

    @Override
    public void start(Stage stage) {
        stage.setTitle("MedPortal — Вход");
        Scene scene = initializeAndGetScene();
        stage.setScene(scene);
        stage.show();
        stage.setOnCloseRequest(e -> shutdown());
    }

    public Scene initializeAndGetScene() {
        connectToServer();
        return createScene();
    }

    public Scene createScene() {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #F0F4FF;");
        root.setFillWidth(true);

        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(40));
        card.setMaxWidth(460);
        card.setMinWidth(460);
        card.setStyle(
                "-fx-background-color: " + BG_COLOR + ";" +
                        "-fx-background-radius: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 24, 0, 0, 6);"
        );

        Label logo = new Label("+ MedPortal");
        logo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        logo.setTextFill(Color.web(ACCENT_BLUE));
        logo.setMaxWidth(Double.MAX_VALUE);
        logo.setAlignment(Pos.CENTER);

        Label title = new Label("Войти в аккаунт");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        title.setTextFill(Color.web(TITLE_COLOR));
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);

        Label subtitle = new Label("Заполните данные для входа в систему");
        subtitle.setFont(Font.font("Segoe UI", 13));
        subtitle.setTextFill(Color.web(SUBTITLE_COLOR));
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setWrapText(true);   // исправлено

        emailField = makeTextField("example@mail.ru");
        passField = makePasswordField("Минимум 8 символов");
        emailError = errorLabel();
        passError = errorLabel();

        VBox emailBlock = fieldBlock("Электронная почта", emailField, emailError);
        VBox passBlock = fieldBlock("Пароль", passField, passError);

        loginBtn = new Button("Войти");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(50);
        loginBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        loginBtn.setTextFill(Color.web(BTN_TEXT));
        loginBtn.setCursor(Cursor.HAND);
        loginBtn.setStyle(btnStyle(ACCENT_BLUE));
        loginBtn.setOnMouseEntered(e -> {
            if (!loginBtn.isDisabled()) loginBtn.setStyle(btnStyle(BTN_HOVER));
        });
        loginBtn.setOnMouseExited(e -> {
            if (!loginBtn.isDisabled()) loginBtn.setStyle(btnStyle(ACCENT_BLUE));
        });
        loginBtn.setOnAction(e -> handleLogin());

        TextFlow regFlow = new TextFlow();
        regFlow.setTextAlignment(TextAlignment.CENTER);
        regFlow.setMaxWidth(Double.MAX_VALUE);
        Text noAcc = new Text("Нет аккаунта? ");
        noAcc.setFont(Font.font("Segoe UI", 13));
        noAcc.setFill(Color.web(HINT_COLOR));
        registerLink = new Text("Регистрация");
        registerLink.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        registerLink.setFill(Color.web(LINK_COLOR));
        registerLink.setCursor(Cursor.HAND);
        registerLink.setOnMouseClicked(e -> navigateToRegister());
        regFlow.getChildren().addAll(noAcc, registerLink);

        card.getChildren().addAll(logo, title, subtitle, emailBlock, passBlock, loginBtn, regFlow);

        HBox wrapper = new HBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(40, 20, 40, 20));
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        root.getChildren().add(wrapper);

        return new Scene(root, 1400, 800);
    }

    private void connectToServer() {
        executor.submit(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                System.out.println("[Login] Подключено к серверу " + SERVER_IP + ":" + SERVER_PORT);
            } catch (Exception ex) {
                System.err.println("[Login] Ошибка подключения: " + ex.getMessage());
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.WARNING, "Нет соединения",
                                "Не удалось подключиться к серверу."));
            }
        });
    }

    private void shutdown() {
        executor.submit(() -> {
            try {
                if (socket != null && !socket.isClosed()) {
                    Command bye = Command.builder(CommandType.LOGOUT).build();
                    sendToServer(bye);
                    out.close();
                    in.close();
                    socket.close();
                }
            } catch (Exception ignored) {}
        });
        executor.shutdown();
    }

    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passField.getText();

        boolean valid = true;
        if (email.isEmpty()) {
            showFieldError(emailError, "Введите email");
            valid = false;
        } else {
            clearFieldError(emailError, emailField);
        }
        if (password.isEmpty()) {
            showFieldError(passError, "Введите пароль");
            valid = false;
        } else if (password.length() < 8) {
            showFieldError(passError, "Пароль должен содержать не менее 8 символов");
            valid = false;
        } else {
            clearFieldError(passError, passField);
        }
        if (!valid) return;

        if (out == null || socket == null || socket.isClosed()) {
            showAlert(Alert.AlertType.ERROR, "Ошибка соединения", "Нет подключения к серверу.");
            return;
        }

        setLoading(true);

        String passwordHash = sha256(password);
        System.out.println("Хеш пароля: " + passwordHash);
        Command request = Command.builder(CommandType.LOGIN)
                .param("login", email)
                .param("password", passwordHash)
                .build();

        executor.submit(() -> {
            try {
                sendToServer(request);
                Object raw = in.readObject();
                Platform.runLater(() -> handleServerResponse(raw));
            } catch (java.io.EOFException eof) {
                Platform.runLater(() -> {
                    setLoading(false);
                    showAlert(Alert.AlertType.ERROR, "Ошибка соединения", "Сервер разорвал соединение.");
                });
            } catch (Exception ex) {
                System.err.println("[Login] Сетевая ошибка: " + ex.getMessage());
                Platform.runLater(() -> {
                    setLoading(false);
                    showAlert(Alert.AlertType.ERROR, "Сетевая ошибка", "Не удалось получить ответ от сервера.");
                });
            }
        });
    }

    private void handleServerResponse(Object raw) {
        setLoading(false);
        if (!(raw instanceof Command response)) {
            showAlert(Alert.AlertType.ERROR, "Ошибка протокола", "Неожиданный тип ответа от сервера.");
            return;
        }

        switch (response.getStatus()) {
            case OK -> {
                String role = response.getParam("role");
                String token = response.getParam("token");
                long userId = Long.parseLong(response.getParam("userId", "0"));
                String fullName = response.getParam("fullName", emailField.getText().trim());

                executor.submit(() -> {
                    try {
                        Command profileCmd = Command.builder(CommandType.GET_PROFILE)
                                .token(token)
                                .build();
                        Command profileResp = sendCommandSync(profileCmd);

                        String phone = "", policy = "", birthDate = "", allergy = "", bloodType = "";
                        if (profileResp.isOk()) {
                            phone = profileResp.getParam("phone", "");
                            policy = profileResp.getParam("policy", "");
                            birthDate = profileResp.getParam("birthDate", "");
                            allergy = profileResp.getParam("allergy", "");
                            bloodType = profileResp.getParam("bloodType", "");
                        }

                        final String fPhone = phone, fPolicy = policy, fBirthDate = birthDate,
                                fAllergy = allergy, fBloodType = bloodType;

                        Platform.runLater(() -> {
                            Stage stage = (Stage) loginBtn.getScene().getWindow();
                            switch (role) {
                                case "PATIENT" -> {
                                    Main main = new Main();
                                    main.setUserSession(fullName, token, userId,
                                            fPhone, fPolicy, fBirthDate, fAllergy, fBloodType);
                                    main.setNetworkStreams(out, in, socket);
                                    Scene scene = main.createScene();
                                    stage.setScene(scene);
                                    stage.setTitle("MedPortal — Главная");
                                }
                                case "DOCTOR" -> {
                                    String specialization = response.getParam("specialization", "");
                                    long doctorId = Long.parseLong(response.getParam("doctorId", "0"));
                                    DoctorMain doctorMain = new DoctorMain();
                                    doctorMain.setUserSession(fullName, token, userId, specialization, doctorId);
                                    doctorMain.setNetworkStreams(out, in, socket);   // ← передаём потоки
                                    Stage docStage = new Stage();
                                    try { doctorMain.start(docStage); } catch (Exception ex) { ex.printStackTrace(); }
                                    stage.close();
                                }
                                case "ADMIN" -> {
                                    AdminMain adminMain = new AdminMain();
                                    adminMain.setUserSession(fullName, token, userId);
                                    adminMain.setNetworkStreams(out, in, socket);    // <-- добавить эту строку
                                    Stage admStage = new Stage();
                                    try { adminMain.start(admStage); } catch (Exception ex) { ex.printStackTrace(); }
                                    stage.close();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() ->
                                showAlert(Alert.AlertType.ERROR, "Ошибка загрузки профиля", e.getMessage()));
                    }
                });
            }
            case CLIENT_ERROR -> {
                String msg = response.getMessage();
                if (msg != null && (msg.toLowerCase().contains("пароль") || msg.toLowerCase().contains("логин"))) {
                    showFieldError(emailError, "Неверный email или пароль");
                    showFieldError(passError, "Неверный email или пароль");
                } else {
                    showAlert(Alert.AlertType.WARNING, "Ошибка входа", msg);
                }
            }
            case SERVER_ERROR -> showAlert(Alert.AlertType.ERROR, "Ошибка сервера", response.getMessage());
            default -> showAlert(Alert.AlertType.ERROR, "Неизвестный ответ", "Статус: " + response.getStatus());
        }
    }

    private void navigateToRegister() {
        Stage stage = (Stage) registerLink.getScene().getWindow();
        Scene regScene = new Registration().initializeAndGetScene();
        stage.setScene(regScene);
        stage.setTitle("MedPortal — Регистрация");
    }

    private void setLoading(boolean loading) {
        loginBtn.setDisable(loading);
        loginBtn.setText(loading ? "Вход..." : "Войти");
        loginBtn.setStyle(loading ? btnStyle(BTN_DISABLED) : btnStyle(ACCENT_BLUE));
        emailField.setDisable(loading);
        passField.setDisable(loading);
    }

    private void showFieldError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearFieldError(Label errorLabel, Control field) {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        highlightField(field, false);
    }

    private void highlightField(Control field, boolean error) {
        String base = "-fx-background-color:" + FIELD_BG +
                ";-fx-border-radius:8;-fx-background-radius:8;" +
                "-fx-border-width:1.5;" +
                "-fx-font-size:14px;-fx-font-family:'Segoe UI';" +
                "-fx-padding:11 14 11 14;-fx-text-fill:#374151;";
        field.setStyle(base + "-fx-border-color:" + (error ? FIELD_ERROR : FIELD_BORDER) + ";");
    }

    private Label errorLabel() {
        Label l = new Label();
        l.setFont(Font.font("Segoe UI", 11));
        l.setTextFill(Color.web(FIELD_ERROR));
        l.setVisible(false);
        l.setManaged(false);
        return l;
    }

    private VBox fieldBlock(String labelText, Control field, Label error) {
        VBox block = new VBox(4);
        block.setMaxWidth(Double.MAX_VALUE);
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        lbl.setTextFill(Color.web(LABEL_COLOR));
        block.getChildren().addAll(lbl, field, error);
        return block;
    }

    private TextField makeTextField(String placeholder) {
        TextField f = new TextField();
        f.setPromptText(placeholder);
        f.setMaxWidth(Double.MAX_VALUE);
        String base = baseFieldStyle();
        f.setStyle(base);
        f.focusedProperty().addListener((obs, was, now) ->
                f.setStyle(base + "-fx-border-color:" + (now ? FIELD_FOCUS : FIELD_BORDER) + ";"));
        return f;
    }

    private PasswordField makePasswordField(String placeholder) {
        PasswordField f = new PasswordField();
        f.setPromptText(placeholder);
        f.setMaxWidth(Double.MAX_VALUE);
        String base = baseFieldStyle();
        f.setStyle(base);
        f.focusedProperty().addListener((obs, was, now) ->
                f.setStyle(base + "-fx-border-color:" + (now ? FIELD_FOCUS : FIELD_BORDER) + ";"));
        return f;
    }

    private String baseFieldStyle() {
        return "-fx-background-color:" + FIELD_BG + ";" +
                "-fx-border-color:" + FIELD_BORDER + ";" +
                "-fx-border-radius:8;-fx-background-radius:8;" +
                "-fx-border-width:1.5;" +
                "-fx-font-size:14px;-fx-font-family:'Segoe UI';" +
                "-fx-padding:11 14 11 14;-fx-text-fill:#374151;";
    }

    private String btnStyle(String bgColor) {
        return "-fx-background-color:" + bgColor + ";" +
                "-fx-background-radius:10;-fx-border-radius:10;" +
                "-fx-cursor:hand;";
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private synchronized void sendToServer(Command command) throws Exception {
        out.writeObject(command);
        out.flush();
        out.reset();
    }

    private Command sendCommandSync(Command request) throws Exception {
        synchronized (sendLock) {
            out.writeObject(request);
            out.flush();
            out.reset();
            Object raw = in.readObject();
            if (raw instanceof Command response) return response;
            throw new RuntimeException("Получен неожиданный объект от сервера");
        }
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

    public static void main(String[] args) { launch(args); }
}