package com.moviebooking.user;

import com.moviebooking.common.ApiException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmailIgnoreCase(email)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }

    @Transactional
    public User register(String email, String rawPassword, String fullName, Role role) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException.Conflict("EMAIL_TAKEN", "An account already exists for " + email);
        }
        return users.save(new User(email.toLowerCase(), passwordEncoder.encode(rawPassword), fullName, role));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException.NotFound("User", id));
    }
}
