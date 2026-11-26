package com.abgl.dms.vaultmaintenance;

import com.abgl.dms.repository.VaultMaintenanceRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class VaultMaintenanceServiceTest {

    @Test
    void rejectsUnsupportedSearchStatusBeforeQueryingRepository() {
        VaultMaintenanceRepository repository = mock(VaultMaintenanceRepository.class);
        VaultMaintenanceService service = new VaultMaintenanceService(repository);
        VaultMaintenanceSearch search = new VaultMaintenanceSearch(
                null, null, null, null, null, null, null, "X", null, null, 0, 25);

        assertThrows(IllegalArgumentException.class, () -> service.search(search));
        verifyNoInteractions(repository);
    }
}
