package com.abgl.dms.userlookup;

import com.abgl.dms.repository.UserLookupRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserLookupService {

    private final UserLookupRepository repository;

    public UserLookupService(UserLookupRepository repository) {
        this.repository = repository;
    }

    public List<UserLookupItem> searchUsers(String searchText) {
        return repository.searchActiveUsers(searchText.trim());
    }
}
