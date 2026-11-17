package com.sparkz.sparkz.sessions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SessionMode {
    ONLINE("online"), IN_PERSON("in-person");

    private final String value;
    SessionMode(String value) { this.value = value; }
    @JsonValue public String value() { return value; }
    @JsonCreator public static SessionMode from(String value) {
        for (SessionMode mode : values()) if (mode.value.equals(value)) return mode;
        throw new IllegalArgumentException("Mode must be online or in-person");
    }
}
