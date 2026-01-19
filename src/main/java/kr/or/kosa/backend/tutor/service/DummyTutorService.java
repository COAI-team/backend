package kr.or.kosa.backend.tutor.service;

import jakarta.annotation.PostConstruct;
import kr.or.kosa.backend.tutor.dto.TutorClientMessage;
import kr.or.kosa.backend.tutor.dto.TutorServerMessage;
import kr.or.kosa.backend.tutor.subscription.SubscriptionTier;
import kr.or.kosa.backend.tutor.subscription.SubscriptionTierResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class DummyTutorService implements TutorService {

    private final SubscriptionTierResolver subscriptionTierResolver;

    @PostConstruct
    public void init() {
        log.info("TutorService impl = {}", this.getClass().getSimpleName());
    }

    @Override
    public TutorServerMessage handleMessage(TutorClientMessage clientMessage) {
        if (clientMessage == null) {
            log.warn("Received null TutorClientMessage");
            return TutorServerMessage.builder()
                    .type("ERROR")
                    .content("튜터 메시지: 요청을 확인할 수 없습니다.")
                    .build();
        }

        // 티어 판단: (Dummy는 SecurityContext를 안 쓰니까, 요청에 실린 userId 기준)
        // local/dev에서만 쓰는 더미이므로 여기서는 OK
        SubscriptionTier tier = subscriptionTierResolver.resolveTier(clientMessage.getUserId());

        // FREE 차단
        if (tier == null || tier == SubscriptionTier.FREE) {
            log.info("Tutor access blocked for FREE tier userId={}", clientMessage.getUserId());
            return TutorServerMessage.builder()
                    .type("ERROR")
                    .content("이 계정은 Live Tutor를 사용할 수 없습니다. Basic 또는 Pro 구독을 활성화해 주세요.")
                    .problemId(clientMessage.getProblemId())
                    .userId(clientMessage.getUserId())
                    .triggerType(normalizeTriggerType(clientMessage.getTriggerType()))
                    .build();
        }

        // BASIC은 AUTO 비허용 (❗ null 반환 금지 → INFO로 반환)
        if (tier == SubscriptionTier.BASIC && "AUTO".equalsIgnoreCase(clientMessage.getTriggerType())) {
            log.debug("AUTO trigger blocked for BASIC tier userId={} problemId={}",
                    clientMessage.getUserId(), clientMessage.getProblemId());

            return TutorServerMessage.builder()
                    .type("INFO")
                    .content("BASIC 플랜에서는 자동 힌트를 사용할 수 없습니다. (Pro 전용)")
                    .problemId(clientMessage.getProblemId())
                    .userId(clientMessage.getUserId())
                    .triggerType("AUTO")
                    .build();
        }

        String normalizedTriggerType = normalizeTriggerType(clientMessage.getTriggerType());
        String effectiveTriggerType = "QUESTION".equals(normalizedTriggerType) ? "USER" : normalizedTriggerType;

        String content = buildContent(effectiveTriggerType, clientMessage.getCode(), clientMessage.getMessage());
        String responseType = ("USER".equals(effectiveTriggerType) || "AUTO".equals(effectiveTriggerType)) ? "HINT" : "INFO";

        return TutorServerMessage.builder()
                .type(responseType)
                .content(content)
                .problemId(clientMessage.getProblemId())
                .userId(clientMessage.getUserId())
                .triggerType(effectiveTriggerType) // normalize해서 내려주는 게 프론트도 편함
                .build();
    }

    private String normalizeTriggerType(String triggerType) {
        if (triggerType == null) {
            return "USER";
        }
        String t = triggerType.trim().toUpperCase(Locale.ROOT);
        return t.isBlank() ? "USER" : t;
    }

    private String buildContent(String triggerType, String code, String question) {
        if ("AUTO".equals(triggerType)) {
            int lineCount = countLines(code);
            String[] autoTips = new String[]{
                    "입출력 예외 상황(빈 입력, 최대 입력 크기)에 대한 처리 여부를 다시 확인해 보세요.",
                    "반례가 될 수 있는 케이스(중복, 음수/0, 이미 정렬된 입력)를 직접 가정해 보세요.",
                    "현재 코드에서 반복되는 계산이 있는지 찾아보고, 값을 저장해서 재사용할 수 있는지 확인해 보세요.",
                    "조건문/루프의 경계(<=, <) 때문에 한 케이스가 빠지는지 점검해 보세요.",
                    "출력 형식(공백/줄바꿈)이 문제 요구사항과 정확히 일치하는지 확인해 보세요."
            };
            int tipIndex = Math.abs(code != null ? code.hashCode() : 0) % autoTips.length;
            return String.format("자동 힌트(더미): 현재 코드 줄 수는 %d줄입니다. %s", lineCount, autoTips[tipIndex]);
        }

        if ("USER".equals(triggerType)) {
            return buildUserResponse(question, code);
        }

        return "튜터 메시지(더미): 지원되지 않는 트리거 타입입니다. AUTO 또는 USER로 다시 시도해 주세요.";
    }

    private int countLines(String code) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        return (int) code.lines().count();
    }

    private String buildUserResponse(String question, String code) {
        String normalizedQuestion = question == null ? "" : question.trim();
        int lineCount = countLines(code);
        int variant = Math.abs((normalizedQuestion + lineCount).hashCode()) % 3;

        String questionSnippet = normalizedQuestion.isBlank()
                ? "질문이 비어 있어요. 정확히 궁금한 부분(로직/반례/입출력)을 한 줄로 적어주세요."
                : "질문: \"" + normalizedQuestion + "\"";

        return switch (variant) {
            case 0 -> String.format(
                    "%s%n- 지금 코드에서 핵심 로직이 어디인지(함수/루프) 먼저 표시해 보세요.%n- 그 부분에 예시 입력을 넣고, 값이 어떻게 변해야 하는지 한 줄씩 따라가 보세요.%n- 틀릴 만한 반례(가장 작은 값/가장 큰 값/중복)를 2~3개 만들어 테스트해 보세요.",
                    questionSnippet
            );
            case 1 -> String.format(
                    "%s%n- 코드 줄 수는 대략 %d줄이에요. 입력 처리/핵심 로직/출력을 분리하면 디버깅이 쉬워져요.%n- 조건문 경계(<, <=) 때문에 한 케이스가 빠지는지 확인해 보세요.%n- 출력 형식(공백/줄바꿈)이 정확한지 다시 점검해 보세요.",
                    questionSnippet, lineCount
            );
            default -> String.format(
                    "%s%n- 문제 요구사항을 단계로 쪼개서(1) 입력 (2) 처리 (3) 출력 순서로 다시 적어보세요.%n- 지금 코드가 그 단계들을 빠짐없이 하고 있는지 체크해 보세요.%n- 애매한 부분이 있으면, 그 단계에서 필요한 변수/조건을 더 명확히 잡아보면 좋아요.",
                    questionSnippet
            );
        };
    }
}
