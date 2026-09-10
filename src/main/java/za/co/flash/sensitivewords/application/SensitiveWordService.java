package za.co.flash.sensitivewords.application;

import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.flash.sensitivewords.config.VocabularyProperties;
import za.co.flash.sensitivewords.domain.TermNormalizer;
import za.co.flash.sensitivewords.dto.SensitiveWordResponse;
import za.co.flash.sensitivewords.exception.*;
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
    private final int maxTerms;

    @Autowired
    public SensitiveWordService(SensitiveWordRepository repository, MatcherCache cache,
                                PlatformTransactionManager transactionManager, VocabularyProperties properties) {
        this.repository = repository;
        this.cache = cache;
        this.maxTerms = properties.maxTerms();
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(10);
    }

    public SensitiveWordService(SensitiveWordRepository repository, MatcherCache cache,
                                PlatformTransactionManager transactionManager) {
        this(repository, cache, transactionManager, new VocabularyProperties(300, 10000));
    }

    @Override
    public synchronized void run(ApplicationArguments args) {
        cache.invalidate();
        refresh();
    }

    @Scheduled(fixedDelayString = "${vocabulary.refresh-delay-ms:30000}",
            initialDelayString = "${vocabulary.refresh-delay-ms:30000}")
    public synchronized void reconcile() {
        try {
            refresh();
        } catch (RuntimeException exception) {
            // Retain a previously trusted snapshot only until its freshness deadline.
            log.warn("Vocabulary reconciliation failed; revision={} ready={} failure={}",
                    cache.revision(), cache.isReady(), exception.getClass().getSimpleName());
        }
    }

    private void refresh() {
        requireOwnTransaction();
        Loaded loaded = Objects.requireNonNull(transactions.execute(status -> {
            // HOLDLOCK retains the version read lock until terms have been read consistently.
            long revision = repository.configurationRevision();
            return new Loaded(revision, revision == cache.revision() ? null : buildMatcher());
        }));
        if (loaded.matcher() == null) {
            cache.confirm(loaded.revision());
        } else {
            cache.publish(loaded.matcher(), loaded.revision());
            log.info("Vocabulary loaded; revision={}", loaded.revision());
        }
    }

    public synchronized SensitiveWordResponse create(String word) {
        return mutate("create", null, () -> {
            if (repository.existsByNormalizedWord(TermNormalizer.normalize(word))) {
                throw new DuplicateWordException();
            }
            if (repository.count() >= maxTerms) {
                throw new InvalidInputException("Vocabulary capacity of " + maxTerms + " terms has been reached");
            }
            return response(repository.saveAndFlush(new SensitiveWordEntity(word)));
        });
    }

    @Transactional(readOnly = true, timeout = 10)
    public SensitiveWordResponse get(long id) {
        return response(find(id));
    }

    @Transactional(readOnly = true, timeout = 10)
    public Page<SensitiveWordResponse> list(int page, int size) {
        validatePage(page, size);
        return repository.findAll(PageRequest.of(page, size, Sort.by("id"))).map(SensitiveWordService::response);
    }

    public static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidInputException("page must be non-negative, size must be 1 to 100, and page * size must not exceed 2147483647");
        }
    }

    public synchronized SensitiveWordResponse update(long id, String word) {
        return mutate("update", id, () -> {
            SensitiveWordEntity entity = find(id);
            if (repository.existsByNormalizedWordAndIdNot(TermNormalizer.normalize(word), id)) {
                throw new DuplicateWordException();
            }
            entity.rename(word);
            repository.flush();
            return response(entity);
        });
    }

    public synchronized void delete(long id) {
        mutate("delete", id, () -> {
            repository.delete(find(id));
            repository.flush();
            return null;
        });
    }

    private SensitiveWordEntity find(long id) {
        return repository.findById(id).orElseThrow(() -> new WordNotFoundException(id));
    }

    private SensitiveWordMatcher buildMatcher() {
        var words = repository.findAllWords();
        if (words.size() > maxTerms) {
            throw new IllegalStateException("Stored vocabulary exceeds configured capacity");
        }
        return SensitiveWordMatcher.compile(words);
    }

    private <T> T mutate(String operation, Long id, Supplier<T> change) {
        requireOwnTransaction();
        long started = System.nanoTime();
        try {
            Mutation<T> committed = Objects.requireNonNull(transactions.execute(status -> {
                // Take the single version-row write lock BEFORE touching vocabulary rows.
                // This orders writers across replicas and keeps version/terms atomic.
                repository.advanceConfigurationRevision();
                T result = change.get();
                return new Mutation<>(result, buildMatcher(), repository.configurationRevision());
            }));
            cache.publish(committed.matcher(), committed.revision());
            Object affectedId = committed.result() instanceof SensitiveWordResponse value ? value.id() : id;
            log.info("Vocabulary operation={} id={} revision={} durationMs={}", operation, affectedId,
                    committed.revision(), (System.nanoTime() - started) / 1_000_000);
            return committed.result();
        } catch (TransactionException | DataAccessException exception) {
            // The server may have committed even if its acknowledgement was lost.
            // Never assert that the old snapshot is current after an uncertain outcome.
            cache.invalidate();
            throw exception;
        }
    }

    private static void requireOwnTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Vocabulary mutations and refresh must be called outside an ambient transaction");
        }
    }

    private static SensitiveWordResponse response(SensitiveWordEntity entity) {
        return new SensitiveWordResponse(entity.getId(), entity.getWord(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private record Loaded(long revision, SensitiveWordMatcher matcher) {}
    private record Mutation<T>(T result, SensitiveWordMatcher matcher, long revision) {}
}
