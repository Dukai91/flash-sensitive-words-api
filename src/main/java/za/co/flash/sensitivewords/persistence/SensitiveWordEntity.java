package za.co.flash.sensitivewords.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;
import za.co.flash.sensitivewords.application.TermNormalizer;

@Entity
@Table(name = "sensitive_words", uniqueConstraints = @UniqueConstraint(
        name = "uq_sensitive_words_normalized", columnNames = "normalized_word"))
public class SensitiveWordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String word;

    @Nationalized
    @Column(name = "normalized_word", nullable = false, length = 200)
    private String normalizedWord;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SensitiveWordEntity() {}

    public SensitiveWordEntity(String word) {
        rename(word);
    }

    public void rename(String word) {
        this.word = TermNormalizer.clean(word);
        this.normalizedWord = TermNormalizer.normalize(word);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getWord() { return word; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
