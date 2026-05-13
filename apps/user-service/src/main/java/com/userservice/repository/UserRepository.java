package com.userservice.repository;

import com.userservice.domain.user.User;
import com.userservice.enums.Role;
import com.userservice.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByStatus(UserStatus status);

    @Query("SELECT COUNT(u) FROM User u JOIN u.userRoles ur WHERE ur.id.role = :role")
    long countByRole(@Param("role") Role role);
}
