package com.frever.cmsAuthorization.entities;

import java.util.Objects;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_role", schema = "cms")
@IdClass(UserRoleId.class)
public class UserRole {
    @Id
    private String email;
    @Id
    private Integer roleId;

    public UserRole(String email, Integer roleId) {
        this.roleId = roleId;
        this.email = email;
    }

    // for JPA
    protected UserRole() {

    }

    public String getEmail() {
        return email;
    }

    public Integer getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRole userRole = (UserRole) o;
        return Objects.equals(email, userRole.email) && Objects.equals(roleId, userRole.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, roleId);
    }

    @Override
    public String toString() {
        return "UserRole{" +
            "email='" + email + '\'' +
            ", roleId=" + roleId +
            '}';
    }
}
