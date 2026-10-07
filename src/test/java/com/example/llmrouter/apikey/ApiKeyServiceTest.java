package com.example.llmrouter.apikey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository repository;

    @Test
    void createApiKey_generatesARandomRawKeyAndPersistsOnlyItsHash() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ApiKeyService service = new ApiKeyService(repository);

        CreatedApiKey created = service.createApiKey("acme", 60, 1000);

        assertThat(created.rawKey()).startsWith("llmr_");
        ApiKey persisted = created.apiKey();
        assertThat(persisted.keyHash()).isNotEqualTo(created.rawKey());
        assertThat(persisted.keyHash()).hasSize(64); // SHA-256 hex digest length
        assertThat(persisted.clientName()).isEqualTo("acme");
        assertThat(persisted.rateLimitPerMinute()).isEqualTo(60);
        assertThat(persisted.dailyQuota()).isEqualTo(1000);
        assertThat(persisted.active()).isTrue();
    }

    @Test
    void createApiKey_generatesADifferentRawKeyEveryTime() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ApiKeyService service = new ApiKeyService(repository);

        String firstKey = service.createApiKey("acme", 60, 1000).rawKey();
        String secondKey = service.createApiKey("acme", 60, 1000).rawKey();

        assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    void authenticate_returnsEmpty_forNullOrBlankKey_withoutQueryingTheRepository() {
        ApiKeyService service = new ApiKeyService(repository);

        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
        assertThat(service.authenticate("   ")).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void authenticate_looksUpByTheHashOfTheRawKey_neverTheRawKeyItself() {
        String rawKey = "llmr_abc123";
        ApiKey stored = new ApiKey("acme", ApiKeyService.sha256Hex(rawKey), 60, 1000);
        when(repository.findByKeyHashAndActiveTrue(ApiKeyService.sha256Hex(rawKey)))
                .thenReturn(Optional.of(stored));
        ApiKeyService service = new ApiKeyService(repository);

        Optional<ApiKey> result = service.authenticate(rawKey);

        assertThat(result).contains(stored);
    }

    @Test
    void authenticate_returnsEmpty_whenRepositoryHasNoMatchingActiveKey() {
        when(repository.findByKeyHashAndActiveTrue(any())).thenReturn(Optional.empty());
        ApiKeyService service = new ApiKeyService(repository);

        assertThat(service.authenticate("llmr_unknown")).isEmpty();
    }
}
