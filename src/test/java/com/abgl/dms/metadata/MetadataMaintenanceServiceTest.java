package com.abgl.dms.metadata;

import com.abgl.dms.cache.AppCacheInitializer;
import com.abgl.dms.repository.MetadataMaintenanceRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MetadataMaintenanceServiceTest {

    @Test
    void rejectsParentTypeAndCodeMismatchBeforeRepositoryCall() {
        MetadataMaintenanceRepository repository = mock(MetadataMaintenanceRepository.class);
        MetadataMaintenanceService service = new MetadataMaintenanceService(
                repository, mock(AppCacheInitializer.class));
        MetadataMaintenanceInput input = new MetadataMaintenanceInput(
                "VAULT_BRANCH", "BRANCH-01", "Branch 01",
                "REGION", null, true);

        assertThrows(IllegalArgumentException.class,
                () -> service.createDraft(input, "admin", "127.0.0.1"));
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsNullParentPair() {
        MetadataMaintenanceRepository repository = mock(MetadataMaintenanceRepository.class);
        MetadataMaintenanceService service = new MetadataMaintenanceService(
                repository, mock(AppCacheInitializer.class));
        MetadataMaintenanceInput input = new MetadataMaintenanceInput(
                "VAULT_BRANCH", "BRANCH-01", "Branch 01",
                null, null, true);

        service.createDraft(input, "admin", "127.0.0.1");
    }
}
