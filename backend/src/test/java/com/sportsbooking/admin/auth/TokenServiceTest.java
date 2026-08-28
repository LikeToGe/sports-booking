package com.sportsbooking.admin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenServiceTest {
    private final TokenService tokenService = new TokenService();

    @Test
    void generatesIndependentUrlSafeTokensAndStableHashes() {
        String first = tokenService.generate();
        String second = tokenService.generate();

        assertNotEquals(first, second);
        assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(tokenService.hash(first), tokenService.hash(first));
        assertNotEquals(tokenService.hash(first), tokenService.hash(second));
    }
}
