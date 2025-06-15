package com.frever.cmsAuthorization.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import com.frever.cmsAuthorization.entities.AccessScope;
import com.frever.cmsAuthorization.entities.Role;
import com.frever.cmsAuthorization.services.CmsAuthorizationService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import java.util.List;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CmsAuthorizationResourceTest {
    @Inject
    CmsAuthorizationService cmsAuthorizationService;

    @Test
    public void testHelloEndpoint() {
        given()
            .when().get("/api")
            .then()
            .statusCode(200)
            .body(is("Hello from CMS Authorization"));
    }

    @Test
    public void testCreateUserRoleEndpoint() {
        String requestBody = """
            {
                "email": "x@frever.com",
                "roleId": 1
            }
            """;
        given().when()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .post("/api/user-role")
            .then()
            .statusCode(200);

        Response response = given().when().get("/api/access-scopes/user/x@frever.com");
        response.then().statusCode(200);
        cmsAuthorizationService.deleteUserRole("x@frever.com", 1);
    }

    @Test
    public void testNotCreateDuplicatedUserRole() {
        String requestBody = """
            {
                "email": "x@frever.com",
                "roleId": 1
            }
            """;
        given().when()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .post("/api/user-role")
            .then()
            .statusCode(200);

        Response response = given().when().get("/api/access-scopes/user/x@frever.com");
        response.then().statusCode(200);

        given().when()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .post("/api/user-role")
            .then()
            .statusCode(400);

        cmsAuthorizationService.deleteUserRole("x@frever.com", 1);
    }

    @Test
    public void testGetAllUsers() {
        ValidatableResponse response = given().when()
            .get("/api/users")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
        JsonPath jsonPath = response.extract().body().jsonPath();
        int size = jsonPath.getList("$").size();
        Assertions.assertEquals(2, size);
        jsonPath.getList("$").forEach(item -> {
            Assertions.assertTrue(item.toString().contains("email"));
            Assertions.assertTrue(item.toString().contains("roleIds=[1]"));
        });
    }

    @Test
    public void testUpdateRoleAccessScopes() {
        List<Role> allRolesBefore = cmsAuthorizationService.getAllRoles();
        Role testRole = new Role();
        testRole.setName("testRole");
        cmsAuthorizationService.persistRole(testRole);
        String requestBody = """
            {
                "roleId": %role-id%,
                "accessScopeIds": [2,3,4,5,6,7]
            }
            """.replace("%role-id%", testRole.getId().toString());
        given().when()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .post("/api/role-access-scopes")
            .then()
            .statusCode(200);
        List<Role> allRolesAfter = cmsAuthorizationService.getAllRoles();
        Role added = allRolesAfter.get(allRolesBefore.size());
        Assertions.assertEquals(testRole.getId(), added.getId());
        Assertions.assertEquals(testRole.getName(), added.getName());
        List<AccessScope> accessScopes = added.getAccessScopes();
        Assertions.assertEquals(6, accessScopes.size());
        Assertions.assertEquals(2, (int) accessScopes.get(0).getId());
        Assertions.assertEquals(7, (int) accessScopes.get(accessScopes.size() - 1).getId());
        cmsAuthorizationService.deleteRole(testRole.getId());
        List<Role> allRolesAfterDelete = cmsAuthorizationService.getAllRoles();
        for (int i = 0; i < allRolesBefore.size(); i++) {
            Assertions.assertEquals(allRolesBefore.get(i).getId(), allRolesAfterDelete.get(i).getId());
            Assertions.assertEquals(allRolesBefore.get(i).getName(), allRolesAfterDelete.get(i).getName());
            Assertions.assertArrayEquals(
                allRolesBefore.get(i).getAccessScopes().toArray(),
                allRolesAfterDelete.get(i).getAccessScopes().toArray()
            );
        }
    }

    @Test
    public void itShouldGetUserRoleByEmail() {
        ValidatableResponse response = given().when()
            .get("/api/user/viktor.angmo@frever.com")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
        JsonPath jsonPath = response.extract().body().jsonPath();
        List<Object> jsonPathList = jsonPath.getList("$");
        int size = jsonPathList.size();
        Assertions.assertEquals(1, size);
        jsonPathList.forEach(item -> {
            Assertions.assertTrue(item.toString().contains("name=Admin"));
            Assertions.assertTrue(item.toString().contains("accessScopes="));
        });
    }

    @BeforeEach
    public void cleanUp() {
    }

}
