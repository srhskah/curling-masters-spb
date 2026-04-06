package com.example.entity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetails implementation that wraps the User entity
 */
public class CustomUserDetails implements UserDetails, Serializable {
    private static final long serialVersionUID = 1L;

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.user = user;
        String role = switch (user.getRole()) {
            case 0 -> "ROLE_SUPER_ADMIN";
            case 1 -> "ROLE_ADMIN";
            default -> "ROLE_USER";
        };
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    public User getUser() {
        return user;
    }

    public Integer getRole() {
        return user.getRole();
    }

    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
