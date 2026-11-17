package com.sparkz.sparkz.sessions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SessionStatus {
    PENDING("pending"), ACCEPTED("accepted"), DECLINED("declined"), CANCELED("canceled");

    private final String value;
    SessionStatus(String value) { this.value = value; }
    @JsonValue public String value() { return value; }
    @JsonCreator public static SessionStatus from(String value) {
        for (SessionStatus status : values()) if (status.value.equals(value)) return status;
        throw new IllegalArgumentException("Unknown learning session status");
    }
}
