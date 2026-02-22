package com.jask.bitbucket.service;

/**
 * Service for creating Bitbucket PR comments from code suggestions.
 * Uses Bitbucket Server REST API to create inline comments on the PR diff.
 */
public interface BitbucketCommentService {

    /**
     * Create a general PR comment with suggestion content.
     *
     * @param projectKey  project key
     * @param repoSlug    repository slug
     * @param prId        pull request ID
     * @param commentText comment text (Markdown)
     * @return the created comment ID, or -1 on failure
     */
    long createPrComment(String projectKey, String repoSlug, long prId, String commentText);

    /**
     * Create an inline comment on a specific file and line in the PR.
     *
     * @param projectKey  project key
     * @param repoSlug    repository slug
     * @param prId        pull request ID
     * @param filePath    file path in the repository
     * @param line        line number to attach the comment
     * @param commentText comment text (Markdown)
     * @return the created comment ID, or -1 on failure
     */
    long createInlineComment(String projectKey, String repoSlug, long prId,
                              String filePath, int line, String commentText);

    /**
     * Format a code suggestion into a Markdown comment string.
     *
     * @param suggestion the suggestion data
     * @return formatted Markdown string
     */
    String formatSuggestionAsComment(SuggestionCommentData suggestion);

    /**
     * DTO for suggestion data needed to create a comment.
     */
    class SuggestionCommentData {
        private String filePath;
        private int startLine;
        private int endLine;
        private String originalCode;
        private String suggestedCode;
        private String explanation;
        private String severity;
        private String category;
        private double confidence;

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public int getStartLine() { return startLine; }
        public void setStartLine(int startLine) { this.startLine = startLine; }

        public int getEndLine() { return endLine; }
        public void setEndLine(int endLine) { this.endLine = endLine; }

        public String getOriginalCode() { return originalCode; }
        public void setOriginalCode(String originalCode) { this.originalCode = originalCode; }

        public String getSuggestedCode() { return suggestedCode; }
        public void setSuggestedCode(String suggestedCode) { this.suggestedCode = suggestedCode; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }
}
