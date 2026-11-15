package com.warbox.security;

import com.warbox.account.AccountService;
import com.warbox.account.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AccountUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(AccountService.normalizeEmail(email))
                .map(AccountUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
