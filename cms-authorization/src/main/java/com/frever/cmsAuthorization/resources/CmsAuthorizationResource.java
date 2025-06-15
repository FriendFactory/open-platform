package com.frever.cmsAuthorization.resources;

import static com.frever.cmsAuthorization.services.CmsAuthorizationService.correctExceptionWhenPersistingDuplicates;

import com.frever.cmsAuthorization.dto.RoleAccessScopes;
import com.frever.cmsAuthorization.dto.UserRoles;
import com.frever.cmsAuthorization.entities.AccessScope;
import com.frever.cmsAuthorization.entities.Role;
import com.frever.cmsAuthorization.entities.UserRole;
import com.frever.cmsAuthorization.services.CmsAuthorizationService;
import com.frever.cmsAuthorization.utils.AdminEndpoint;
import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
public class CmsAuthorizationResource {
    @Inject
    CmsAuthorizationService cmsAuthorizationService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @PermitAll
    public String hello() {
        return "Hello from CMS Authorization";
    }

    @GET
    @Path(("/access-scopes/role/{roleId}"))
    @Produces(MediaType.APPLICATION_JSON)
    public List<AccessScope> getAccessScopesByRoleId(@PathParam("roleId") Integer roleId) {
        return cmsAuthorizationService.getAccessScopesByRoleId(roleId);
    }

    @GET
    @Path("/access-scopes/user/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<AccessScope> getAccessScopesByUserEmail(@PathParam("email") String email) {
        return cmsAuthorizationService.getAccessScopesByEmail(email);
    }

    @GET
    @Path(("/roles"))
    @Produces(MediaType.APPLICATION_JSON)
    public List<Role> getRoles() {
        return cmsAuthorizationService.getAllRoles();
    }

    @GET
    @Path("/access-scopes")
    @Produces(MediaType.APPLICATION_JSON)
    public List<AccessScope> getAccessScopes() {
        return cmsAuthorizationService.getAllAccessScopes();
    }

    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public List<UserRoles> getAllUsers() {
        return cmsAuthorizationService.getAllUserRoles();
    }

    @GET
    @Path("/user/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Role> getUserByEmail(@PathParam("email") String email) {
        return cmsAuthorizationService.getRolesByEmail(email);
    }

    @DELETE
    @Path("/user-role/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    @AdminEndpoint
    public int deleteUser(@PathParam("email") String email) {
        Log.infof("Deleting user with email: %s", email);
        return cmsAuthorizationService.deleteUser(email);
    }

    @DELETE
    @Path("/user-role/{email}/{roleId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AdminEndpoint
    public int deleteUserRole(@PathParam("email") String email, @PathParam("roleId") Integer roleId) {
        Log.infof("Deleting user with email: %s and role %d", email, roleId);
        return cmsAuthorizationService.deleteUserRole(email, roleId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user-role")
    @AdminEndpoint
    public Response createUserWithRole(UserRole userRole) {
        Role role = cmsAuthorizationService.getRoleById(userRole.getRoleId());
        if (role == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Role does not exist").build();
        }
        try {
            cmsAuthorizationService.persistUserRole(userRole);
            Log.info("Successfully persisted UserRole with email: " + userRole.getEmail() + " and role: "
                + userRole.getRoleId());
        } catch (Exception e) {
            if (correctExceptionWhenPersistingDuplicates(e)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("User already has this role").build();
            }
            Log.warnf(
                e,
                "Got exception when persisting UserRole with email: %s and role: %s",
                userRole.getEmail(),
                userRole.getRoleId()
            );
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok().build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user-roles")
    @AdminEndpoint
    public Response createUserWithRoles(UserRoles userRoles) {
        if (!cmsAuthorizationService.roleIdExists(userRoles.getRoleIds())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Some roles does not exist").build();
        }
        try {
            cmsAuthorizationService.persistUserRoles(userRoles);
            Log.info("Successfully persisted UserRole with email: " + userRoles.getEmail() + " and roles: "
                + userRoles.getRoleIds());
        } catch (Exception e) {
            if (correctExceptionWhenPersistingDuplicates(e)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("User already has this role").build();
            }
            Log.warnf(
                e,
                "Got exception when persisting UserRole with email: %s and roleIds: %s",
                userRoles.getEmail(),
                userRoles.getRoleIds()
            );
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok().build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/role-access-scopes")
    @AdminEndpoint
    public Response updateRoleAccessScopes(RoleAccessScopes roleAccessScopes) {
        cmsAuthorizationService.updateRoleAccessScopes(roleAccessScopes);
        return Response.ok().build();
    }
}
