package com.warbox.security;

import com.warbox.account.User;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal stored in the HTTP session. Only the id is meant to be used by controllers:
 * profile data must be re-read from the database. The password hash is erased after authentication.
 */
public final class AccountUserDetails implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final UUID id;
    private final String email;
    private String passwordHash;

    public AccountUserDetails(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static AccountUserDetails from(User user) {
        return new AccountUserDetails(user.getId(), user.getEmail(), user.getPasswordHash());
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AccountUserDetails other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AccountUserDetails[id=" + id + "]";
    }
}
