package com.redhat.service.bridge.integration.tests.common;

import org.keycloak.representations.AccessTokenResponse;

import static io.restassured.RestAssured.given;

public class BridgeUtils {
    public static final String MANAGER_URL = Utils.getSystemProperty("event-bridge.manager.url");

    protected static final String USER_NAME = Utils.getSystemProperty("bridge.token.username");
    protected static final String PASSWORD = Utils.getSystemProperty("bridge.token.password");
    protected static final String CLIENT_ID = Utils.getSystemProperty("bridge.client.id");
    protected static final String CLIENT_SECRET = Utils.getSystemProperty("bridge.client.secret");

    protected static String token;
    protected static String keycloakURL = System.getProperty("keycloak.realm.url");

    public static String retrieveAccessToken() {
        if (token == null) {
            String env_token = System.getenv("OB_TOKEN");
            if (env_token != null) {
                token = env_token;
            } else if (keycloakURL != null && !keycloakURL.isEmpty()) {
                token = getAccessToken();
            } else {
                throw new RuntimeException("Environment variable token and keycloak.realm.url was not defined for token generation.");
            }
        }
        return token;
    }

    private static String getAccessToken() {
        return given().param("grant_type", "password")
                .param("username", USER_NAME)
                .param("password", PASSWORD)
                .param("client_id", CLIENT_ID)
                .param("client_secret", CLIENT_SECRET)
                .when()
                .post(keycloakURL + "/protocol/openid-connect/token")
                .as(AccessTokenResponse.class)
                .getToken();
    }
}
