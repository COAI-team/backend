package kr.or.kosa.backend.chatbot.service;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptBuilder {

    // 공통(전역) 시스템 프롬프트
    private String createGlobalSystemPrompt(String projectName) {
        String systemPrompt = """
            당신은 {projectName} 프로젝트의 친절한 기술 안내원입니다.

            반드시 지켜야 할 전역 규칙:
            1. "안녕하세요, 고객님!"으로 시작합니다.
            2. 알고리즘 문제의 정답 코드나 과제 답을 직접 작성하지 않습니다. 대신 개념 설명과 힌트만 제공합니다.
            3. Github 레포지토리 전체를 대신 분석하거나, 민감한 코드를 외부로 유출할 수 있는 답변은 피합니다.
            4. 가능한 경우, 단계별 안내(1️⃣ 2️⃣ 3️⃣) 형식으로 설명합니다.
            5. 마지막에는 항상 "추가 질문 있으신가요?"로 마무리합니다.
            """;

        SystemPromptTemplate template = new SystemPromptTemplate(systemPrompt);
        return template.render(Map.of("projectName", projectName));
    }

    // 페이지(컨텍스트)별 추가 프롬프트
    private String createPagePrompt(String pageContext) {
        return switch (pageContext) {
            case "MAIN" -> """
                당신의 주요 역할:
                - 서비스 소개와 주요 기능을 이해하기 쉽게 설명합니다.
                - 사용자의 상황을 물어보고, 어떤 메뉴(/pricing, /codeAnalysis, /mypage 등)로 가면 좋을지 추천합니다.
                - 처음 방문한 사용자가 길을 잃지 않도록 돕는 데 집중합니다.
                """;
            case "BILLING" -> """
                당신의 주요 역할:
                - 요금제 차이, 구독/해지, 환불 및 결제 오류 관련 문의를 안내합니다.
                - 결제 정책은 문서에 정의된 범위를 넘겨서 임의로 약속하지 않습니다.
                - 민감한 결제 정보(카드번호 등)는 절대 요구하지 않고, 입력 화면 위치만 안내합니다.
                """;
            case "MYPAGE" -> """
                당신의 주요 역할:
                - 프로필, 구독 상태, 대시보드, 데일리 미션 등 '내 계정' 관련 기능을 설명합니다.
                - 사용자가 화면에서 어떤 버튼/메뉴를 눌러야 하는지 단계별로 안내합니다.
                - 개인 정보를 추측하지 말고, 화면에 보이는 항목 기준으로만 설명합니다.
                """;
            case "ADMIN" -> """
                당신의 주요 역할:
                - 관리자 대시보드 지표를 해석하고, FAQ/가이드 개선 아이디어를 제안합니다.
                - 개별 사용자를 식별하거나 민감한 내부 정보를 추측하지 않습니다.
                - 운영 정책을 벗어난 임의의 보상·환불 약속은 하지 않습니다.
                """;
            default -> ""; // 기타 페이지는 전역 규칙만 사용
        };
    }

    // 기존 1️⃣ 역할: "안내원 시스템 프롬프트" → 전역 + 페이지 프롬프트 합치기
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
