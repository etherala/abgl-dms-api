package com.abgl.dms.controller;

import com.abgl.dms.navigation.NavigationItem;
import com.abgl.dms.navigation.NavigationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/navigation")
public class NavigationController {

    private final NavigationService navigationService;

    public NavigationController(NavigationService navigationService) {
        this.navigationService = navigationService;
    }

    @GetMapping
    public Map<String, List<NavigationItem>> getNavigation(Authentication authentication) {
        return navigationService.getNavigationForUser(authentication.getName());
    }
}
