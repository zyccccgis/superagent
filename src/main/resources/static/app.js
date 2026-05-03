class SuperBizConsole {
    constructor() {
        this.apiBaseUrl = `${window.location.origin}/api`;
        this.mode = 'quick';
        this.sessionId = this.createSessionId();
        this.messages = [];
        this.historyRows = [];
        this.memoryView = 'long';
        this.ragDocuments = [];
        this.tools = [];
        this.mcpServers = [];
        this.mcpTools = [];
        this.skills = [];
        this.sandboxStatus = null;
        this.sandboxLastResult = null;
        this.traces = [];
        this.scheduledTasks = [];
        this.scheduledRunItems = [];
        this.selectedTraceId = null;
        this.selectedScheduleId = null;
        this.selectedScheduledRunId = null;
        this.editingScheduleId = null;
        this.toolsView = 'local';
        this.selectedSkillName = null;
        this.selectedRagDocId = null;
        this.currentPage = 1;
        this.pageSize = 8;
        this.tracePage = 1;
        this.tracePageSizeValue = 20;
        this.traceTotal = 0;
        this.memoryTotal = 0;
        this.selectedHistoryId = null;
        this.isBusy = false;

        this.availableEndpoints = [
            { method: 'POST', path: '/api/chat', purpose: '快速对话，后端自动维护 session 历史窗口' },
            { method: 'POST', path: '/api/chat_stream', purpose: 'SSE 流式对话，适合长回答实时渲染' },
            { method: 'POST', path: '/api/chat/clear', purpose: '清空指定 session 的后端历史' },
            { method: 'GET', path: '/api/chat/session/{sessionId}', purpose: '查询后端 session 概要' },
            { method: 'GET', path: '/api/memory/files', purpose: '查询记忆文件列表' },
            { method: 'GET', path: '/api/memory/files/content', purpose: '读取记忆文件内容' },
            { method: 'POST', path: '/api/memory/files', purpose: '创建记忆文件' },
            { method: 'PUT', path: '/api/memory/files', purpose: '更新记忆文件' },
            { method: 'DELETE', path: '/api/memory/files', purpose: '删除记忆文件' },
            { method: 'GET', path: '/api/memory/executions', purpose: '分页查询短期记忆记录' },
            { method: 'GET', path: '/api/memory/executions/detail', purpose: '读取短期记忆详情' },
            { method: 'PUT', path: '/api/memory/executions', purpose: '更新短期记忆记录' },
            { method: 'DELETE', path: '/api/memory/executions', purpose: '删除短期记忆记录' },
            { method: 'POST', path: '/api/memory/extract', purpose: '从执行结果抽取长期记忆' },
            { method: 'POST', path: '/api/memory/compress', purpose: '把旧短期记忆压缩为摘要记录' },
            { method: 'POST', path: '/api/ai_ops/tasks', purpose: '创建 AI Ops 诊断任务' },
            { method: 'GET', path: '/api/ai_ops/tasks/{taskId}', purpose: '轮询 AI Ops 任务状态和报告' },
            { method: 'POST', path: '/api/rag/documents', purpose: '上传知识库文件并建立索引' },
            { method: 'GET', path: '/api/rag/documents', purpose: '分页查询 RAG 文档元数据' },
            { method: 'POST', path: '/api/rag/retrieve', purpose: 'RAG 纯召回，返回 TopK 片段和 L2 distance' },
            { method: 'GET', path: '/api/tools', purpose: '查询后端本地工具列表和启用状态' },
            { method: 'PUT', path: '/api/tools/{toolName}/enabled', purpose: '修改本地工具启用状态' },
            { method: 'GET', path: '/api/mcp/servers', purpose: '查询 MCP 服务配置和运行状态' },
            { method: 'POST', path: '/api/mcp/servers', purpose: '新增 MCP 服务并刷新运行时' },
            { method: 'GET', path: '/api/mcp/tools', purpose: '查询当前 MCP 工具快照' },
            { method: 'GET', path: '/api/skills', purpose: '查询已安装 Skills' },
            { method: 'POST', path: '/api/skills/install', purpose: '从 ZIP URL 安装 Skill' },
            { method: 'GET', path: '/api/sandbox/status', purpose: '查询 Docker 沙箱运行状态和挂载配置' },
            { method: 'POST', path: '/api/sandbox/test', purpose: '在 Docker 沙箱中执行一段 Python 测试代码' },
            { method: 'GET', path: '/api/scheduled-diagnostics', purpose: '查询定时排查任务' },
            { method: 'POST', path: '/api/scheduled-diagnostics', purpose: '创建定时排查任务' },
            { method: 'POST', path: '/api/scheduled-diagnostics/{scheduleId}/run', purpose: '立即运行定时排查任务' },
            { method: 'GET', path: '/api/traces', purpose: '查询 Agent 执行 Trace 列表' },
            { method: 'GET', path: '/api/traces/{traceId}', purpose: '查询 Agent 执行 Trace 详情' },
            { method: 'GET', path: '/milvus/health', purpose: '检测向量库健康状态' }
        ];

        this.bindElements();
        this.bindEvents();
        this.renderAll();
    }

    bindElements() {
        this.chatPageBtn = document.getElementById('chatPageBtn');
        this.historyPageBtn = document.getElementById('historyPageBtn');
        this.ragPageBtn = document.getElementById('ragPageBtn');
        this.toolsPageBtn = document.getElementById('toolsPageBtn');
        this.skillsPageBtn = document.getElementById('skillsPageBtn');
        this.scheduledPageBtn = document.getElementById('scheduledPageBtn');
        this.sandboxPageBtn = document.getElementById('sandboxPageBtn');
        this.tracesPageBtn = document.getElementById('tracesPageBtn');
        this.chatPage = document.getElementById('chatPage');
        this.historyPage = document.getElementById('historyPage');
        this.ragPage = document.getElementById('ragPage');
        this.toolsPage = document.getElementById('toolsPage');
        this.skillsPage = document.getElementById('skillsPage');
        this.scheduledPage = document.getElementById('scheduledPage');
        this.sandboxPage = document.getElementById('sandboxPage');
        this.tracesPage = document.getElementById('tracesPage');
        this.sidebarSessionId = document.getElementById('sidebarSessionId');
        this.copySessionBtn = document.getElementById('copySessionBtn');
        this.newSessionBtn = document.getElementById('newSessionBtn');
        this.clearBackendSessionBtn = document.getElementById('clearBackendSessionBtn');
        this.aiOpsBtn = document.getElementById('aiOpsBtn');
        this.messagesEl = document.getElementById('messages');
        this.messageInput = document.getElementById('messageInput');
        this.sendBtn = document.getElementById('sendBtn');
        this.uploadBtn = document.getElementById('uploadBtn');
        this.fileInput = document.getElementById('fileInput');
        this.chatMeta = document.getElementById('chatMeta');
        this.sessionPairCount = document.getElementById('sessionPairCount');
        this.sessionCreateTime = document.getElementById('sessionCreateTime');
        this.refreshSessionBtn = document.getElementById('refreshSessionBtn');
        this.endpointList = document.getElementById('endpointList');
        this.checkMilvusBtn = document.getElementById('checkMilvusBtn');
        this.milvusHealth = document.getElementById('milvusHealth');
        this.historyKeyword = document.getElementById('historyKeyword');
        this.historyStatus = document.getElementById('historyStatus');
        this.pageSizeSelect = document.getElementById('pageSizeSelect');
        this.historyTableBody = document.getElementById('historyTableBody');
        this.historyResultCount = document.getElementById('historyResultCount');
        this.historyPageInfo = document.getElementById('historyPageInfo');
        this.prevPageBtn = document.getElementById('prevPageBtn');
        this.nextPageBtn = document.getElementById('nextPageBtn');
        this.pageNumbers = document.getElementById('pageNumbers');
        this.historyDetailHeading = document.getElementById('historyDetailHeading');
        this.historyDetail = document.getElementById('historyDetail');
        this.longMemoryBtn = document.getElementById('longMemoryBtn');
        this.shortMemoryBtn = document.getElementById('shortMemoryBtn');
        this.seedHistoryBtn = document.getElementById('seedHistoryBtn');
        this.syncVisibleRowsBtn = document.getElementById('syncVisibleRowsBtn');
        this.ragUploadTriggerBtn = document.getElementById('ragUploadTriggerBtn');
        this.ragFileInput = document.getElementById('ragFileInput');
        this.ragUploadZone = document.getElementById('ragUploadZone');
        this.ragDocFilter = document.getElementById('ragDocFilter');
        this.ragDocList = document.getElementById('ragDocList');
        this.ragLibraryMeta = document.getElementById('ragLibraryMeta');
        this.ragQueryInput = document.getElementById('ragQueryInput');
        this.ragSearchBtn = document.getElementById('ragSearchBtn');
        this.ragQueryHints = document.getElementById('ragQueryHints');
        this.ragResultMeta = document.getElementById('ragResultMeta');
        this.ragTopKSelect = document.getElementById('ragTopKSelect');
        this.ragResultList = document.getElementById('ragResultList');
        this.refreshToolsBtn = document.getElementById('refreshToolsBtn');
        this.localToolsTabBtn = document.getElementById('localToolsTabBtn');
        this.mcpToolsTabBtn = document.getElementById('mcpToolsTabBtn');
        this.localToolsPanel = document.getElementById('localToolsPanel');
        this.mcpToolsPanel = document.getElementById('mcpToolsPanel');
        this.toolsMeta = document.getElementById('toolsMeta');
        this.toolsList = document.getElementById('toolsList');
        this.refreshMcpBtn = document.getElementById('refreshMcpBtn');
        this.mcpMeta = document.getElementById('mcpMeta');
        this.mcpForm = document.getElementById('mcpForm');
        this.mcpServerName = document.getElementById('mcpServerName');
        this.mcpTransportType = document.getElementById('mcpTransportType');
        this.mcpBaseUrl = document.getElementById('mcpBaseUrl');
        this.mcpEndpoint = document.getElementById('mcpEndpoint');
        this.mcpRequestTimeoutSeconds = document.getElementById('mcpRequestTimeoutSeconds');
        this.mcpHeadersJson = document.getElementById('mcpHeadersJson');
        this.mcpServerList = document.getElementById('mcpServerList');
        this.mcpToolList = document.getElementById('mcpToolList');
        this.refreshSkillsBtn = document.getElementById('refreshSkillsBtn');
        this.skillInstallForm = document.getElementById('skillInstallForm');
        this.skillSourceUrl = document.getElementById('skillSourceUrl');
        this.skillOverwrite = document.getElementById('skillOverwrite');
        this.skillsMeta = document.getElementById('skillsMeta');
        this.skillsList = document.getElementById('skillsList');
        this.skillDetail = document.getElementById('skillDetail');
        this.refreshScheduledBtn = document.getElementById('refreshScheduledBtn');
        this.scheduledMeta = document.getElementById('scheduledMeta');
        this.scheduledForm = document.getElementById('scheduledForm');
        this.scheduledTaskName = document.getElementById('scheduledTaskName');
        this.scheduledQuestion = document.getElementById('scheduledQuestion');
        this.scheduledPrompt = document.getElementById('scheduledPrompt');
        this.scheduledInterval = document.getElementById('scheduledInterval');
        this.scheduledNextRunAt = document.getElementById('scheduledNextRunAt');
        this.scheduledEnabled = document.getElementById('scheduledEnabled');
        this.resetScheduledFormBtn = document.getElementById('resetScheduledFormBtn');
        this.scheduledList = document.getElementById('scheduledList');
        this.scheduledRunsMeta = document.getElementById('scheduledRunsMeta');
        this.runScheduledNowBtn = document.getElementById('runScheduledNowBtn');
        this.exportScheduledReportBtn = document.getElementById('exportScheduledReportBtn');
        this.scheduledRunsEl = document.getElementById('scheduledRuns');
        this.scheduledReport = document.getElementById('scheduledReport');
        this.refreshSandboxStatusBtn = document.getElementById('refreshSandboxStatusBtn');
        this.runSandboxSmokeBtn = document.getElementById('runSandboxSmokeBtn');
        this.sandboxCodeInput = document.getElementById('sandboxCodeInput');
        this.sandboxArgsInput = document.getElementById('sandboxArgsInput');
        this.runSandboxCodeBtn = document.getElementById('runSandboxCodeBtn');
        this.sandboxStatusCards = document.getElementById('sandboxStatusCards');
        this.sandboxResult = document.getElementById('sandboxResult');
        this.refreshTracesBtn = document.getElementById('refreshTracesBtn');
        this.traceKeyword = document.getElementById('traceKeyword');
        this.traceStatus = document.getElementById('traceStatus');
        this.tracePageSize = document.getElementById('tracePageSize');
        this.traceResultCount = document.getElementById('traceResultCount');
        this.tracePageInfo = document.getElementById('tracePageInfo');
        this.traceList = document.getElementById('traceList');
        this.traceDetail = document.getElementById('traceDetail');
        this.prevTracePageBtn = document.getElementById('prevTracePageBtn');
        this.nextTracePageBtn = document.getElementById('nextTracePageBtn');
        this.toast = document.getElementById('toast');
    }

    bindEvents() {
        this.chatPageBtn.addEventListener('click', () => this.switchPage('chat'));
        this.historyPageBtn.addEventListener('click', () => this.switchPage('history'));
        this.ragPageBtn.addEventListener('click', () => this.switchPage('rag'));
        this.toolsPageBtn.addEventListener('click', () => this.switchPage('tools'));
        this.skillsPageBtn.addEventListener('click', () => this.switchPage('skills'));
        this.scheduledPageBtn.addEventListener('click', () => this.switchPage('scheduled'));
        this.sandboxPageBtn.addEventListener('click', () => this.switchPage('sandbox'));
        this.tracesPageBtn.addEventListener('click', () => this.switchPage('traces'));
        this.copySessionBtn.addEventListener('click', () => this.copySessionId());
        this.newSessionBtn.addEventListener('click', () => this.startNewSession());
        this.clearBackendSessionBtn.addEventListener('click', () => this.clearBackendSession());
        this.aiOpsBtn.addEventListener('click', () => this.runAiOps());
        this.refreshSessionBtn.addEventListener('click', () => this.refreshSessionSummary());
        this.checkMilvusBtn.addEventListener('click', () => this.checkMilvusHealth());
        this.sendBtn.addEventListener('click', () => this.sendMessage());
        this.uploadBtn.addEventListener('click', () => this.fileInput.click());
        this.fileInput.addEventListener('change', (event) => this.uploadFile(event.target.files[0]));
        this.messageInput.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                this.sendMessage();
            }
        });

        document.querySelectorAll('.conversation-toolbar .segment').forEach(button => {
            button.addEventListener('click', () => {
                this.mode = button.dataset.mode;
                document.querySelectorAll('.conversation-toolbar .segment').forEach(item => item.classList.toggle('active', item === button));
                this.updateChatMeta();
            });
        });

        [this.historyKeyword, this.historyStatus].forEach(input => {
            input.addEventListener('input', () => {
                this.currentPage = 1;
                this.refreshMemoryFiles(false);
            });
        });
        this.pageSizeSelect.addEventListener('change', () => {
            this.pageSize = Number(this.pageSizeSelect.value);
            this.currentPage = 1;
            this.refreshMemoryFiles(false);
        });
        this.prevPageBtn.addEventListener('click', () => this.movePage(-1));
        this.nextPageBtn.addEventListener('click', () => this.movePage(1));
        this.longMemoryBtn.addEventListener('click', () => this.switchMemoryView('long'));
        this.shortMemoryBtn.addEventListener('click', () => this.switchMemoryView('short'));
        this.seedHistoryBtn.addEventListener('click', () => this.seedStaticHistory());
        this.syncVisibleRowsBtn.addEventListener('click', () => this.syncVisibleHistorySummaries());

        this.ragUploadTriggerBtn.addEventListener('click', () => this.ragFileInput.click());
        this.ragUploadZone.addEventListener('click', () => this.ragFileInput.click());
        this.ragFileInput.addEventListener('change', (event) => this.handleRagFiles(event.target.files));
        this.ragDocFilter.addEventListener('input', () => this.renderRagDocuments());
        this.ragSearchBtn.addEventListener('click', () => this.searchRag());
        this.ragQueryInput.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                this.searchRag();
            }
        });
        this.ragTopKSelect.addEventListener('change', () => this.searchRag(false));
        this.refreshToolsBtn.addEventListener('click', () => this.refreshCurrentToolsView(true));
        this.localToolsTabBtn.addEventListener('click', () => this.switchToolsView('local'));
        this.mcpToolsTabBtn.addEventListener('click', () => this.switchToolsView('mcp'));
        this.refreshSkillsBtn.addEventListener('click', () => this.refreshSkills(true));
        this.refreshScheduledBtn.addEventListener('click', () => this.refreshScheduledTasks(true));
        this.scheduledForm.addEventListener('submit', (event) => {
            event.preventDefault();
            this.saveScheduledTask();
        });
        this.resetScheduledFormBtn.addEventListener('click', () => this.resetScheduledForm());
        this.runScheduledNowBtn.addEventListener('click', () => this.runSelectedScheduleNow());
        this.exportScheduledReportBtn.addEventListener('click', () => this.exportScheduledReport());
        this.refreshSandboxStatusBtn.addEventListener('click', () => this.refreshSandboxStatus(true));
        this.runSandboxSmokeBtn.addEventListener('click', () => this.runSandboxSmokeTest());
        this.runSandboxCodeBtn.addEventListener('click', () => this.runSandboxCode());
        this.refreshMcpBtn.addEventListener('click', () => this.refreshMcpRuntime(true));
        this.mcpForm.addEventListener('submit', (event) => {
            event.preventDefault();
            this.createMcpServer();
        });
        this.skillInstallForm.addEventListener('submit', (event) => {
            event.preventDefault();
            this.installSkill();
        });
        this.refreshTracesBtn.addEventListener('click', () => this.refreshTraces(true));
        [this.traceKeyword, this.traceStatus].forEach(input => {
            input.addEventListener('input', () => {
                this.tracePage = 1;
                this.refreshTraces(false);
            });
        });
        this.tracePageSize.addEventListener('change', () => {
            this.tracePageSizeValue = Number(this.tracePageSize.value);
            this.tracePage = 1;
            this.refreshTraces(false);
        });
        this.prevTracePageBtn.addEventListener('click', () => this.moveTracePage(-1));
        this.nextTracePageBtn.addEventListener('click', () => this.moveTracePage(1));
        this.ragUploadZone.addEventListener('dragover', (event) => {
            event.preventDefault();
            this.ragUploadZone.classList.add('dragover');
        });
        this.ragUploadZone.addEventListener('dragleave', () => this.ragUploadZone.classList.remove('dragover'));
        this.ragUploadZone.addEventListener('drop', (event) => {
            event.preventDefault();
            this.ragUploadZone.classList.remove('dragover');
            this.handleRagFiles(event.dataTransfer.files);
        });
    }

    renderAll() {
        this.renderEndpoints();
        this.renderMessages();
        this.configureMemoryStatusOptions();
        this.refreshMemoryFiles();
        this.renderRagDocuments();
        this.renderRagHints();
        this.renderTools();
        this.renderMcpServers();
        this.renderMcpTools();
        this.renderSkills();
        this.renderScheduledTasks();
        this.renderSandboxStatus();
        this.renderSandboxResult();
        this.renderTraces();
        this.updateSessionDisplay();
        this.updateChatMeta();
    }

    switchPage(page) {
        const showHistory = page === 'history';
        const showRag = page === 'rag';
        const showTools = page === 'tools';
        const showSkills = page === 'skills';
        const showScheduled = page === 'scheduled';
        const showSandbox = page === 'sandbox';
        const showTraces = page === 'traces';
        this.chatPage.classList.toggle('active', !showHistory && !showRag && !showTools && !showSkills && !showScheduled && !showSandbox && !showTraces);
        this.historyPage.classList.toggle('active', showHistory);
        this.ragPage.classList.toggle('active', showRag);
        this.toolsPage.classList.toggle('active', showTools);
        this.skillsPage.classList.toggle('active', showSkills);
        this.scheduledPage.classList.toggle('active', showScheduled);
        this.sandboxPage.classList.toggle('active', showSandbox);
        this.tracesPage.classList.toggle('active', showTraces);
        this.chatPageBtn.classList.toggle('active', !showHistory && !showRag && !showTools && !showSkills && !showScheduled && !showSandbox && !showTraces);
        this.historyPageBtn.classList.toggle('active', showHistory);
        this.ragPageBtn.classList.toggle('active', showRag);
        this.toolsPageBtn.classList.toggle('active', showTools);
        this.skillsPageBtn.classList.toggle('active', showSkills);
        this.scheduledPageBtn.classList.toggle('active', showScheduled);
        this.sandboxPageBtn.classList.toggle('active', showSandbox);
        this.tracesPageBtn.classList.toggle('active', showTraces);
        if (showHistory) {
            this.captureCurrentSessionToHistory();
            this.refreshMemoryFiles();
        }
        if (showRag) {
            this.refreshRagDocumentsFromBackend();
            this.renderRagDocuments();
        }
        if (showTools) {
            this.refreshCurrentToolsView(false);
        }
        if (showSkills) {
            this.refreshSkills(false);
        }
        if (showScheduled) {
            this.refreshScheduledTasks(false);
        }
        if (showSandbox) {
            this.refreshSandboxStatus(false);
        }
        if (showTraces) {
            this.refreshTraces(false);
        }
    }

    createSessionId() {
        return `session_${Math.random().toString(36).slice(2, 10)}_${Date.now()}`;
    }

    updateSessionDisplay() {
        this.sidebarSessionId.textContent = this.sessionId;
    }

    updateChatMeta(text) {
        this.chatMeta.textContent = text || `${this.mode === 'stream' ? '流式' : '快速'}模式 · ${this.messages.length} 条前端消息`;
    }

    renderEndpoints() {
        this.endpointList.innerHTML = this.availableEndpoints.map(endpoint => `
            <div class="endpoint-pill">
                <strong>${endpoint.method} ${endpoint.path}</strong>
                <span>${endpoint.purpose}</span>
            </div>
        `).join('');
    }

    renderMessages() {
        if (this.messages.length === 0) {
            this.messagesEl.innerHTML = `
                <div class="empty-state">
                    <div class="empty-title">开始一次诊断对话</div>
                    <div class="empty-copy">你可以询问故障现象、日志、告警或让 AI Ops 自动生成诊断报告。</div>
                </div>
            `;
            return;
        }

        this.messagesEl.innerHTML = this.messages.map(message => `
            <div class="message-row ${message.role}">
                <div class="message-bubble">${this.renderMarkdown(message.content)}</div>
            </div>
        `).join('');
        this.messagesEl.querySelectorAll('pre code').forEach(block => {
            if (window.hljs) window.hljs.highlightElement(block);
        });
        this.messagesEl.scrollTop = this.messagesEl.scrollHeight;
    }

    async sendMessage() {
        const question = this.messageInput.value.trim();
        if (!question || this.isBusy) return;

        this.messageInput.value = '';
        this.messages.push({ role: 'user', content: question, timestamp: new Date().toISOString() });
        this.renderMessages();
        this.setBusy(true);

        try {
            if (this.mode === 'stream') {
                await this.sendStreamMessage(question);
            } else {
                await this.sendQuickMessage(question);
            }
            this.captureCurrentSessionToHistory();
            await this.refreshSessionSummary(false);
        } catch (error) {
            this.messages.push({ role: 'assistant', content: `请求失败：${error.message}`, timestamp: new Date().toISOString() });
            this.renderMessages();
            this.showToast(`发送失败：${error.message}`);
        } finally {
            this.setBusy(false);
        }
    }

    async sendQuickMessage(question) {
        this.updateChatMeta('正在请求 /api/chat ...');
        const payload = await this.fetchJson(`${this.apiBaseUrl}/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ Id: this.sessionId, Question: question })
        });

        const chatResponse = payload.data;
        if (payload.code !== 200 || !chatResponse) {
            throw new Error(payload.message || '接口返回异常');
        }
        if (!chatResponse.success) {
            throw new Error(chatResponse.errorMessage || '对话失败');
        }
        this.messages.push({ role: 'assistant', content: chatResponse.answer || '（无回复内容）', timestamp: new Date().toISOString() });
        this.renderMessages();
    }

    async sendStreamMessage(question) {
        this.updateChatMeta('正在读取 /api/chat_stream ...');
        const response = await fetch(`${this.apiBaseUrl}/chat_stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ Id: this.sessionId, Question: question })
        });
        if (!response.ok || !response.body) {
            throw new Error(`HTTP ${response.status}`);
        }

        const assistantMessage = { role: 'assistant', content: '', timestamp: new Date().toISOString() };
        this.messages.push(assistantMessage);
        this.renderMessages();

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
                if (!line.startsWith('data:')) continue;
                const raw = line.slice(5).trim();
                if (!raw || raw === '[DONE]') continue;
                try {
                    const event = JSON.parse(raw);
                    if (event.type === 'content') {
                        assistantMessage.content += event.data || '';
                        this.renderMessages();
                    }
                    if (event.type === 'error') {
                        throw new Error(event.data || '流式响应错误');
                    }
                } catch (error) {
                    if (error instanceof SyntaxError) {
                        assistantMessage.content += raw;
                        this.renderMessages();
                    } else {
                        throw error;
                    }
                }
            }
        }
    }

    async refreshSessionSummary(showToast = true) {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/chat/session/${encodeURIComponent(this.sessionId)}`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || '会话不存在');
            }
            this.sessionPairCount.textContent = payload.data.messagePairCount;
            this.sessionCreateTime.textContent = this.formatDate(payload.data.createTime);
            if (showToast) this.showToast('后端会话概要已刷新');
        } catch (error) {
            this.sessionPairCount.textContent = '-';
            this.sessionCreateTime.textContent = '-';
            if (showToast) this.showToast(`概要查询失败：${error.message}`);
        }
    }

    async clearBackendSession() {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/chat/clear`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ Id: this.sessionId })
            });
            if (payload.code !== 200) throw new Error(payload.message || '清空失败');
            this.messages = [];
            this.renderMessages();
            this.captureCurrentSessionToHistory();
            await this.refreshSessionSummary(false);
            this.showToast('后端历史已清空');
        } catch (error) {
            this.showToast(`清空失败：${error.message}`);
        }
    }

    async runAiOps() {
        if (this.isBusy) return;
        this.setBusy(true);
        this.messages.push({ role: 'user', content: '触发 AI Ops 自动诊断', timestamp: new Date().toISOString() });
        const message = { role: 'assistant', content: '任务创建中...', timestamp: new Date().toISOString() };
        this.messages.push(message);
        this.renderMessages();

        try {
            const created = await this.fetchJson(`${this.apiBaseUrl}/ai_ops/tasks`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sessionId: this.sessionId, triggerSource: 'MANUAL' })
            });
            if (created.code !== 200 || !created.data?.taskId) {
                throw new Error(created.message || '创建任务失败');
            }
            const taskId = created.data.taskId;
            message.content = `AI Ops 任务已创建：${taskId}\n正在轮询诊断结果...`;
            this.renderMessages();

            const finalTask = await this.pollAiOpsTask(taskId, message);
            message.content = finalTask.status === 'SUCCESS'
                ? (finalTask.finalReport || '任务成功，但未返回报告。')
                : `任务失败：${finalTask.errorMessage || '未知错误'}`;
            this.renderMessages();
            this.captureCurrentSessionToHistory();
        } catch (error) {
            message.content = `AI Ops 执行失败：${error.message}`;
            this.renderMessages();
        } finally {
            this.setBusy(false);
        }
    }

    async pollAiOpsTask(taskId, message) {
        for (let attempt = 1; attempt <= 60; attempt++) {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/ai_ops/tasks/${encodeURIComponent(taskId)}`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || '任务查询失败');
            }
            const task = payload.data;
            message.content = `AI Ops 任务：${taskId}\n状态：${task.status}\n轮询：${attempt}/60`;
            this.renderMessages();
            if (task.status === 'SUCCESS' || task.status === 'FAILED') return task;
            await this.sleep(3000);
        }
        throw new Error('任务轮询超时');
    }

    async uploadFile(file) {
        if (!file) return;
        if (!/\.(txt|md|markdown)$/i.test(file.name)) {
            this.showToast('只支持 TXT 或 Markdown 文件');
            return;
        }
        const formData = new FormData();
        formData.append('file', file);
        this.setBusy(true);
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/rag/documents`, { method: 'POST', body: formData });
            if (payload.code !== 200) throw new Error(payload.message || '上传失败');
            this.messages.push({ role: 'assistant', content: `知识库文件上传成功：${file.name}`, timestamp: new Date().toISOString() });
            this.renderMessages();
        } catch (error) {
            this.showToast(`上传失败：${error.message}`);
        } finally {
            this.fileInput.value = '';
            this.setBusy(false);
        }
    }

    async checkMilvusHealth() {
        this.milvusHealth.textContent = '检测中';
        try {
            const payload = await this.fetchJson(`${window.location.origin}/milvus/health`);
            this.milvusHealth.textContent = payload.status || payload.message || '正常';
        } catch (error) {
            this.milvusHealth.textContent = '异常';
            this.showToast(`Milvus 检测失败：${error.message}`);
        }
    }

    startNewSession() {
        this.captureCurrentSessionToHistory();
        this.sessionId = this.createSessionId();
        this.messages = [];
        this.sessionPairCount.textContent = '-';
        this.sessionCreateTime.textContent = '-';
        this.updateSessionDisplay();
        this.updateChatMeta();
        this.renderMessages();
    }

    captureCurrentSessionToHistory() {
        // 短期记忆由后端异步写入 MySQL agent_execution_memory。
    }

    async seedStaticHistory() {
        const path = `topics/new-memory-${Date.now()}.md`;
        const payload = await this.fetchJson(`${this.apiBaseUrl}/memory/files`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path, content: '# New Memory\n\n- ' })
        });
        if (payload.code !== 200) {
            this.showToast(payload.message || '创建失败');
            return;
        }
        this.selectedHistoryId = path;
        await this.refreshMemoryFiles(false);
        await this.selectHistoryRow(path);
        this.showToast('已创建主题记忆');
    }

    getFilteredRows() {
        if (this.memoryView === 'short') {
            return this.historyRows;
        }
        const keyword = this.historyKeyword.value.trim().toLowerCase();
        const status = this.historyStatus.value;
        return this.historyRows.filter(row => {
            const keywordMatched = !keyword
                || row.path.toLowerCase().includes(keyword)
                || row.type.toLowerCase().includes(keyword);
            const statusMatched = status === 'all' || row.type === status;
            return keywordMatched && statusMatched;
        });
    }

    renderHistoryPage() {
        const rows = this.getFilteredRows();
        const totalItems = this.memoryView === 'short' ? this.memoryTotal : rows.length;
        const totalPages = Math.max(1, Math.ceil(totalItems / this.pageSize));
        this.currentPage = Math.min(this.currentPage, totalPages);
        const start = (this.currentPage - 1) * this.pageSize;
        const visibleRows = this.memoryView === 'short' ? rows : rows.slice(start, start + this.pageSize);

        this.historyResultCount.textContent = `${totalItems} 条结果`;
        this.historyPageInfo.textContent = `第 ${this.currentPage} / ${totalPages} 页`;
        this.prevPageBtn.disabled = this.currentPage <= 1;
        this.nextPageBtn.disabled = this.currentPage >= totalPages;

        this.historyTableBody.innerHTML = visibleRows.map(row => `
            <tr data-id="${this.escapeAttr(this.memoryRowKey(row))}" class="${this.memoryRowKey(row) === this.selectedHistoryId ? 'selected' : ''}">
                <td><span class="badge ${this.memoryRowType(row).toLowerCase()}">${this.memoryRowType(row)}</span></td>
                <td>
                    <div class="cell-title">${this.escapeHtml(this.memoryRowTitle(row))}</div>
                    <div class="cell-subtitle">${this.escapeHtml(this.memoryRowSubtitle(row))}</div>
                </td>
            </tr>
        `).join('');

        this.historyTableBody.querySelectorAll('tr').forEach(rowEl => {
            rowEl.addEventListener('click', () => this.selectHistoryRow(rowEl.dataset.id));
        });

        this.pageNumbers.innerHTML = '';
        for (let page = 1; page <= totalPages; page++) {
            const button = document.createElement('button');
            button.className = `page-dot ${page === this.currentPage ? 'active' : ''}`;
            button.type = 'button';
            button.textContent = String(page);
            button.addEventListener('click', () => {
                this.currentPage = page;
                if (this.memoryView === 'short') {
                    this.refreshMemoryFiles(false);
                } else {
                    this.renderHistoryPage();
                }
            });
            this.pageNumbers.appendChild(button);
        }

        if (!this.selectedHistoryId && visibleRows[0]) {
            this.selectHistoryRow(this.memoryRowKey(visibleRows[0]), false);
        } else {
            this.renderHistoryDetail();
        }
    }

    async selectHistoryRow(id, rerenderTable = true) {
        this.selectedHistoryId = id;
        if (this.memoryView === 'long') {
            await this.loadMemoryFileContent(id);
        } else {
            await this.loadExecutionMemoryContent(id);
        }
        this.renderHistoryDetail();
        if (rerenderTable) this.renderHistoryPage();
    }

    renderHistoryDetail() {
        const row = this.historyRows.find(item => this.memoryRowKey(item) === this.selectedHistoryId);
        if (!row) {
            this.historyDetail.innerHTML = this.memoryView === 'long' ? '选择一个长期记忆文件查看详情。' : '选择一条短期记忆记录查看详情。';
            return;
        }
        if (this.memoryView === 'short') {
            this.renderShortMemoryDetail(row);
            return;
        }

        this.historyDetail.innerHTML = `
            <div class="detail-title">${this.escapeHtml(row.path)}</div>
            <div class="detail-meta">
                <span><strong>类型</strong>${row.type}</span>
                <span><strong>大小</strong>${this.formatFileSize(row.size || 0)}</span>
                <span><strong>更新时间</strong>${this.formatDate(row.updatedAt)}</span>
            </div>
            <div class="detail-preview">
                <div class="block-heading">Markdown 内容</div>
                <textarea class="memory-editor" id="memoryEditor">${this.escapeHtml(row.content || '')}</textarea>
                <div class="memory-actions">
                    <button class="accent-btn" id="saveMemoryBtn" type="button">保存</button>
                    <button class="secondary-btn" id="deleteMemoryBtn" type="button" ${row.path === 'MEMORY.md' ? 'disabled' : ''}>删除</button>
                </div>
            </div>
        `;
        document.getElementById('saveMemoryBtn').addEventListener('click', () => this.saveSelectedMemory());
        document.getElementById('deleteMemoryBtn').addEventListener('click', () => this.deleteSelectedMemory());
    }

    movePage(delta) {
        this.currentPage += delta;
        if (this.memoryView === 'short') {
            this.refreshMemoryFiles(false);
        } else {
            this.renderHistoryPage();
        }
    }

    async syncVisibleHistorySummaries() {
        await this.refreshMemoryFiles();
        this.showToast(this.memoryView === 'long' ? '长期记忆已刷新' : '短期记忆已刷新');
    }

    switchMemoryView(view) {
        this.memoryView = view;
        this.currentPage = 1;
        this.selectedHistoryId = null;
        this.longMemoryBtn.classList.toggle('active', view === 'long');
        this.shortMemoryBtn.classList.toggle('active', view === 'short');
        this.seedHistoryBtn.textContent = '新建主题记忆';
        this.seedHistoryBtn.style.display = view === 'long' ? '' : 'none';
        this.syncVisibleRowsBtn.textContent = view === 'long' ? '刷新记忆文件' : '刷新短期记忆';
        this.historyDetailHeading.textContent = view === 'long' ? '记忆文件编辑' : '短期记忆详情';
        this.historyKeyword.placeholder = view === 'long' ? '按路径过滤' : '按 executionId、sessionId、内容过滤';
        this.configureMemoryStatusOptions();
        this.refreshMemoryFiles();
    }

    configureMemoryStatusOptions() {
        const options = this.memoryView === 'long'
            ? [
                ['all', '全部'],
                ['LONG_TERM_INDEX', '长期索引'],
                ['LONG_TERM_TOPIC', '长期主题']
            ]
            : [
                ['all', '全部'],
                ['SUCCESS', '成功'],
                ['FAILED', '失败'],
                ['MANUAL', '手动'],
                ['COMPRESSED', '压缩记录']
            ];
        this.historyStatus.innerHTML = options
            .map(([value, label]) => `<option value="${value}">${label}</option>`)
            .join('');
    }

    async refreshMemoryFiles(showError = true) {
        try {
            if (this.memoryView === 'short') {
                await this.refreshExecutionMemories(showError);
                return;
            }
            const url = new URL(`${this.apiBaseUrl}/memory/files`);
            const type = this.historyStatus.value || 'all';
            if (type !== 'all') url.searchParams.set('type', type);
            const keyword = this.historyKeyword.value.trim();
            if (keyword) url.searchParams.set('keyword', keyword);
            const payload = await this.fetchJson(url.toString());
            if (payload.code !== 200) throw new Error(payload.message || '查询记忆文件失败');
            this.historyRows = payload.data?.items || [];
            this.renderHistoryPage();
        } catch (error) {
            if (showError) this.showToast(`记忆文件加载失败：${error.message}`);
        }
    }

    async loadMemoryFileContent(path) {
        const url = new URL(`${this.apiBaseUrl}/memory/files/content`);
        url.searchParams.set('path', path);
        const payload = await this.fetchJson(url.toString());
        if (payload.code !== 200) {
            this.showToast(payload.message || '读取失败');
            return;
        }
        const index = this.historyRows.findIndex(row => row.path === path);
        if (index >= 0) this.historyRows[index] = payload.data;
    }

    async refreshExecutionMemories(showError = true) {
        try {
            const url = new URL(`${this.apiBaseUrl}/memory/executions`);
            url.searchParams.set('page', this.currentPage);
            url.searchParams.set('pageSize', this.pageSize);
            const status = this.historyStatus.value || 'all';
            if (status !== 'all') url.searchParams.set('status', status);
            const keyword = this.historyKeyword.value.trim();
            if (keyword) url.searchParams.set('keyword', keyword);
            const payload = await this.fetchJson(url.toString());
            if (payload.code !== 200) throw new Error(payload.message || '查询短期记忆失败');
            this.historyRows = payload.data?.items || [];
            this.memoryTotal = payload.data?.total || 0;
            this.renderHistoryPage();
        } catch (error) {
            if (showError) this.showToast(`短期记忆加载失败：${error.message}`);
        }
    }

    async loadExecutionMemoryContent(executionId) {
        const url = new URL(`${this.apiBaseUrl}/memory/executions/detail`);
        url.searchParams.set('executionId', executionId);
        const payload = await this.fetchJson(url.toString());
        if (payload.code !== 200) {
            this.showToast(payload.message || '读取失败');
            return;
        }
        const index = this.historyRows.findIndex(row => row.executionId === executionId);
        if (index >= 0) this.historyRows[index] = payload.data;
    }

    renderShortMemoryDetail(row) {
        this.historyDetail.innerHTML = `
            <div class="detail-title">${this.escapeHtml(this.shortMemoryTitle(row))}</div>
            <div class="detail-meta">
                <span><strong>执行 ID</strong>${this.escapeHtml(row.executionId || '-')}</span>
                <span><strong>Session</strong>${this.escapeHtml(row.sessionId || '-')}</span>
                <span><strong>状态</strong>${this.escapeHtml(row.status || '-')}</span>
                <span><strong>耗时</strong>${row.durationMs ?? 0} ms</span>
                <span><strong>更新时间</strong>${this.formatDate(row.updatedAt || row.createdAt)}</span>
            </div>
            <div class="detail-preview short-memory-detail">
                <div class="block-heading">短期记忆内容</div>
                <div class="short-memory-fields">
                    <div class="short-memory-field user-field">
                        <label class="memory-label">用户输入</label>
                        <textarea class="memory-editor compact" id="shortUserInput">${this.escapeHtml(row.userInput || '')}</textarea>
                    </div>
                    <div class="short-memory-field output-field">
                        <label class="memory-label">Agent 输出</label>
                        <textarea class="memory-editor" id="shortAgentOutput">${this.escapeHtml(row.agentOutput || '')}</textarea>
                    </div>
                    <div class="short-memory-field status-field">
                        <label class="memory-label">状态</label>
                        <input class="memory-input" id="shortStatus" value="${this.escapeAttr(row.status || 'MANUAL')}">
                    </div>
                </div>
                <div class="memory-actions">
                    <button class="accent-btn" id="saveShortMemoryBtn" type="button">保存</button>
                    <button class="secondary-btn" id="deleteShortMemoryBtn" type="button">删除</button>
                </div>
            </div>
        `;
        document.getElementById('saveShortMemoryBtn').addEventListener('click', () => this.saveSelectedShortMemory());
        document.getElementById('deleteShortMemoryBtn').addEventListener('click', () => this.deleteSelectedShortMemory());
    }

    async saveSelectedMemory() {
        const row = this.historyRows.find(item => item.path === this.selectedHistoryId);
        const editor = document.getElementById('memoryEditor');
        if (!row || !editor) return;
        const payload = await this.fetchJson(`${this.apiBaseUrl}/memory/files`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path: row.path, content: editor.value })
        });
        if (payload.code !== 200) {
            this.showToast(payload.message || '保存失败');
            return;
        }
        await this.refreshMemoryFiles(false);
        await this.selectHistoryRow(row.path);
        this.showToast('记忆已保存');
    }

    async saveSelectedShortMemory() {
        const row = this.historyRows.find(item => item.executionId === this.selectedHistoryId);
        if (!row) return;
        const payload = await this.fetchJson(`${this.apiBaseUrl}/memory/executions?executionId=${encodeURIComponent(row.executionId)}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                executionId: row.executionId,
                sessionId: row.sessionId,
                userInput: document.getElementById('shortUserInput').value,
                agentOutput: document.getElementById('shortAgentOutput').value,
                memoryIndexSnapshot: row.memoryIndexSnapshot,
                shortMemorySnapshot: row.shortMemorySnapshot,
                status: document.getElementById('shortStatus').value || 'MANUAL',
                errorMessage: row.errorMessage
            })
        });
        if (payload.code !== 200) {
            this.showToast(payload.message || '保存失败');
            return;
        }
        await this.refreshMemoryFiles(false);
        await this.selectHistoryRow(row.executionId);
        this.showToast('短期记忆已保存');
    }

    async deleteSelectedShortMemory() {
        const row = this.historyRows.find(item => item.executionId === this.selectedHistoryId);
        if (!row) return;
        const url = new URL(`${this.apiBaseUrl}/memory/executions`);
        url.searchParams.set('executionId', row.executionId);
        const payload = await this.fetchJson(url.toString(), { method: 'DELETE' });
        if (payload.code !== 200) {
            this.showToast(payload.message || '删除失败');
            return;
        }
        this.selectedHistoryId = null;
        await this.refreshMemoryFiles(false);
        this.showToast('短期记忆已删除');
    }

    async deleteSelectedMemory() {
        const row = this.historyRows.find(item => item.path === this.selectedHistoryId);
        if (!row || row.path === 'MEMORY.md') return;
        const url = new URL(`${this.apiBaseUrl}/memory/files`);
        url.searchParams.set('path', row.path);
        const payload = await this.fetchJson(url.toString(), { method: 'DELETE' });
        if (payload.code !== 200) {
            this.showToast(payload.message || '删除失败');
            return;
        }
        this.selectedHistoryId = null;
        await this.refreshMemoryFiles(false);
        this.showToast('记忆已删除');
    }

    memoryRowKey(row) {
        return this.memoryView === 'long' ? row.path : row.executionId;
    }

    memoryRowTitle(row) {
        return this.memoryView === 'long' ? row.path : this.shortMemoryTitle(row);
    }

    memoryRowSubtitle(row) {
        if (this.memoryView === 'long') {
            return row.path === 'MEMORY.md' ? '200 行限制' : 'Markdown 记忆文件';
        }
        return row.sessionId || '无 session';
    }

    memoryRowType(row) {
        return this.memoryView === 'long' ? row.type : (row.status || 'UNKNOWN');
    }

    shortMemoryTitle(row) {
        const input = (row.userInput || '').trim().replace(/\s+/g, ' ');
        if (!input) {
            return row.executionId || '未命名短期记忆';
        }
        return input.length > 60 ? `${input.slice(0, 60)}...` : input;
    }

    async handleRagFiles(fileList) {
        const files = Array.from(fileList || []);
        const validFiles = files.filter(file => /\.(txt|md|markdown)$/i.test(file.name));
        if (validFiles.length === 0) {
            this.showToast('请选择 TXT 或 Markdown 文档');
            return;
        }

        let backendUploadCount = 0;
        for (const file of validFiles) {
            try {
                await this.uploadRagDocumentToBackend(file);
                backendUploadCount++;
            } catch (error) {
                this.showToast(`后端上传失败：${error.message}`);
            }
        }

        this.ragFileInput.value = '';
        if (backendUploadCount > 0) {
            await this.refreshRagDocumentsFromBackend(false);
        }
        this.renderRagDocuments();
        this.renderRagHints();
        this.showToast(`已上传并索引 ${backendUploadCount}/${validFiles.length} 个文档`);
    }

    async uploadRagDocumentToBackend(file) {
        const formData = new FormData();
        formData.append('file', file);
        const payload = await this.fetchJson(`${this.apiBaseUrl}/rag/documents`, {
            method: 'POST',
            body: formData
        });
        if (payload.code !== 200 || !payload.data) {
            throw new Error(payload.message || 'RAG 文档上传失败');
        }
        return payload.data;
    }

    async refreshRagDocumentsFromBackend(showToast = false) {
        try {
            const keyword = this.ragDocFilter ? this.ragDocFilter.value.trim() : '';
            const url = new URL(`${this.apiBaseUrl}/rag/documents`);
            url.searchParams.set('page', '1');
            url.searchParams.set('pageSize', '100');
            if (keyword) {
                url.searchParams.set('keyword', keyword);
            }

            const payload = await this.fetchJson(url.toString());
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || '文档列表查询失败');
            }

            const backendDocs = (payload.data.items || []).map(item => ({
                id: `backend_${item.documentId}`,
                documentId: item.documentId,
                title: item.fileName,
                source: 'backend',
                tags: ['Backend', item.extension || 'doc', this.formatFileSize(item.fileSize)],
                updatedAt: item.updatedAt ? new Date(item.updatedAt).toISOString() : new Date().toISOString(),
                content: `${item.fileName}\n${item.filePath || ''}`,
                filePath: item.filePath,
                status: item.status
            }));

            this.ragDocuments = backendDocs;
            if (!this.selectedRagDocId && this.ragDocuments[0]) {
                this.selectedRagDocId = this.ragDocuments[0].id;
            }
            if (this.selectedRagDocId && !this.ragDocuments.some(doc => doc.id === this.selectedRagDocId)) {
                this.selectedRagDocId = this.ragDocuments[0]?.id || null;
            }
            this.renderRagDocuments();
            if (showToast) {
                this.showToast('后端知识库列表已刷新');
            }
        } catch (error) {
            this.ragLibraryMeta.textContent = '后端列表不可用';
            this.ragDocList.innerHTML = '<div class="history-detail-empty">无法加载后端知识库文档，请检查服务、Milvus 和上传目录。</div>';
            if (showToast) {
                this.showToast(`刷新后端知识库失败：${error.message}`);
            }
        }
    }

    renderRagDocuments() {
        const keyword = this.ragDocFilter.value.trim().toLowerCase();
        const docs = this.ragDocuments.filter(doc => {
            const haystack = `${doc.title} ${doc.tags.join(' ')} ${doc.content}`.toLowerCase();
            return !keyword || haystack.includes(keyword);
        });

        this.ragLibraryMeta.textContent = `${this.ragDocuments.length} 个文档 · ${docs.length} 个可见`;
        this.ragDocList.innerHTML = docs.map(doc => `
            <div class="rag-doc-card ${doc.id === this.selectedRagDocId ? 'active' : ''}" data-id="${this.escapeAttr(doc.id)}">
                <div class="rag-card-title">${this.escapeHtml(doc.title)}</div>
                <div class="rag-card-meta">
                    <span>${this.ragSourceLabel(doc.source)}</span>
                    <span>${this.formatDate(doc.updatedAt)}</span>
                    <span>${this.countWords(doc.content)} 字</span>
                </div>
                <div class="rag-card-meta">
                    ${doc.tags.map(tag => `<span class="rag-tag">${this.escapeHtml(tag)}</span>`).join('')}
                </div>
            </div>
        `).join('') || '<div class="history-detail-empty">没有匹配的文档。</div>';

        this.ragDocList.querySelectorAll('.rag-doc-card').forEach(card => {
            card.addEventListener('click', () => {
                this.selectedRagDocId = card.dataset.id;
                this.renderRagDocuments();
                const doc = this.ragDocuments.find(item => item.id === this.selectedRagDocId);
                if (doc) {
                    this.ragQueryInput.value = doc.title;
                }
            });
        });
    }

    renderRagHints() {
        const hints = ['CPU 告警', '磁盘清理', '服务不可用', '接口慢响应', '内存泄漏'];
        this.ragQueryHints.innerHTML = hints.map(hint => `
            <button class="rag-hint" type="button">${this.escapeHtml(hint)}</button>
        `).join('');
        this.ragQueryHints.querySelectorAll('.rag-hint').forEach(button => {
            button.addEventListener('click', () => {
                this.ragQueryInput.value = button.textContent;
                this.searchRag();
            });
        });
    }

    async searchRag(showEmptyToast = true) {
        const query = this.ragQueryInput.value.trim();
        if (!query) {
            if (showEmptyToast) this.showToast('请输入 RAG 查询关键词');
            this.ragResultMeta.textContent = '等待查询';
            return;
        }

        const terms = this.tokenize(query);
        const topK = Number(this.ragTopKSelect.value || 5);
        this.ragResultMeta.textContent = '正在请求后端召回...';

        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/rag/retrieve`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text: query, topK })
            });
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || 'RAG 召回失败');
            }
            this.renderBackendRagResults(payload.data, terms);
            return;
        } catch (error) {
            this.showToast(`后端召回失败：${error.message}`);
        }
        this.ragResultMeta.textContent = '召回失败';
        this.ragResultList.innerHTML = `
            <div class="empty-state compact">
                <div class="empty-title">后端召回不可用</div>
                <div class="empty-copy">请确认后端服务、Milvus、Embedding 配置正常，并且已经上传文档。</div>
            </div>
        `;
    }

    renderBackendRagResults(data, terms) {
        const results = data.results || [];
        const scoreDirection = data.higherScoreBetter ? '越大越相似' : '越小越相似';
        this.ragResultMeta.textContent = `${results.length} 条召回 · ${data.scoreType || 'score'} · ${scoreDirection}`;
        this.ragResultList.innerHTML = results.map(result => {
            const title = result.fileName || result.source || result.chunkId || `Chunk ${result.rank}`;
            return `
                <div class="rag-result-card">
                    <div class="rag-score-row">
                        <div class="rag-card-title">#${result.rank} ${this.escapeHtml(title)}</div>
                        <div class="rag-score">distance ${this.formatNumber(result.distance)}</div>
                    </div>
                    <div class="rag-card-meta">
                        <span class="rag-tag">${this.escapeHtml(result.scoreType || data.scoreType || 'L2_DISTANCE')}</span>
                        ${result.source ? `<span class="rag-tag">${this.escapeHtml(result.source)}</span>` : ''}
                        ${result.chunkId ? `<span class="rag-tag">${this.escapeHtml(result.chunkId)}</span>` : ''}
                    </div>
                    <div class="rag-snippet">${this.highlightTerms(result.content || '', terms)}</div>
                </div>
            `;
        }).join('') || `
            <div class="empty-state compact">
                <div class="empty-title">没有召回结果</div>
                <div class="empty-copy">请先上传并索引文档，或换一段更具体的输入。</div>
            </div>
        `;
    }

    async refreshTools(showToast = false) {
        try {
            this.toolsMeta.textContent = '正在加载...';
            const payload = await this.fetchJson(`${this.apiBaseUrl}/tools`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || '工具列表查询失败');
            }
            this.tools = payload.data.items || [];
            this.renderTools();
            if (showToast) this.showToast('工具列表已刷新');
        } catch (error) {
            this.toolsMeta.textContent = '加载失败';
            this.toolsList.innerHTML = `
                <div class="history-detail-empty">无法加载工具列表：${this.escapeHtml(error.message)}</div>
            `;
            if (showToast) this.showToast(`工具列表加载失败：${error.message}`);
        }
    }

    switchToolsView(view) {
        this.toolsView = view;
        const showLocal = view === 'local';
        const showMcp = view === 'mcp';
        this.localToolsTabBtn.classList.toggle('active', showLocal);
        this.mcpToolsTabBtn.classList.toggle('active', showMcp);
        this.localToolsPanel.classList.toggle('active', showLocal);
        this.mcpToolsPanel.classList.toggle('active', showMcp);
        this.refreshToolsBtn.textContent = showLocal ? '刷新' : '刷新 MCP';
        this.refreshCurrentToolsView(false);
    }

    async refreshCurrentToolsView(showToast = false) {
        if (this.toolsView === 'mcp') {
            await this.refreshMcpServers(showToast);
            await this.refreshMcpTools(false);
            return;
        }
        await this.refreshTools(showToast);
    }

    renderTools() {
        if (!this.toolsList || !this.toolsMeta) return;
        const enabledCount = this.tools.filter(tool => tool.enabled).length;
        this.toolsMeta.textContent = `${this.tools.length} 个工具 · ${enabledCount} 个已启用`;
        this.toolsList.innerHTML = this.tools.map(tool => `
            <article class="tool-card ${tool.enabled ? 'enabled' : ''} ${tool.available === false ? 'unavailable' : ''}">
                <div class="tool-card-main">
                    <div class="tool-title-row">
                        <div>
                            <div class="tool-name">${this.escapeHtml(tool.displayName || tool.toolName)}</div>
                            <div class="tool-code">${this.escapeHtml(tool.toolName || '-')} · ${this.escapeHtml(tool.sourceClass || '-')}</div>
                        </div>
                        <span class="risk-badge ${this.riskClass(tool.riskLevel)}">${this.escapeHtml(tool.riskLevel || 'LOW')}</span>
                    </div>
                    <div class="tool-description">${this.escapeHtml(tool.description || '')}</div>
                    <div class="tool-meta-row">
                        <span>${tool.available === false ? '后端不可用' : '后端可用'}</span>
                        <span>更新时间 ${this.formatDate(tool.updatedAt)}</span>
                    </div>
                </div>
                <label class="tool-switch">
                    <input type="checkbox" data-tool-name="${this.escapeAttr(tool.toolName)}" ${tool.enabled ? 'checked' : ''} ${tool.available === false ? 'disabled' : ''}>
                    <span></span>
                </label>
            </article>
        `).join('') || `
            <div class="empty-state compact">
                <div class="empty-title">没有可管理工具</div>
                <div class="empty-copy">后端没有扫描到带 @ManagedTool 的本地 Tool Bean。</div>
            </div>
        `;

        this.toolsList.querySelectorAll('.tool-switch input').forEach(input => {
            input.addEventListener('change', () => this.toggleTool(input.dataset.toolName, input.checked, input));
        });
    }

    async toggleTool(toolName, enabled, input) {
        if (!toolName) return;
        input.disabled = true;
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/tools/${encodeURIComponent(toolName)}/enabled`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled })
            });
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || '工具开关更新失败');
            }
            const index = this.tools.findIndex(tool => tool.toolName === toolName);
            if (index >= 0) this.tools[index] = payload.data;
            this.renderTools();
            this.showToast(`${payload.data.displayName || toolName} 已${payload.data.enabled ? '启用' : '停用'}`);
        } catch (error) {
            input.checked = !enabled;
            input.disabled = false;
            this.showToast(`工具开关更新失败：${error.message}`);
        }
    }

    async refreshMcpServers(showToast = false) {
        try {
            this.mcpMeta.textContent = '正在加载 MCP...';
            const payload = await this.fetchJson(`${this.apiBaseUrl}/mcp/servers`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || 'MCP 服务列表查询失败');
            }
            this.mcpServers = payload.data.items || [];
            this.renderMcpServers();
            if (showToast) this.showToast('MCP 服务已刷新');
        } catch (error) {
            this.mcpMeta.textContent = 'MCP 加载失败';
            this.mcpServerList.innerHTML = `<div class="history-detail-empty">无法加载 MCP 服务：${this.escapeHtml(error.message)}</div>`;
            if (showToast) this.showToast(`MCP 服务加载失败：${error.message}`);
        }
    }

    async refreshMcpTools(showToast = false) {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/mcp/tools`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || 'MCP 工具查询失败');
            }
            this.mcpTools = payload.data.items || [];
            this.renderMcpTools();
            if (showToast) this.showToast('MCP 工具快照已刷新');
        } catch (error) {
            this.mcpToolList.innerHTML = `<div class="history-detail-empty">无法加载 MCP 工具：${this.escapeHtml(error.message)}</div>`;
        }
    }

    async refreshMcpRuntime(showToast = false) {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/mcp/servers/refresh`, { method: 'POST' });
            if (payload.code !== 200) {
                throw new Error(payload.message || 'MCP 运行时刷新失败');
            }
            this.mcpServers = payload.data?.items || [];
            await this.refreshMcpTools(false);
            this.renderMcpServers();
            if (showToast) this.showToast('MCP 运行时已刷新');
        } catch (error) {
            if (showToast) this.showToast(`MCP 运行时刷新失败：${error.message}`);
        }
    }

    async createMcpServer() {
        const request = {
            serverName: this.mcpServerName.value.trim(),
            transportType: this.mcpTransportType.value,
            baseUrl: this.mcpBaseUrl.value.trim(),
            endpoint: this.mcpEndpoint.value.trim(),
            headersJson: this.mcpHeadersJson.value.trim() || null,
            requestTimeoutSeconds: Number(this.mcpRequestTimeoutSeconds.value || 30),
            enabled: true
        };
        if (!request.serverName || !request.baseUrl || !request.endpoint) {
            this.showToast('请填写 MCP 服务名、baseUrl 和 endpoint');
            return;
        }
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/mcp/servers`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });
            if (payload.code !== 200) {
                throw new Error(payload.message || 'MCP 服务创建失败');
            }
            this.mcpForm.reset();
            this.mcpRequestTimeoutSeconds.value = '30';
            await this.refreshMcpServers(false);
            await this.refreshMcpTools(false);
            this.showToast('MCP 服务已创建');
        } catch (error) {
            this.showToast(`MCP 服务创建失败：${error.message}`);
        }
    }

    renderMcpServers() {
        if (!this.mcpServerList || !this.mcpMeta) return;
        const connectedCount = this.mcpServers.filter(server => server.status === 'CONNECTED').length;
        this.mcpMeta.textContent = `${this.mcpServers.length} 个服务 · ${connectedCount} 个已连接`;
        this.mcpServerList.innerHTML = this.mcpServers.map(server => `
            <article class="mcp-server-card">
                <div class="mcp-server-top">
                    <div>
                        <div class="tool-name">${this.escapeHtml(server.serverName || '-')}</div>
                        <div class="tool-code">${this.escapeHtml(server.transportType || '-')} · ${this.escapeHtml(server.baseUrl || '')}${this.escapeHtml(server.endpoint || '')}</div>
                    </div>
                    <span class="mcp-status ${String(server.status || 'unknown').toLowerCase()}">${this.escapeHtml(server.status || 'UNKNOWN')}</span>
                </div>
                ${server.status === 'FAILED' && server.lastError ? `<div class="mcp-error">${this.escapeHtml(server.lastError)}</div>` : ''}
                <div class="tool-meta-row">
                    <span>${server.toolCount || 0} 个工具</span>
                    <span>${server.enabled ? '已启用' : '已停用'}</span>
                    <span>${server.requestTimeoutSeconds || 30}s 超时</span>
                    <span>${server.headersJson ? '已配置请求头' : '无请求头'}</span>
                </div>
                <div class="mcp-actions">
                    <button class="secondary-btn compact-btn" data-action="toggle" data-id="${server.id}" data-enabled="${server.enabled ? 'false' : 'true'}" type="button">${server.enabled ? '停用' : '启用'}</button>
                    <button class="secondary-btn compact-btn" data-action="refresh" data-id="${server.id}" type="button">刷新</button>
                    <button class="secondary-btn compact-btn danger" data-action="delete" data-id="${server.id}" type="button">删除</button>
                </div>
            </article>
        `).join('') || '<div class="history-detail-empty">还没有 MCP Server。</div>';

        this.mcpServerList.querySelectorAll('button[data-action]').forEach(button => {
            button.addEventListener('click', () => this.handleMcpAction(button.dataset.action, button.dataset.id, button.dataset.enabled));
        });
    }

    renderMcpTools() {
        if (!this.mcpToolList) return;
        this.mcpToolList.innerHTML = this.mcpTools.map(tool => `
            <div class="mcp-tool-card">
                <strong>${this.escapeHtml(tool.toolName || '-')}</strong>
                <span>${this.escapeHtml(tool.serverName || '-')}</span>
                <p>${this.escapeHtml(tool.description || '')}</p>
            </div>
        `).join('') || '<div class="history-detail-empty">当前 MCP 快照没有工具。</div>';
    }

    async handleMcpAction(action, id, enabled) {
        try {
            if (action === 'toggle') {
                const payload = await this.fetchJson(`${this.apiBaseUrl}/mcp/servers/${encodeURIComponent(id)}/enabled`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ enabled: enabled === 'true' })
                });
                if (payload.code !== 200) throw new Error(payload.message || '更新失败');
                this.showToast('MCP 开关已更新');
            }
            if (action === 'refresh') {
                const payload = await this.fetchJson(`${this.apiBaseUrl}/mcp/servers/${encodeURIComponent(id)}/refresh`, { method: 'POST' });
                if (payload.code !== 200) throw new Error(payload.message || '刷新失败');
                this.showToast('MCP 服务已刷新');
            }
            if (action === 'delete') {
                const payload = await this.fetchJson(`${this.apiBaseUrl}/mcp/servers/${encodeURIComponent(id)}`, { method: 'DELETE' });
                if (payload.code !== 200) throw new Error(payload.message || '删除失败');
                this.showToast('MCP 服务已删除');
            }
            await this.refreshMcpServers(false);
            await this.refreshMcpTools(false);
        } catch (error) {
            this.showToast(`MCP 操作失败：${error.message}`);
        }
    }

    async refreshSkills(showToast = false) {
        try {
            this.skillsMeta.textContent = '正在加载 Skills...';
            const payload = await this.fetchJson(`${this.apiBaseUrl}/skills`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || 'Skills 查询失败');
            }
            this.skills = payload.data.items || [];
            this.renderSkills();
            if (showToast) this.showToast('Skills 已刷新');
        } catch (error) {
            this.skillsMeta.textContent = 'Skills 加载失败';
            this.skillsList.innerHTML = `<div class="history-detail-empty">无法加载 Skills：${this.escapeHtml(error.message)}</div>`;
        }
    }

    async installSkill() {
        const sourceUrl = this.skillSourceUrl.value.trim();
        if (!sourceUrl) {
            this.showToast('请输入 Skill ZIP URL');
            return;
        }
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/skills/install`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sourceUrl, overwrite: this.skillOverwrite.checked })
            });
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || 'Skill 安装失败');
            }
            this.skillInstallForm.reset();
            this.selectedSkillName = payload.data.name;
            await this.refreshSkills(false);
            await this.loadSkillDetail(payload.data.name);
            this.showToast('Skill 已安装');
        } catch (error) {
            this.showToast(`Skill 安装失败：${error.message}`);
        }
    }

    renderSkills() {
        if (!this.skillsList || !this.skillsMeta) return;
        const enabledCount = this.skills.filter(skill => skill.enabled).length;
        this.skillsMeta.textContent = `${this.skills.length} 个 Skill · ${enabledCount} 个已启用`;
        this.skillsList.innerHTML = this.skills.map(skill => `
            <article class="skill-card ${skill.name === this.selectedSkillName ? 'active' : ''}" data-name="${this.escapeAttr(skill.name)}">
                <div>
                    <div class="tool-name">${this.escapeHtml(skill.displayName || skill.name)}</div>
                    <div class="tool-code">${this.escapeHtml(skill.name)} · ${this.escapeHtml(skill.sourceType || '-')}</div>
                    <div class="tool-description">${this.escapeHtml(skill.description || '')}</div>
                    <div class="tool-meta-row">
                        <span>${skill.enabled ? '已启用' : '已停用'}</span>
                        <span>${this.formatFileSize(skill.size || 0)}</span>
                        <span>${this.formatDate(skill.updatedAt)}</span>
                    </div>
                </div>
                <div class="mcp-actions">
                    <button class="secondary-btn compact-btn" data-action="toggle" data-name="${this.escapeAttr(skill.name)}" data-enabled="${skill.enabled ? 'false' : 'true'}" type="button">${skill.enabled ? '停用' : '启用'}</button>
                    <button class="secondary-btn compact-btn danger" data-action="delete" data-name="${this.escapeAttr(skill.name)}" type="button">删除</button>
                </div>
            </article>
        `).join('') || '<div class="history-detail-empty">还没有安装 Skills。</div>';

        this.skillsList.querySelectorAll('.skill-card').forEach(card => {
            card.addEventListener('click', (event) => {
                if (event.target.closest('button')) return;
                this.loadSkillDetail(card.dataset.name);
            });
        });
        this.skillsList.querySelectorAll('button[data-action]').forEach(button => {
            button.addEventListener('click', () => this.handleSkillAction(button.dataset.action, button.dataset.name, button.dataset.enabled));
        });
    }

    async loadSkillDetail(name) {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/skills/${encodeURIComponent(name)}`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || 'Skill 读取失败');
            }
            this.selectedSkillName = name;
            this.skillDetail.innerHTML = `
                <div class="detail-title">${this.escapeHtml(payload.data.displayName || payload.data.name)}</div>
                <div class="detail-meta">
                    <span><strong>名称</strong>${this.escapeHtml(payload.data.name)}</span>
                    <span><strong>来源</strong>${this.escapeHtml(payload.data.sourceType || '-')}</span>
                    <span><strong>状态</strong>${payload.data.enabled ? '启用' : '停用'}</span>
                </div>
                <textarea class="memory-editor skill-content" readonly>${this.escapeHtml(payload.data.content || '')}</textarea>
            `;
            this.renderSkills();
        } catch (error) {
            this.showToast(`Skill 读取失败：${error.message}`);
        }
    }

    async handleSkillAction(action, name, enabled) {
        try {
            if (action === 'toggle') {
                const payload = await this.fetchJson(`${this.apiBaseUrl}/skills/${encodeURIComponent(name)}/enabled`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ enabled: enabled === 'true' })
                });
                if (payload.code !== 200) throw new Error(payload.message || '更新失败');
                this.showToast('Skill 开关已更新');
            }
            if (action === 'delete') {
                const payload = await this.fetchJson(`${this.apiBaseUrl}/skills/${encodeURIComponent(name)}`, { method: 'DELETE' });
                if (payload.code !== 200) throw new Error(payload.message || '删除失败');
                if (this.selectedSkillName === name) {
                    this.selectedSkillName = null;
                    this.skillDetail.innerHTML = '选择一个 Skill 查看内容。';
                }
                this.showToast('Skill 已删除');
            }
            await this.refreshSkills(false);
        } catch (error) {
            this.showToast(`Skill 操作失败：${error.message}`);
        }
    }

    async refreshScheduledTasks(showToast = false) {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/scheduled-diagnostics?page=1&pageSize=50`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || '定时排查任务查询失败');
            }
            this.scheduledTasks = payload.data.items || [];
            this.renderScheduledTasks();
            if (showToast) this.showToast('定时排查任务已刷新');
        } catch (error) {
            this.scheduledList.innerHTML = `<div class="history-detail-empty">无法加载定时任务：${this.escapeHtml(error.message)}</div>`;
            if (showToast) this.showToast(`定时任务加载失败：${error.message}`);
        }
    }

    renderScheduledTasks() {
        if (!this.scheduledList || !this.scheduledMeta) return;
        const enabledCount = this.scheduledTasks.filter(task => task.enabled).length;
        this.scheduledMeta.textContent = `${this.scheduledTasks.length} 个任务 · ${enabledCount} 个启用`;
        this.scheduledList.innerHTML = this.scheduledTasks.map(task => `
            <article class="scheduled-card ${task.scheduleId === this.selectedScheduleId ? 'active' : ''}" data-schedule-id="${this.escapeAttr(task.scheduleId)}">
                <div class="scheduled-card-main">
                    <div class="tool-name">${this.escapeHtml(task.taskName || '-')}</div>
                    <div class="tool-description">${this.escapeHtml(this.truncate(task.userQuestion || '', 120))}</div>
                    <div class="tool-meta-row">
                        <span>${task.enabled ? '已启用' : '已停用'}</span>
                        <span>每 ${task.intervalMinutes || '-'} 分钟</span>
                        <span>下次 ${this.formatDate(task.nextRunAt)}</span>
                    </div>
                    <div class="tool-meta-row">
                        <span>最近 ${this.escapeHtml(task.lastStatus || '-')}</span>
                        <span>${this.formatDate(task.lastRunAt)}</span>
                    </div>
                </div>
                <div class="mcp-actions">
                    <button class="secondary-btn compact-btn" data-action="edit" data-schedule-id="${this.escapeAttr(task.scheduleId)}" type="button">编辑</button>
                    <button class="secondary-btn compact-btn" data-action="run" data-schedule-id="${this.escapeAttr(task.scheduleId)}" type="button">运行</button>
                    <button class="secondary-btn compact-btn danger" data-action="delete" data-schedule-id="${this.escapeAttr(task.scheduleId)}" type="button">删除</button>
                </div>
            </article>
        `).join('') || '<div class="history-detail-empty">还没有定时排查任务。</div>';

        this.scheduledList.querySelectorAll('.scheduled-card').forEach(card => {
            card.addEventListener('click', (event) => {
                if (event.target.closest('button')) return;
                this.selectScheduledTask(card.dataset.scheduleId);
            });
        });
        this.scheduledList.querySelectorAll('button[data-action]').forEach(button => {
            button.addEventListener('click', () => this.handleScheduledAction(button.dataset.action, button.dataset.scheduleId));
        });
    }

    selectScheduledTask(scheduleId) {
        this.selectedScheduleId = scheduleId;
        this.selectedScheduledRunId = null;
        const task = this.scheduledTasks.find(item => item.scheduleId === scheduleId);
        if (task) this.fillScheduledForm(task);
        this.scheduledReport.innerHTML = '<div class="history-detail-empty">选择一条运行记录查看报告。</div>';
        this.updateScheduledReportExportState();
        this.renderScheduledTasks();
        this.refreshScheduledRuns(scheduleId);
    }

    fillScheduledForm(task) {
        this.editingScheduleId = task.scheduleId;
        this.scheduledTaskName.value = task.taskName || '';
        this.scheduledQuestion.value = task.userQuestion || '';
        this.scheduledPrompt.value = task.promptTemplate || '';
        this.scheduledInterval.value = task.intervalMinutes || 60;
        this.scheduledNextRunAt.value = this.toDatetimeLocal(task.nextRunAt);
        this.scheduledEnabled.checked = Boolean(task.enabled);
    }

    resetScheduledForm() {
        this.editingScheduleId = null;
        this.selectedScheduleId = null;
        this.scheduledTaskName.value = '';
        this.scheduledQuestion.value = '请检查当前系统是否存在异常告警、日志错误或性能风险。';
        this.scheduledPrompt.value = '你是企业级 SRE，请使用 Plan-Execute 方式执行定时排查。';
        this.scheduledInterval.value = 60;
        this.scheduledNextRunAt.value = this.toDatetimeLocal(new Date(Date.now() + 60 * 60 * 1000).toISOString());
        this.scheduledEnabled.checked = true;
        this.scheduledRunItems = [];
        this.selectedScheduledRunId = null;
        this.scheduledRunsMeta.textContent = '选择任务查看运行记录';
        this.scheduledRunsEl.innerHTML = '<div class="history-detail-empty">选择任务查看运行记录。</div>';
        this.scheduledReport.innerHTML = '<div class="history-detail-empty">选择一条运行记录查看报告。</div>';
        this.updateScheduledReportExportState();
        this.renderScheduledTasks();
    }

    async saveScheduledTask() {
        try {
            const body = {
                taskName: this.scheduledTaskName.value.trim(),
                userQuestion: this.scheduledQuestion.value.trim(),
                promptTemplate: this.scheduledPrompt.value.trim(),
                intervalMinutes: Number(this.scheduledInterval.value || 60),
                nextRunAt: this.scheduledNextRunAt.value,
                enabled: this.scheduledEnabled.checked,
                createdBy: 'web'
            };
            const url = this.editingScheduleId
                ? `${this.apiBaseUrl}/scheduled-diagnostics/${encodeURIComponent(this.editingScheduleId)}`
                : `${this.apiBaseUrl}/scheduled-diagnostics`;
            const payload = await this.fetchJson(url, {
                method: this.editingScheduleId ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            if (payload.code !== 200 || !payload.data) throw new Error(payload.message || '保存失败');
            this.selectedScheduleId = payload.data.scheduleId;
            this.editingScheduleId = payload.data.scheduleId;
            await this.refreshScheduledTasks(false);
            this.showToast('定时排查任务已保存');
        } catch (error) {
            this.showToast(`保存失败：${error.message}`);
        }
    }

    async handleScheduledAction(action, scheduleId) {
        if (action === 'edit') {
            this.selectScheduledTask(scheduleId);
            return;
        }
        if (action === 'run') {
            await this.runScheduleNow(scheduleId);
            return;
        }
        if (action === 'delete') {
            try {
                const payload = await this.fetchJson(`${this.apiBaseUrl}/scheduled-diagnostics/${encodeURIComponent(scheduleId)}`, { method: 'DELETE' });
                if (payload.code !== 200) throw new Error(payload.message || '删除失败');
                if (this.selectedScheduleId === scheduleId) this.resetScheduledForm();
                await this.refreshScheduledTasks(false);
                this.showToast('定时排查任务已删除');
            } catch (error) {
                this.showToast(`删除失败：${error.message}`);
            }
        }
    }

    async runSelectedScheduleNow() {
        if (!this.selectedScheduleId) {
            this.showToast('请先选择一个定时排查任务');
            return;
        }
        await this.runScheduleNow(this.selectedScheduleId);
    }

    async runScheduleNow(scheduleId) {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/scheduled-diagnostics/${encodeURIComponent(scheduleId)}/run`, { method: 'POST' });
            if (payload.code !== 200 || !payload.data) throw new Error(payload.message || '运行失败');
            this.showToast(`已触发运行：${payload.data.taskId}`);
            this.selectedScheduleId = scheduleId;
            await this.refreshScheduledTasks(false);
            await this.refreshScheduledRuns(scheduleId);
        } catch (error) {
            this.showToast(`运行失败：${error.message}`);
        }
    }

    async refreshScheduledRuns(scheduleId) {
        if (!scheduleId) return;
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/scheduled-diagnostics/${encodeURIComponent(scheduleId)}/runs?page=1&pageSize=20`);
            if (payload.code !== 200 || !payload.data) throw new Error(payload.message || '运行记录查询失败');
            this.scheduledRunItems = payload.data.items || [];
            this.selectedScheduledRunId = null;
            this.renderScheduledRuns();
            this.updateScheduledReportExportState();
        } catch (error) {
            this.scheduledRunsEl.innerHTML = `<div class="history-detail-empty">无法加载运行记录：${this.escapeHtml(error.message)}</div>`;
            this.updateScheduledReportExportState();
        }
    }

    renderScheduledRuns() {
        this.scheduledRunsMeta.textContent = `${this.scheduledRunItems.length} 条运行记录`;
        this.scheduledRunsEl.innerHTML = this.scheduledRunItems.map(run => `
            <article class="scheduled-run-card ${run.taskId === this.selectedScheduledRunId ? 'active' : ''}" data-task-id="${this.escapeAttr(run.taskId)}">
                <div class="trace-card-top">
                    <span class="trace-status ${String(run.status || '').toLowerCase()}">${this.escapeHtml(run.status || '-')}</span>
                    <strong>${this.escapeHtml(this.formatDuration(run.durationMs))}</strong>
                </div>
                <div class="trace-question">${this.escapeHtml(this.truncate(run.inputSummary || '-', 120))}</div>
                <div class="tool-meta-row">
                    <span>${this.formatDate(run.startedAt)}</span>
                    <span>${this.escapeHtml(run.taskId)}</span>
                </div>
            </article>
        `).join('') || '<div class="history-detail-empty">还没有运行记录。</div>';
        this.scheduledRunsEl.querySelectorAll('.scheduled-run-card').forEach(card => {
            card.addEventListener('click', () => this.renderScheduledReport(card.dataset.taskId));
        });
    }

    renderScheduledReport(taskId) {
        const run = this.scheduledRunItems.find(item => item.taskId === taskId);
        if (!run) return;
        this.selectedScheduledRunId = taskId;
        this.renderScheduledRuns();
        const content = run.status === 'SUCCESS'
            ? (run.finalReport || '任务成功，但没有报告内容。')
            : `任务状态：${run.status}\n\n${run.errorMessage || '暂无结果。'}`;
        this.scheduledReport.innerHTML = `
            <div class="detail-title">${this.escapeHtml(run.taskId)}</div>
            <div class="detail-meta">
                <span><strong>状态</strong>${this.escapeHtml(run.status || '-')}</span>
                <span><strong>开始</strong>${this.formatDate(run.startedAt)}</span>
                <span><strong>耗时</strong>${this.escapeHtml(this.formatDuration(run.durationMs))}</span>
            </div>
            <div class="scheduled-report-markdown">${this.renderMarkdown(content)}</div>
        `;
        this.scheduledReport.querySelectorAll('pre code').forEach(block => {
            if (window.hljs) hljs.highlightElement(block);
        });
        this.updateScheduledReportExportState();
    }

    updateScheduledReportExportState() {
        if (!this.exportScheduledReportBtn) return;
        this.exportScheduledReportBtn.disabled = !this.selectedScheduledRunId;
    }

    exportScheduledReport() {
        const run = this.scheduledRunItems.find(item => item.taskId === this.selectedScheduledRunId);
        if (!run) {
            this.showToast('请先选择一条运行记录');
            return;
        }
        const task = this.scheduledTasks.find(item => item.scheduleId === this.selectedScheduleId);
        const report = run.status === 'SUCCESS'
            ? (run.finalReport || '任务成功，但没有报告内容。')
            : `任务状态：${run.status}\n\n${run.errorMessage || '暂无结果。'}`;
        const title = task?.taskName || '定时排查报告';
        const content = [
            `# ${title}`,
            '',
            `- 任务 ID: ${this.selectedScheduleId || '-'}`,
            `- 运行 ID: ${run.taskId || '-'}`,
            `- 状态: ${run.status || '-'}`,
            `- 开始时间: ${this.formatDate(run.startedAt)}`,
            `- 耗时: ${this.formatDuration(run.durationMs)}`,
            '',
            '## 定时提问',
            '',
            task?.userQuestion || run.inputSummary || '-',
            '',
            '## 运行报告',
            '',
            report
        ].join('\n');
        const filename = `${this.safeFilename(title)}-${this.safeFilename(run.taskId || 'report')}.md`;
        this.downloadTextFile(filename, content, 'text/markdown;charset=utf-8');
        this.showToast('报告已导出');
    }

    async refreshSandboxStatus(showToast = false) {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/sandbox/status`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || '沙箱状态查询失败');
            }
            this.sandboxStatus = payload.data;
            this.renderSandboxStatus();
            if (showToast) this.showToast('沙箱状态已刷新');
        } catch (error) {
            this.sandboxStatusCards.innerHTML = `<div class="history-detail-empty">无法加载沙箱状态：${this.escapeHtml(error.message)}</div>`;
            if (showToast) this.showToast(`沙箱状态查询失败：${error.message}`);
        }
    }

    renderSandboxStatus() {
        if (!this.sandboxStatusCards) return;
        if (!this.sandboxStatus) {
            this.sandboxStatusCards.innerHTML = '<div class="history-detail-empty">点击刷新状态查看沙箱配置。</div>';
            return;
        }
        const items = [
            ['启用状态', this.sandboxStatus.enabled ? 'ENABLED' : 'DISABLED', this.sandboxStatus.enabled ? 'success' : 'failed'],
            ['Docker', this.sandboxStatus.dockerAvailable ? 'AVAILABLE' : 'UNAVAILABLE', this.sandboxStatus.dockerAvailable ? 'success' : 'failed'],
            ['镜像', this.sandboxStatus.imageAvailable ? 'READY' : 'MISSING', this.sandboxStatus.imageAvailable ? 'success' : 'failed'],
            ['网络', this.sandboxStatus.networkMode || '-'],
            ['镜像名', this.sandboxStatus.pythonImage || '-'],
            ['挂载', this.sandboxStatus.mountSpec || '-'],
            ['资源限制', `${this.sandboxStatus.cpus || '-'} CPU / ${this.sandboxStatus.memory || '-'}`],
            ['超时', `${this.sandboxStatus.timeoutSeconds || '-'}s`]
        ];
        this.sandboxStatusCards.innerHTML = items.map(([label, value, status]) => `
            <div class="sandbox-status-card">
                <span>${this.escapeHtml(label)}</span>
                <strong class="${status ? `trace-status ${status}` : ''}">${this.escapeHtml(value)}</strong>
            </div>
        `).join('') + this.renderSandboxStatusHint();
    }

    renderSandboxStatusHint() {
        if (!this.sandboxStatus) return '';
        if (this.sandboxStatus.imageAvailable) return '';
        const image = this.sandboxStatus.pythonImage || 'superbiz-agent:latest';
        return `
            <div class="sandbox-status-hint">
                <strong>沙箱镜像不存在</strong>
                <span>请先在项目根目录执行 <code>docker build -t ${this.escapeHtml(image)} .</code>，或使用 <code>make up</code> 让 compose 自动构建应用镜像。</span>
            </div>
        `;
    }

    async runSandboxSmokeTest() {
        await this.runSandboxRequest(null);
    }

    async runSandboxCode() {
        const code = this.sandboxCodeInput.value.trim();
        if (!code) {
            this.showToast('请输入要执行的 Python 代码');
            return;
        }
        const args = this.sandboxArgsInput.value.trim()
            ? this.sandboxArgsInput.value.trim().split(/\s+/)
            : [];
        await this.runSandboxRequest({ code, args });
    }

    async runSandboxRequest(body) {
        this.setSandboxBusy(true);
        this.sandboxResult.innerHTML = '<div class="history-detail-empty">沙箱执行中...</div>';
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/sandbox/test`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: body ? JSON.stringify(body) : '{}'
            });
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || '沙箱执行失败');
            }
            this.sandboxLastResult = payload.data;
            this.renderSandboxResult();
            this.showToast(payload.data.exitCode === 0 ? '沙箱测试通过' : `沙箱测试退出码 ${payload.data.exitCode}`);
        } catch (error) {
            this.sandboxLastResult = null;
            this.sandboxResult.innerHTML = `<div class="history-detail-empty">沙箱执行失败：${this.escapeHtml(error.message)}</div>`;
            this.showToast(`沙箱执行失败：${error.message}`);
        } finally {
            this.setSandboxBusy(false);
            this.refreshSandboxStatus(false);
        }
    }

    renderSandboxResult() {
        if (!this.sandboxResult) return;
        if (!this.sandboxLastResult) {
            this.sandboxResult.innerHTML = '<div class="history-detail-empty">运行测试后查看 stdout、stderr 和 docker 命令。</div>';
            return;
        }
        const result = this.sandboxLastResult;
        this.sandboxResult.innerHTML = `
            <div class="sandbox-result-header">
                <span class="trace-status ${result.exitCode === 0 ? 'success' : 'failed'}">exit ${this.escapeHtml(result.exitCode)}</span>
                <span>${this.escapeHtml(this.formatDuration(result.durationMs))}</span>
                <span>${result.timeout ? 'TIMEOUT' : 'FINISHED'}</span>
            </div>
            <div class="detail-meta">
                <span><strong>Run ID</strong>${this.escapeHtml(result.runId || '-')}</span>
                <span><strong>Image</strong>${this.escapeHtml(result.image || '-')}</span>
            </div>
            <details class="trace-collapse" open>
                <summary>stdout</summary>
                <pre class="trace-summary">${this.escapeHtml(result.stdout || '')}</pre>
            </details>
            <details class="trace-collapse" ${result.stderr ? 'open' : ''}>
                <summary>stderr</summary>
                <pre class="trace-summary">${this.escapeHtml(result.stderr || '')}</pre>
            </details>
            <details class="trace-collapse">
                <summary>Docker 命令</summary>
                <pre class="trace-summary">${this.escapeHtml(result.command || '')}</pre>
            </details>
        `;
    }

    setSandboxBusy(isBusy) {
        this.runSandboxSmokeBtn.disabled = isBusy;
        this.runSandboxCodeBtn.disabled = isBusy;
        this.refreshSandboxStatusBtn.disabled = isBusy;
    }

    async refreshTraces(showToast = false) {
        try {
            const params = new URLSearchParams({
                page: String(this.tracePage),
                pageSize: String(this.tracePageSizeValue),
                status: this.traceStatus.value || 'all'
            });
            const keyword = this.traceKeyword.value.trim();
            if (keyword) params.set('keyword', keyword);
            const payload = await this.fetchJson(`${this.apiBaseUrl}/traces?${params.toString()}`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || 'Trace 列表查询失败');
            }
            this.traces = payload.data.items || [];
            this.traceTotal = payload.data.total || 0;
            this.renderTraces();
            if (showToast) this.showToast('Trace 列表已刷新');
        } catch (error) {
            this.traceList.innerHTML = `<div class="history-detail-empty">无法加载 Trace：${this.escapeHtml(error.message)}</div>`;
            if (showToast) this.showToast(`Trace 加载失败：${error.message}`);
        }
    }

    renderTraces() {
        if (!this.traceList || !this.traceResultCount) return;
        const totalPages = Math.max(1, Math.ceil(this.traceTotal / this.tracePageSizeValue));
        this.traceResultCount.textContent = `${this.traceTotal} 条 Trace`;
        this.tracePageInfo.textContent = `第 ${this.tracePage} / ${totalPages} 页`;
        this.prevTracePageBtn.disabled = this.tracePage <= 1;
        this.nextTracePageBtn.disabled = this.tracePage >= totalPages;
        this.traceList.innerHTML = this.traces.map(trace => `
            <article class="trace-card ${trace.traceId === this.selectedTraceId ? 'active' : ''}" data-trace-id="${this.escapeAttr(trace.traceId)}">
                <div class="trace-card-top">
                    <span class="trace-status ${String(trace.status || '').toLowerCase()}">${this.escapeHtml(trace.status || '-')}</span>
                    <strong>${this.escapeHtml(this.formatDuration(trace.durationMs))}</strong>
                </div>
                <div class="trace-question">${this.escapeHtml(this.truncate(trace.userInput || '-', 96))}</div>
                <div class="trace-card-grid">
                    <span>Session</span><strong>${this.escapeHtml(this.truncate(trace.sessionId || '-', 28))}</strong>
                    <span>开始</span><strong>${this.formatDate(trace.startTime)}</strong>
                    <span>模型</span><strong>${this.escapeHtml(trace.modelName || '-')}</strong>
                </div>
            </article>
        `).join('') || '<div class="history-detail-empty">还没有 Trace 记录。</div>';

        this.traceList.querySelectorAll('.trace-card').forEach(card => {
            card.addEventListener('click', () => this.loadTraceDetail(card.dataset.traceId));
        });
    }

    async loadTraceDetail(traceId) {
        try {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/traces/${encodeURIComponent(traceId)}`);
            if (payload.code !== 200 || !payload.data) {
                throw new Error(payload.message || 'Trace 详情查询失败');
            }
            this.selectedTraceId = traceId;
            this.renderTraceDetail(payload.data);
            this.renderTraces();
        } catch (error) {
            this.showToast(`Trace 详情加载失败：${error.message}`);
        }
    }

    renderTraceDetail(detail) {
        const trace = detail.trace || {};
        const steps = detail.steps || [];
        const toolCalls = detail.toolCalls || [];
        const toolCallsByStep = new Map();
        toolCalls.forEach(call => {
            const items = toolCallsByStep.get(call.stepId) || [];
            items.push(call);
            toolCallsByStep.set(call.stepId, items);
        });
        const toolSuccess = toolCalls.filter(call => call.status === 'SUCCESS').length;
        const toolFailed = toolCalls.filter(call => call.status === 'FAILED').length;
        this.traceDetail.innerHTML = `
            <div class="trace-detail-shell">
                <section class="trace-overview">
                    <div class="trace-overview-main">
                        <span class="trace-status ${String(trace.status || '').toLowerCase()}">${this.escapeHtml(trace.status || '-')}</span>
                        <div>
                            <div class="trace-detail-title">${this.escapeHtml(this.truncate(trace.userInput || '-', 120))}</div>
                            <div class="trace-id-line">${this.escapeHtml(trace.traceId || '-')}</div>
                        </div>
                    </div>
                    <div class="trace-metric-grid">
                        <div><span>总耗时</span><strong>${this.escapeHtml(this.formatDuration(trace.durationMs))}</strong></div>
                        <div><span>步骤</span><strong>${steps.length}</strong></div>
                        <div><span>工具</span><strong>${toolCalls.length}</strong></div>
                        <div><span>失败</span><strong>${toolFailed}</strong></div>
                        <div><span>模型</span><strong>${this.escapeHtml(trace.modelName || '-')}</strong></div>
                    </div>
                    <div class="trace-user-input">${this.escapeHtml(trace.userInput || '')}</div>
                    ${trace.errorMessage ? `<div class="mcp-error">${this.escapeHtml(trace.errorMessage)}</div>` : ''}
                </section>

                <div class="trace-detail-columns">
                    <section class="trace-column">
                        <div class="trace-section-title">
                            <strong>步骤时间线</strong>
                            <span>${steps.length} steps</span>
                        </div>
                        <div class="trace-timeline">
                            ${steps.map((step, index) => this.renderTraceStep(step, toolCallsByStep.get(step.id) || [], index + 1)).join('') || '<div class="history-detail-empty">没有步骤记录。</div>'}
                        </div>
                    </section>

                    <section class="trace-column">
                        <div class="trace-section-title">
                            <strong>工具调用</strong>
                            <span>${toolSuccess} success · ${toolFailed} failed</span>
                        </div>
                        <div class="trace-tool-list">
                            ${toolCalls.map(call => this.renderTraceToolCall(call)).join('') || '<div class="history-detail-empty">本轮没有调用工具。</div>'}
                        </div>
                    </section>
                </div>
            </div>
        `;
    }

    renderTraceStep(step, toolCalls, index) {
        return `
            <section class="trace-step">
                <div class="trace-step-head">
                    <div class="trace-step-title">
                        <span class="trace-index">${index}</span>
                        <strong>${this.escapeHtml(step.stepType || '-')}</strong>
                        <span>${this.escapeHtml(step.stepName || '-')}</span>
                    </div>
                    <div class="trace-step-meta">
                        <span class="trace-status ${String(step.status || '').toLowerCase()}">${this.escapeHtml(step.status || '-')}</span>
                        <span class="trace-duration">${this.escapeHtml(this.formatDuration(step.durationMs))}</span>
                    </div>
                </div>
                <div class="trace-step-stats">
                    <span>input ${this.countWords(step.inputSummary || '')} chars</span>
                    <span>output ${this.countWords(step.outputSummary || '')} chars</span>
                    <span>${toolCalls.length} tools</span>
                </div>
                ${step.inputSummary ? `<details class="trace-collapse"><summary>输入摘要</summary><pre class="trace-summary">${this.escapeHtml(step.inputSummary)}</pre></details>` : ''}
                ${step.outputSummary ? `<details class="trace-collapse"><summary>输出摘要</summary><pre class="trace-summary">${this.escapeHtml(step.outputSummary)}</pre></details>` : ''}
                ${step.errorMessage ? `<div class="mcp-error">${this.escapeHtml(step.errorMessage)}</div>` : ''}
            </section>
        `;
    }

    renderTraceToolCall(call) {
        return `
            <article class="trace-tool-call">
                <div class="trace-tool-head">
                    <div>
                        <strong>${this.escapeHtml(call.toolName || '-')}</strong>
                        <span>${this.escapeHtml(call.toolSource || 'TOOL')}</span>
                    </div>
                    <div class="trace-step-meta">
                        <span class="trace-status ${String(call.status || '').toLowerCase()}">${this.escapeHtml(call.status || '-')}</span>
                        <span class="trace-duration">${this.escapeHtml(this.formatDuration(call.durationMs))}</span>
                    </div>
                </div>
                ${call.requestJson ? `<details class="trace-collapse"><summary>请求参数</summary><pre class="trace-summary">${this.escapeHtml(call.requestJson)}</pre></details>` : ''}
                ${call.responseSummary ? `<details class="trace-collapse"><summary>响应摘要</summary><pre class="trace-summary">${this.escapeHtml(call.responseSummary)}</pre></details>` : ''}
                ${call.errorMessage ? `<div class="mcp-error">${this.escapeHtml(call.errorMessage)}</div>` : ''}
            </article>
        `;
    }

    moveTracePage(delta) {
        const totalPages = Math.max(1, Math.ceil(this.traceTotal / this.tracePageSizeValue));
        this.tracePage = Math.min(totalPages, Math.max(1, this.tracePage + delta));
        this.refreshTraces(false);
    }

    tokenize(text) {
        return String(text || '')
            .toLowerCase()
            .split(/[\s,，。.;；:：/\\|()[\]{}"'`~!@#$%^&*_+=<>?-]+/)
            .map(token => token.trim())
            .filter(Boolean);
    }

    buildSnippet(content, terms) {
        const text = String(content || '').replace(/\s+/g, ' ').trim();
        const lower = text.toLowerCase();
        const hitIndex = terms.map(term => lower.indexOf(term)).find(index => index >= 0);
        const start = Math.max(0, (hitIndex >= 0 ? hitIndex : 0) - 80);
        const snippet = text.slice(start, start + 220);
        return `${start > 0 ? '...' : ''}${snippet}${start + 220 < text.length ? '...' : ''}`;
    }

    highlightTerms(text, terms) {
        let escaped = this.escapeHtml(text);
        terms.forEach(term => {
            if (!term || term.length > 40) return;
            const pattern = new RegExp(`(${this.escapeRegExp(term)})`, 'gi');
            escaped = escaped.replace(pattern, '<mark>$1</mark>');
        });
        return escaped;
    }

    setBusy(isBusy) {
        this.isBusy = isBusy;
        this.sendBtn.disabled = isBusy;
        this.aiOpsBtn.disabled = isBusy;
        this.uploadBtn.disabled = isBusy;
        this.updateChatMeta(isBusy ? '请求处理中...' : undefined);
    }

    async fetchJson(url, options = {}) {
        const response = await fetch(url, options);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    }

    renderMarkdown(content) {
        if (!content) return '';
        if (!window.marked) return this.escapeHtml(content);
        try {
            return marked.parse(content, { gfm: true, breaks: true });
        } catch {
            return this.escapeHtml(content);
        }
    }

    copySessionId() {
        navigator.clipboard?.writeText(this.sessionId);
        this.showToast('Session ID 已复制');
    }

    showToast(message) {
        this.toast.textContent = message;
        this.toast.hidden = false;
        clearTimeout(this.toastTimer);
        this.toastTimer = setTimeout(() => {
            this.toast.hidden = true;
        }, 2800);
    }

    sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    truncate(text, length) {
        return text.length > length ? `${text.slice(0, length)}...` : text;
    }

    formatDuration(ms) {
        if (ms == null || Number.isNaN(Number(ms))) return '-';
        const value = Number(ms);
        if (value < 1000) return `${value}ms`;
        if (value < 60000) return `${Math.round(value / 100) / 10}s`;
        return `${Math.floor(value / 60000)}m ${Math.round((value % 60000) / 1000)}s`;
    }

    countWords(text) {
        return String(text || '').replace(/\s+/g, '').length;
    }

    formatFileSize(bytes) {
        if (!bytes) return '0 B';
        const units = ['B', 'KB', 'MB'];
        let size = bytes;
        let index = 0;
        while (size >= 1024 && index < units.length - 1) {
            size /= 1024;
            index++;
        }
        return `${Math.round(size * 10) / 10} ${units[index]}`;
    }

    formatNumber(value) {
        const number = Number(value);
        if (Number.isNaN(number)) return '-';
        return Math.round(number * 10000) / 10000;
    }

    ragSourceLabel(source) {
        if (source === 'backend') return '后端知识库';
        return '知识库文档';
    }

    riskClass(riskLevel) {
        return String(riskLevel || 'LOW').toLowerCase();
    }

    formatDate(value) {
        if (!value) return '-';
        const date = this.parseDateValue(value);
        if (Number.isNaN(date.getTime())) return String(value);
        const pad = number => String(number).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    parseDateValue(value) {
        if (Array.isArray(value)) {
            const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0] = value;
            return new Date(year, month - 1, day, hour, minute, second);
        }
        if (typeof value === 'string') {
            const normalized = value.trim().replace(' ', 'T');
            return new Date(normalized);
        }
        return new Date(value);
    }

    toDatetimeLocal(value) {
        if (!value) return '';
        const date = this.parseDateValue(value);
        if (Number.isNaN(date.getTime())) return '';
        const offsetMs = date.getTimezoneOffset() * 60000;
        return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
    }

    safeFilename(value) {
        return String(value || 'document')
            .trim()
            .replace(/[\\/:*?"<>|]+/g, '-')
            .replace(/\s+/g, '-')
            .replace(/-+/g, '-')
            .slice(0, 80) || 'document';
    }

    downloadTextFile(filename, content, mimeType) {
        const blob = new Blob([content], { type: mimeType || 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    }

    escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value == null ? '' : String(value);
        return div.innerHTML;
    }

    escapeAttr(value) {
        return this.escapeHtml(value).replace(/"/g, '&quot;');
    }

    escapeRegExp(value) {
        return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new SuperBizConsole();
});
