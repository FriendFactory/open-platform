package com.frever.cmsAuthorization.dto;

import java.util.List;

public class RoleAccessScopes {
    private Integer roleId;
    private List<Integer> accessScopeIds;

    public RoleAccessScopes(Integer roleId, List<Integer> accessScopeIds) {
        this.roleId = roleId;
        this.accessScopeIds = accessScopeIds;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public List<Integer> getAccessScopeIds() {
        return accessScopeIds;
    }

    public void setAccessScopeIds(List<Integer> accessScopeIds) {
        this.accessScopeIds = accessScopeIds;
    }
}
