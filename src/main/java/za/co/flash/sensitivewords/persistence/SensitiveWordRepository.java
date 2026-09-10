package za.co.flash.sensitivewords.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWordEntity, Long> {
    @Query(value = "SELECT revision FROM dbo.vocabulary_configuration WITH (HOLDLOCK) WHERE id=1", nativeQuery = true)
    long configurationRevision();

    @Modifying
    @Query(value = "UPDATE dbo.vocabulary_configuration SET revision=revision+1 WHERE id=1", nativeQuery = true)
    void advanceConfigurationRevision();

    boolean existsByNormalizedWord(String normalizedWord);
    boolean existsByNormalizedWordAndIdNot(String normalizedWord, Long id);

    @Query("select w.word from SensitiveWordEntity w")
    List<String> findAllWords();
}
