package com.jask.bitbucket.security;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * EndpointValidator SSRF 방지 테스트.
 */
public class EndpointValidatorTest {

    @Test
    public void testValidHttpsEndpoint() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate("https://api.example.com/v1/chat", false);
        assertTrue(result.isValid());
    }

    @Test
    public void testValidLocalhostWhenAllowed() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate("http://localhost:11434/api/chat", true);
        assertTrue(result.isValid());
    }

    @Test
    public void testLocalhostBlockedWhenNotAllowed() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate("http://localhost:11434/api/chat", false);
        assertFalse(result.isValid());
    }

    @Test
    public void testInternalIpBlocked() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate("http://192.168.1.100:8080/api", false);
        assertFalse(result.isValid());
    }

    @Test
    public void testMetadataEndpointBlocked() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate("http://169.254.169.254/metadata", false);
        assertFalse(result.isValid());
    }

    @Test
    public void testNullUrl() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate(null, false);
        assertFalse(result.isValid());
    }

    @Test
    public void testEmptyUrl() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate("", false);
        assertFalse(result.isValid());
    }

    @Test
    public void testMalformedUrl() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate("not-a-url", false);
        assertFalse(result.isValid());
    }

    @Test
    public void testIsTlsEndpoint() {
        assertTrue(EndpointValidator.isTlsEndpoint("https://api.example.com"));
        assertFalse(EndpointValidator.isTlsEndpoint("http://api.example.com"));
    }

    @Test
    public void testFileProtocolBlocked() {
        EndpointValidator.ValidationResult result =
                EndpointValidator.validate("file:///etc/passwd", false);
        assertFalse(result.isValid());
    }
}
