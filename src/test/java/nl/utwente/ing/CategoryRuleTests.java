/*
 * Copyright (c) 2018, Tom Leemreize <https://github.com/oplosthee>
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

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CategoryRuleTests {

    private static final Path CATEGORYRULE_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/categoryrules/categoryrule.json");
    private static final Path CATEGORYRULE_LIST_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/categoryrules/categoryrule-list.json");
    private static final int INVALID_CATEGORYRULE_ID = -26_07_1581;
    private static final String TEST_CATEGORYRULE = "{" +
            "\"description\":\"University of Twente\"," +
            "\"iBAN\":\"NL39RABO0300065264\"," +
            "\"type\": \"deposit\"," +
            "\"category\": { " +
                "\"id\": 25, " +
                "\"name\": \"groceries\" " +
            "}," +
            "\"applyOnHistory\": false " +
            "}";
    private static final String TEST_TRANSACTION =
            "{" +
                    "\"date\": \"1889-04-20T19:45:04.030Z\", " +
                    "\"amount\": 213.12, " +
                    "\"externalIBAN\": \"NL39RABO0300065264\", " +
                    "\"type\": \"deposit\", " +
                    "\"description\":\"University of Twente\"," +
                    "}";

    private static Integer testTransactionId;
    private static Integer testCategoryRuleId;
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
     * Deletes the test data used for testing before and after running every test.
     * This avoids duplicate entries and leftover entries in the database after running tests.
     */
    @Before
    @After
    public void deleteTestData() {
        // Make sure that the session exists in case deleteTestCategoryRule() is called before getTestSession().
        if (sessionId == null) {
            getTestSession();
        }

        if (testCategoryRuleId != null) {
            given()
                    .header("X-session-ID", sessionId)
                    .delete(String.format("api/v1/categoryRules/%d", testCategoryRuleId));
        }

        if (testTransactionId != null) {
            given()
                    .header("X-session-ID", sessionId)
                    .delete(String.format("api/v1/transactions/%d", testTransactionId));
        }
    }

    /*
     *  Tests related to GET requests on the /categoryRules API endpoint.
     */

    /**
     * Performs a GET request on the categoryRules endpoint.
     *
     * This test uses a valid session ID and tests whether the output is formatted according to the specification.
     */
    @Test
    public void validSessionCategoryRulesGetTest() {
        given()
                .header("X-session-ID", sessionId)
                .get("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_LIST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200);
    }

    /**
     * Performs a GET request on the categoryRules endpoint.
     *
     * This test uses an invalid session ID and tests whether the output is formatted according to the specification.
     */
    @Test
    public void invalidSessionCategoryRulesGetTest() {
        given()
                .get("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_LIST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(401);
    }

    /**
     * Performs a GET request on the categoryRules endpoint.
     *
     * This test uses a valid session ID and tests whether the output is formatted according to the specification.
     * This test also adds rules first, making sure the correct rules are returned.
     */
    @Test
    public void validSessionWithDataCategoryRulesGetTest() {
        categoryRulePostTest();

        given()
                .header("X-session-ID", sessionId)
                .get("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_LIST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(401);
    }

    /**
     * Performs a GET request on the categoryRules endpoint.
     *
     * This test uses an invalid session ID and tests whether the output is formatted according to the specification.
     * This test also adds rules first, making sure the correct rules are returned.
     */
    @Test
    public void invalidSessionWithDataCategoryRulesGetTest() {
        categoryRulePostTest();

        given()
                .get("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_LIST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(401);
    }

    /*
     *  Tests related to POST requests on the /categoryRules API endpoint.
     */

    /**
     * Performs a POST request on the categoryRules endpoint.
     *
     * This test uses a valid session ID and a body formatted according to the given specification.
     * This test will check whether the resulting status code is 201 Created.
     */
    @Test
    public void categoryRulePostTest() {
        testCategoryRuleId = given()
                .header("X-session-ID", sessionId)
                .body(TEST_CATEGORYRULE)
                .post("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");
    }

    /**
     * Performs a POST request on the categoryRules endpoint.
     *
     * This test uses an invalid session ID and a body formatted according to the given specification.
     * This test will check whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionCategoryRulePostTest() {
        given()
                .body(TEST_CATEGORYRULE)
                .post("api/v1/categoryRules")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a POST request on the categoryRules endpoint.
     *
     * This test uses a valid session ID and a body NOT formatted according to the given specification.
     * This test will check whether the resulting status code is 405 Method Not Allowed.
     */
    @Test
    public void validSessionInvalidBodyCategoryRulePostTest() {
        given()
                .header("X-session-ID", sessionId)
                .body("{\"description\":\"INVALID BODY TEST\", \"invalid\":\"body\"}")
                .post("api/v1/categoryRules")
                .then()
                .assertThat()
                .statusCode(405);
    }

    /*
     *  Tests related to GET requests on the /categoryRules/{categoryRuleId} API endpoint.
     */

    /**
     * Performs a GET request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses a valid session ID and tests whether the output is formatted according to the specification.
     */
    @Test
    public void validSessionCategoryRulesByIdGetTest() {
        // Ensure that the category rule exists:
        categoryRulePostTest();

        String description = given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("description");

        assertEquals("University of Twente", description);
    }

    /**
     * Performs a GET request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses an invalid session ID and a valid rule ID.
     * This test will check whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionCategoryRulesByIdGetTest() {
        // Ensure that the category rule exists:
        categoryRulePostTest();

        given()
                .get(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a GET request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses an valid session ID and invalid rule ID.
     * This test will check whether the resulting status code is 404 Not Found.
     */
    @Test
    public void validSessionCategoryRulesByInvalidIdGetTest() {
        given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/categoryRules/%d", INVALID_CATEGORYRULE_ID))
                .then()
                .assertThat()
                .statusCode(404);
    }

    /*
     *  Tests related to DELETE requests on the /categoryRules/{categoryRuleId} API endpoint.
     */

    /**
     * Performs a DELETE request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses a valid session ID and valid category ID
     * and tests whether the resulting status code is 204 No Content.
     */
    @Test
    public void validSessionCategoryRulesByIdDeleteTest() {
        // Ensure that the category rule exists:
        categoryRulePostTest();

        given()
                .header("X-session-ID", sessionId)
                .delete(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .statusCode(204);

        // Ensure that it was actually deleted
        given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .statusCode(404);
    }

    /**
     * Performs a DELETE request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses an invalid session ID and valid category ID
     * and tests whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionCategoryRulesByIdDeleteTest() {
        // Ensure that the category rule exists:
        categoryRulePostTest();

        given()
                .delete(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a DELETE request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses an valid session ID and invalid category ID
     * and tests whether the resulting status code is 404 Not Found.
     */
    @Test
    public void validSessionCategoryRulesByInvalidIdDeleteTest() {
        given()
                .header("X-session-ID", sessionId)
                .delete(String.format("api/v1/categoryRules/%d", INVALID_CATEGORYRULE_ID))
                .then()
                .assertThat()
                .statusCode(404);
    }

    /*
     *  Tests related to PUT requests on the /categoryRules/{categoryRuleId} API endpoint.
     */

    /**
     * Performs a PUT request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses an valid session ID, valid category ID and valid body
     * and tests whether the resulting status code is 200 OK.
     */
    @Test
    public void validSessionCategoryRulesByIdPutTest() {
        categoryRulePostTest();

        given()
                .header("X-session-ID", sessionId)
                .body("{" +
                        "\"description\":\"CATEGORY_PUT_TEST\"," +
                        "\"iBAN\":\"NL39RABO0300065264\"," +
                        "\"type\": \"deposit\"," +
                        "\"category\": { " +
                        "\"id\": 25, " +
                        "\"name\": \"groceries\" " +
                        "}," +
                        "\"applyOnHistory\": false " +
                        "}")
                .put(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200);

        // Ensure that it was actually changed
        String description = given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("description");

        assertEquals("CATEGORY_PUT_TEST", description);
    }

    /**
     * Performs a PUT request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses an invalid session ID, valid category ID and valid body
     * and tests whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionCategoryRulesByIdPutTest() {
        categoryRulePostTest();

        given()
                .body("{" +
                        "\"description\":\"CATEGORY_PUT_TEST\"," +
                        "\"iBAN\":\"NL39RABO0300065264\"," +
                        "\"type\": \"deposit\"," +
                        "\"category\": { " +
                        "\"id\": 25, " +
                        "\"name\": \"groceries\" " +
                        "}," +
                        "\"applyOnHistory\": false " +
                        "}")
                .put(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a PUT request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses a valid session ID, invalid category ID and valid body
     * and tests whether the resulting status code is 404 Not Found.
     */
    @Test
    public void validSessionCategoryRulesByInvalidIdPutTest() {
        given()
                .header("X-session-ID", sessionId)
                .body("{" +
                        "\"description\":\"CATEGORY_PUT_TEST\"," +
                        "\"iBAN\":\"NL39RABO0300065264\"," +
                        "\"type\": \"deposit\"," +
                        "\"category\": { " +
                        "\"id\": 25, " +
                        "\"name\": \"groceries\" " +
                        "}," +
                        "\"applyOnHistory\": false " +
                        "}")
                .put(String.format("api/v1/categoryRules/%d", INVALID_CATEGORYRULE_ID))
                .then()
                .assertThat()
                .statusCode(404);
    }

    /**
     * Performs a PUT request on the /categoryRules/{categoryRuleId} endpoint.
     *
     * This test uses a valid session ID, valid category ID and invalid body
     * and tests whether the resulting status code is 405 Method Not Allowed.
     */
    @Test
    public void validSessionInvalidBodyCategoryRulesByIdPutTest() {
        categoryRulePostTest();

        given()
                .header("X-session-ID", sessionId)
                .body("{\"description\":\"INVALID BODY TEST\", \"invalid\":\"body\"}")
                .put(String.format("api/v1/categoryRules/%d", testCategoryRuleId))
                .then()
                .assertThat()
                .statusCode(405);
    }

    /*
     *  Tests related to making sure the categories are applied correctly to transactions.
     */

    /**
     * Tests whether categories are applied to transactions when they match a category rule.
     * First creates a category rule and then submits a transaction, testing whether the category was set.
     */
    @Test
    public void applyCategoryRuleTest() {
        // Create a new category rule
        testCategoryRuleId = given()
                .header("X-session-ID", sessionId)
                .body(TEST_CATEGORYRULE)
                .post("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Create a new transaction matching the category rule created earlier
        testTransactionId = given()
                .header("X-session-ID", sessionId)
                .body(TEST_TRANSACTION)
                .post("api/v1/transactions")
                .then()
                .assertThat()
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Assure the transaction category was set according to the new rule
        String categoryName = given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/transactions/%d", testTransactionId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("category.name");

        assertEquals("groceries", categoryName);
    }

    /**
     * Tests whether categories are applied to transactions when they match a category rule.
     * First submits a transaction and then creates a new category rule, testing whether the category was set.
     */
    @Test
    public void applyOnHistoryCategoryRuleTest() {
        // Create a new transaction
        testTransactionId = given()
                .header("X-session-ID", sessionId)
                .body(TEST_TRANSACTION)
                .post("api/v1/transactions")
                .then()
                .assertThat()
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Create a new category rule with applyOnHistory set to true, matching the transaction created earlier
        testCategoryRuleId = given()
                .header("X-session-ID", sessionId)
                .body("{" +
                        "\"description\":\"University of Twente\"," +
                        "\"iBAN\":\"NL39RABO0300065264\"," +
                        "\"type\": \"deposit\"," +
                        "\"category\": { " +
                        "\"id\": 25, " +
                        "\"name\": \"groceries\" " +
                        "}," +
                        "\"applyOnHistory\": true " +
                        "}")
                .post("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Assure the transaction category was set according to the new rule
        String categoryName = given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/transactions/%d", testTransactionId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("category.name");

        assertEquals("groceries", categoryName);
    }

    /**
     * Tests whether categories are applied to transactions when they do not match a category rule.
     * First creates a category rule and then submits a transaction, testing whether the category was not set.
     */
    @Test
    public void applyCategoryRuleWrongTest() {
        // Create a new category rule
        testCategoryRuleId = given()
                .header("X-session-ID", sessionId)
                .body(TEST_CATEGORYRULE)
                .post("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Create a new transaction which does not match the category rule created earlier
        // Note: The description is different (NOT University of Twente).
        testTransactionId = given()
                .header("X-session-ID", sessionId)
                .body("{" +
                        "\"date\": \"1889-04-20T19:45:04.030Z\", " +
                        "\"amount\": 213.12, " +
                        "\"externalIBAN\": \"NL39RABO0300065264\", " +
                        "\"type\": \"deposit\", " +
                        "\"description\":\"University of Enschede\"," +
                        "}")
                .post("api/v1/transactions")
                .then()
                .assertThat()
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Assure the transaction category was not set according to the new rule
        String categoryName = given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/transactions/%d", testTransactionId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getJsonObject("category");

        assertNull(categoryName);
    }

    /**
     * Tests whether categories are applied to transactions when the rule fields are left blank.
     * First creates a category rule and then submits a transaction, testing whether the category was set.
     */
    @Test
    public void applyCategoryRuleBlankTest() {
        // Create a new category rule with all fields left blank, causing all transactions to match it.
        testCategoryRuleId = given()
                .header("X-session-ID", sessionId)
                .body("{" +
                        "\"description\":\"\"," +
                        "\"iBAN\":\"\"," +
                        "\"type\": \"\"," +
                        "\"category\": { " +
                        "\"id\": 25, " +
                        "\"name\": \"groceries\" " +
                        "}," +
                        "\"applyOnHistory\": false " +
                        "}")
                .post("api/v1/categoryRules")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CATEGORYRULE_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Create a new transaction matching the category rule created earlier
        testTransactionId = given()
                .header("X-session-ID", sessionId)
                .body(TEST_TRANSACTION)
                .post("api/v1/transactions")
                .then()
                .assertThat()
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Assure the transaction category was set according to the new rule
        String categoryName = given()
                .header("X-session-ID", sessionId)
                .get(String.format("api/v1/transactions/%d", testTransactionId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("category.name");

        assertEquals("groceries", categoryName);
    }
}
