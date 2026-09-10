package za.co.flash.sensitivewords.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWordEntity, Long> {
    boolean existsByNormalizedWord(String normalizedWord);
    boolean existsByNormalizedWordAndIdNot(String normalizedWord, Long id);

    @Query("select w.word from SensitiveWordEntity w")
    List<String> findAllWords();
}
