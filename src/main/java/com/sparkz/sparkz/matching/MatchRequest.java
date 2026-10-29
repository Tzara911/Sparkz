package com.sparkz.sparkz.matching;

import java.time.Instant;
import java.util.UUID;

public record MatchRequest(long id, UUID senderId, UUID receiverId, long skillId,
                           MatchStatus status, Instant createdAt, Instant statusUpdatedAt) {}
