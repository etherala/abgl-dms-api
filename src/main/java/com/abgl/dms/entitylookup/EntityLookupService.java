package com.abgl.dms.entitylookup;

import com.abgl.dms.repository.EntityLookupRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EntityLookupService {

    private final EntityLookupRepository entityLookupRepository;

    public EntityLookupService(EntityLookupRepository entityLookupRepository) {
        this.entityLookupRepository = entityLookupRepository;
    }

    public List<EntityLookupItem> searchVisibleEntities(String username, String prefix) {
        return entityLookupRepository.findVisibleEntities(username, prefix.trim());
    }
}
