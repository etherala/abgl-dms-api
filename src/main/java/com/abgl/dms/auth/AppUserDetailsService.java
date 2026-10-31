package com.abgl.dms.auth;

import com.abgl.dms.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(AppUserDetailsService.class);

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        try {
            return appUserRepository.findForAuthentication(username);
        } catch (UsernameNotFoundException exception) {
            log.debug("No account found for username={}", username);
            throw exception;
        } catch (DataAccessException exception) {
            log.error("Database lookup failed during authentication for username={}", username, exception);
            throw exception;
        }
    }
}
