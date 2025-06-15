package com.frever.cmsAuthorization.entities;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Column;

public class UserRoleId implements Serializable {
    private static final long serialVersionUID = 1L;
    private String email;
    @Column(name = "role_id")
    private Integer roleId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRoleId that = (UserRoleId) o;
        return email.equals(that.email) && roleId.equals(that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, roleId);
    }

    public String getEmail() {
        return email;
    }

    public Integer getRoleId() {
        return roleId;
    }
}
