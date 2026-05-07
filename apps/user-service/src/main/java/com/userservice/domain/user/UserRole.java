package com.userservice.domain.user;

import com.userservice.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_roles")
@Getter
@NoArgsConstructor
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    public UserRole(User user, Role role) {
        this.user = user;
        this.id = new UserRoleId(user.getId(), role);
    }

    public Role getRole() {
        return id.getRole();
    }
}
