package com.sparkz.sparkz.matching;

public enum MatchStatus {
    Pending("pending"), Accepted("accepted"), Declined("declined"), Canceled("canceled");

    private final String databaseValue;

    MatchStatus(String databaseValue) { this.databaseValue = databaseValue; }

    public String databaseValue() { return databaseValue; }

    public static MatchStatus fromDatabaseValue(String value) {
        for (MatchStatus status : values()) {
            if (status.databaseValue.equals(value)) return status;
        }
        throw new IllegalArgumentException("Unknown match request status: " + value);
    }
}
