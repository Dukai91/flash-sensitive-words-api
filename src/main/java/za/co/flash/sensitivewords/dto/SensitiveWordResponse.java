package za.co.flash.sensitivewords.dto;

import java.time.Instant;

public record SensitiveWordResponse(Long id, String word, Instant createdAt, Instant updatedAt) {}
