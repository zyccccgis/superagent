class SuperBizConsole {
    constructor() {
        this.apiBaseUrl = `${window.location.origin}/api`;
        this.mode = 'quick';
        this.sessionId = this.createSessionId();
        this.messages = [];
        this.historyRows = [];
        this.memoryView = 'long';
        this.ragDocuments = [];
        this.selectedRagDocId = null;
        this.currentPage = 1;
        this.pageSize = 8;
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
            { method: 'POST', path: '/api/memory/executions', purpose: '创建短期记忆记录' },
            { method: 'PUT', path: '/api/memory/executions', purpose: '更新短期记忆记录' },
            { method: 'DELETE', path: '/api/memory/executions', purpose: '删除短期记忆记录' },
            { method: 'POST', path: '/api/memory/extract', purpose: '从执行结果抽取长期记忆' },
            { method: 'POST', path: '/api/memory/compress', purpose: '把旧短期记忆压缩为摘要记录' },
            { method: 'POST', path: '/api/ai_ops/tasks', purpose: '创建 AI Ops 诊断任务' },
            { method: 'GET', path: '/api/ai_ops/tasks/{taskId}', purpose: '轮询 AI Ops 任务状态和报告' },
            { method: 'POST', path: '/api/rag/documents', purpose: '上传知识库文件并建立索引' },
            { method: 'GET', path: '/api/rag/documents', purpose: '分页查询 RAG 文档元数据' },
            { method: 'POST', path: '/api/rag/retrieve', purpose: 'RAG 纯召回，返回 TopK 片段和 L2 distance' },
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
        this.chatPage = document.getElementById('chatPage');
        this.historyPage = document.getElementById('historyPage');
        this.ragPage = document.getElementById('ragPage');
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
        this.toast = document.getElementById('toast');
    }

    bindEvents() {
        this.chatPageBtn.addEventListener('click', () => this.switchPage('chat'));
        this.historyPageBtn.addEventListener('click', () => this.switchPage('history'));
        this.ragPageBtn.addEventListener('click', () => this.switchPage('rag'));
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

        document.querySelectorAll('.segment').forEach(button => {
            button.addEventListener('click', () => {
                this.mode = button.dataset.mode;
                document.querySelectorAll('.segment').forEach(item => item.classList.toggle('active', item === button));
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
        this.updateSessionDisplay();
        this.updateChatMeta();
    }

    switchPage(page) {
        const showHistory = page === 'history';
        const showRag = page === 'rag';
        this.chatPage.classList.toggle('active', !showHistory && !showRag);
        this.historyPage.classList.toggle('active', showHistory);
        this.ragPage.classList.toggle('active', showRag);
        this.chatPageBtn.classList.toggle('active', !showHistory && !showRag);
        this.historyPageBtn.classList.toggle('active', showHistory);
        this.ragPageBtn.classList.toggle('active', showRag);
        if (showHistory) {
            this.captureCurrentSessionToHistory();
            this.refreshMemoryFiles();
        }
        if (showRag) {
            this.refreshRagDocumentsFromBackend();
            this.renderRagDocuments();
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
        if (this.memoryView === 'short') {
            const payload = await this.fetchJson(`${this.apiBaseUrl}/memory/executions`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sessionId: this.sessionId,
                    userInput: '手动创建短期记忆',
                    agentOutput: '',
                    status: 'MANUAL'
                })
            });
            if (payload.code !== 200) {
                this.showToast(payload.message || '创建失败');
                return;
            }
            this.selectedHistoryId = payload.data.executionId;
            await this.refreshMemoryFiles(false);
            await this.selectHistoryRow(this.selectedHistoryId);
            this.showToast('已创建短期记忆');
            return;
        }
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
                this.renderHistoryPage();
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
        this.seedHistoryBtn.textContent = view === 'long' ? '新建主题记忆' : '新建短期记忆';
        this.syncVisibleRowsBtn.textContent = view === 'long' ? '刷新记忆文件' : '刷新短期记忆';
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
            <div class="detail-preview">
                <div class="block-heading">短期记忆内容</div>
                <label class="memory-label">用户输入</label>
                <textarea class="memory-editor compact" id="shortUserInput">${this.escapeHtml(row.userInput || '')}</textarea>
                <label class="memory-label">Agent 输出</label>
                <textarea class="memory-editor" id="shortAgentOutput">${this.escapeHtml(row.agentOutput || '')}</textarea>
                <label class="memory-label">状态</label>
                <input class="memory-input" id="shortStatus" value="${this.escapeAttr(row.status || 'MANUAL')}">
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
            return marked.parse(content);
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

    formatDate(value) {
        if (!value) return '-';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value);
        return date.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
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
