package com.frever.cmsAuthorization.services;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.frever.cmsAuthorization.dto.RoleAccessScopes;
import com.frever.cmsAuthorization.dto.UserRoles;
import com.frever.cmsAuthorization.entities.AccessScope;
import com.frever.cmsAuthorization.entities.Role;
import com.frever.cmsAuthorization.entities.UserRole;
import com.google.common.base.Throwables;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.postgresql.util.PSQLException;

@ApplicationScoped
@Transactional
public class CmsAuthorizationService {
    @Inject
    EntityManager entityManager;

    public Role getRoleById(Integer id) {
        return entityManager.find(Role.class, id);
    }

    public boolean roleIdExists(List<Integer> roleIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Role> cq = cb.createQuery(Role.class);
        Root<Role> roleRoot = cq.from(Role.class);
        CriteriaQuery<Role> roleCriteriaQuery = cq.select(roleRoot).where(roleRoot.get("id").in(roleIds));
        TypedQuery<Role> allQuery = entityManager.createQuery(roleCriteriaQuery);
        return allQuery.getResultList().size() == roleIds.size();
    }

    public AccessScope getAccessScopeById(Integer id) {
        return entityManager.find(AccessScope.class, id);
    }

    public List<Role> getAllRoles() {
        return entityManager.createQuery(
            "SELECT DISTINCT r FROM Role r JOIN FETCH r.accessScopes order by r.id",
            Role.class
        ).getResultList();
    }

    public List<AccessScope> getAllAccessScopes() {
        return entityManager.createQuery("SELECT a FROM AccessScope a", AccessScope.class).getResultList();
    }

    public List<AccessScope> getAccessScopesByRoleId(Integer roleId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AccessScope> cq = cb.createQuery(AccessScope.class);
        Root<Role> roleRoot = cq.from(Role.class);
        CriteriaQuery<AccessScope> accessScopeCriteriaQuery =
            cq.select(roleRoot.get("accessScopes")).where(cb.equal(roleRoot.get("id"), roleId));
        TypedQuery<AccessScope> allQuery = entityManager.createQuery(accessScopeCriteriaQuery);
        return allQuery.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<AccessScope> getAccessScopesByEmail(String email) {
        return entityManager.createQuery("SELECT r.accessScopes FROM UserRole u JOIN Role r ON u.roleId = r.id WHERE "
                + "u.email = :email")
            .setParameter("email", email)
            .getResultList();
    }

    public boolean isUserAdmin(String email) {
        try {
            return entityManager.createQuery("SELECT r FROM UserRole u JOIN Role r ON u.roleId = r.id WHERE u.email ="
                    + " :email AND r.name = 'Admin'", Role.class)
                .setParameter("email", email)
                .getSingleResult() != null;
        } catch (NoResultException e) {
            return false;
        }
    }

    public int deleteUserRole(UserRole userRole) {
        return deleteUserRole(userRole.getEmail(), userRole.getRoleId());
    }

    public int deleteUserRole(String email, Integer roleId) {
        return entityManager.createQuery("DELETE FROM UserRole u WHERE u.email = :email AND u.roleId = :roleId")
            .setParameter("email", email)
            .setParameter("roleId", roleId)
            .executeUpdate();
    }

    public int deleteUser(String email) {
        return entityManager.createQuery("DELETE FROM UserRole u WHERE u.email = :email")
            .setParameter("email", email)
            .executeUpdate();
    }

    public void persistUserRole(UserRole userRole) {
        entityManager.persist(userRole);
    }

    public void persistUserRoles(UserRoles userRoles) {
        for (Integer roleId : userRoles.getRoleIds()) {
            UserRole userRole = new UserRole(userRoles.getEmail(), roleId);
            persistUserRole(userRole);
        }
    }

    public void persistRole(Role role) {
        entityManager.persist(role);
    }

    public void deleteRole(Integer roleId) {
        Role role = getRoleById(roleId);
        if (!role.getAccessScopes().isEmpty()) {
            role.setAccessScopes(Collections.emptyList());
        }
        entityManager.persist(role);
        entityManager.remove(role);
    }

    public void persistAccessScope(AccessScope accessScope) {
        entityManager.persist(accessScope);
    }

    public void createUserWithRole(String email, Integer roleId) {
        UserRole userRole = new UserRole(email, roleId);
        persistUserRole(userRole);
    }

    public void deleteAllUserRoles() {
        entityManager.createQuery("DELETE FROM UserRole").executeUpdate();
    }

    public List<Role> getRolesByEmail(String email) {
        return entityManager.createQuery(
                "SELECT DISTINCT r FROM Role r JOIN FETCH r.accessScopes JOIN UserRole u ON u.roleId = r.id WHERE u"
                    + ".email = :email",
                Role.class
            )
            .setParameter("email", email)
            .getResultList();
    }

    public List<UserRoles> getAllUserRoles() {
        List<UserRole> allUserRoles =
            entityManager.createQuery("SELECT u FROM UserRole u", UserRole.class).getResultList();
        Map<String, List<Integer>> userRolesMap =
            allUserRoles.stream().collect(groupingBy(UserRole::getEmail, mapping(UserRole::getRoleId, toList())));
        return userRolesMap.entrySet().stream().map(entry -> new UserRoles(entry.getKey(), entry.getValue())).collect(
            toList());
    }

    public static boolean correctExceptionWhenPersistingDuplicates(Exception e) {
        Throwable rootCause = Throwables.getRootCause(e);
        return rootCause instanceof PSQLException && rootCause.getMessage()
            .contains("duplicate key value violates unique constraint");
    }

    public void updateRoleAccessScopes(RoleAccessScopes roleAccessScopes) {
        Role role = getRoleById(roleAccessScopes.getRoleId());
        if (role == null) {
            throw new BadRequestException("Role with id " + roleAccessScopes.getRoleId() + " not found");
        }
        List<AccessScope> accessScopes =
            entityManager.createQuery("SELECT a FROM AccessScope a WHERE a.id IN :accessScopeIds", AccessScope.class)
                .setParameter("accessScopeIds", roleAccessScopes.getAccessScopeIds())
                .getResultList();
        if (accessScopes.size() != roleAccessScopes.getAccessScopeIds().size()) {
            throw new BadRequestException("One or more access scopes not found");
        }
        role.setAccessScopes(accessScopes);
        entityManager.merge(role);
    }
}
