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
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;
import static org.junit.Assert.assertEquals;

public class BalanceHistoryTests {

    private static final Path BALANCE_HISTORY_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/balancehistory.json");
    private static final String TEST_TRANSACTION_FORMAT = "{" +
            "\"date\": \"%s\", " +
            "\"amount\": %d, " +
            "\"externalIBAN\": \"NL39RABO0300065264\", " +
            "\"type\": \"%s\", " +
            "\"description\":\"University of Twente\"" +
            "}";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private static String sessionId;
    // A list containing all transactions used for testing, which should be deleted after running all tests.
    // TODO: Delete all transactions in testTransactions.
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
     * Performs a GET request on the balance history endpoint.
     *
     * This test uses a valid session ID and tests whether the output is formatted according to the specification.
     */
    @Test
    public void validSessionBalanceHistoryGetTest() {
        given()
                .header("X-session-ID", sessionId)
                .get("api/v1/balance/history")
                .then()
                .assertThat()
                .body(matchesJsonSchema(BALANCE_HISTORY_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200);
    }

    /**
     * Performs a GET request on the balance history endpoint.
     *
     * This test uses an invalid session ID and tests whether the resulting status code is 401 Unauthorized.
     */
    @Test
    public void invalidSessionBalanceHistoryGetTest() {
        given()
                .get("api/v1/balance/history")
                .then()
                .assertThat()
                .statusCode(401);
    }

    /**
     * Performs a GET request on the balance history endpoint.
     *
     * This test uses a valid session ID and invalid query parameters and
     * tests whether the resulting status code is 405 Method Not Allowed.
     */
    @Test
    public void validSessionInvalidParamsBalanceHistoryGetTest() {
        given()
                .header("X-session-ID", sessionId)
                .get("api/v1/balance/history?interval=invalid")
                .then()
                .assertThat()
                .statusCode(405);
    }

    /**
     * Checks whether the intervals are created correctly by checking whether the open/close/high/low/volume fields
     * have been set properly after inserting test data.
     */
    @Test
    public void correctValuesBalanceHistoryGetTest() {
        // A new session is used so that transactions added by other tests do not influence this test.
        String session = Util.getSessionID();

        Calendar calendar = Calendar.getInstance();
        // First deposit 500 as a starting amount, months before the interval to check.
        calendar.add(Calendar.MONTH, -5);
        createTransaction(session, DATE_FORMAT.format(calendar.getTime()), 500, "deposit");
        // Then create two transactions to create some data for the last month.
        // The order is reversed, so chronologically we first have a deposit and then a withdrawal.
        calendar.add(Calendar.MONTH, 5);
        createTransaction(session, DATE_FORMAT.format(calendar.getTime()), 100, "withdrawal");
        calendar.add(Calendar.DAY_OF_WEEK, -1);
        createTransaction(session, DATE_FORMAT.format(calendar.getTime()), 300, "deposit");

        JsonPath path = given()
                .header("X-session-ID", session)
                .get("api/v1/balance/history")
                .then()
                .assertThat()
                .body(matchesJsonSchema(BALANCE_HISTORY_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath();

        assertEquals(500, path.getInt("[0].open"));
        assertEquals(700, path.getInt("[0].close"));
        assertEquals(800, path.getInt("[0].high"));
        assertEquals(500, path.getInt("[0].low"));
        assertEquals(400, path.getInt("[0].volume"));
    }

    /**
     * Checks whether intervals are created correctly after inserting test data over a span of multiple months.
     */
    @Test
    public void  multipleIntervalsBalanceHistoryGetTest() {
        // A new session is used so that transactions added by other tests do not influence this test.
        String session = Util.getSessionID();

        Calendar calendar = Calendar.getInstance();
        // We create a batch of transactions, each one month apart. The chronological order is the reverse of
        // the order in which they are created here.
        createTransaction(session, DATE_FORMAT.format(calendar.getTime()), 300, "withdrawal");// 300
        calendar.add(Calendar.MONTH, -1);
        createTransaction(session, DATE_FORMAT.format(calendar.getTime()), 400, "deposit"); // 600
        calendar.add(Calendar.MONTH, -1);
        createTransaction(session, DATE_FORMAT.format(calendar.getTime()), 200, "withdrawal");// 200
        calendar.add(Calendar.MONTH, -1);
        createTransaction(session, DATE_FORMAT.format(calendar.getTime()), 100, "withdrawal");// 400
        calendar.add(Calendar.MONTH, -1);
        createTransaction(session, DATE_FORMAT.format(calendar.getTime()), 500, "deposit"); // 500

        JsonPath path = given()
                .header("X-session-ID", session)
                .get("api/v1/balance/history?intervals=5")
                .then()
                .assertThat()
                .body(matchesJsonSchema(BALANCE_HISTORY_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(200)
                .extract()
                .response()
                .getBody()
                .jsonPath();

        // Pick some arbitrary elements from the result and make sure they are correct.
        assertEquals(600, path.getInt("[0].open"));
        assertEquals(300, path.getInt("[0].close"));
        assertEquals(600, path.getInt("[1].high"));
        assertEquals(200, path.getInt("[1].low"));
        assertEquals(200, path.getInt("[2].volume"));
        assertEquals(100, path.getInt("[3].volume"));
    }

    // TODO: Different interval timings? day/week/month ..

    /**
     * Helper function to create a Transaction.
     *
     * @param sessionId the session for which to create the Transaction
     * @param date the date on which the Transaction was made
     * @param amount the amount corresponding to the Transaction
     * @param type the type of the Transaction, either withdrawal or deposit
     */
    private static void createTransaction(String sessionId, String date, int amount, String type) {
        int transactionId = given()
                .header("X-session-ID", sessionId)
                .body(String.format(TEST_TRANSACTION_FORMAT, date, amount, type))
                .post("api/v1/transactions")
                .then()
                .assertThat()
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");
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
