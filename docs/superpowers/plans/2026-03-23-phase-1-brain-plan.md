# Phase 1: aura-api-analyzer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the modular RCA engine using Spring AI and Hexagonal Architecture to analyze Kubernetes incidents with zero fallback code.

**Architecture:** Hexagonal (Ports & Adapters) with a "No-Fallback" philosophy. Uses Java 21 Sealed Classes for strict state management and Virtual Threads for high-concurrency LLM calls.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring AI 1.0, Gradle (Kotlin DSL), JUnit 5.

---

## File Structure Mapping

- `aura-api-analyzer/`
  - `src/main/java/com/auraops/analyzer/`
    - `domain/` (Pure Logic)
      - `model/`: `AnalysisResult.java`, `RemediationAction.java`, `Incident.java`, `ActionType.java`, `ErrorCode.java`
      - `service/`: `IncidentAnalysisService.java`
    - `application/` (Orchestration)
      - `ports/in/`: `AnalyzeIncidentUseCase.java`
      - `ports/out/`: `LLMProvider.java`, `TelemetrySource.java`
      - `usecases/`: `AnalyzeIncidentUseCaseImpl.java`
    - `infrastructure/` (Technical Details)
      - `adapters/in/web/`: `AnalysisController.java`, `AnalysisRequestDto.java`, `AnalysisResponseDto.java`
      - `adapters/out/ai/`: `SpringAIAdapter.java`, `AnalysisResponseParser.java`
      - `config/`: `SpringAIConfig.java`, `AsyncConfig.java` (Virtual Threads)
  - `src/test/java/com/auraops/analyzer/`
    - `domain/`: Unit tests for models and services.
    - `infrastructure/adapters/`: Integration tests with Testcontainers.

---

### Task 1: Project Initialization & Build Configuration

**Files:**
- Create: `aura-api-analyzer/build.gradle.kts`
- Create: `aura-api-analyzer/settings.gradle.kts`
- Create: `aura-api-analyzer/src/main/resources/application.yml`

- [ ] **Step 1: Create `settings.gradle.kts`**
```kotlin
rootProject.name = "aura-api-analyzer"
```

- [ ] **Step 2: Create `build.gradle.kts` with Spring Boot 3.5 and Spring AI 1.0**
```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.5.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.auraops"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter") // Default for dev
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Verify build**
Run: `./gradlew build`
Expected: SUCCESS (Build finished)

- [ ] **Step 4: Commit**
```bash
git add aura-api-analyzer/
git commit -m "chore: initialize aura-api-analyzer with gradle and java 21"
```

---

### Task 2: Implement "No-Fallback" Domain Models

**Files:**
- Create: `aura-api-analyzer/src/main/java/com/auraops/analyzer/domain/model/AnalysisResult.java`
- Create: `aura-api-analyzer/src/main/java/com/auraops/analyzer/domain/model/RemediationAction.java`
- Create: `aura-api-analyzer/src/main/java/com/auraops/analyzer/domain/model/ErrorCode.java`

- [ ] **Step 1: Write the failing test for model immutability**
```java
public class AnalysisResultTest {
    @Test
    void shouldBeImmutable() {
        // Assert record behavior
    }
}
```

- [ ] **Step 2: Implement `AnalysisResult` as a Sealed Interface**
```java
package com.auraops.analyzer.domain.model;

import java.util.List;

public sealed interface AnalysisResult 
    permits AnalysisResult.Success, AnalysisResult.Inconclusive, AnalysisResult.CriticalFailure {
    
    record Success(
        String incidentId,
        String diagnosis,
        double confidence,
        RemediationAction recommendedAction,
        String technicalReasoning
    ) implements AnalysisResult {}

    record Inconclusive(
        String incidentId,
        String reason,
        List<String> missingDataPoints
    ) implements AnalysisResult {}

    record CriticalFailure(
        String incidentId,
        ErrorCode errorCode,
        String message
    ) implements AnalysisResult {}
}
```

- [ ] **Step 3: Implement `RemediationAction` and `ErrorCode`**
```java
public record RemediationAction(String type, java.util.Map<String, Object> parameters) {}
public enum ErrorCode { LLM_PROVIDER_ERROR, PARSING_ERROR, TELEMETRY_UNAVAILABLE }
```

- [ ] **Step 4: Run tests and verify success**
Run: `./gradlew test`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add aura-api-analyzer/src/main/java/com/auraops/analyzer/domain/model/
git commit -m "feat: implement no-fallback domain models using sealed classes"
```

---

### Task 3: Define Ports and Application Use Case

**Files:**
- Create: `aura-api-analyzer/src/main/java/com/auraops/analyzer/application/ports/out/LLMProvider.java`
- Create: `aura-api-analyzer/src/main/java/com/auraops/analyzer/application/ports/in/AnalyzeIncidentUseCase.java`
- Create: `aura-api-analyzer/src/main/java/com/auraops/analyzer/application/usecases/AnalyzeIncidentUseCaseImpl.java`

- [ ] **Step 1: Create `LLMProvider` interface (Outbound Port)**
```java
public interface LLMProvider {
    AnalysisResult analyze(Incident incident);
}
```

- [ ] **Step 2: Implement `AnalyzeIncidentUseCaseImpl`**
```java
@Service
public class AnalyzeIncidentUseCaseImpl implements AnalyzeIncidentUseCase {
    private final LLMProvider llmProvider;
    // Constructor injection
    @Override
    public AnalysisResult execute(Incident incident) {
        return llmProvider.analyze(incident);
    }
}
```

- [ ] **Step 3: Run tests**
Run: `./gradlew test`
Expected: PASS

- [ ] **Step 4: Commit**
```bash
git add aura-api-analyzer/src/main/java/com/auraops/analyzer/application/
git commit -m "feat: define hexagonal ports and use case implementation"
```

---

### Task 4: Modular Spring AI Adapter with Virtual Threads

**Files:**
- Create: `aura-api-analyzer/src/main/java/com/auraops/analyzer/infrastructure/adapters/out/ai/SpringAIAdapter.java`
- Create: `aura-api-analyzer/src/main/java/com/auraops/analyzer/infrastructure/config/AsyncConfig.java`

- [ ] **Step 1: Configure Virtual Threads in `AsyncConfig.java`**
```java
@Configuration
public class AsyncConfig {
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerCustomizer() {
        return protocolHandler -> protocolHandler.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

- [ ] **Step 2: Implement `SpringAIAdapter`**
```java
@Component
public class SpringAIAdapter implements LLMProvider {
    private final ChatModel chatModel;
    
    public AnalysisResult analyze(Incident incident) {
        // AI Logic using chatModel.call()
    }
}
```

- [ ] **Step 3: Verify with Mockito test**
- [ ] **Step 4: Commit**
```bash
git add aura-api-analyzer/src/main/java/com/auraops/analyzer/infrastructure/
git commit -m "feat: implement modular Spring AI adapter with virtual threads"
```
