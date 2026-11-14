package com.sparkz.sparkz.sessions;

import java.time.Instant;
import java.util.UUID;

public record LearningSession(long id, long matchRequestId, UUID proposedBy,
        UUID teacherId, UUID learnerId, Instant scheduledStart, Instant scheduledEnd,
        SessionMode mode, String location, String meetingUrl, SessionStatus status,
        Instant createdAt, String message) {}
