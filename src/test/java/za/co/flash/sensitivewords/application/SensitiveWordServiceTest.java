package za.co.flash.sensitivewords.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import za.co.flash.sensitivewords.exception.*;
import za.co.flash.sensitivewords.matcher.MatcherCache;
import za.co.flash.sensitivewords.matcher.SensitiveWordMatcher;
import za.co.flash.sensitivewords.persistence.*;

@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceTest {
    @Mock private SensitiveWordRepository repository;
    @Mock private PlatformTransactionManager transactionManager;
    private MatcherCache cache;
    private SensitiveWordService service;

    @BeforeEach
    void setUp() {
        cache = new MatcherCache();
        cache.publish(SensitiveWordMatcher.compile(List.of("CREATE")));
        service = new SensitiveWordService(repository, cache, transactionManager);
    }

    private void transaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void invalidOffsetsNeverReachPersistence() {
        assertThatThrownBy(() -> service.list(Integer.MAX_VALUE, 100)).isInstanceOf(InvalidInputException.class);
        verifyNoInteractions(repository);
        assertThatCode(() -> SensitiveWordService.validatePage(Integer.MAX_VALUE, 1)).doesNotThrowAnyException();
    }

    @Test
    void refusesAmbientTransactionsBeforeTouchingDatabase() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.create("DROP")).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(repository, transactionManager);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void failedStartupLeavesMatcherUnavailable() {
        transaction();
        when(repository.findAllWords()).thenThrow(new IllegalStateException("unreadable configuration"));
        assertThatThrownBy(() -> service.run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(cache.isReady()).isFalse();
    }

    @Test
    void pollingOnlyLoadsTermsWhenDatabaseRevisionChanges() {
        transaction();
        service.reconcile();
        verify(repository, never()).findAllWords();
        when(repository.configurationRevision()).thenReturn(1L);
        when(repository.findAllWords()).thenReturn(List.of("DROP"));
        service.reconcile();
        assertThat(cache.sanitize("CREATE DROP")).isEqualTo("CREATE ****");
        assertThat(cache.revision()).isEqualTo(1);
    }

    @Test
    void initializesFromDatabase() {
        transaction();
        when(repository.findAllWords()).thenReturn(List.of("DROP"));
        service.run(null);
        assertThat(cache.sanitize("CREATE DROP")).isEqualTo("CREATE ****");
    }

    @Test
    void createsAndPublishesOnlyAfterCommit() {
        transaction();
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.findAllWords()).thenReturn(List.of("CREATE", "DROP"));
        doAnswer(call -> {
            assertThat(cache.sanitize("DROP")).isEqualTo("DROP");
            return null;
        }).when(transactionManager).commit(any());
        assertThat(service.create(" DROP ").word()).isEqualTo("DROP");
        assertThat(cache.sanitize("CREATE DROP")).isEqualTo("****** ****");
        verify(repository).existsByNormalizedWord("drop");
    }

    @Test
    void getsAndListsDtoPagesInStableOrder() {
        var entity = new SensitiveWordEntity("CREATE");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        var request = PageRequest.of(0, 20, Sort.by("id"));
        when(repository.findAll(request)).thenReturn(new PageImpl<>(List.of(entity), request, 1));
        assertThat(service.get(1).word()).isEqualTo("CREATE");
        assertThat(service.list(0, 20).getContent()).extracting("word").containsExactly("CREATE");
    }

    @Test
    void updatesAndRefreshes() {
        transaction();
        when(repository.findById(1L)).thenReturn(Optional.of(new SensitiveWordEntity("CREATE")));
        when(repository.findAllWords()).thenReturn(List.of("DROP"));
        assertThat(service.update(1, "DROP").word()).isEqualTo("DROP");
        assertThat(cache.sanitize("CREATE DROP")).isEqualTo("CREATE ****");
        verify(repository).existsByNormalizedWordAndIdNot("drop", 1L);
    }

    @Test
    void deletesAndRefreshesToAnEmptyVocabulary() {
        transaction();
        var entity = new SensitiveWordEntity("CREATE");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.findAllWords()).thenReturn(List.of());
        service.delete(1);
        verify(repository).delete(entity);
        assertThat(cache.sanitize("CREATE")).isEqualTo("CREATE");
    }

    @Test
    void missingGetIsNotFound() {
        assertThatThrownBy(() -> service.get(99)).isInstanceOf(WordNotFoundException.class);
    }

    @Test
    void missingUpdateAndDeleteRollBack() {
        transaction();
        assertThatThrownBy(() -> service.update(99, "DROP")).isInstanceOf(WordNotFoundException.class);
        assertThatThrownBy(() -> service.delete(99)).isInstanceOf(WordNotFoundException.class);
        verify(transactionManager, times(2)).rollback(any());
        verify(repository, never()).findAllWords();
    }

    @Test
    void duplicateCreateDoesNotPublish() {
        transaction();
        when(repository.existsByNormalizedWord("create")).thenReturn(true);
        assertThatThrownBy(() -> service.create(" create ")).isInstanceOf(DuplicateWordException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).findAllWords();
        verify(transactionManager).rollback(any());
    }

    @Test
    void duplicateUpdateDoesNotModifyEntity() {
        transaction();
        var entity = new SensitiveWordEntity("CREATE");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.existsByNormalizedWordAndIdNot("drop", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(1, "DROP")).isInstanceOf(DuplicateWordException.class);
        assertThat(entity.getWord()).isEqualTo("CREATE");
        verify(repository, never()).flush();
    }

    @Test
    void invalidCreateRollsBack() {
        transaction();
        assertThatThrownBy(() -> service.create(" ")).isInstanceOf(InvalidInputException.class);
        verify(transactionManager).rollback(any());
    }

    @Test
    void failedCommitInvalidatesUntilReconciliationSucceeds() {
        transaction();
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.findAllWords()).thenReturn(List.of("DROP"));
        doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());
        assertThatThrownBy(() -> service.create("DROP")).isInstanceOf(TransactionSystemException.class);
        assertThatThrownBy(() -> cache.sanitize("CREATE DROP")).isInstanceOf(MatcherUnavailableException.class);
        doNothing().when(transactionManager).commit(any());
        service.reconcile();
        assertThat(cache.sanitize("CREATE DROP")).isEqualTo("CREATE ****");
    }

    @Test
    void failedMatcherBuildRollsBackAndKeepsPreviousSnapshot() {
        transaction();
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.findAllWords()).thenReturn(List.of(" "));
        assertThatThrownBy(() -> service.create("DROP")).isInstanceOf(InvalidInputException.class);
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        assertThat(cache.sanitize("CREATE DROP")).isEqualTo("****** DROP");
    }
}
