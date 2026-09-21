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
import java.util.regex.Pattern;

public class Registration extends Application {

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

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

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

    private TextField nameField;
    private TextField emailField;
    private PasswordField passField;
    private PasswordField confirmPassField;
    private Label nameError;
    private Label emailError;
    private Label passError;
    private Label confirmPassError;
    private Button registerBtn;
    private Text loginLink;

    public Scene createScene() {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color:#F0F4FF;");
        root.setFillWidth(true);

        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(40, 40, 36, 40));
        card.setMaxWidth(460);
        card.setMinWidth(460);
        card.setStyle(
                "-fx-background-color:" + BG_COLOR + ";" +
                        "-fx-background-radius:16;" +
                        "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.12),24,0,0,6);"
        );

        Label logo = new Label("+ MedPortal");
        logo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        logo.setTextFill(Color.web(ACCENT_BLUE));
        logo.setMaxWidth(Double.MAX_VALUE);
        logo.setAlignment(Pos.CENTER);

        Label title = new Label("Создать аккаунт");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        title.setTextFill(Color.web(TITLE_COLOR));
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);

        Label subtitle = new Label("Заполните данные для регистрации в системе");
        subtitle.setFont(Font.font("Segoe UI", 13));
        subtitle.setTextFill(Color.web(SUBTITLE_COLOR));
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setWrapText(true);
        VBox.setMargin(subtitle, new Insets(0, 0, 4, 0));

        nameField = makeTextField("Иванов Иван Иванович");
        emailField = makeTextField("example@mail.ru");
        passField = makePasswordField("Минимум 8 символов");
        confirmPassField = makePasswordField("Повторите пароль");

        nameError = errorLabel();
        emailError = errorLabel();
        passError = errorLabel();
        confirmPassError = errorLabel();

        VBox nameBlock = fieldBlock("Полное имя", nameField, nameError);
        VBox emailBlock = fieldBlock("Электронная почта", emailField, emailError);
        VBox passBlock = fieldBlock("Пароль", passField, passError);
        VBox confirmBlock = fieldBlock("Подтверждение пароля", confirmPassField, confirmPassError);

        registerBtn = new Button("Зарегистрироваться");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setPrefHeight(50);
        registerBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        registerBtn.setTextFill(Color.web(BTN_TEXT));
        registerBtn.setCursor(Cursor.HAND);
        registerBtn.setStyle(btnStyle(ACCENT_BLUE));
        registerBtn.setOnMouseEntered(e -> {
            if (!registerBtn.isDisabled()) registerBtn.setStyle(btnStyle(BTN_HOVER));
        });
        registerBtn.setOnMouseExited(e -> {
            if (!registerBtn.isDisabled()) registerBtn.setStyle(btnStyle(ACCENT_BLUE));
        });
        registerBtn.setOnAction(e -> handleRegister());

        TextFlow loginFlow = new TextFlow();
        loginFlow.setTextAlignment(TextAlignment.CENTER);
        loginFlow.setMaxWidth(Double.MAX_VALUE);
        Text alreadyTxt = new Text("Уже есть аккаунт? ");
        alreadyTxt.setFont(Font.font("Segoe UI", 13));
        alreadyTxt.setFill(Color.web(HINT_COLOR));
        loginLink = new Text("Войти");
        loginLink.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        loginLink.setFill(Color.web(LINK_COLOR));
        loginLink.setCursor(Cursor.HAND);
        loginLink.setOnMouseClicked(e -> navigateToLogin());
        loginFlow.getChildren().addAll(alreadyTxt, loginLink);

        card.getChildren().addAll(
                logo, title, subtitle,
                nameBlock, emailBlock, passBlock, confirmBlock,
                registerBtn, loginFlow
        );

        HBox wrapper = new HBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(40, 20, 40, 20));
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        root.getChildren().add(wrapper);

        return new Scene(root, 1400, 800);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("MedPortal — Регистрация");
        stage.setScene(createScene());
        stage.show();
        connectToServer();
        stage.setOnCloseRequest(e -> shutdown());
    }

    public Scene initializeAndGetScene() {
        connectToServer();
        return createScene();
    }

    private void connectToServer() {
        executor.submit(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                System.out.println("[Registration] Подключено к серверу " + SERVER_IP + ":" + SERVER_PORT);
            } catch (Exception ex) {
                System.err.println("[Registration] Ошибка подключения: " + ex.getMessage());
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.WARNING,
                                "Нет соединения",
                                "Не удалось подключиться к серверу.\nПроверьте, запущен ли сервер.")
                );
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
            } catch (Exception ignored) {
            }
        });
        executor.shutdown();
    }

    private void handleRegister() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passField.getText();
        String confirmPass = confirmPassField.getText();

        boolean valid = true;

        if (name.isEmpty()) {
            showFieldError(nameError, "Введите ваше полное имя");
            valid = false;
        } else if (name.length() < 3) {
            showFieldError(nameError, "Имя слишком короткое");
            valid = false;
        } else {
            clearFieldError(nameError, nameField);
        }

        if (email.isEmpty()) {
            showFieldError(emailError, "Введите электронную почту");
            valid = false;
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            showFieldError(emailError, "Некорректный формат email");
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

        if (confirmPass.isEmpty()) {
            showFieldError(confirmPassError, "Повторите пароль");
            valid = false;
        } else if (!password.equals(confirmPass)) {
            showFieldError(confirmPassError, "Пароли не совпадают");
            valid = false;
        } else {
            clearFieldError(confirmPassError, confirmPassField);
        }

        if (!valid) return;

        if (out == null || socket == null || socket.isClosed()) {
            showAlert(Alert.AlertType.ERROR, "Ошибка соединения",
                    "Нет подключения к серверу.\nПопробуйте перезапустить приложение.");
            return;
        }

        setLoading(true);

        String passwordHash = sha256(password);

        Command request = Command.builder(CommandType.REGISTER)
                .param("fullName", name)
                .param("email", email)
                .param("password", passwordHash)
                .param("role", "PATIENT")
                .build();

        executor.submit(() -> {
            try {
                sendToServer(request);
                System.out.println("[Registration] Отправлен запрос: " + request);

                Object raw = in.readObject();

                Platform.runLater(() -> handleServerResponse(raw));

            } catch (java.io.EOFException eof) {
                Platform.runLater(() -> {
                    setLoading(false);
                    showAlert(Alert.AlertType.ERROR, "Ошибка соединения",
                            "Сервер разорвал соединение.");
                });
            } catch (Exception ex) {
                System.err.println("[Registration] Сетевая ошибка: " + ex.getMessage());
                Platform.runLater(() -> {
                    setLoading(false);
                    showAlert(Alert.AlertType.ERROR, "Сетевая ошибка",
                            "Не удалось получить ответ от сервера.\n" + ex.getMessage());
                });
            }
        });
    }

    private void handleServerResponse(Object raw) {
        setLoading(false);

        if (!(raw instanceof Command response)) {
            showAlert(Alert.AlertType.ERROR, "Ошибка протокола",
                    "Получен неожиданный тип ответа от сервера.");
            return;
        }

        System.out.println("[Registration] Получен ответ: " + response);

        switch (response.getStatus()) {

            case OK -> {
                String fullName = nameField.getText().trim();   // имя из формы
                String token = response.getParam("token");
                long userId = Long.parseLong(response.getParam("userId", "0"));
                showAlert(Alert.AlertType.INFORMATION, "Регистрация завершена",
                        "Аккаунт успешно создан!\nДобро пожаловать в MedPortal.");
                navigateToMain(fullName, token, userId);
            }

            case CLIENT_ERROR -> {
                // Ошибка по вине клиента (например, email уже занят)
                String msg = response.getMessage();
                if (msg != null && msg.toLowerCase().contains("email")) {
                    showFieldError(emailError, "Этот email уже зарегистрирован");
                    highlightField(emailField, true);
                } else {
                    showAlert(Alert.AlertType.WARNING, "Ошибка регистрации",
                            msg != null ? msg : "Проверьте введённые данные.");
                }
            }

            case SERVER_ERROR -> {
                String msg = response.getMessage();
                showAlert(Alert.AlertType.ERROR, "Ошибка сервера",
                        "Сервер не смог обработать запрос.\n" +
                                (msg != null ? msg : "Повторите попытку позже."));
            }

            default -> {
                showAlert(Alert.AlertType.ERROR, "Неизвестный ответ",
                        "Статус ответа сервера: " + response.getStatus());
            }
        }
    }


    private synchronized void sendToServer(Command command) throws Exception {
        out.writeObject(command);
        out.flush();
        out.reset();
    }

    private void navigateToMain(String fullName, String token, long userId) {
        Stage stage = (Stage) registerBtn.getScene().getWindow();
        Main main = new Main();
        main.setUserSession(fullName, token, userId);
        main.setNetworkStreams(out, in, socket);
        Scene scene = main.createScene();
        stage.setScene(scene);
        stage.setTitle("MedPortal — Главная");
    }

    private void navigateToLogin() {
        Stage stage = (Stage) loginLink.getScene().getWindow();
        Login login = new Login();
        Scene loginScene = login.initializeAndGetScene();
        stage.setScene(loginScene);
        stage.setTitle("MedPortal — Вход");
    }


    private void setLoading(boolean loading) {
        registerBtn.setDisable(loading);
        registerBtn.setText(loading ? "Регистрация..." : "Зарегистрироваться");
        registerBtn.setStyle(loading ? btnStyle(BTN_DISABLED) : btnStyle(ACCENT_BLUE));
        nameField.setDisable(loading);
        emailField.setDisable(loading);
        passField.setDisable(loading);
        confirmPassField.setDisable(loading);
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
        String base =
                "-fx-background-color:" + FIELD_BG + ";" +
                        "-fx-border-radius:8;-fx-background-radius:8;" +
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
                f.setStyle(base + "-fx-border-color:" + (now ? FIELD_FOCUS : FIELD_BORDER) + ";")
        );
        return f;
    }

    private PasswordField makePasswordField(String placeholder) {
        PasswordField f = new PasswordField();
        f.setPromptText(placeholder);
        f.setMaxWidth(Double.MAX_VALUE);
        String base = baseFieldStyle();
        f.setStyle(base);
        f.focusedProperty().addListener((obs, was, now) ->
                f.setStyle(base + "-fx-border-color:" + (now ? FIELD_FOCUS : FIELD_BORDER) + ";")
        );
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
                "-fx-cursor:hand;" +
                "-fx-effect:dropshadow(gaussian,rgba(59,130,246,0.35),8,0,0,3);";
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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

    public static void main(String[] args) {
        launch(args);
    }
}