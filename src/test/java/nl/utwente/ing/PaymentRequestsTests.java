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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.junit.AfterClass;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

public class PaymentRequestsTests {

    private static final Path PAYMENT_REQUEST_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/paymentrequest.json");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final String PAYMENT_REQUEST_FORMAT =
            "{" +
                    "\"description\": \"%s\"," +
                    "\"due_date\": \"%s\"," +
                    "\"amount\": %s," +
                    "\"number_of_requests\": %d" +
                    "}";

    private static String sessionId;
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
     * Performs a GET request on the payment requests endpoint.
     *
     * This test uses a valid session ID and tests whether the output is formatted according to the specification.
     */
    @Test
    public void validSessionPaymentRequestGetTest() {
        // Ensure there is some data available to compare.
        paymentRequestPostTest();

        given()
                .header("X-session-ID", sessionId)
                .get("api/v1/paymentRequests")
                .then()
                .assertThat()
                .body(matchesJsonSchema(PAYMENT_REQUEST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200);
    }

    /**
     * Performs a GET request on the payment requests endpoint.
     *
     * This test uses an invalid session ID and tests whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionPaymentRequestGetTest() {
        given()
                .get("api/v1/paymentRequests")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a POST request on the payment requests endpoint.
     *
     * This test uses a valid session ID and a body formatted according to the given specification for a PaymentRequest.
     * This test will check whether the resulting status code is 201 Created.
     */
    @Test
    public void paymentRequestPostTest() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 2); // Set the date to two months into the future.
        Util.createTestPaymentRequest(
                "paymentRequestPostTest",
                calendar,
                "100.0",
                1,
                sessionId
        );
    }

    /**
     * Performs a POST request on the payment requests endpoint.
     *
     * This test uses an invalid session ID and a body formatted according to the given specification for a PaymentRequest.
     * This test will check whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionPaymentRequestPostTest() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 2); // Set the date to two months into the future.

        given()
                .body(String.format(PAYMENT_REQUEST_FORMAT,
                        "invalidSessionPaymentRequestPostTest",
                        DATE_FORMAT.format(calendar.getTime()),
                        "100.00",
                        1))
                .post("api/v1/paymentRequests")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a POST request on the payment requests endpoint.
     *
     * This test uses a valid session ID and a body NOT formatted according to the given specification for a PaymentRequest.
     * This test will check whether the resulting status code is 405 Method Not Allowed.
     */
    @Test
    public void invalidBodyPaymentRequestPostTest() {
        given()
                .header("X-session-ID", sessionId)
                .body("INVALID_BODY")
                .post("api/v1/paymentRequests")
                .then()
                .assertThat()
                .statusCode(405);
    }

    @Test
    public void paymentRequestFunctionalityTest() {
        int amount = 31415;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 2); // Set the date to two months into the future.

        String uniqueSessionId = Util.getSessionID();

        // Create a new payment request for a specific amount, to be fulfilled  by a new transaction.
        Util.createTestPaymentRequest("paymentRequestFunctionalityTest",
                calendar,
                "" + amount,
                1,
                uniqueSessionId
        );

        calendar.add(Calendar.MONTH, 1); // Ensure the transaction occurs after the payment request.

        // Create a new transaction, fulfilling the payment request.
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                amount,
                "paymentRequestFunctionalityTest",
                uniqueSessionId
        );

        // Check whether the payment request has been fulfilled.
        assertTrue(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/paymentRequests")
                .then()
                .assertThat()
                .body(matchesJsonSchema(PAYMENT_REQUEST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getBoolean("[0].filled"));
    }

    @Test
    public void paymentRequestFunctionalityDifferentAmountTest() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 2); // Set the date to two months into the future.

        String uniqueSessionId = Util.getSessionID();

        // Create a new payment request for a specific amount, to be fulfilled  by a new transaction.
        Util.createTestPaymentRequest("paymentRequestFunctionalityDifferentAmountTest",
                calendar,
                "123",
                1,
                uniqueSessionId
        );

        calendar.add(Calendar.MONTH, 1); // Ensure the transaction occurs after the payment request.

        // Create a new transaction, fulfilling the payment request.
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                456,
                "paymentRequestFunctionalityDifferentAmountTest",
                uniqueSessionId
        );

        // Check whether the payment request has been fulfilled.
        assertFalse(given()
                .header("X-session-ID", sessionId)
                .get("api/v1/paymentRequests")
                .then()
                .assertThat()
                .body(matchesJsonSchema(PAYMENT_REQUEST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getBoolean("[0].filled"));
    }

    @Test
    public void multiplePaymentRequestFunctionalityTest() {
        int amount = 31415;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 2); // Set the date to two months into the future.

        String uniqueSessionId = Util.getSessionID();

        // Create a new payment request for a specific amount, to be fulfilled  by a new transaction.
        Util.createTestPaymentRequest("multiplePaymentRequestFunctionalityTest",
                calendar,
                "" + amount,
                2,
                uniqueSessionId
        );

        calendar.add(Calendar.MONTH, 1); // Ensure the transaction occurs after the payment request.

        // Create a new transaction, going towards the payment request.
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                amount,
                "multiplePaymentRequestFunctionalityTest",
                uniqueSessionId
        );

        // Check whether the payment request has NOT been fulfilled yet.
        assertFalse(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/paymentRequests")
                .then()
                .assertThat()
                .body(matchesJsonSchema(PAYMENT_REQUEST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getBoolean("[0].filled"));

        // Create a new transaction, fulfilling the payment request.
        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                amount,
                "multiplePaymentRequestFunctionalityTest",
                uniqueSessionId
        );

        // Check whether the payment request has been fulfilled.
        assertTrue(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/paymentRequests")
                .then()
                .assertThat()
                .body(matchesJsonSchema(PAYMENT_REQUEST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getBoolean("[0].filled"));
    }

    @Test
    public void expiredPaymentRequestFunctionalityTest() {
        int amount = 31415;
        Calendar calendar = Calendar.getInstance();

        String uniqueSessionId = Util.getSessionID();

        // Create a new payment request for a specific amount, to be fulfilled  by a new transaction.
        Util.createTestPaymentRequest("expiredPaymentRequestFunctionalityTest",
                calendar,
                "" + amount,
                2,
                uniqueSessionId
        );

        // Check whether the payment request has NOT been fulfilled yet.
        assertFalse(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/paymentRequests")
                .then()
                .assertThat()
                .body(matchesJsonSchema(PAYMENT_REQUEST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getBoolean("[0].filled"));

        // Create a new transaction, matching the amount, but made AFTER the expiry date.
        calendar.add(Calendar.YEAR, 1);

        createTransaction(DATE_FORMAT.format(calendar.getTime()),
                amount,
                "multiplePaymentRequestFunctionalityTest",
                uniqueSessionId
        );

        // Check whether the payment request has still not been fulfilled.
        assertFalse(given()
                .header("X-session-ID", uniqueSessionId)
                .get("api/v1/paymentRequests")
                .then()
                .assertThat()
                .body(matchesJsonSchema(PAYMENT_REQUEST_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getBoolean("[0].filled"));
    }

    /**
     * Helper function to create a Transaction.
     *  @param date the date on which the Transaction was made
     * @param amount the amount corresponding to the Transaction
     * @param description the description of the Transaction, used for identifying
     * @param sessionId the session for which to create the Transaction
     */
    private static void createTransaction(String date, int amount, String description, String sessionId) {
        int transactionId = Util.createTestTransaction(date, amount, "NL39RABO0300065264",
                "deposit", description, sessionId);
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
