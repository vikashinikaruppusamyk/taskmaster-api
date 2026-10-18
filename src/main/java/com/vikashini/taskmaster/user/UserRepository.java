package com.vikashini.taskmaster.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Emails are stored lower-cased, so callers must normalise before looking up. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
