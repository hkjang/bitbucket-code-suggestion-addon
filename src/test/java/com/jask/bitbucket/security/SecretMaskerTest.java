package com.jask.bitbucket.security;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SecretMasker 단위 테스트.
 */
public class SecretMaskerTest {

    @Test
    public void testMaskApiKey() {
        String input = "Authorization: Bearer sk-abc123def456xyz789";
        String masked = SecretMasker.mask(input);
        assertFalse(masked.contains("sk-abc123def456xyz789"));
        assertTrue(masked.contains("***MASKED***") || masked.contains("***"));
    }

    @Test
    public void testMaskPassword() {
        String input = "password = \"mySecretPassword123\"";
        String masked = SecretMasker.mask(input);
        assertFalse(masked.contains("mySecretPassword123"));
    }

    @Test
    public void testMaskAwsKey() {
        String input = "aws_secret_access_key = AKIAIOSFODNN7EXAMPLE";
        String masked = SecretMasker.mask(input);
        assertFalse(masked.contains("AKIAIOSFODNN7EXAMPLE"));
    }

    @Test
    public void testNoMaskForNormalCode() {
        String input = "int count = 42;\nString name = \"hello\";";
        String masked = SecretMasker.mask(input);
        assertEquals(input, masked);
    }

    @Test
    public void testNullInput() {
        assertNull(SecretMasker.mask(null));
    }

    @Test
    public void testEmptyInput() {
        assertEquals("", SecretMasker.mask(""));
    }

    @Test
    public void testMaskJdbcUrl() {
        String input = "jdbc:mysql://user:password123@localhost:3306/db";
        String masked = SecretMasker.mask(input);
        assertFalse(masked.contains("password123"));
    }

    @Test
    public void testMaskPrivateKey() {
        String input = "-----BEGIN RSA PRIVATE KEY-----\nMIIEpQIBAAKCAQEA\n-----END RSA PRIVATE KEY-----";
        String masked = SecretMasker.mask(input);
        assertFalse(masked.contains("MIIEpQIBAAKCAQEA"));
    }
}
