package com.abgl.dms.navigation;

import com.abgl.dms.repository.NavigationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NavigationService {

    private final NavigationRepository navigationRepository;

    public NavigationService(NavigationRepository navigationRepository) {
        this.navigationRepository = navigationRepository;
    }

    public Map<String, List<NavigationItem>> getNavigationForUser(String username) {
        return Map.of("items", navigationRepository.findNavigationForUser(username));
    }
}
