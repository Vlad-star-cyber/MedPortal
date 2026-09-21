package org.example.common;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Command implements Serializable {
    private final String commandId;
    private final String correlationId;
    private final CommandType type;
    private final CommandStatus status;
    private final String sessionToken;
    private final Map<String, String> params;
    private final String message;
    private final long timestamp;

    private Command(Builder builder) {
        this.commandId = builder.commandId;
        this.correlationId = builder.correlationId;
        this.type = builder.type;
        this.status = builder.status;
        this.sessionToken = builder.sessionToken;
        this.params = Map.copyOf(builder.params);
        this.message = builder.message;
        this.timestamp = builder.timestamp;
    }

    public static Builder builder(CommandType type) {
        return new Builder(type);
    }

    public static Builder response(Command original, CommandStatus status) {
        return new Builder(original.type)
                .correlationId(original.commandId)
                .status(status);
    }

    public static Command error(Command original, String message) {
        return response(original, CommandStatus.SERVER_ERROR)
                .message(message)
                .build();
    }

    public static Command unauthorized(Command original) {
        return response(original, CommandStatus.CLIENT_ERROR)
                .message("Требуется авторизация. Токен недействителен или истёк.")
                .build();
    }

    public String getRequestId() { return commandId; }
    public String getCorrelationId() { return correlationId; }
    public CommandType getType() { return type; }
    public CommandStatus getStatus() { return status; }
    public String getSessionToken() { return sessionToken; }
    public Map<String, String> getParams(){ return params; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }

    public String getParam(String key) {
        return params.get(key);
    }

    public String getParam(String key, String defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    public int getIntParam(String key) {
        try {
            String val = params.get(key);
            return val != null ? Integer.parseInt(val) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    public boolean isOk() { return status == CommandStatus.OK; }
    public boolean isPending() { return status == CommandStatus.PENDING; }
    public boolean isError() { return status == CommandStatus.SERVER_ERROR
            || status == CommandStatus.CLIENT_ERROR; }
    public boolean isServerError() { return status == CommandStatus.SERVER_ERROR; }
    public boolean isClientError() { return status == CommandStatus.CLIENT_ERROR; }

    public boolean isResponse() { return correlationId != null; }

    public boolean requiresAuth() {
        return type != CommandType.LOGIN
                && type != CommandType.REGISTER
                && type != CommandType.PING;
    }

    @Override
    public String toString() {
        return "Command{" +
                "requestId='" + commandId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", params=" + params +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static final class Builder {

        private final String commandId;
        private String correlationId;
        private final CommandType type;
        private CommandStatus status = CommandStatus.PENDING;
        private String sessionToken;
        private final Map<String, String> params = new HashMap<>();
        private String message;
        private long timestamp = Instant.now().toEpochMilli();

        private Builder(CommandType type) {
            this.commandId = UUID.randomUUID().toString();
            this.type = type;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder status(CommandStatus status) {
            this.status = status;
            return this;
        }

        public Builder token(String token) {
            this.sessionToken = token;
            return this;
        }

        public Builder param(String key, String value) {
            if (key != null && value != null) params.put(key, value);
            return this;
        }

        public Builder param(String key, int value) {
            return param(key, String.valueOf(value));
        }

        public Builder param(String key, boolean value) {
            return param(key, String.valueOf(value));
        }

        public Builder param(String key, long value) {
            return param(key, String.valueOf(value));
        }

        public Builder params(Map<String, String> map) {
            if (map != null) this.params.putAll(map);
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(long epochMillis) {
            this.timestamp = epochMillis;
            return this;
        }

        public Command build() {
            if (type == null) throw new IllegalStateException("CommandType обязателен");
            return new Command(this);
        }
    }
}