/*
 * Copyright (c) 2018, Joost Prins <github.com/joostprins>, Tom Leemreize <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nl.utwente.ing;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;
import static org.junit.Assert.assertEquals;

public class CategoryTests {

    static final Path CATEGORY_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/categories/category.json");
    private static final Path CATEGORY_LIST_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/categories/category-list.json");

    private static Integer testCategoryId;
    private static final String TEST_CATEGORY_NAME = "Test Category";
    private static final int INVALID_CATEGORY_ID = -26_07_1581;

    private static String sessionId;

    /**
     * Makes sure all tests share the same session ID by setting sessionId if it does not exist yet.
     */
    @Before
    public void getTestSession() {
        if (sessionId == null) {
            sessionId = Util.getSessionID();
        }
    }

    /**
     * Deletes the category used for testing before and after running every test.
     * This avoids duplicate entries and leftover entries in the database after running tests.
     */
    @Before
    @After
    public void deleteTestCategory() {
        // Make sure that the session exists in case deleteTestCategory() is called before getTestSession().
        if (sessionId == null) {
            getTestSession();
        }

        if (testCategoryId != null) {
            given()
                    .header("X-session-ID", sessionId)
                    .delete(String.format("api/v1/categories/%d", testCategoryId));
        }
    }

    /*
     *  Tests related to GET requests on the /categories API endpoint.
     *  API Documentation: https://app.swaggerhub.com/apis/djhuistra/INGHonours/1.0.1#/categories/get_categories
     */

    /**
     * Performs a GET request on the categories endpoint.
     *
     * This test uses a valid session ID and tests whether the output is formatted according to the specification.
     */
    @Test
    public void validSessionCategoriesGetTest() {
        given()
                .header("X-session-ID", sessionId)
                .get("api/v1/categories")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORY_LIST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200);
    }

    /**
     * Performs a GET request on the categories endpoint.
     *
     * This test uses an invalid session ID and checks whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionCategoriesGetTest() {
        get("api/v1/categories")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /*
     *  Tests related to POST requests on the /categories API endpoint.
     *  API Documentation: https://app.swaggerhub.com/apis/djhuistra/INGHonours/1.0.1#/categories/post_categories
     */

    /**
     * Performs a POST request on the categories endpoint.
     *
     * This test uses a valid session ID and a body formatted according to the given specification for a Category.
     * This test will check whether the resulting status code is 201 Created.
     */
    @Test
    public void categoriesPostTest() {
        testCategoryId = given()
                .header("X-session-ID", sessionId)
                .body(String.format("{\"name\": \"%s\"}", TEST_CATEGORY_NAME))
                .post("api/v1/categories")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORY_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");
    }

    /**
     * Performs a POST request on the categories endpoint.
     *
     * This test uses an invalid session ID and a body formatted according to the given specification for a Category.
     * This test will check whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionCategoriesPostTest() {
        given()
                .body(String.format("{\"name\": \"%s\"}", TEST_CATEGORY_NAME))
                .post("api/v1/categories")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a POST request on the categories endpoint.
     *
     * This test uses a valid session ID and an invalid body.
     * This test will check whether the resulting status code is 405 Method Not Allowed.
     */
    @Test
    public void invalidFormatCategoriesPostTest() {
        given()
                .header("X-session-ID", sessionId)
                .body(String.format("{\"invalid\": \"%s\"}", TEST_CATEGORY_NAME))
                .post("api/v1/categories")
                .then()
                .assertThat()
                .statusCode(405);
    }

    /*
     *  Tests related to GET requests on the /categories/{categoryId} API endpoint.
     *  API Documentation: https://app.swaggerhub.com/apis/djhuistra/INGHonours/1.0.1#/categories/get_categories__categoryId_
     */

    /**
     * Performs a GET request on the categories/{categoryId} endpoint.
     *
     * This test uses a valid session ID and tests whether a previously created category can be fetched and is formatted
     * according to the specification.
     */
    @Test
    public void validSessionByIdGetTest() {
        // Use the /categories POST test to create the test category.
        categoriesPostTest();

        String categoryName = given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/categories/%d", testCategoryId))
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORY_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("name");

        assertEquals(TEST_CATEGORY_NAME, categoryName);
    }

    /**
     * Performs a GET request on the categories/{categoryId} endpoint.
     *
     * This test uses a valid session ID and tests whether a non-existent category returns a status of 404 Not Found.
     */
    @Test
    public void validSessionByInvalidIdGetTest() {
        // Unlike validSessionByIdGetTest() we do not create the test category in this case.
        // categoriesPostTest();

        given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/categories/%d", INVALID_CATEGORY_ID))
                .then()
                .assertThat()
                .statusCode(404);
    }

    /**
     * Performs a GET request on the categories/{categoryId} endpoint.
     *
     * This test uses an invalid session ID and checks whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionByIdGetTest() {
        // Use the /categories POST test to create the test category.
        categoriesPostTest();

        given()
                .get(String.format("api/v1/categories/%d", testCategoryId))
                .then()
                .assertThat()
                .statusCode(401);
    }

    /*
     *  Tests related to PUT requests on the /categories/{categoryId} API endpoint.
     *  API Documentation: https://app.swaggerhub.com/apis/djhuistra/INGHonours/1.0.1#/categories/put_categories__categoryId_
     */

    /**
     * Performs a PUT request on the categories/{categoryId} endpoint.
     *
     * This test uses a valid session ID and tests whether a previously created category can be updated with a PUT request.
     */
    @Test
    public void validSessionByIdPutTest() {
        // Use the /categories POST test to create the test category.
        categoriesPostTest();

        final String newCategoryName = "validSessionByIdPutTest() Updated Name";

        String categoryName = given()
                .header("X-session-ID", sessionId)
                .body(String.format("{\"name\": \"%s\"}", newCategoryName))
                .put(String.format("api/v1/categories/%d", testCategoryId))
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORY_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("name");

        assertEquals(categoryName, newCategoryName);
    }

    /**
     * Performs a PUT request on the categories/{categoryId} endpoint.
     *
     * This test uses a valid session ID and tests whether a non-existent category returns a status of 404 Not Found.
     */
    @Test
    public void validSessionByInvalidIdPutTest() {
        // Unlike validSessionByIdPutTest() we do not create the test category in this case.
        // categoriesPostTest();

        given()
                .header("X-session-ID", sessionId)
                .body(String.format("{\"name\": \"%s\"}", TEST_CATEGORY_NAME))
                .put(String.format("api/v1/categories/%d", INVALID_CATEGORY_ID))
                .then()
                .assertThat()
                .statusCode(404);
    }

    /**
     * Performs a PUT request on the categories/{categoryId} endpoint.
     *
     * This test uses an invalid session ID and checks whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionByIdPutTest() {
        // Use the /categories POST test to create the test category.
        categoriesPostTest();

        given()
                .body(String.format("{\"name\": \"%s\"}", TEST_CATEGORY_NAME))
                .put(String.format("api/v1/categories/%d", testCategoryId))
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a PUT request on the categories/{categoryId} endpoint.
     *
     * This test uses an invalid body and checks whether the resulting status code is 405 Method Not Allowed.
     */
    @Test
    public void validSessionInvalidBodyByIdPutTest() {
        // Use the /categories POST test to create the test category.
        categoriesPostTest();

        given()
                .header("X-session-ID", sessionId)
                .body("{\"invalid\": true}")
                .put(String.format("api/v1/categories/%d", testCategoryId))
                .then()
                .assertThat()
                .statusCode(405);
    }

    /*
     *  Tests related to DELETE requests on the /categories/{categoryId} API endpoint.
     *  API Documentation: https://app.swaggerhub.com/apis/djhuistra/INGHonours/1.0.1#/categories/delete_categories__categoryId_
     */

    /**
     * Performs a DELETE request on the categories/{categoryId} endpoint.
     *
     * This test uses a valid session ID and tests whether a previously created category can be updated with a PUT request.
     */
    @Test
    public void validSessionByIdDeleteTest() {
        // Use the /categories POST test to create the test category.
        categoriesPostTest();

        given()
                .header("X-session-ID", sessionId)
                .delete(String.format("api/v1/categories/%d", testCategoryId))
                .then()
                .assertThat()
                .statusCode(204);
    }

    /**
     * Performs a DELETE request on the categories/{categoryId} endpoint.
     *
     * This test uses a valid session ID and tests whether a non-existent category returns a status of 404 Not Found.
     */
    @Test
    public void validSessionByInvalidIdDeleteTest() {
        // Unlike validSessionByIdDeleteTest() we do not create the test category in this case.
        // categoriesPostTest();

        given()
                .header("X-session-ID", sessionId)
                .delete(String.format("api/v1/categories/%d", INVALID_CATEGORY_ID))
                .then()
                .assertThat()
                .statusCode(404);
    }

    /**
     * Performs a DELETE request on the categories/{categoryId} endpoint.
     *
     * This test uses an invalid session ID and checks whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionByIdDeleteTest() {
        // Use the /categories POST test to create the test category.
        categoriesPostTest();

        given()
                .delete(String.format("api/v1/categories/%d", testCategoryId))
                .then()
                .assertThat()
                .statusCode(401);
    }
}
