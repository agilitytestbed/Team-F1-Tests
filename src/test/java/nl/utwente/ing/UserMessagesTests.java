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

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;
import io.restassured.path.json.JsonPath;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

public class UserMessagesTests {

    private static final Path USER_MESSAGES_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/usermessages.json");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private static String sessionId;
    private static int userMessageId;
    private static List<TestTransaction> testTransactions = new ArrayList<>();

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
        for (TestTransaction transaction : testTransactions) {
            given()
                    .header("X-session-ID", transaction.getSessionId())
                    .delete(String.format("api/v1/transactions/%d", transaction.getTransactionId()));
        }
    }

    /**
     * Performs a GET request on the user messages endpoint.
     *
     * This test uses an invalid session ID and tests whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionUserMessagesGetTest() {
        given()
                .get("api/v1/messages")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a PUT request on the user messages endpoint.
     *
     * This test uses a valid session ID and tests whether the message has been marked as read afterwards.
     */
    @Test
    public void userMessagesPutTest() {
        // Create a message for a session.
        balanceBelowZeroFunctionalityTest();

        given()
                .header("X-session-ID", sessionId)
                .put("api/v1/messages/" + userMessageId)
                .then()
                .assertThat()
                .statusCode(200);

        assertTrue(given()
                .header("X-session-ID", sessionId)
                .get("api/v1/messages")
                .then()
                .assertThat()
                .body(matchesJsonSchema(USER_MESSAGES_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getBoolean("read"));
    }

    /**
     * Performs a PUT request on the user messages endpoint.
     *
     * This test uses an invalid session ID and tests whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionUserMessagesPutTest() {
        // Create a message for a session.
        balanceBelowZeroFunctionalityTest();

        given()
                .put("api/v1/messages/" + userMessageId)
                .then()
                .assertThat()
                .statusCode(401);

        assertFalse(given()
                .header("X-session-ID", sessionId)
                .get("api/v1/messages")
                .then()
                .assertThat()
                .body(matchesJsonSchema(USER_MESSAGES_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getBoolean("read"));
    }

    /**
     * Performs a PUT request on the user messages endpoint.
     *
     * This test uses a valid session ID and an invalid message ID
     * and tests whether the resulting status code is 404 Resource not found.
     */
    @Test
    public void invalidIdUserMessagesPutTest() {
        // Create a message for a session so we can use its ID.
        balanceBelowZeroFunctionalityTest();

        given()
                .header("X-session-ID", sessionId)
                .put("api/v1/messages/" + userMessageId + 1)// Add one in order to get an (hopefully) invalid ID.
                .then()
                .assertThat()
                .statusCode(404);
    }

    /**
     * Creates transactions such that a message should be created indicating that the balance dropped below zero.
     * Checks whether a warning message is present.
     */
    @Test
    public void balanceBelowZeroFunctionalityTest() {
        Calendar calendar = Calendar.getInstance();
        // Create a new payment, withdrawing money from the account, making the balance negative.
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                100,
                "withdrawal",
                "balanceBelowZeroFunctionalityTest",
                sessionId
        );

        // Check whether a message has been created.
        JsonPath jsonPath = given()
                .header("X-session-ID", sessionId)
                .get("api/v1/messages")
                .then()
                .assertThat()
                .body(matchesJsonSchema(USER_MESSAGES_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath();

        assertEquals(jsonPath.getString("[0].type"), "warning");
        userMessageId = jsonPath.getInt("[0].id");
    }

    /**
     * Creates transactions such that a message should be created indicating that a new high has been reached.
     * Checks whether an info message is present.
     */
    @Test
    public void balanceNewHighFunctionalityTest() {
        String uniqueSessionId = Util.getSessionID();
        Calendar calendar = Calendar.getInstance();
        // Create a new payment, withdrawing money from the account, making the balance negative.
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                100,
                "deposit",
                "balanceNewHighFunctionalityTest",
                uniqueSessionId
        );

        // Ensure the transaction is made at least 3 months after the first one, in order to trigger the event.
        calendar.add(Calendar.YEAR, 1);
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                100,
                "deposit",
                "balanceNewHighFunctionalityTest",
                uniqueSessionId
        );

        // Check whether a message has been created.
        assertEquals(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/messages")
                .then()
                .assertThat()
                .body(matchesJsonSchema(USER_MESSAGES_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("[0].type"), "info");
    }

    /**
     * Creates a payment request and a transaction that fulfills it.
     * Checks whether an info message is present.
     */
    @Test
    public void paymentRequestFillFunctionalityTest() {
        String uniqueSessionId = Util.getSessionID();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 1);

        // Create a payment request to be fulfilled before the due date.
        Util.createTestPaymentRequest("paymentRequestFillFunctionalityTest",
                calendar,
                "100",
                1,
                uniqueSessionId
        );

        // Create a new transaction before the due date of the payment request
        calendar.add(Calendar.MONTH, -1);
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                100,
                "deposit",
                "paymentRequestFillFunctionalityTest",
                uniqueSessionId
        );

        // Check whether a message has been created.
        assertEquals(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/messages")
                .then()
                .assertThat()
                .body(matchesJsonSchema(USER_MESSAGES_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("[0].type"), "info");
    }

    /**
     * Creates a payment request and sets the system date to ensure it expires.
     * Checks whether a warning message is present.
     */
    @Test
    public void paymentRequestExpiredFunctionalityTest() {
        String uniqueSessionId = Util.getSessionID();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 1);

        // Create a payment request to be fulfilled before the due date.
        Util.createTestPaymentRequest("paymentRequestExpiredFunctionalityTest",
                calendar,
                "100",
                1,
                uniqueSessionId
        );

        // Create a new transaction AFTER the due date of the payment request
        calendar.add(Calendar.MONTH, 1);
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                100,
                "deposit",
                "paymentRequestExpiredFunctionalityTest",
                uniqueSessionId
        );

        // Check whether a message has been created.
        assertEquals(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/messages")
                .then()
                .assertThat()
                .body(matchesJsonSchema(USER_MESSAGES_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("[0].type"), "warning");
    }

    /**
     * Creates a savings goal and transactions to completes it.
     * Checks whether an info message is present.
     */
    @Test
    public void savingsGoalReachedFunctionalityTest() {
        String uniqueSessionId = Util.getSessionID();
        Calendar calendar = Calendar.getInstance();

        // Create a new savings goal, which will be completed later.
        int savingsGoalId = given()
                .header("X-session-ID", uniqueSessionId)
                .body(String.format(SavingGoalsTests.TEST_SAVING_GOAL_FORMAT, 100, 100, 0))
                .post("api/v1/savingGoals")
                .then()
                .assertThat()
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");

        // Create a new transaction after the savings goal, causing money to be set aside towards it.
        calendar.add(Calendar.MONTH, 1);
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                10000,
                "deposit",
                "savingsGoalReachedFunctionalityTest",
                uniqueSessionId
        );

        // Check whether a message has been created.
        assertEquals(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/messages")
                .then()
                .assertThat()
                .body(matchesJsonSchema(USER_MESSAGES_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("[0].type"), "info");

        // Delete the savings goal
        given()
                .header("X-session-ID", uniqueSessionId)
                .delete(String.format("api/v1/savingGoals/%d", savingsGoalId));
    }

    /**
     * Helper function to create a Transaction.
     *  @param date the date on which the Transaction was made
     * @param amount the amount corresponding to the Transaction
     * @param description the description of the Transaction, used for identifying
     * @param sessionId the session for which to create the Transaction
     */
    private static void createTransaction(String date, int amount, String type, String description, String sessionId) {
        int transactionId = Util.createTestTransaction(date, amount, "NL39RABO0300065264",
                type, description, sessionId);
        testTransactions.add(new TestTransaction(sessionId, transactionId));
    }

    /**
     * Helper class that allows sessions and their respective transactions to be stored together.
     * Used to remove all transactions for the sessions and clear up the database after running tests.
     */
    private static class TestTransaction {

        private String sessionId;
        private int transactionId;

        private TestTransaction(String sessionId, int transactionId) {
            this.sessionId = sessionId;
            this.transactionId = transactionId;
        }

        private String getSessionId() {
            return sessionId;
        }

        private int getTransactionId() {
            return transactionId;
        }
    }
}
