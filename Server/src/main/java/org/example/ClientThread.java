package org.example;

import common.Command;
import common.CommandType;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class ClientThread implements Runnable {
    private static final Logger LOG = Logger.getLogger(ClientThread.class.getName());
    private final Socket socket;
    private final CommandHandler handler;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private long userId = 0;
    private models.Role role = null;
    private String token = null;

    public ClientThread(Socket socket) {
        this.socket = socket;
        this.handler = new CommandHandler();
    }

    @Override
    public void run() {
        String ip = socket.getInetAddress().getHostAddress();
        LOG.info("Клиент подключён: " + ip);
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            while (!socket.isClosed()) {
                Object raw;
                try {
                    raw = in.readObject();
                } catch (EOFException | java.net.SocketException e) {
                    break;
                }
                if (!(raw instanceof Command cmd)) continue;

                LOG.info("<- " + cmd.getType() + " от " + ip);

                if (cmd.requiresAuth() && !authenticate(cmd)) {
                    send(Command.unauthorized(cmd));
                    continue;
                }

                Command response = handler.handle(cmd, userId, role, ip);

                if ((cmd.getType() == CommandType.LOGIN || cmd.getType() == CommandType.REGISTER)
                        && response.isOk()) {
                    token = response.getParam("token");
                    String roleStr = response.getParam("role");
                    if (roleStr != null) {
                        try { role = models.Role.valueOf(roleStr); } catch (Exception ignored) {}
                    }
                    String uidStr = response.getParam("userId");
                    if (uidStr != null) {
                        try { userId = Long.parseLong(uidStr); } catch (Exception ignored) {}
                    }
                }

                if (cmd.getType() == CommandType.LOGOUT) {
                    userId = 0;
                    role = null;
                    token = null;
                }

                LOG.info("-> " + response.getStatus() + " для " + cmd.getType());
                send(response);
            }
        } catch (Exception e) {
            LOG.warning("Ошибка в потоке клиента " + ip + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            LOG.info("Клиент отключён: " + ip);
        }
    }

    private boolean authenticate(Command cmd) {
        if (userId > 0 && role != null) return true;
        String cmdToken = cmd.getSessionToken();
        if (cmdToken == null || cmdToken.isEmpty()) return false;
        DAO.UserDAO userDAO = new DAO.UserDAO();
        models.Session session = userDAO.findSession(cmdToken);
        if (session == null || session.isExpired()) return false;
        userId = session.getUserId();
        token = cmdToken;
        models.User user = userDAO.findById(userId);
        if (user == null || user.isBlocked()) {
            userId = 0;
            return false;
        }
        role = user.getRole();
        return true;
    }

    private void send(Command response) {
        try {
            out.writeObject(response);
            out.flush();
            out.reset(); // сброс кэша
        } catch (IOException e) {
            LOG.warning("Ошибка отправки: " + e.getMessage());
        }
    }
}