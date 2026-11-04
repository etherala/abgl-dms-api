package com.abgl.dms.controller;

import com.abgl.dms.userlookup.UserLookupItem;
import com.abgl.dms.userlookup.UserLookupService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserLookupController {

    private final UserLookupService userLookupService;

    public UserLookupController(UserLookupService userLookupService) {
        this.userLookupService = userLookupService;
    }

    @GetMapping
    public List<UserLookupItem> searchUsers(
            @RequestParam("search") @NotBlank @Size(max = 100) String searchText) {
        return userLookupService.searchUsers(searchText);
    }
}
