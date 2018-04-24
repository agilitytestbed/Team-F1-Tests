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

import java.nio.file.Path;
import java.nio.file.Paths;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.post;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;

public class Util {

    private static final Path SESSION_SCHEMA_PATH = Paths.get("src/test/java/nl/utwente/ing/schemas/session.json");

    /**
     * Accesses the session API endpoint to generate a new session ID.
     *
     * @return a newly generated session ID
     */
    public static String getSessionID() {
        return post("api/v1/sessions")
                .then()
                .assertThat()
                .body(matchesJsonSchema(SESSION_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getString("id");
    }

    public static int createTestCategory(String name, String sessionId) {
        return given()
                .header("X-session-ID", sessionId)
                .body(String.format("{\"name\": \"%s\"}", name))
                .post("api/v1/categories")
                .then()
                .assertThat()
                .body(matchesJsonSchema(CategoryTests.CATEGORY_SCHEMA_PATH.toAbsolutePath().toUri()))
                .statusCode(201)
                .extract()
                .response()
                .getBody()
                .jsonPath()
                .getInt("id");
    }

    public static void deleteTestCategory(int id, String sessionId) {
        given()
                .header("X-session-ID", sessionId)
                .delete(String.format("api/v1/categories/%d", id));
    }
}
