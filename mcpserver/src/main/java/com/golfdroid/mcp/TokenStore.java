package com.golfdroid.mcp;

import java.security.SecureRandom;

/**
 * MCP auth token store.
 *
 * <p>Used by the Android app via {@link #get(android.content.Context)} which
 * reads/writes SecurePreferences (key {@code mcp_token}). Used by unit tests
 * via {@link #TokenStore(String)} which holds an in-memory token.
 *
 * <p>Token is exactly 6 alphanumeric characters.
 */
public class TokenStore {

    private static final String CHARSET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TOKEN_LEN = 6;
    private static final SecureRandom RND = new SecureRandom();

    private final String token;

    /** Test-only constructor holding an in-memory token. */
    public TokenStore(String token) {
        this.token = token;
    }

    /** Generate a fresh random 6-char token. */
    public static String generate() {
        StringBuilder sb = new StringBuilder(TOKEN_LEN);
        for (int i = 0; i < TOKEN_LEN; i++) {
            sb.append(CHARSET.charAt(RND.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    /** Current token (never null). */
    public String get() {
        return token;
    }
}
