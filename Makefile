# SuperBizAgent Makefile

SERVER_URL ?= http://localhost:9900
UPLOAD_API = $(SERVER_URL)/api/rag/documents
HEALTH_CHECK_API = $(SERVER_URL)/milvus/health
DOCS_DIR ?= aiops-docs
COMPOSE_FILE ?= docker-compose.yml
ENV_FILE ?= .env
JAVA17_HOME ?= /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

GREEN = \033[0;32m
YELLOW = \033[0;33m
RED = \033[0;31m
NC = \033[0m

.PHONY: help env up down restart logs logs-app ps status build check wait upload clean dev-start dev-stop dev-restart compile compose-config

help:
	@echo "$(GREEN)SuperBizAgent Makefile$(NC)"
	@echo ""
	@echo "Docker 部署："
	@echo "  $(YELLOW)make env$(NC)            - 创建 .env 模板文件"
	@echo "  $(YELLOW)make up$(NC)             - 构建并启动完整 Docker 栈（App + MySQL + Milvus + 沙箱）"
	@echo "  $(YELLOW)make down$(NC)           - 停止 Docker 栈"
	@echo "  $(YELLOW)make restart$(NC)        - 重启 Docker 栈"
	@echo "  $(YELLOW)make logs$(NC)           - 查看所有容器日志"
	@echo "  $(YELLOW)make logs-app$(NC)       - 查看应用日志"
	@echo "  $(YELLOW)make ps/status$(NC)      - 查看容器状态"
	@echo "  $(YELLOW)make compose-config$(NC) - 校验 docker compose 配置"
	@echo ""
	@echo "应用操作："
	@echo "  $(YELLOW)make check$(NC)          - 检查服务和 Milvus 健康状态"
	@echo "  $(YELLOW)make wait$(NC)           - 等待服务就绪"
	@echo "  $(YELLOW)make upload$(NC)         - 上传 aiops-docs 下的 Markdown 文档"
	@echo "  $(YELLOW)make compile$(NC)        - Maven 编译检查"
	@echo ""
	@echo "本地开发："
	@echo "  $(YELLOW)make dev-start$(NC)      - 本地 Maven 启动 Spring Boot（依赖服务需已启动）"
	@echo "  $(YELLOW)make dev-stop$(NC)       - 停止本地 Maven 启动的服务"
	@echo "  $(YELLOW)make dev-restart$(NC)    - 重启本地开发服务"

env:
	@if [ -f "$(ENV_FILE)" ]; then \
		echo "$(YELLOW)$(ENV_FILE) 已存在，跳过创建$(NC)"; \
	else \
		cp .env.example $(ENV_FILE); \
		echo "$(GREEN)已创建 $(ENV_FILE)，请填写 DASHSCOPE_API_KEY 并修改生产密码$(NC)"; \
	fi

up: env
	@echo "$(YELLOW)启动 Docker Compose 完整栈...$(NC)"
	@docker compose -f $(COMPOSE_FILE) up -d --build
	@$(MAKE) ps

down:
	@echo "$(YELLOW)停止 Docker Compose 完整栈...$(NC)"
	@docker compose -f $(COMPOSE_FILE) down

restart:
	@$(MAKE) down
	@$(MAKE) up

build:
	@docker compose -f $(COMPOSE_FILE) build

logs:
	@docker compose -f $(COMPOSE_FILE) logs -f

logs-app:
	@docker compose -f $(COMPOSE_FILE) logs -f app

ps status:
	@docker compose -f $(COMPOSE_FILE) ps

compose-config:
	@docker compose -f $(COMPOSE_FILE) config

check:
	@echo "$(YELLOW)检查服务状态...$(NC)"
	@if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
		echo "$(GREEN)服务运行正常: $(SERVER_URL)$(NC)"; \
	else \
		echo "$(RED)服务未就绪或 Milvus 健康检查失败$(NC)"; \
		echo "$(YELLOW)查看日志: make logs-app$(NC)"; \
		exit 1; \
	fi

wait:
	@echo "$(YELLOW)等待服务就绪...$(NC)"
	@max_attempts=120; \
	attempt=0; \
	while [ $$attempt -lt $$max_attempts ]; do \
		if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
			echo "$(GREEN)服务已就绪: $(SERVER_URL)$(NC)"; \
			exit 0; \
		fi; \
		attempt=$$((attempt + 1)); \
		printf "$(YELLOW)等待中... [$$attempt/$$max_attempts]$(NC)\r"; \
		sleep 2; \
	done; \
	echo ""; \
	echo "$(RED)服务启动超时$(NC)"; \
	echo "$(YELLOW)查看日志: make logs-app$(NC)"; \
	exit 1

upload:
	@echo "$(YELLOW)上传 $(DOCS_DIR) 目录下的 Markdown 文档...$(NC)"
	@if [ ! -d "$(DOCS_DIR)" ]; then \
		echo "$(RED)目录不存在: $(DOCS_DIR)$(NC)"; \
		exit 1; \
	fi
	@count=0; success=0; failed=0; \
	for file in $(DOCS_DIR)/*.md; do \
		if [ -f "$$file" ]; then \
			count=$$((count + 1)); \
			filename=$$(basename "$$file"); \
			echo "$(YELLOW)[$$count] 上传: $$filename$(NC)"; \
			response=$$(curl -s -w "\n%{http_code}" -X POST $(UPLOAD_API) -F "file=@$$file" -H "Accept: application/json"); \
			http_code=$$(echo "$$response" | tail -n1); \
			body=$$(echo "$$response" | sed '$$d'); \
			if [ "$$http_code" = "200" ]; then \
				echo "$(GREEN)成功: $$filename$(NC)"; \
				success=$$((success + 1)); \
			else \
				echo "$(RED)失败: $$filename (HTTP $$http_code)$(NC)"; \
				echo "$$body" | head -n 3; \
				failed=$$((failed + 1)); \
			fi; \
		fi; \
	done; \
	echo "$(GREEN)上传完成: 成功 $$success / 总计 $$count$(NC)"; \
	if [ $$failed -gt 0 ]; then exit 1; fi

compile:
	@env JAVA_HOME=$(JAVA17_HOME) PATH="$(JAVA17_HOME)/bin:$$PATH" mvn -q -DskipTests compile

dev-start:
	@echo "$(YELLOW)本地启动 Spring Boot...$(NC)"
	@if [ -f server.pid ] && ps -p $$(cat server.pid) > /dev/null 2>&1; then \
		echo "$(GREEN)本地服务已运行，PID: $$(cat server.pid)$(NC)"; \
	else \
		nohup env JAVA_HOME=$(JAVA17_HOME) PATH="$(JAVA17_HOME)/bin:$$PATH" mvn spring-boot:run > server.log 2>&1 & \
		echo $$! > server.pid; \
		echo "$(GREEN)已启动，PID: $$(cat server.pid)，日志: server.log$(NC)"; \
	fi

dev-stop:
	@echo "$(YELLOW)停止本地 Spring Boot...$(NC)"
	@if [ -f server.pid ]; then \
		pid=$$(cat server.pid); \
		if ps -p $$pid > /dev/null 2>&1; then kill $$pid; fi; \
		rm -f server.pid; \
		echo "$(GREEN)已停止本地服务$(NC)"; \
	else \
		echo "$(YELLOW)未找到 server.pid$(NC)"; \
	fi

dev-restart:
	@$(MAKE) dev-stop
	@$(MAKE) dev-start

clean:
	@rm -f server.pid server.log
	@rm -rf uploads/*.tmp sandbox/runs
	@echo "$(GREEN)清理完成$(NC)"
