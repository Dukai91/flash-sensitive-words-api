package za.co.flash.sensitivewords.application;

import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.flash.sensitivewords.dto.SensitiveWordResponse;
import za.co.flash.sensitivewords.exception.DuplicateWordException;
import za.co.flash.sensitivewords.exception.WordNotFoundException;
import za.co.flash.sensitivewords.matcher.MatcherCache;
import za.co.flash.sensitivewords.matcher.SensitiveWordMatcher;
import za.co.flash.sensitivewords.persistence.SensitiveWordEntity;
import za.co.flash.sensitivewords.persistence.SensitiveWordRepository;

@Service
public class SensitiveWordService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SensitiveWordService.class);
    private final SensitiveWordRepository repository;
    private final MatcherCache cache;
    private final TransactionTemplate transactions;

    public SensitiveWordService(SensitiveWordRepository repository, MatcherCache cache,
                                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.cache = cache;
        this.transactions = new TransactionTemplate(transactionManager);
        // Own the commit boundary even if a future caller has an ambient transaction.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public synchronized void run(ApplicationArguments args) {
        cache.publish(Objects.requireNonNull(transactions.execute(status -> buildMatcher())));
        log.info("Sensitive-word matcher initialized");
    }

    public synchronized SensitiveWordResponse create(String word) {
        return mutate("create", () -> {
            if (repository.existsByNormalizedWord(TermNormalizer.normalize(word))) {
                throw new DuplicateWordException();
            }
            return SensitiveWordResponse.from(repository.saveAndFlush(new SensitiveWordEntity(word)));
        });
    }

    @Transactional(readOnly = true)
    public SensitiveWordResponse get(long id) {
        return SensitiveWordResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<SensitiveWordResponse> list(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, Sort.by("id"))).map(SensitiveWordResponse::from);
    }

    public synchronized SensitiveWordResponse update(long id, String word) {
        return mutate("update", () -> {
            SensitiveWordEntity entity = find(id);
            if (repository.existsByNormalizedWordAndIdNot(TermNormalizer.normalize(word), id)) {
                throw new DuplicateWordException();
            }
            entity.rename(word);
            repository.flush();
            return SensitiveWordResponse.from(entity);
        });
    }

    public synchronized void delete(long id) {
        mutate("delete", () -> {
            repository.delete(find(id));
            repository.flush();
            return null;
        });
    }

    private SensitiveWordEntity find(long id) {
        return repository.findById(id).orElseThrow(() -> new WordNotFoundException(id));
    }

    private SensitiveWordMatcher buildMatcher() {
        return SensitiveWordMatcher.compile(repository.findAllWords());
    }

    private <T> T mutate(String operation, Supplier<T> change) {
        // Compile inside the transaction: failed compilation rolls back. Publish only after commit.
        // The caller's monitor covers commit AND publication, preventing out-of-order snapshots.
        Mutation<T> committed = Objects.requireNonNull(transactions.execute(status ->
                new Mutation<>(change.get(), buildMatcher())));
        cache.publish(committed.matcher());
        log.info("Sensitive-word operation={} committed; matcher refreshed", operation);
        return committed.result();
    }

    private record Mutation<T>(T result, SensitiveWordMatcher matcher) {}
}
