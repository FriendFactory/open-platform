package com.frever.cmsAuthorization.dto;

import java.util.List;

public class UserRoles {
    private String email;
    private List<Integer> roleIds;

    public UserRoles(String email, List<Integer> roleIds) {
        this.email = email;
        this.roleIds = roleIds;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<Integer> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Integer> roleIds) {
        this.roleIds = roleIds;
    }
}
