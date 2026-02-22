/**
 * Jask Code Suggestion - PR 코드 제안 프론트엔드
 */
(function ($) {
    'use strict';

    var REST_BASE = AJS.contextPath() + '/rest/code-suggestion/1.0';
    var container = null;
    var currentFilter = 'all';
    var allSuggestions = [];
    var currentJobId = null;
    var pollTimer = null;

    /**
     * Initialize the plugin when the page loads.
     */
    function init() {
        container = $('#jask-code-suggestions');
        if (!container.length) return;

        var prId = container.data('pr-id');
        var repoId = container.data('repo-id');

        if (!prId || !repoId) return;

        bindEvents();
        loadSuggestions(repoId, prId);
    }

    /**
     * Get analysis options from category checkboxes.
     */
    function getAnalysisOptions() {
        return {
            checkSecurity: $('#jask-opt-security').is(':checked') !== false,
            checkPerformance: $('#jask-opt-performance').is(':checked') !== false,
            checkStyle: $('#jask-opt-style').is(':checked') !== false,
            checkBestPractice: $('#jask-opt-bestpractice').is(':checked') !== false,
            checkErrorHandling: $('#jask-opt-errorhandling').is(':checked') !== false
        };
    }

    /**
     * Bind UI event handlers.
     */
    function bindEvents() {
        // Re-analyze button
        $(document).on('click', '#jask-reanalyze-btn', function () {
            var prId = container.data('pr-id');
            var repoId = container.data('repo-id');
            reanalyze(repoId, prId);
        });

        // Toggle analysis options panel
        $(document).on('click', '#jask-options-toggle', function () {
            $('#jask-analysis-options').slideToggle(200);
            $(this).toggleClass('expanded');
        });

        // Filter buttons
        $(document).on('click', '.jask-filter-btn', function () {
            var filter = $(this).data('filter');
            currentFilter = filter;
            $('.jask-filter-btn').removeClass('active');
            $(this).addClass('active');
            renderSuggestions(allSuggestions);
        });

        // Accept suggestion
        $(document).on('click', '.jask-accept-btn', function () {
            var id = $(this).data('id');
            updateSuggestionStatus(id, 'ACCEPTED');
        });

        // Reject suggestion
        $(document).on('click', '.jask-reject-btn', function () {
            var id = $(this).data('id');
            updateSuggestionStatus(id, 'REJECTED');
        });

        // Dismiss suggestion
        $(document).on('click', '.jask-dismiss-btn', function () {
            var id = $(this).data('id');
            updateSuggestionStatus(id, 'DISMISSED');
        });

        // Toggle code diff
        $(document).on('click', '.jask-toggle-code', function () {
            var $codeBlock = $(this).closest('.jask-suggestion-item').find('.jask-code-diff');
            $codeBlock.slideToggle(200);
            $(this).toggleClass('expanded');
        });
    }

    /**
     * Load suggestions from REST API.
     */
    function loadSuggestions(repoId, prId) {
        showLoading(true);

        $.ajax({
            url: REST_BASE + '/suggestions/' + repoId + '/' + prId,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                showLoading(false);
                allSuggestions = data.suggestions || [];

                if (allSuggestions.length === 0) {
                    showEmpty(true);
                    return;
                }

                updateSummary(data.stats);
                renderSuggestions(allSuggestions);
                $('#jask-suggestions-summary').show();
                $('#jask-suggestions-list').show();
                $('#jask-suggestions-badge').text(allSuggestions.length).show();
            },
            error: function (xhr) {
                showLoading(false);
                showError('제안 목록을 불러오지 못했습니다: ' + (xhr.responseJSON ? xhr.responseJSON.error : xhr.statusText));
            }
        });
    }

    /**
     * Trigger re-analysis via async job.
     */
    function reanalyze(repoId, prId) {
        var $btn = $('#jask-reanalyze-btn');
        $btn.prop('disabled', true).text('분석 요청 중...');

        // Delete old suggestions first
        $.ajax({
            url: REST_BASE + '/suggestions/' + repoId + '/' + prId,
            type: 'DELETE',
            success: function () {
                // Submit async analysis job
                $.ajax({
                    url: REST_BASE + '/analyze',
                    type: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify({
                        pullRequestId: prId,
                        repositoryId: repoId,
                        options: getAnalysisOptions()
                    }),
                    success: function (data) {
                        currentJobId = data.jobId;
                        showJobProgress(0, 'QUEUED');

                        AJS.flag({
                            type: 'info',
                            title: 'AI 코드 분석',
                            body: '분석 잡이 큐에 등록되었습니다 (Job #' + data.jobId + ')',
                            close: 'auto'
                        });

                        // Start polling for job status
                        startJobPolling(data.jobId, repoId, prId);
                    },
                    error: function (xhr) {
                        resetReanalyzeButton();
                        AJS.flag({
                            type: 'error',
                            title: '오류',
                            body: '분석 요청에 실패했습니다: ' +
                                (xhr.responseJSON ? xhr.responseJSON.error : xhr.statusText),
                            close: 'auto'
                        });
                    }
                });
            },
            error: function () {
                resetReanalyzeButton();
                AJS.flag({
                    type: 'error',
                    title: '오류',
                    body: '기존 제안 삭제에 실패했습니다.',
                    close: 'auto'
                });
            }
        });
    }

    /**
     * Poll for async job status.
     */
    function startJobPolling(jobId, repoId, prId) {
        stopJobPolling();

        var pollCount = 0;
        var maxPolls = 120; // 최대 10분 (5초 간격)

        pollTimer = setInterval(function () {
            pollCount++;

            if (pollCount > maxPolls) {
                stopJobPolling();
                resetReanalyzeButton();
                AJS.flag({
                    type: 'warning',
                    title: 'AI 코드 분석',
                    body: '분석이 예상보다 오래 걸리고 있습니다. 나중에 페이지를 새로고침해 주세요.',
                    close: 'auto'
                });
                return;
            }

            $.ajax({
                url: REST_BASE + '/analyze/' + jobId,
                type: 'GET',
                dataType: 'json',
                success: function (status) {
                    showJobProgress(status.progress || 0, status.status);

                    if (status.status === 'COMPLETED') {
                        stopJobPolling();
                        resetReanalyzeButton();
                        hideJobProgress();
                        loadSuggestions(repoId, prId);

                        AJS.flag({
                            type: 'success',
                            title: 'AI 코드 분석',
                            body: '분석이 완료되었습니다. ' + (status.suggestionCount || 0) + '개의 제안이 생성되었습니다.',
                            close: 'auto'
                        });
                    } else if (status.status === 'FAILED') {
                        stopJobPolling();
                        resetReanalyzeButton();
                        hideJobProgress();

                        AJS.flag({
                            type: 'error',
                            title: 'AI 코드 분석',
                            body: '분석 실패: ' + (status.errorMessage || '알 수 없는 오류'),
                            close: 'auto'
                        });
                    } else if (status.status === 'CANCELLED') {
                        stopJobPolling();
                        resetReanalyzeButton();
                        hideJobProgress();

                        AJS.flag({
                            type: 'info',
                            title: 'AI 코드 분석',
                            body: '분석이 취소되었습니다.',
                            close: 'auto'
                        });
                    }
                },
                error: function () {
                    // 일시적 오류는 무시하고 계속 폴링
                }
            });
        }, 5000); // 5초 간격
    }

    /**
     * Stop job polling.
     */
    function stopJobPolling() {
        if (pollTimer) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
    }

    /**
     * Show job progress bar.
     */
    function showJobProgress(progress, status) {
        var $progress = $('#jask-job-progress');
        if (!$progress.length) {
            container.prepend(
                '<div id="jask-job-progress" class="jask-job-progress">' +
                '  <div class="jask-progress-header">' +
                '    <span class="jask-progress-label">AI 분석 진행 중...</span>' +
                '    <span class="jask-progress-status"></span>' +
                '    <button class="aui-button aui-button-subtle jask-cancel-job-btn" title="분석 취소">' +
                '      <span class="aui-icon aui-icon-small aui-iconfont-close-dialog"></span>' +
                '    </button>' +
                '  </div>' +
                '  <div class="aui-progress-indicator"><span class="aui-progress-indicator-value"></span></div>' +
                '</div>'
            );

            // Bind cancel button
            $(document).on('click', '.jask-cancel-job-btn', function () {
                if (currentJobId) {
                    cancelJob(currentJobId);
                }
            });
        }

        var statusLabels = {
            'QUEUED': '대기 중',
            'RUNNING': '분석 중 (' + progress + '%)',
            'COMPLETED': '완료',
            'FAILED': '실패',
            'CANCELLED': '취소됨'
        };

        $progress.show();
        $progress.find('.jask-progress-status').text(statusLabels[status] || status);
        $progress.find('.aui-progress-indicator-value')
            .css('width', Math.max(progress, 5) + '%');
    }

    /**
     * Hide job progress bar.
     */
    function hideJobProgress() {
        $('#jask-job-progress').fadeOut(300);
    }

    /**
     * Cancel an analysis job.
     */
    function cancelJob(jobId) {
        $.ajax({
            url: REST_BASE + '/analyze/' + jobId,
            type: 'DELETE',
            success: function () {
                stopJobPolling();
                resetReanalyzeButton();
                hideJobProgress();
            }
        });
    }

    /**
     * Reset re-analyze button to default state.
     */
    function resetReanalyzeButton() {
        $('#jask-reanalyze-btn').prop('disabled', false).html(
            '<span class="aui-icon aui-icon-small aui-iconfont-refresh"></span> 다시 분석'
        );
    }

    /**
     * Update suggestion status via REST API.
     */
    function updateSuggestionStatus(suggestionId, status) {
        $.ajax({
            url: REST_BASE + '/suggestions/' + suggestionId + '/status',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify({
                status: status,
                resolvedBy: AJS.params.remoteUser || 'unknown'
            }),
            success: function (data) {
                // Update local data
                for (var i = 0; i < allSuggestions.length; i++) {
                    if (allSuggestions[i].id === suggestionId) {
                        allSuggestions[i].status = status;
                        break;
                    }
                }

                // Update UI
                var $item = $('.jask-suggestion-item[data-id="' + suggestionId + '"]');
                $item.addClass('jask-status-' + status.toLowerCase());
                $item.find('.jask-status-badge').text(getStatusLabel(status));

                var statusMsg = {
                    'ACCEPTED': '제안을 적용했습니다.',
                    'REJECTED': '제안을 거부했습니다.',
                    'DISMISSED': '제안을 무시했습니다.'
                };

                AJS.flag({
                    type: status === 'ACCEPTED' ? 'success' : 'info',
                    title: '코드 제안',
                    body: statusMsg[status] || '상태가 업데이트되었습니다.',
                    close: 'auto'
                });
            },
            error: function () {
                AJS.flag({
                    type: 'error',
                    title: '오류',
                    body: '제안 상태 업데이트에 실패했습니다.',
                    close: 'auto'
                });
            }
        });
    }

    /**
     * Render suggestions list.
     */
    function renderSuggestions(suggestions) {
        var $list = $('#jask-suggestions-list');
        $list.empty();

        var filtered = filterSuggestions(suggestions);

        if (filtered.length === 0) {
            $list.html('<div class="jask-no-results">필터 조건에 맞는 제안이 없습니다.</div>');
            return;
        }

        filtered.forEach(function (suggestion) {
            $list.append(buildSuggestionCard(suggestion));
        });
    }

    /**
     * Filter suggestions by current filter.
     */
    function filterSuggestions(suggestions) {
        if (currentFilter === 'all') return suggestions;

        return suggestions.filter(function (s) {
            if (currentFilter === 'PENDING') return s.status === 'PENDING';
            return s.severity === currentFilter;
        });
    }

    /**
     * Build a suggestion card HTML element.
     */
    function buildSuggestionCard(suggestion) {
        var severityClass = 'jask-severity-' + (suggestion.severity || 'info').toLowerCase();
        var statusClass = 'jask-status-' + (suggestion.status || 'pending').toLowerCase();

        var html = '<div class="jask-suggestion-item ' + severityClass + ' ' + statusClass + '" data-id="' + suggestion.id + '">';

        // Header
        html += '<div class="jask-suggestion-header">';
        html += '<span class="jask-severity-badge ' + severityClass + '">' + getSeverityLabel(suggestion.severity) + '</span>';
        html += '<span class="jask-category-badge">' + getCategoryLabel(suggestion.category) + '</span>';
        html += '<span class="jask-file-path">' + escapeHtml(suggestion.filePath || '') + '</span>';
        if (suggestion.startLine > 0) {
            html += '<span class="jask-line-range">L' + suggestion.startLine;
            if (suggestion.endLine > suggestion.startLine) {
                html += '-' + suggestion.endLine;
            }
            html += '</span>';
        }
        html += '<span class="jask-confidence">' + Math.round((suggestion.confidence || 0) * 100) + '% 확신</span>';
        html += '<span class="jask-status-badge">' + getStatusLabel(suggestion.status) + '</span>';
        html += '</div>';

        // Explanation
        html += '<div class="jask-suggestion-body">';
        html += '<p class="jask-explanation">' + escapeHtml(suggestion.explanation || '') + '</p>';

        // Code diff toggle
        if (suggestion.originalCode || suggestion.suggestedCode) {
            html += '<button class="aui-button aui-button-link jask-toggle-code">코드 변경 보기</button>';
            html += '<div class="jask-code-diff" style="display:none;">';

            if (suggestion.originalCode) {
                html += '<div class="jask-code-block jask-code-original">';
                html += '<div class="jask-code-label">기존 코드</div>';
                html += '<pre><code>' + escapeHtml(suggestion.originalCode) + '</code></pre>';
                html += '</div>';
            }

            if (suggestion.suggestedCode) {
                html += '<div class="jask-code-block jask-code-suggested">';
                html += '<div class="jask-code-label">개선 코드</div>';
                html += '<pre><code>' + escapeHtml(suggestion.suggestedCode) + '</code></pre>';
                html += '</div>';
            }

            html += '</div>';
        }

        // Actions
        if (suggestion.status === 'PENDING') {
            html += '<div class="jask-suggestion-actions">';
            html += '<button class="aui-button aui-button-primary jask-accept-btn" data-id="' + suggestion.id + '">적용</button>';
            html += '<button class="aui-button jask-reject-btn" data-id="' + suggestion.id + '">거부</button>';
            html += '<button class="aui-button aui-button-subtle jask-dismiss-btn" data-id="' + suggestion.id + '">무시</button>';
            html += '</div>';
        }

        html += '</div>';
        html += '</div>';

        return html;
    }

    /**
     * Update summary panel.
     */
    function updateSummary(stats) {
        if (!stats) return;

        $('#jask-critical-count').text(stats.critical || 0);
        $('#jask-warning-count').text(stats.warning || 0);
        $('#jask-info-count').text((stats.total || 0) - (stats.critical || 0) - (stats.warning || 0));
        $('#jask-hint-count').text(0);

        // Calculate quality score
        var score = 100 - (stats.critical || 0) * 20 - (stats.warning || 0) * 5;
        score = Math.max(0, Math.min(100, score));
        var $scoreEl = $('#jask-quality-score');
        $scoreEl.text(score);

        if (score >= 80) {
            $scoreEl.addClass('jask-score-good');
        } else if (score >= 50) {
            $scoreEl.addClass('jask-score-warning');
        } else {
            $scoreEl.addClass('jask-score-critical');
        }
    }

    // --- Helper Functions ---

    function getSeverityLabel(severity) {
        var labels = {
            'CRITICAL': '심각',
            'WARNING': '경고',
            'INFO': '정보',
            'HINT': '참고'
        };
        return labels[severity] || severity;
    }

    function getCategoryLabel(category) {
        var labels = {
            'SECURITY': '보안',
            'PERFORMANCE': '성능',
            'BUG_RISK': '버그 위험',
            'CODE_STYLE': '코드 스타일',
            'BEST_PRACTICE': '모범 사례',
            'DUPLICATION': '중복 코드',
            'COMPLEXITY': '복잡도',
            'ERROR_HANDLING': '에러 처리'
        };
        return labels[category] || category;
    }

    function getStatusLabel(status) {
        var labels = {
            'PENDING': '미처리',
            'ACCEPTED': '적용됨',
            'REJECTED': '거부됨',
            'DISMISSED': '무시됨'
        };
        return labels[status] || status;
    }

    function showLoading(show) {
        $('#jask-suggestions-loading').toggle(show);
    }

    function showEmpty(show) {
        $('#jask-suggestions-empty').toggle(show);
    }

    function showError(message) {
        $('#jask-error-message').text(message);
        $('#jask-suggestions-error').show();
    }

    function escapeHtml(text) {
        var map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
        return text.replace(/[&<>"']/g, function (m) { return map[m]; });
    }

    // Initialize on page ready
    $(document).ready(function () {
        // Delay init to ensure Bitbucket PR page is fully loaded
        setTimeout(init, 500);
    });

})(AJS.$);
