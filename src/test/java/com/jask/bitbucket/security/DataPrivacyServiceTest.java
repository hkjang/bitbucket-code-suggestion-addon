package com.jask.bitbucket.security;

import com.jask.bitbucket.config.PluginSettingsService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.*;

/**
 * DataPrivacyService 단위 테스트.
 */
public class DataPrivacyServiceTest {

    @Mock
    private PluginSettingsService settingsService;

    private DataPrivacyService privacyService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        privacyService = new DataPrivacyService(settingsService);
    }

    @Test
    public void testMaskEmail() {
        String input = "String email = \"user@example.com\";";
        String masked = privacyService.maskPii(input);
        assertFalse(masked.contains("user@example.com"));
        assertTrue(masked.contains("[EMAIL_MASKED]"));
    }

    @Test
    public void testMaskPhoneKorean() {
        String input = "String phone = \"010-1234-5678\";";
        String masked = privacyService.maskPii(input);
        assertFalse(masked.contains("010-1234-5678"));
        assertTrue(masked.contains("[PHONE_MASKED]"));
    }

    @Test
    public void testMaskCreditCard() {
        String input = "card = \"4111-1111-1111-1111\";";
        String masked = privacyService.maskPii(input);
        assertFalse(masked.contains("4111-1111-1111-1111"));
        assertTrue(masked.contains("[CARD_MASKED]"));
    }

    @Test
    public void testDetectPii_email() {
        String input = "contact@company.com in code";
        List<DataPrivacyService.PiiDetection> detections = privacyService.detectPii(input);
        assertFalse(detections.isEmpty());
        assertEquals("EMAIL", detections.get(0).getType());
    }

    @Test
    public void testDetectPii_noDetection() {
        String input = "int count = 42;\nString name = \"hello\";";
        List<DataPrivacyService.PiiDetection> detections = privacyService.detectPii(input);
        assertTrue(detections.isEmpty());
    }

    @Test
    public void testSanitizeForLlm() {
        String input = "password = \"secret123\"\nemail = \"user@test.com\"";
        String sanitized = privacyService.sanitizeForLlm(input);
        assertFalse(sanitized.contains("secret123"));
        assertFalse(sanitized.contains("user@test.com"));
    }

    @Test
    public void testNullInput() {
        assertNull(privacyService.maskPii(null));
        assertNull(privacyService.sanitizeForLlm(null));
        assertTrue(privacyService.detectPii(null).isEmpty());
    }

    @Test
    public void testEmptyInput() {
        assertEquals("", privacyService.maskPii(""));
        assertTrue(privacyService.detectPii("").isEmpty());
    }

    @Test
    public void testGetRetentionPolicy() {
        DataPrivacyService.DataRetentionPolicy policy = privacyService.getRetentionPolicy();
        assertEquals(90, policy.getSuggestionRetentionDays());
        assertEquals(365, policy.getAuditLogRetentionDays());
        assertFalse(policy.isStoreLlmPayloads());
    }

    @Test
    public void testGetPrivacyPolicyText() {
        String text = privacyService.getPrivacyPolicyText();
        assertNotNull(text);
        assertTrue(text.contains("개인정보 처리 방침"));
        assertTrue(text.contains("AES-256-GCM"));
    }
}
