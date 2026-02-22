package com.jask.bitbucket.rest;

import com.jask.bitbucket.model.AnalysisResponse;
import com.jask.bitbucket.model.CodeSuggestion;
import com.jask.bitbucket.security.PermissionCheckService;
import com.jask.bitbucket.service.AnalysisJobService;
import com.jask.bitbucket.service.CodeAnalysisService;
import com.jask.bitbucket.service.SuggestionService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CodeSuggestionResourceTest {

    @Mock
    private CodeAnalysisService codeAnalysisService;

    @Mock
    private SuggestionService suggestionService;

    @Mock
    private AnalysisJobService analysisJobService;

    @Mock
    private PermissionCheckService permissionCheck;

    private CodeSuggestionResource resource;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        resource = new CodeSuggestionResource(
                codeAnalysisService, suggestionService,
                analysisJobService, permissionCheck);
    }

    @Test
    public void testGetSuggestions_success() {
        CodeSuggestion suggestion = new CodeSuggestion();
        suggestion.setId(1);
        suggestion.setSeverity(CodeSuggestion.Severity.WARNING);
        suggestion.setExplanation("Test suggestion");

        when(suggestionService.getSuggestions(1L, 100))
                .thenReturn(Arrays.asList(suggestion));
        when(suggestionService.getStats(1L, 100))
                .thenReturn(new SuggestionService.SuggestionStats());

        Response response = resource.getSuggestions(100, 1L);

        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    public void testGetSuggestions_empty() {
        when(suggestionService.getSuggestions(1L, 100))
                .thenReturn(Collections.emptyList());
        when(suggestionService.getStats(1L, 100))
                .thenReturn(new SuggestionService.SuggestionStats());

        Response response = resource.getSuggestions(100, 1L);

        assertEquals(200, response.getStatus());
    }

    @Test
    public void testUpdateSuggestionStatus_invalidStatus() {
        String requestBody = "{\"status\": \"INVALID\"}";
        Response response = resource.updateSuggestionStatus(1L, requestBody);

        assertEquals(400, response.getStatus());
    }

    @Test
    public void testUpdateSuggestionStatus_missingStatus() {
        String requestBody = "{}";
        Response response = resource.updateSuggestionStatus(1L, requestBody);

        assertEquals(400, response.getStatus());
    }

    @Test
    public void testDeleteSuggestions() {
        doNothing().when(suggestionService).deleteSuggestions(1L, 100);

        Response response = resource.deleteSuggestions(100, 1L);

        assertEquals(200, response.getStatus());
        verify(suggestionService).deleteSuggestions(1L, 100);
    }

    @Test
    public void testGetStats() {
        SuggestionService.SuggestionStats stats = new SuggestionService.SuggestionStats();
        stats.setTotal(5);
        stats.setCritical(1);
        stats.setWarning(2);

        when(suggestionService.getStats(1L, 100)).thenReturn(stats);

        Response response = resource.getStats(100, 1L);

        assertEquals(200, response.getStatus());
    }

    @Test
    public void testAnalyzeCode_missingParams() {
        String requestBody = "{\"pullRequestId\": 0}";
        Response response = resource.analyzeCode(requestBody);

        assertEquals(400, response.getStatus());
    }

    @Test
    public void testAnalyzeCode_asyncSubmit() {
        when(analysisJobService.submitJob(
                eq(1L), eq(100), anyString(), anyString(), anyString(), any()))
                .thenReturn(42L);

        String requestBody = "{\"pullRequestId\": 1, \"repositoryId\": 100, " +
                "\"projectKey\": \"PROJ\", \"repositorySlug\": \"repo\"}";
        Response response = resource.analyzeCode(requestBody);

        assertEquals(202, response.getStatus());
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity().toString().contains("42"));
    }

    @Test
    public void testGetJobStatus_found() {
        AnalysisJobService.JobStatus status = new AnalysisJobService.JobStatus();
        status.setJobId(42);
        status.setStatus("RUNNING");
        status.setProgress(50);

        when(analysisJobService.getJobStatus(42L)).thenReturn(status);

        Response response = resource.getJobStatus(42L);

        assertEquals(200, response.getStatus());
    }

    @Test
    public void testGetJobStatus_notFound() {
        when(analysisJobService.getJobStatus(999L)).thenReturn(null);

        Response response = resource.getJobStatus(999L);

        assertEquals(404, response.getStatus());
    }

    @Test
    public void testCancelJob_success() {
        when(analysisJobService.cancelJob(42L, null)).thenReturn(true);

        Response response = resource.cancelJob(42L);

        assertEquals(200, response.getStatus());
    }

    @Test
    public void testGetSuggestionsForFile_missingPath() {
        Response response = resource.getSuggestionsForFile(100, 1L, null);

        assertEquals(400, response.getStatus());
    }

    @Test
    public void testGetSuggestionsForFile_success() {
        when(suggestionService.getSuggestionsForFile(1L, 100, "src/Main.java"))
                .thenReturn(Collections.emptyList());

        Response response = resource.getSuggestionsForFile(100, 1L, "src/Main.java");

        assertEquals(200, response.getStatus());
    }
}
