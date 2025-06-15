package com.frever.cmsAuthorization.services;

import static com.frever.cmsAuthorization.services.CmsAuthorizationService.correctExceptionWhenPersistingDuplicates;
import static org.junit.jupiter.api.Assertions.fail;

import com.frever.cmsAuthorization.dto.UserRoles;
import com.frever.cmsAuthorization.entities.AccessScope;
import com.frever.cmsAuthorization.entities.Role;
import com.frever.cmsAuthorization.entities.UserRole;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CmsAuthorizationServiceTest {
    public static final String EMAIL = "x@frever.com";
    @Inject
    CmsAuthorizationService cmsAuthorizationService;

    @Test
    public void itShouldFindExistingRole() {
        Role role = cmsAuthorizationService.getRoleById(1);
        Assertions.assertNotNull(role);
        Assertions.assertEquals("Admin", role.getName());
    }

    @Test
    public void itShouldFindExistingAccessScope() {
        AccessScope scope = cmsAuthorizationService.getAccessScopeById(1);
        Assertions.assertNotNull(scope);
        Assertions.assertEquals("AssetRead", scope.getName());
    }

    @Test
    public void roleShouldHaveCorrectAccessScopes() {
        List<AccessScope> allAccessScopes = cmsAuthorizationService.getAllAccessScopes();
        List<AccessScope> adminAccessScopes = cmsAuthorizationService.getAccessScopesByRoleId(1);
        Assertions.assertIterableEquals(allAccessScopes, adminAccessScopes);
        List<AccessScope> artistAccessScopes = cmsAuthorizationService.getAccessScopesByRoleId(3);
        Assertions.assertEquals(3, artistAccessScopes.size());
    }

    @Test
    public void itShouldGetAccessRolesByUserId() {
        UserRole userRole = new UserRole(EMAIL, 1);
        cmsAuthorizationService.persistUserRole(userRole);
        List<AccessScope> accessScopes = cmsAuthorizationService.getAccessScopesByEmail(EMAIL);
        Assertions.assertNotNull(accessScopes);
        List<AccessScope> allAccessScopes = cmsAuthorizationService.getAllAccessScopes();
        Assertions.assertIterableEquals(allAccessScopes, accessScopes);
        cmsAuthorizationService.deleteUserRole(userRole);
    }

    @Test
    public void itShouldGetAllRoles() {
        List<Role> roles = cmsAuthorizationService.getAllRoles();
        Assertions.assertEquals(8, roles.size());
        Assertions.assertEquals(9, roles.get(0).getAccessScopes().size());
    }

    @Test
    public void itShouldCheckIfUserIsAdmin() {
        boolean karenIsAdmin = cmsAuthorizationService.isUserAdmin("karen.oliveira@frever.com");
        boolean viktorIsAdmin = cmsAuthorizationService.isUserAdmin("viktor.angmo@frever.com");
        Assertions.assertTrue(karenIsAdmin);
        Assertions.assertTrue(viktorIsAdmin);
    }

    @Test
    public void itShouldFailWhenCreatingDuplicatedUserRole() {
        UserRole userRole = new UserRole(EMAIL, 7);
        cmsAuthorizationService.persistUserRole(userRole);

        try {
            UserRoles userRoles = new UserRoles(EMAIL, List.of(1, 2, 3, 4, 5, 6, 7));
            cmsAuthorizationService.persistUserRoles(userRoles);
            fail("Should have thrown an exception");
        } catch (Exception e) {
            if (!correctExceptionWhenPersistingDuplicates(e)) {
                fail("Should have thrown an exception about duplication");
            }
        }

        List<Role> rolesByEmail = cmsAuthorizationService.getRolesByEmail(EMAIL);
        Assertions.assertEquals(1, rolesByEmail.size());
        Assertions.assertEquals(7, rolesByEmail.get(0).getId());

        cmsAuthorizationService.deleteUser(EMAIL);
    }

    @Test
    public void itShouldCreateUserRoles() {
        UserRoles userRoles = new UserRoles(EMAIL, List.of(1, 2, 3, 4, 5, 6, 7));
        cmsAuthorizationService.persistUserRoles(userRoles);
        List<Role> rolesByEmail = cmsAuthorizationService.getRolesByEmail(EMAIL);
        Assertions.assertEquals(7, rolesByEmail.size());
        Assertions.assertEquals(1, rolesByEmail.get(0).getId());
        cmsAuthorizationService.deleteUser(EMAIL);
    }

    @Test
    public void itShouldDeleteUserRoles() {
        UserRoles userRoles = new UserRoles(EMAIL, List.of(1, 2));
        cmsAuthorizationService.persistUserRoles(userRoles);
        cmsAuthorizationService.deleteUserRole(EMAIL, 1);
        cmsAuthorizationService.deleteUserRole(EMAIL, 2);
        List<Role> rolesByEmail = cmsAuthorizationService.getRolesByEmail(EMAIL);
        Assertions.assertEquals(0, rolesByEmail.size());
    }

    public void itShouldGetAllUserRoles() {
        List<UserRoles> userRoles = cmsAuthorizationService.getAllUserRoles();
        Assertions.assertEquals(2, userRoles.size());
        Assertions.assertEquals(1, userRoles.get(0).getRoleIds().size());
        Assertions.assertEquals(1, userRoles.get(0).getRoleIds().get(0));
    }

    @BeforeEach
    public void cleanUp() {
    }
}
