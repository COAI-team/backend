package kr.or.kosa.backend.chatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class PromptBuilder {

    // .st 파일들에서 프롬프트 로드
    private final Map<String, String> pagePrompts;
    private final String globalPromptTemplate;

    public PromptBuilder() {
        this.pagePrompts = Map.ofEntries(
                Map.entry("MAIN", loadStTemplate("Main.st")),
                Map.entry("BILLING", loadStTemplate("Billing.st")),
                Map.entry("MYPAGE", loadStTemplate("MyPage.st")),
                Map.entry("ADMIN", loadStTemplate("Admin.st")),
                Map.entry("ALGORITHM", loadStTemplate("Algorithm.st")),
                Map.entry("CODE_ANALYSIS", loadStTemplate("CodeAnalysis.st")),
                Map.entry("CODEBOARD", loadStTemplate("CodeBoard.st") + "\n\n" + loadStTemplate("BoardCommonGuide.st")),
                Map.entry("FREEBOARD", loadStTemplate("FreeBoard.st") + "\n\n" + loadStTemplate("BoardCommonGuide.st")),
                Map.entry("PAYMENTS", loadStTemplate("Payments.st")),
                Map.entry("SOCIAL_LOGIN", loadStTemplate("SocialLogin.st")),
                Map.entry("USERS", loadStTemplate("Users.st"))
        );

        // Global.st 템플릿 로드 (하드코딩 제거)
        this.globalPromptTemplate = loadStTemplate("Global.st");
    }

    // .st 파일 로드 헬퍼 메서드
    private String loadStTemplate(String filename) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/chatbot/" + filename);
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            log.debug("✅ Loaded prompts/chatbot/{} ({} chars)", filename, content.length());
            return content;
        } catch (Exception e) {
            log.error("❌ Failed to load prompts/chatbot/{}: {}", filename, e.getMessage(), e);
            return "";
        }
    }

    // 공통(전역) 시스템 프롬프트 - Global.st 파일에서 로드 + 템플릿 치환
    private String createGlobalSystemPrompt(String projectName) {
        // Global.st 내용에 {projectName} 치환
        String systemPrompt = globalPromptTemplate.replace("{projectName}", projectName);

        // Spring AI 템플릿으로 추가 처리 (필요시)
        if (systemPrompt.contains("{projectName}")) {
            SystemPromptTemplate template = new SystemPromptTemplate(globalPromptTemplate);
            return template.render(Map.of("projectName", projectName));
        }
        return systemPrompt;
    }

    // 페이지(컨텍스트)별 추가 프롬프트 - .st 파일에서 로드
    private String createPagePrompt(String pageContext) {
        return pagePrompts.getOrDefault(pageContext, "");
    }

    // 1️⃣ "안내원 시스템 프롬프트" → 전역 + 페이지 프롬프트 합치기
    public String createGuideSystemPrompt(String projectName, String pageContext) {
        String global = createGlobalSystemPrompt(projectName);
        String page = createPagePrompt(pageContext);
        return global + "\n\n" + page;
    }

    // 2️⃣ 완전한 프롬프트 생성 (페이지 컨텍스트 추가)
    public String buildCompleteGuidePrompt(String projectName, String pageContext, String userQuery) {
        String systemPrompt = createGuideSystemPrompt(projectName, pageContext);
        PromptTemplate userTemplate = new PromptTemplate("고객님 질문: {query}");
        String userPrompt = userTemplate.render(Map.of("query", userQuery));
        return systemPrompt + "\n\n" + userPrompt;
    }
}