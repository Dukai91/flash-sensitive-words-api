package za.co.flash.sensitivewords.dto;

import java.time.Instant;
import za.co.flash.sensitivewords.persistence.SensitiveWordEntity;

public record SensitiveWordResponse(Long id, String word, Instant createdAt, Instant updatedAt) {
    public static SensitiveWordResponse from(SensitiveWordEntity entity) {
        return new SensitiveWordResponse(entity.getId(), entity.getWord(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
