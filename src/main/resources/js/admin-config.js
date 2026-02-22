/**
 * Jask Code Suggestion - Admin Configuration Page
 *
 * 요건 19: UI 일관성 — AUI 컴포넌트 기반 SPA 관리 화면
 * 좌측 네비게이션 + 우측 상세 패널 구조
 */
(function ($) {
    'use strict';

    var BASE_URL = AJS.contextPath() + '/rest/code-suggestion/1.0/admin';
    var currentSection = 'GLOBAL_SETTINGS';

    // =========================================================================
    // 초기화
    // =========================================================================

    $(document).ready(function () {
        if (!$('.jask-admin-config').length) return;
        console.log('Jask Admin Config initialized');

        initNavigation();
        loadNavigation();
        loadSection(currentSection);
    });

    function initNavigation() {
        $(document).on('click', '.jask-nav-item', function () {
            var section = $(this).data('section');
            if (section) {
                $('.jask-nav-item').removeClass('active');
                $(this).addClass('active');
                loadSection(section);
            }
        });
    }

    function loadNavigation() {
        $.getJSON(BASE_URL + '/navigation').done(function (data) {
            if (data.pendingApprovals > 0) {
                $('#jask-pending-count').text(data.pendingApprovals);
                $('#jask-pending-badge').show();
            }
            if (data.unreadAlerts > 0) {
                $('#jask-alert-count').text(data.unreadAlerts);
                $('#jask-alert-badge').show();
            }
            // 접근 불가 섹션 비활성화
            if (data.sections) {
                data.sections.forEach(function (s) {
                    if (!s.accessible) {
                        $('.jask-nav-item[data-section="' + s.id + '"]').addClass('disabled');
                    }
                });
            }
        });
    }

    // =========================================================================
    // 섹션 로딩
    // =========================================================================

    function loadSection(section) {
        currentSection = section;
        var $content = $('#jask-admin-content');
        $content.html('<div class="jask-loading-indicator"><aui-spinner size="large"></aui-spinner></div>');

        switch (section) {
            case 'GLOBAL_SETTINGS': loadGlobalSettings(); break;
            case 'ENGINE_CONNECTION': loadEngineProfiles(); break;
            case 'SECURITY_MASKING': loadMaskingRules(); break;
            case 'PROJECT_POLICY': loadProjectPolicy(); break;
            case 'USAGE_COST': loadUsageCost(); break;
            case 'AUDIT_LOG': loadAuditLog(); break;
            case 'DIAGNOSTICS': loadDiagnostics(); break;
            case 'BACKUP': loadBackups(); break;
            case 'RBAC': loadRbac(); break;
            default: $content.html('<p>알 수 없는 섹션입니다.</p>');
        }
    }

    // =========================================================================
    // 전역 설정 (요건 3)
    // =========================================================================

    function loadGlobalSettings() {
        $.getJSON(BASE_URL + '/settings').done(function (data) {
            var html = '<div class="jask-section">' +
                '<h2><span class="aui-icon aui-icon-small aui-iconfont-configure"></span> 전역 설정</h2>' +
                '<form id="jask-global-form" class="aui">' +
                '<div class="jask-form-grid">' +
                formGroup('LLM 엔드포인트', '<input class="text long-field" type="url" id="cfg-endpoint" value="' + esc(data.llmEndpoint) + '" placeholder="http://localhost:11434">') +
                formGroup('기본 모델', '<input class="text medium-field" type="text" id="cfg-model" value="' + esc(data.llmModel) + '" placeholder="codellama:13b">') +
                formGroup('Temperature', '<input class="text short-field" type="number" id="cfg-temperature" value="' + data.llmTemperature + '" min="0" max="2" step="0.1">') +
                formGroup('Max Tokens', '<input class="text short-field" type="number" id="cfg-max-tokens" value="' + data.llmMaxTokens + '" min="100" max="8192">') +
                formGroup('API 키', '<input class="text medium-field" type="password" id="cfg-api-key" placeholder="' + (data.llmHasApiKey ? '(설정됨)' : '(미설정)') + '">') +
                '</div>' +
                '<h3>분석 설정</h3>' +
                '<div class="jask-form-grid">' +
                formCheckbox('자동 분석 활성화', 'cfg-auto-analysis', data.autoAnalysisEnabled) +
                formCheckbox('머지 체크 활성화', 'cfg-merge-check', data.mergeCheckEnabled) +
                formGroup('머지 차단 임계 Critical 수', '<input class="text short-field" type="number" id="cfg-merge-max" value="' + data.mergeCheckMaxCritical + '" min="0">') +
                formGroup('최소 신뢰도 임계값', '<input class="text short-field" type="number" id="cfg-confidence" value="' + data.minConfidenceThreshold + '" min="0" max="1" step="0.05">') +
                '</div>' +
                '<h3>파일 설정</h3>' +
                '<div class="jask-form-grid">' +
                formGroup('제외 파일 패턴', '<input class="text long-field" type="text" id="cfg-excluded" value="' + esc(data.excludedFilePatterns) + '">') +
                formGroup('지원 언어', '<input class="text long-field" type="text" id="cfg-languages" value="' + esc(data.supportedLanguages) + '">') +
                formGroup('최대 파일 수', '<input class="text short-field" type="number" id="cfg-max-files" value="' + data.maxFilesPerAnalysis + '">') +
                formGroup('최대 파일 크기 (KB)', '<input class="text short-field" type="number" id="cfg-max-size" value="' + data.maxFileSizeKb + '">') +
                '</div>' +
                '<div class="jask-form-actions">' +
                '<button class="aui-button aui-button-primary" type="submit">설정 저장</button>' +
                '<button class="aui-button" type="button" id="btn-test-conn">연결 테스트</button>' +
                '<button class="aui-button" type="button" id="btn-load-templates">템플릿 적용</button>' +
                '</div>' +
                '</form>' +
                '<div id="jask-templates-panel" style="display:none"></div>' +
                '</div>';
            $('#jask-admin-content').html(html);
            bindGlobalSettingsEvents();
        }).fail(function () { showError('전역 설정 로드 실패'); });
    }

    function bindGlobalSettingsEvents() {
        $('#jask-global-form').on('submit', function (e) {
            e.preventDefault();
            var settings = {
                llmEndpoint: $('#cfg-endpoint').val(),
                llmModel: $('#cfg-model').val(),
                llmTemperature: parseFloat($('#cfg-temperature').val()),
                llmMaxTokens: parseInt($('#cfg-max-tokens').val()),
                autoAnalysisEnabled: $('#cfg-auto-analysis').is(':checked'),
                mergeCheckEnabled: $('#cfg-merge-check').is(':checked'),
                mergeCheckMaxCritical: parseInt($('#cfg-merge-max').val()),
                minConfidenceThreshold: parseFloat($('#cfg-confidence').val()),
                excludedFilePatterns: $('#cfg-excluded').val(),
                supportedLanguages: $('#cfg-languages').val(),
                maxFilesPerAnalysis: parseInt($('#cfg-max-files').val()),
                maxFileSizeKb: parseInt($('#cfg-max-size').val())
            };
            var apiKey = $('#cfg-api-key').val();
            if (apiKey) settings.llmApiKey = apiKey;

            ajaxPut(BASE_URL + '/settings', settings, '설정이 저장되었습니다.');
        });

        $('#btn-test-conn').on('click', function () {
            var $btn = $(this).prop('disabled', true).text('테스트 중...');
            $.post(BASE_URL + '/test-connection').done(function (data) {
                showFlag(data.success ? 'success' : 'warning', data.message);
            }).always(function () { $btn.prop('disabled', false).text('연결 테스트'); });
        });

        $('#btn-load-templates').on('click', function () {
            var $panel = $('#jask-templates-panel');
            if ($panel.is(':visible')) { $panel.hide(); return; }
            $.getJSON(BASE_URL + '/policy-templates').done(function (templates) {
                var html = '<h3>정책 템플릿</h3><div class="jask-templates-grid">';
                templates.forEach(function (t) {
                    html += '<div class="jask-template-card" data-id="' + t.id + '">' +
                        '<h4>' + esc(t.name) + '</h4>' +
                        '<p>' + esc(t.description) + '</p>' +
                        '<button class="aui-button aui-button-link btn-apply-template" data-id="' + t.id + '">적용</button>' +
                        '</div>';
                });
                html += '</div>';
                $panel.html(html).show();
            });
        });

        $(document).on('click', '.btn-apply-template', function () {
            var id = $(this).data('id');
            $.getJSON(BASE_URL + '/policy-templates/' + id).done(function (t) {
                if (t.settings) {
                    if (t.settings.autoAnalysisEnabled !== undefined) $('#cfg-auto-analysis').prop('checked', t.settings.autoAnalysisEnabled);
                    if (t.settings.mergeCheckEnabled !== undefined) $('#cfg-merge-check').prop('checked', t.settings.mergeCheckEnabled);
                    if (t.settings.minConfidenceThreshold !== undefined) $('#cfg-confidence').val(t.settings.minConfidenceThreshold);
                    if (t.settings.maxFilesPerAnalysis !== undefined) $('#cfg-max-files').val(t.settings.maxFilesPerAnalysis);
                    if (t.settings.maxFileSizeKb !== undefined) $('#cfg-max-size').val(t.settings.maxFileSizeKb);
                    if (t.settings.excludedFilePatterns) $('#cfg-excluded').val(t.settings.excludedFilePatterns);
                    if (t.settings.supportedLanguages) $('#cfg-languages').val(t.settings.supportedLanguages);
                    showFlag('success', '템플릿 "' + t.name + '" 적용됨 (저장 필요)');
                }
            });
        });
    }

    // =========================================================================
    // 엔진 프로파일 (요건 5, 6, 7)
    // =========================================================================

    function loadEngineProfiles() {
        $.getJSON(BASE_URL + '/engines').done(function (profiles) {
            var html = '<div class="jask-section">' +
                '<h2><span class="aui-icon aui-icon-small aui-iconfont-link"></span> 엔진 프로파일 관리</h2>' +
                '<p>LLM 엔진 연결을 관리합니다. 여러 엔진을 등록하고 우선순위를 설정할 수 있습니다.</p>' +
                '<button class="aui-button aui-button-primary" id="btn-add-engine">+ 프로파일 추가</button>' +
                '<div id="jask-engines-list" class="jask-card-list">';

            profiles.forEach(function (p) {
                var statusClass = p.lastTestResult === 'SUCCESS' ? 'success' :
                    (p.lastTestResult === 'FAILURE' ? 'error' : 'default');
                html += '<div class="jask-engine-card" data-id="' + p.id + '">' +
                    '<div class="jask-card-header">' +
                    '<h3>' + esc(p.profileName) +
                    (p.defaultProfile ? ' <span class="aui-lozenge aui-lozenge-success">기본</span>' : '') +
                    (p.enabled ? '' : ' <span class="aui-lozenge">비활성</span>') +
                    '</h3>' +
                    '<span class="aui-lozenge aui-lozenge-' + statusClass + '">' + (p.lastTestResult || 'NEVER_TESTED') + '</span>' +
                    '</div>' +
                    '<div class="jask-card-body">' +
                    '<dl><dt>유형</dt><dd>' + esc(p.engineType) + '</dd>' +
                    '<dt>엔드포인트</dt><dd>' + esc(p.endpointUrl) + '</dd>' +
                    '<dt>모델</dt><dd>' + esc(p.defaultModel) + '</dd>' +
                    '<dt>우선순위</dt><dd>' + p.priority + '</dd>' +
                    '<dt>API 키</dt><dd>' + (p.hasApiKey ? '설정됨' : '미설정') + '</dd>' +
                    (p.lastTestLatencyMs > 0 ? '<dt>응답 시간</dt><dd>' + p.lastTestLatencyMs + 'ms</dd>' : '') +
                    '</dl></div>' +
                    '<div class="jask-card-actions">' +
                    '<button class="aui-button btn-test-engine" data-id="' + p.id + '">연결 테스트</button>' +
                    '<button class="aui-button btn-edit-engine" data-id="' + p.id + '">편집</button>' +
                    (!p.defaultProfile ? '<button class="aui-button btn-default-engine" data-id="' + p.id + '">기본 설정</button>' : '') +
                    '<button class="aui-button aui-button-link btn-delete-engine" data-id="' + p.id + '">삭제</button>' +
                    '</div></div>';
            });

            html += '</div><div id="jask-engine-form-panel" style="display:none"></div></div>';
            $('#jask-admin-content').html(html);
            bindEngineEvents();
        }).fail(function () { showError('엔진 프로파일 로드 실패'); });
    }

    function bindEngineEvents() {
        $('#btn-add-engine').on('click', function () { showEngineForm(null); });

        $(document).on('click', '.btn-test-engine', function () {
            var id = $(this).data('id');
            var $btn = $(this).prop('disabled', true).text('테스트 중...');
            $.post(BASE_URL + '/engines/' + id + '/test').done(function (result) {
                var msg = result.success ? '연결 성공' : '연결 실패';
                if (result.steps) {
                    result.steps.forEach(function (s) {
                        msg += '\n' + (s.passed ? '✓' : '✗') + ' ' + s.name + ': ' + s.message;
                    });
                }
                showFlag(result.success ? 'success' : 'error', msg);
                loadEngineProfiles();
            }).always(function () { $btn.prop('disabled', false).text('연결 테스트'); });
        });

        $(document).on('click', '.btn-edit-engine', function () {
            var id = $(this).data('id');
            showEngineForm(id);
        });

        $(document).on('click', '.btn-default-engine', function () {
            var id = $(this).data('id');
            ajaxPut(BASE_URL + '/engines/' + id + '/default', {}, '기본 프로파일 변경됨', loadEngineProfiles);
        });

        $(document).on('click', '.btn-delete-engine', function () {
            var id = $(this).data('id');
            if (confirm('이 엔진 프로파일을 삭제하시겠습니까?')) {
                ajaxDelete(BASE_URL + '/engines/' + id, '엔진 삭제됨', loadEngineProfiles);
            }
        });
    }

    function showEngineForm(editId) {
        var isEdit = editId !== null;
        var fillForm = function (p) {
            var html = '<div class="jask-form-panel">' +
                '<h3>' + (isEdit ? '엔진 편집' : '엔진 추가') + '</h3>' +
                '<form id="jask-engine-form" class="aui"><div class="jask-form-grid">' +
                formGroup('프로파일 이름', '<input class="text medium-field" id="eng-name" value="' + esc(p.profileName || '') + '" required>') +
                formGroup('엔진 유형', '<select id="eng-type" class="select"><option value="OLLAMA"' + sel(p.engineType, 'OLLAMA') + '>Ollama</option><option value="VLLM"' + sel(p.engineType, 'VLLM') + '>vLLM</option><option value="OPENAI_COMPATIBLE"' + sel(p.engineType, 'OPENAI_COMPATIBLE') + '>OpenAI Compatible</option></select>') +
                formGroup('엔드포인트 URL', '<input class="text long-field" id="eng-url" value="' + esc(p.endpointUrl || '') + '" required>') +
                formGroup('모델', '<input class="text medium-field" id="eng-model" value="' + esc(p.defaultModel || '') + '" required>') +
                formGroup('API 키', '<input class="text medium-field" type="password" id="eng-apikey" placeholder="' + (p.hasApiKey ? '(변경 시 입력)' : '') + '">') +
                formGroup('Temperature', '<input class="text short-field" type="number" id="eng-temp" value="' + (p.temperature || 0.3) + '" min="0" max="2" step="0.1">') +
                formGroup('Max Tokens', '<input class="text short-field" type="number" id="eng-tokens" value="' + (p.maxTokens || 2048) + '">') +
                formGroup('타임아웃 (초)', '<input class="text short-field" type="number" id="eng-timeout" value="' + (p.timeoutSeconds || 60) + '">') +
                formGroup('우선순위', '<input class="text short-field" type="number" id="eng-priority" value="' + (p.priority || 10) + '" min="1">') +
                formGroup('설명', '<textarea id="eng-desc" class="textarea" rows="2">' + esc(p.description || '') + '</textarea>') +
                '</div><div class="jask-form-actions">' +
                '<button class="aui-button aui-button-primary" type="submit">' + (isEdit ? '수정' : '추가') + '</button>' +
                '<button class="aui-button" type="button" id="btn-cancel-engine">취소</button>' +
                '</div></form></div>';
            $('#jask-engine-form-panel').html(html).show();

            $('#jask-engine-form').on('submit', function (e) {
                e.preventDefault();
                var data = {
                    profileName: $('#eng-name').val(),
                    engineType: $('#eng-type').val(),
                    endpointUrl: $('#eng-url').val(),
                    defaultModel: $('#eng-model').val(),
                    temperature: parseFloat($('#eng-temp').val()),
                    maxTokens: parseInt($('#eng-tokens').val()),
                    timeoutSeconds: parseInt($('#eng-timeout').val()),
                    priority: parseInt($('#eng-priority').val()),
                    description: $('#eng-desc').val()
                };
                var apiKey = $('#eng-apikey').val();
                if (apiKey) data.apiKey = apiKey;

                if (isEdit) {
                    ajaxPut(BASE_URL + '/engines/' + editId, data, '엔진 수정됨', loadEngineProfiles);
                } else {
                    ajaxPost(BASE_URL + '/engines', data, '엔진 추가됨', loadEngineProfiles);
                }
            });
            $('#btn-cancel-engine').on('click', function () { $('#jask-engine-form-panel').hide(); });
        };

        if (isEdit) {
            // 기존 데이터 로드 (목록에서 가져옴)
            $.getJSON(BASE_URL + '/engines').done(function (profiles) {
                var p = profiles.find(function (x) { return x.id === editId; }) || {};
                fillForm(p);
            });
        } else {
            fillForm({});
        }
    }

    // =========================================================================
    // 마스킹 규칙 (요건 9, 10)
    // =========================================================================

    function loadMaskingRules() {
        $.getJSON(BASE_URL + '/masking-rules').done(function (rules) {
            var html = '<div class="jask-section">' +
                '<h2><span class="aui-icon aui-icon-small aui-iconfont-lock-filled"></span> 보안·마스킹 규칙</h2>' +
                '<p>LLM에 코드를 전송하기 전 적용할 마스킹 규칙을 관리합니다.</p>' +
                '<button class="aui-button aui-button-primary" id="btn-add-rule">+ 규칙 추가</button>' +
                '<button class="aui-button" id="btn-test-masking">패턴 테스트</button>' +
                '<table class="aui jask-table"><thead><tr>' +
                '<th>이름</th><th>카테고리</th><th>패턴</th><th>치환</th><th>상태</th><th>유형</th><th>작업</th>' +
                '</tr></thead><tbody>';

            rules.forEach(function (r) {
                html += '<tr><td>' + esc(r.name) + '</td>' +
                    '<td><span class="aui-lozenge">' + esc(r.category) + '</span></td>' +
                    '<td><code>' + esc(r.pattern).substring(0, 40) + (r.pattern.length > 40 ? '...' : '') + '</code></td>' +
                    '<td><code>' + esc(r.replacement) + '</code></td>' +
                    '<td>' + (r.enabled ? '<span class="aui-lozenge aui-lozenge-success">활성</span>' : '<span class="aui-lozenge">비활성</span>') + '</td>' +
                    '<td>' + (r.builtIn ? '내장' : '커스텀') + '</td>' +
                    '<td>' + (!r.builtIn ? '<button class="aui-button aui-button-link btn-edit-rule" data-id="' + r.id + '">편집</button><button class="aui-button aui-button-link btn-delete-rule" data-id="' + r.id + '">삭제</button>' : '-') + '</td>' +
                    '</tr>';
            });

            html += '</tbody></table>' +
                '<div id="jask-rule-form-panel" style="display:none"></div>' +
                '<div id="jask-test-panel" style="display:none"></div></div>';
            $('#jask-admin-content').html(html);
            bindMaskingEvents();
        }).fail(function () { showError('마스킹 규칙 로드 실패'); });
    }

    function bindMaskingEvents() {
        $('#btn-add-rule').on('click', function () { showRuleForm(null); });

        $('#btn-test-masking').on('click', function () {
            var $panel = $('#jask-test-panel');
            if ($panel.is(':visible')) { $panel.hide(); return; }
            var html = '<div class="jask-form-panel"><h3>패턴 테스트</h3>' +
                '<form id="jask-test-form" class="aui"><div class="jask-form-grid">' +
                formGroup('정규식 패턴', '<input class="text long-field" id="test-pattern">') +
                formGroup('치환 텍스트', '<input class="text medium-field" id="test-replacement" value="[MASKED]">') +
                formGroup('테스트 입력', '<textarea id="test-input" class="textarea" rows="3" placeholder="테스트할 텍스트를 입력하세요"></textarea>') +
                '</div><button class="aui-button aui-button-primary" type="submit">테스트</button></form>' +
                '<div id="test-result" style="display:none"></div></div>';
            $panel.html(html).show();

            $('#jask-test-form').on('submit', function (e) {
                e.preventDefault();
                $.ajax({
                    url: BASE_URL + '/masking-rules/test',
                    method: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify({
                        pattern: $('#test-pattern').val(),
                        replacement: $('#test-replacement').val(),
                        testInput: $('#test-input').val()
                    })
                }).done(function (r) {
                    var html = r.valid ?
                        '<p class="jask-success">매치 수: ' + r.matchCount + '</p><pre>' + esc(r.maskedOutput) + '</pre>' :
                        '<p class="jask-error">오류: ' + esc(r.error) + '</p>';
                    $('#test-result').html(html).show();
                });
            });
        });

        $(document).on('click', '.btn-delete-rule', function () {
            var id = $(this).data('id');
            if (confirm('이 마스킹 규칙을 삭제하시겠습니까?')) {
                ajaxDelete(BASE_URL + '/masking-rules/' + id, '규칙 삭제됨', loadMaskingRules);
            }
        });

        $(document).on('click', '.btn-edit-rule', function () {
            showRuleForm($(this).data('id'));
        });
    }

    function showRuleForm(editId) {
        var html = '<div class="jask-form-panel"><h3>' + (editId ? '규칙 편집' : '규칙 추가') + '</h3>' +
            '<form id="jask-rule-form" class="aui"><div class="jask-form-grid">' +
            formGroup('규칙 이름', '<input class="text medium-field" id="rule-name" required>') +
            formGroup('카테고리', '<select id="rule-category" class="select"><option value="PII">PII</option><option value="SECRET">SECRET</option><option value="CUSTOM">CUSTOM</option></select>') +
            formGroup('정규식 패턴', '<input class="text long-field" id="rule-pattern" required>') +
            formGroup('치환 텍스트', '<input class="text medium-field" id="rule-replacement" value="[MASKED]" required>') +
            '</div><div class="jask-form-actions">' +
            '<button class="aui-button aui-button-primary" type="submit">' + (editId ? '수정' : '추가') + '</button>' +
            '<button class="aui-button" type="button" onclick="$(\'#jask-rule-form-panel\').hide()">취소</button>' +
            '</div></form></div>';
        $('#jask-rule-form-panel').html(html).show();

        $('#jask-rule-form').on('submit', function (e) {
            e.preventDefault();
            var data = {
                name: $('#rule-name').val(),
                pattern: $('#rule-pattern').val(),
                replacement: $('#rule-replacement').val(),
                category: $('#rule-category').val()
            };
            if (editId) {
                ajaxPut(BASE_URL + '/masking-rules/' + editId, data, '규칙 수정됨', loadMaskingRules);
            } else {
                ajaxPost(BASE_URL + '/masking-rules', data, '규칙 추가됨', loadMaskingRules);
            }
        });
    }

    // =========================================================================
    // 프로젝트 정책 (요건 4, 8)
    // =========================================================================

    function loadProjectPolicy() {
        var html = '<div class="jask-section">' +
            '<h2><span class="aui-icon aui-icon-small aui-iconfont-page-default"></span> 프로젝트별 정책</h2>' +
            '<p>프로젝트별로 분석 설정을 재정의할 수 있습니다. 정책 템플릿을 활용하여 빠르게 설정하세요.</p>' +
            '<div class="aui-message aui-message-info">' +
            '<p>프로젝트별 정책은 각 프로젝트의 설정 페이지에서도 관리할 수 있습니다.</p>' +
            '</div>' +
            '<h3>정책 템플릿</h3>' +
            '<div id="jask-policy-templates"></div>' +
            '</div>';
        $('#jask-admin-content').html(html);

        $.getJSON(BASE_URL + '/policy-templates').done(function (templates) {
            var thtml = '<div class="jask-templates-grid">';
            templates.forEach(function (t) {
                thtml += '<div class="jask-template-card">' +
                    '<h4>' + esc(t.name) + (t.builtIn ? ' <span class="aui-lozenge aui-lozenge-subtle">내장</span>' : '') + '</h4>' +
                    '<p>' + esc(t.description) + '</p>' +
                    '<details><summary>설정 상세</summary><pre>' + JSON.stringify(t.settings, null, 2) + '</pre></details>' +
                    '</div>';
            });
            thtml += '</div>';
            $('#jask-policy-templates').html(thtml);
        });
    }

    // =========================================================================
    // 사용량/비용 (요건 11, 12, 14)
    // =========================================================================

    function loadUsageCost() {
        $.when(
            $.getJSON(BASE_URL + '/usage-summary'),
            $.getJSON(BASE_URL + '/quotas'),
            $.getJSON(BASE_URL + '/metrics'),
            $.getJSON(BASE_URL + '/alerts?limit=10')
        ).done(function (summaryR, quotasR, metricsR, alertsR) {
            var summary = summaryR[0], quotas = quotasR[0], metrics = metricsR[0], alerts = alertsR[0];

            var html = '<div class="jask-section">' +
                '<h2><span class="aui-icon aui-icon-small aui-iconfont-graph-bar"></span> 사용량·비용 대시보드</h2>';

            // 사용량 요약 카드
            html += '<div class="jask-dashboard-cards">' +
                dashCard('호출 횟수', summary.totalCalls + ' / ' + (summary.maxCalls || '∞'), summary.status) +
                dashCard('입력 토큰', formatNumber(summary.totalInputTokens), 'info') +
                dashCard('출력 토큰', formatNumber(summary.totalOutputTokens), 'info') +
                dashCard('사용률', (summary.usagePercent || 0).toFixed(1) + '%', summary.status === 'EXCEEDED' ? 'error' : (summary.status === 'WARNING' ? 'warning' : 'success'));
            if (metrics.derived) {
                html += dashCard('LLM 성공률', metrics.derived['llm.success_rate'] || 'N/A', 'info') +
                    dashCard('캐시 히트율', metrics.derived['cache.hit_rate'] || 'N/A', 'info');
            }
            html += '</div>';

            // 알림 섹션
            if (alerts.alerts && alerts.alerts.length > 0) {
                html += '<h3>최근 알림</h3><div class="jask-alert-list">';
                alerts.alerts.forEach(function (a) {
                    var levelClass = a.level === 'CRITICAL' ? 'error' : (a.level === 'WARNING' ? 'warning' : 'info');
                    html += '<div class="aui-message aui-message-' + levelClass + '">' +
                        '<span class="jask-alert-time">' + formatTime(a.timestamp) + '</span> ' +
                        esc(a.message) +
                        (!a.acknowledged ? ' <button class="aui-button aui-button-link btn-ack-alert" data-id="' + a.id + '">확인</button>' : '') +
                        '</div>';
                });
                html += '</div>';
            }

            // 한도 설정 테이블
            html += '<h3>한도 설정</h3>' +
                '<button class="aui-button aui-button-primary" id="btn-add-quota">+ 한도 추가</button>' +
                '<table class="aui jask-table"><thead><tr><th>범위</th><th>기간</th><th>최대 호출</th><th>최대 토큰</th><th>경고 임계</th><th>초과 동작</th><th>상태</th><th>작업</th></tr></thead><tbody>';
            quotas.forEach(function (q) {
                html += '<tr><td>' + q.scope + '/' + q.scopeKey + '</td><td>' + q.period + '</td>' +
                    '<td>' + q.maxCalls + '</td><td>' + formatNumber(q.maxTokens) + '</td>' +
                    '<td>' + q.warningThresholdPercent + '%</td><td>' + q.exceedAction + '</td>' +
                    '<td>' + (q.enabled ? '활성' : '비활성') + '</td>' +
                    '<td><button class="aui-button aui-button-link btn-delete-quota" data-id="' + q.id + '">삭제</button></td></tr>';
            });
            html += '</tbody></table><div id="jask-quota-form" style="display:none"></div></div>';

            $('#jask-admin-content').html(html);
            bindUsageEvents();
        }).fail(function () { showError('사용량 데이터 로드 실패'); });
    }

    function bindUsageEvents() {
        $(document).on('click', '.btn-ack-alert', function () {
            var id = $(this).data('id');
            $.post(BASE_URL + '/alerts/' + id + '/acknowledge').done(function () {
                loadUsageCost();
            });
        });

        $('#btn-add-quota').on('click', function () {
            var html = '<div class="jask-form-panel"><h3>한도 추가</h3>' +
                '<form id="jask-quota-form-inner" class="aui"><div class="jask-form-grid">' +
                formGroup('범위', '<select id="q-scope"><option value="GLOBAL">전역</option><option value="PROJECT">프로젝트</option></select>') +
                formGroup('범위 키', '<input class="text" id="q-scope-key" value="global">') +
                formGroup('기간', '<select id="q-period"><option value="DAILY">일간</option><option value="WEEKLY">주간</option><option value="MONTHLY">월간</option></select>') +
                formGroup('최대 호출 수', '<input class="text short-field" type="number" id="q-max-calls" value="1000">') +
                formGroup('최대 토큰', '<input class="text short-field" type="number" id="q-max-tokens" value="500000">') +
                formGroup('경고 임계 (%)', '<input class="text short-field" type="number" id="q-warn" value="80">') +
                formGroup('초과 동작', '<select id="q-action"><option value="WARN_ONLY">경고만</option><option value="BLOCK">차단</option><option value="THROTTLE">속도 제한</option></select>') +
                '</div><button class="aui-button aui-button-primary" type="submit">추가</button></form></div>';
            $('#jask-quota-form').html(html).show();

            $('#jask-quota-form-inner').on('submit', function (e) {
                e.preventDefault();
                ajaxPost(BASE_URL + '/quotas', {
                    scope: $('#q-scope').val(), scopeKey: $('#q-scope-key').val(),
                    period: $('#q-period').val(), maxCalls: parseInt($('#q-max-calls').val()),
                    maxTokens: parseInt($('#q-max-tokens').val()),
                    warningThresholdPercent: parseInt($('#q-warn').val()),
                    exceedAction: $('#q-action').val(), enabled: true
                }, '한도 추가됨', loadUsageCost);
            });
        });

        $(document).on('click', '.btn-delete-quota', function () {
            if (confirm('이 한도 설정을 삭제하시겠습니까?')) {
                ajaxDelete(BASE_URL + '/quotas/' + $(this).data('id'), '한도 삭제됨', loadUsageCost);
            }
        });
    }

    // =========================================================================
    // 감사 로그 (요건 13)
    // =========================================================================

    function loadAuditLog() {
        var html = '<div class="jask-section">' +
            '<h2><span class="aui-icon aui-icon-small aui-iconfont-file-txt"></span> 감사 로그</h2>' +
            '<div class="jask-filter-bar">' +
            '<input class="text" id="audit-user-filter" placeholder="사용자 필터">' +
            '<select id="audit-type-filter"><option value="">전체 유형</option>' +
            '<option value="SETTINGS_CHANGED">설정 변경</option>' +
            '<option value="ENGINE_CREATED">엔진 생성</option><option value="ENGINE_UPDATED">엔진 수정</option>' +
            '<option value="MASKING_RULE_CREATED">마스킹 규칙 생성</option>' +
            '<option value="CHANGE_APPROVED">변경 승인</option><option value="CHANGE_REJECTED">변경 거부</option>' +
            '<option value="BACKUP_CREATED">백업 생성</option><option value="BACKUP_RESTORED">복원</option>' +
            '</select>' +
            '<button class="aui-button" id="btn-audit-search">검색</button>' +
            '<button class="aui-button" id="btn-audit-export">CSV 내보내기</button>' +
            '</div>' +
            '<table class="aui jask-table" id="audit-table"><thead><tr>' +
            '<th>시간</th><th>유형</th><th>사용자</th><th>대상</th><th>상세</th><th>IP</th>' +
            '</tr></thead><tbody id="audit-tbody"></tbody></table>' +
            '<div class="jask-pagination" id="audit-pagination"></div></div>';
        $('#jask-admin-content').html(html);

        loadAuditPage(0);

        $('#btn-audit-search').on('click', function () { loadAuditPage(0); });
        $('#btn-audit-export').on('click', function () { exportAuditCsv(); });
    }

    function loadAuditPage(page) {
        var params = { page: page, size: 30 };
        var user = $('#audit-user-filter').val();
        var type = $('#audit-type-filter').val();
        if (user) params.user = user;
        if (type) params.type = type;

        $.getJSON(BASE_URL + '/audit-log', params).done(function (data) {
            var html = '';
            data.entries.forEach(function (e) {
                html += '<tr><td>' + formatTime(e.timestamp) + '</td>' +
                    '<td><span class="aui-lozenge">' + esc(e.eventType) + '</span></td>' +
                    '<td>' + esc(e.username) + '</td>' +
                    '<td>' + esc(e.targetType || '') + '/' + esc(e.targetId || '') + '</td>' +
                    '<td class="jask-details-cell">' + esc((e.details || '').substring(0, 80)) + '</td>' +
                    '<td>' + esc(e.ipAddress || '') + '</td></tr>';
            });
            $('#audit-tbody').html(html || '<tr><td colspan="6">로그가 없습니다.</td></tr>');

            var totalPages = Math.ceil(data.total / 30);
            var pagHtml = '';
            for (var i = 0; i < Math.min(totalPages, 10); i++) {
                pagHtml += '<button class="aui-button ' + (i === page ? 'aui-button-primary' : '') +
                    ' btn-audit-page" data-page="' + i + '">' + (i + 1) + '</button>';
            }
            $('#audit-pagination').html(pagHtml);
            $(document).off('click', '.btn-audit-page').on('click', '.btn-audit-page', function () {
                loadAuditPage(parseInt($(this).data('page')));
            });
        });
    }

    function exportAuditCsv() {
        $.getJSON(BASE_URL + '/audit-log', { page: 0, size: 1000 }).done(function (data) {
            var csv = 'Time,Type,User,TargetType,TargetId,Details,IP\n';
            data.entries.forEach(function (e) {
                csv += '"' + formatTime(e.timestamp) + '","' + e.eventType + '","' + e.username + '","' +
                    (e.targetType || '') + '","' + (e.targetId || '') + '","' +
                    (e.details || '').replace(/"/g, '""') + '","' + (e.ipAddress || '') + '"\n';
            });
            var blob = new Blob([csv], { type: 'text/csv' });
            var a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = 'audit-log-' + new Date().toISOString().slice(0, 10) + '.csv';
            a.click();
        });
    }

    // =========================================================================
    // 진단 (요건 18)
    // =========================================================================

    function loadDiagnostics() {
        $.getJSON(BASE_URL + '/diagnostics').done(function (report) {
            var statusColor = report.overallStatus === 'HEALTHY' ? 'success' :
                (report.overallStatus === 'WARNING' ? 'warning' : 'error');

            var html = '<div class="jask-section">' +
                '<h2><span class="aui-icon aui-icon-small aui-iconfont-info-circle"></span> 시스템 진단</h2>' +
                '<div class="jask-status-banner jask-status-' + statusColor + '">' +
                '<strong>전체 상태: ' + report.overallStatus + '</strong>' +
                '</div>';

            // 진단 체크 결과
            html += '<div class="jask-diag-checks">';
            report.checks.forEach(function (c) {
                var icon = c.status === 'HEALTHY' ? 'approve' : (c.status === 'WARNING' ? 'warning' : 'cross-circle');
                html += '<div class="jask-diag-check">' +
                    '<span class="aui-icon aui-icon-small aui-iconfont-' + icon + '"></span>' +
                    '<strong>' + esc(c.name) + '</strong>: ' + esc(c.message);
                if (c.details) {
                    html += '<details><summary>상세</summary><pre>' + JSON.stringify(c.details, null, 2) + '</pre></details>';
                }
                html += '</div>';
            });
            html += '</div>';

            // 시스템 정보
            if (report.systemInfo) {
                html += '<h3>시스템 정보</h3><dl class="jask-sysinfo">';
                Object.keys(report.systemInfo).forEach(function (k) {
                    html += '<dt>' + esc(k) + '</dt><dd>' + esc(String(report.systemInfo[k])) + '</dd>';
                });
                html += '</dl>';
            }

            // AO 테이블 통계
            html += '<h3>DB 테이블 통계</h3>';
            $.getJSON(BASE_URL + '/diagnostics/tables').done(function (stats) {
                var thtml = '<table class="aui jask-table"><thead><tr><th>테이블</th><th>레코드 수</th></tr></thead><tbody>';
                Object.keys(stats).forEach(function (k) {
                    thtml += '<tr><td>' + esc(k) + '</td><td>' + stats[k] + '</td></tr>';
                });
                thtml += '</tbody></table>';
                $('#jask-table-stats').html(thtml);
            });
            html += '<div id="jask-table-stats"></div></div>';

            $('#jask-admin-content').html(html);
        }).fail(function () { showError('진단 실행 실패'); });
    }

    // =========================================================================
    // 백업/복원 (요건 17)
    // =========================================================================

    function loadBackups() {
        $.getJSON(BASE_URL + '/backups').done(function (snapshots) {
            var html = '<div class="jask-section">' +
                '<h2><span class="aui-icon aui-icon-small aui-iconfont-devtools-clone"></span> 백업·복원</h2>' +
                '<button class="aui-button aui-button-primary" id="btn-create-backup">스냅샷 생성</button>' +
                '<table class="aui jask-table"><thead><tr>' +
                '<th>이름</th><th>유형</th><th>설명</th><th>크기</th><th>생성자</th><th>생성 시간</th><th>작업</th>' +
                '</tr></thead><tbody>';

            snapshots.forEach(function (s) {
                html += '<tr><td>' + esc(s.snapshotName) + '</td>' +
                    '<td><span class="aui-lozenge">' + esc(s.snapshotType) + '</span></td>' +
                    '<td>' + esc(s.description || '-') + '</td>' +
                    '<td>' + formatBytes(s.sizeBytes) + '</td>' +
                    '<td>' + esc(s.createdBy || '') + '</td>' +
                    '<td>' + formatTime(s.createdAt) + '</td>' +
                    '<td><button class="aui-button aui-button-link btn-restore-backup" data-id="' + s.id + '">복원</button>' +
                    '<button class="aui-button aui-button-link btn-delete-backup" data-id="' + s.id + '">삭제</button></td></tr>';
            });

            html += '</tbody></table></div>';
            $('#jask-admin-content').html(html);

            $('#btn-create-backup').on('click', function () {
                var name = prompt('스냅샷 이름:', 'backup_' + new Date().toISOString().slice(0, 10));
                if (name) {
                    ajaxPost(BASE_URL + '/backups', { name: name, description: '수동 백업' },
                        '스냅샷 생성됨', loadBackups);
                }
            });

            $(document).on('click', '.btn-restore-backup', function () {
                if (confirm('이 스냅샷에서 설정을 복원하시겠습니까? 현재 설정이 덮어씌워집니다.')) {
                    ajaxPost(BASE_URL + '/backups/' + $(this).data('id') + '/restore', {},
                        '설정 복원됨', loadBackups);
                }
            });

            $(document).on('click', '.btn-delete-backup', function () {
                if (confirm('이 스냅샷을 삭제하시겠습니까?')) {
                    ajaxDelete(BASE_URL + '/backups/' + $(this).data('id'), '스냅샷 삭제됨', loadBackups);
                }
            });
        }).fail(function () { showError('백업 목록 로드 실패'); });
    }

    // =========================================================================
    // RBAC 역할 관리 (요건 2)
    // =========================================================================

    function loadRbac() {
        $.getJSON(BASE_URL + '/roles').done(function (data) {
            var html = '<div class="jask-section">' +
                '<h2><span class="aui-icon aui-icon-small aui-iconfont-people"></span> 역할 관리</h2>' +
                '<p>사용자별 관리 역할을 할당합니다. 시스템 관리자(SYS_ADMIN)는 Bitbucket 설정에서 관리됩니다.</p>' +
                '<div class="jask-form-inline">' +
                '<input class="text" id="rbac-username" placeholder="사용자명">' +
                '<select id="rbac-role"><option value="PROJECT_ADMIN">프로젝트 관리자</option>' +
                '<option value="AUDIT_VIEWER">감사 조회자</option>' +
                '<option value="READ_ONLY">읽기 전용</option></select>' +
                '<button class="aui-button aui-button-primary" id="btn-assign-role">역할 할당</button></div>' +
                '<table class="aui jask-table"><thead><tr><th>사용자</th><th>역할</th><th>작업</th></tr></thead><tbody>';

            var assignments = data.assignments || {};
            Object.keys(assignments).forEach(function (username) {
                html += '<tr><td>' + esc(username) + '</td>' +
                    '<td><span class="aui-lozenge aui-lozenge-success">' + assignments[username] + '</span></td>' +
                    '<td><button class="aui-button aui-button-link btn-remove-role" data-user="' + esc(username) + '">제거</button></td></tr>';
            });
            if (Object.keys(assignments).length === 0) {
                html += '<tr><td colspan="3">할당된 역할이 없습니다. (시스템 관리자는 자동으로 SYS_ADMIN 역할을 가집니다)</td></tr>';
            }

            html += '</tbody></table></div>';
            $('#jask-admin-content').html(html);

            $('#btn-assign-role').on('click', function () {
                var username = $('#rbac-username').val();
                var role = $('#rbac-role').val();
                if (!username) { showFlag('warning', '사용자명을 입력하세요.'); return; }
                ajaxPut(BASE_URL + '/roles/' + username, { role: role }, '역할 할당됨', loadRbac);
            });

            $(document).on('click', '.btn-remove-role', function () {
                ajaxDelete(BASE_URL + '/roles/' + $(this).data('user'), '역할 제거됨', loadRbac);
            });
        }).fail(function () { showError('역할 정보 로드 실패'); });
    }

    // =========================================================================
    // 유틸리티
    // =========================================================================

    function formGroup(label, input) {
        return '<div class="field-group"><label>' + label + '</label>' + input + '</div>';
    }

    function formCheckbox(label, id, checked) {
        return '<div class="field-group"><label><input type="checkbox" id="' + id + '"' +
            (checked ? ' checked' : '') + '> ' + label + '</label></div>';
    }

    function dashCard(title, value, type) {
        return '<div class="jask-dash-card jask-dash-' + type + '">' +
            '<div class="jask-dash-value">' + value + '</div>' +
            '<div class="jask-dash-title">' + title + '</div></div>';
    }

    function esc(s) { return $('<span>').text(s || '').html(); }

    function sel(val, opt) { return val === opt ? ' selected' : ''; }

    function formatTime(ts) {
        if (!ts) return '-';
        return new Date(ts).toLocaleString('ko-KR');
    }

    function formatNumber(n) {
        if (!n) return '0';
        return n.toLocaleString();
    }

    function formatBytes(b) {
        if (!b) return '0 B';
        if (b < 1024) return b + ' B';
        if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
        return (b / 1048576).toFixed(1) + ' MB';
    }

    function showFlag(type, message) {
        AJS.flag({ type: type, body: message, close: 'auto' });
    }

    function showError(message) {
        $('#jask-admin-content').html('<div class="aui-message aui-message-error"><p>' + esc(message) + '</p></div>');
    }

    function ajaxPost(url, data, successMsg, callback) {
        $.ajax({
            url: url, method: 'POST', contentType: 'application/json',
            data: JSON.stringify(data)
        }).done(function () {
            showFlag('success', successMsg);
            if (callback) callback();
        }).fail(function (xhr) {
            var err = xhr.responseJSON ? xhr.responseJSON.error : '요청 실패';
            showFlag('error', err);
        });
    }

    function ajaxPut(url, data, successMsg, callback) {
        $.ajax({
            url: url, method: 'PUT', contentType: 'application/json',
            data: JSON.stringify(data)
        }).done(function () {
            showFlag('success', successMsg);
            if (callback) callback();
        }).fail(function (xhr) {
            var err = xhr.responseJSON ? xhr.responseJSON.error : '요청 실패';
            showFlag('error', err);
        });
    }

    function ajaxDelete(url, successMsg, callback) {
        $.ajax({
            url: url, method: 'DELETE'
        }).done(function () {
            showFlag('success', successMsg);
            if (callback) callback();
        }).fail(function (xhr) {
            var err = xhr.responseJSON ? xhr.responseJSON.error : '삭제 실패';
            showFlag('error', err);
        });
    }

})(AJS.$);
