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

import io.restassured.path.json.JsonPath;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;
import static org.junit.Assert.assertEquals;

public class SavingGoalsTests {

    private static final Path SAVING_GOAL_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/savinggoals/savinggoal.json");
    private static final Path SAVING_GOAL_LIST_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/savinggoals/savinggoal-list.json");
    private static final String TEST_SAVING_GOAL_FORMAT = "{" +
            "\"name\":\"Test Goal\"," +
            "\"goal\": %d," +
            "\"savePerMonth\": %d," +
            "\"minBalanceRequired\": %d" +
            "}";

    private static String sessionId;
    // Lists containing all saving goals and transactions used for testing, which should be deleted after running all tests.
    private static List<Integer> testSavingGoals = new ArrayList<>();
    private static List<Integer> testTransactions = new ArrayList<>();

    /**
     * Sets up the fields which are shared between all tests ensuring that all tests can depend on these
     * fields existing and being set properly.
     */
    @BeforeClass
    public static void setup() {
        sessionId = Util.getSessionID();
    }

    /**
     * Deletes the test data used for testing after running all tests.
     * This avoids duplicate entries and leftover entries in the database after running tests.
     */
    @AfterClass
    public static void deleteTestData() {
        for (Integer savingGoalId : testSavingGoals) {
            given()
                    .header("X-session-ID", sessionId)
                    .delete(String.format("api/v1/savingGoals/%d",savingGoalId));
        }

        for (Integer transactionId : testTransactions) {
            given()
                    .header("X-session-ID", sessionId)
                    .delete(String.format("api/v1/transactions/%d", transactionId));
        }

        testSavingGoals.clear();
        testTransactions.clear();
    }

    /**
     * Performs a GET request on the saving goals endpoint.
     *
     * This test uses a valid session ID and tests whether the output is formatted according to the specification.
     */
    @Test
    public void validSessionSavingGoalsGetTest() {
        given()
                .header("X-session-ID", sessionId)
                .get("api/v1/savingGoals")
                .then()
                .assertThat()
                .body(matchesJsonSchema(SAVING_GOAL_LIST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200);
    }

    /**
     * Performs a GET request on the saving goals endpoint.
     *
     * This test uses an invalid session ID and tests whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionSavingGoalsGetTest() {
        given()
                .get("api/v1/savingGoals")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a POST request on the saving goals endpoint.
     *
     * This test uses a valid session ID and a body formatted according to the given specification.
     * This test will check whether the resulting status code is 201 Created.
     */
    @Test
    public void savingGoalPostTest() {
        createSavingGoal(5000, 100, 0, sessionId);
    }

    /**
     * Performs a POST request on the saving goals endpoint.
     *
     * This test uses an invalid session ID and a body formatted according to the given specification.
     * This test will check whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionSavingGoalPostTest() {
        given()
                .body(String.format(TEST_SAVING_GOAL_FORMAT, 5000, 100, 0))
                .post("api/v1/savingGoals")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a POST request on the saving goals endpoint.
     *
     * This test uses a valid session ID and a body NOT formatted according to the given specification.
     * This test will check whether the resulting status code is 405 Method Not Allowed.
     */
    @Test
    public void invalidBodySavingGoalPostTest() {
        given()
                .header("X-session-ID", sessionId)
                .body("INVALID_BODY")
                .post("api/v1/savingGoals")
                .then()
                .assertThat()
                .statusCode(405);
    }

    /**
     * Performs a DELETE request on the /savingGoals/{savingGoalId} endpoint.
     *
     * This test uses a valid session ID and valid saving goal ID
     * and tests whether the resulting status code is 204 No Content.
     */
    @Test
    public void validSessionSavingGoalByIdDeleteTest() {
        // Delete all current goals to ensure there are no other goals influencing the outcome.
        deleteTestData();
        // Ensure that the saving goal exists.
        savingGoalPostTest();

        given()
                .header("X-session-ID", sessionId)
                .delete(String.format("api/v1/savingGoals/%d", testSavingGoals.get(0)))
                .then()
                .assertThat()
                .statusCode(204);

        // Ensure that it was actually deleted
        given()
                .header("X-session-ID", sessionId)
                .get("api/v1/savingGoals")
                .then()
                .assertThat()
                .statusCode(200)
                .body("$", Matchers.hasSize(0));
    }

    /**
     * Tests whether the functionality of a saving goal works as described by creating goals and transactions.
     * Checks whether the final balance is correct.
     */
    @Test
    public void savingGoalFunctionalityTest() {
        // Transactions made by other tests influence the system time, so they have to be removed in order to be
        // predictable.
        deleteTestData();

        // Deposit €1200 on May the 28th.
        testTransactions.add(Util.createTestTransaction("2018-05-28T00:00:00.000Z", 1200,
                "NL39RABO0300065264", "deposit", "test", sessionId));
        // Add saving goal for €250 each month.
        createSavingGoal(10000, 250, 0, sessionId);
        // Add saving goal for €500 each month IF balance > €1000.
        createSavingGoal(10000, 500, 1000, sessionId);
        // Withdraw €100 on June the 2nd.
        testTransactions.add(Util.createTestTransaction("2018-06-02T00:00:00.000Z", 100,
                "NL39RABO0300065264", "withdrawal", "test", sessionId));
        // Check balance, should now be €850 after processing saving goals.
        JsonPath path = given()
                .header("X-session-ID", sessionId)
                .get("api/v1/balance/history?intervals=1")
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath();
        assertEquals(850, path.getDouble("[0].close"), 0.01);
    }

    /**
     * Tests whether the functionality of a saving goal works as described by creating goals and transactions.
     * A savings goal is fully completed in this test.
     * Checks whether the final balance is correct.
     */
    @Test
    public void savingGoalCompletedFunctionalityTest() {
        // Transactions made by other tests influence the system time, so they have to be removed in order to be
        // predictable.
        deleteTestData();

        // Deposit €1200 on August the 28th.
        testTransactions.add(Util.createTestTransaction("2018-08-28T00:00:00.000Z", 1200,
                "NL39RABO0300065264", "deposit", "test", sessionId));
        // Add saving goal for €250 each month and a goal of €250.
        // This goal will be completed after one month.
        createSavingGoal(250, 250, 0, sessionId);
        // Withdraw €100 on September the 2nd.
        testTransactions.add(Util.createTestTransaction("2018-09-02T00:00:00.000Z", 100,
                "NL39RABO0300065264", "withdrawal", "test", sessionId));
        // Withdraw another €100 on October the 3rd.
        testTransactions.add(Util.createTestTransaction("2018-10-03T00:00:00.000Z", 100,
                "NL39RABO0300065264", "withdrawal", "test", sessionId));
        // The savings goal should only have been applied once, check whether balance confirms this.
        JsonPath path = given()
                .header("X-session-ID", sessionId)
                .get("api/v1/balance/history?intervals=1")
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath();
        assertEquals(750, path.getDouble("[0].close"), 0.01);
    }

    /**
     * Helper function to create a new saving goal.
     *
     * @param goal the goal to reach
     * @param savePerMonth the amount of money put aside per month
     * @param minBalanceRequired the minimum balance required for the savings to be put aside
     * @param sessionId the session ID for this saving goal
     */
    private static void createSavingGoal(int goal, int savePerMonth, int minBalanceRequired, String sessionId) {
        testSavingGoals.add(given()
                .header("X-session-ID", sessionId)
                .body(String.format(TEST_SAVING_GOAL_FORMAT, goal, savePerMonth, minBalanceRequired))
                .post("api/v1/savingGoals")
                .then()
                .assertThat()
                .body(matchesJsonSchema(SAVING_GOAL_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id"));
    }
}
