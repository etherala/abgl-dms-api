package com.abgl.dms.vaultmaintenance;

import com.abgl.dms.repository.VaultMaintenanceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void normalizesOptionalHierarchyCodesBeforeQueryingRepository() {
        VaultMaintenanceRepository repository = mock(VaultMaintenanceRepository.class);
        VaultMaintenanceService service = new VaultMaintenanceService(repository);
        VaultHierarchy expected = new VaultHierarchy(
                List.of(new VaultHierarchyOption("LOC-01", "Central")),
                List.of(), List.of(), List.of(), List.of());
        when(repository.findHierarchy("LOC-01", null, null, null)).thenReturn(expected);

        assertEquals(expected, service.getHierarchy(" LOC-01 ", " ", null, null));
        verify(repository).findHierarchy("LOC-01", null, null, null);
    }

    @Test
    void rejectsMissingOrUnsupportedHierarchyChildQueries() {
        VaultMaintenanceRepository repository = mock(VaultMaintenanceRepository.class);
        VaultMaintenanceService service = new VaultMaintenanceService(repository);

        assertThrows(IllegalArgumentException.class,
                () -> service.getHierarchyChildren("VAULT_LOCATION", " ", "VAULT_BRANCH"));
        assertThrows(IllegalArgumentException.class,
                () -> service.getHierarchyChildren("VAULT_LOCATION", "LOC-01", "VAULT_ROOM"));
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsEstablishedMixedCaseShelfTypeForRackChildren() {
        VaultMaintenanceRepository repository = mock(VaultMaintenanceRepository.class);
        VaultMaintenanceService service = new VaultMaintenanceService(repository);
        VaultHierarchyOption shelf = new VaultHierarchyOption("SHELF-01", "Shelf A");
        when(repository.findHierarchyChildren("VAULT_RACK", "RACK-01", "VAULT_SHELF"))
                .thenReturn(List.of(shelf));

        assertEquals(List.of(shelf), service.getHierarchyChildren("VAULT_RACK", " RACK-01 ", "VAULT_SHELF"));
        verify(repository).findHierarchyChildren("VAULT_RACK", "RACK-01", "VAULT_SHELF");
    }
}
