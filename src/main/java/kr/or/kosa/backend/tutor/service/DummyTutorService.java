package kr.or.kosa.backend.tutor.service;

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

    @Override
    public TutorServerMessage handleMessage(TutorClientMessage clientMessage) {
        if (clientMessage == null) {
            log.warn("Received null TutorClientMessage");
            return TutorServerMessage.builder()
                    .type("INFO")
                    .content("튜터 메시지: 요청을 확인할 수 없습니다.")
                    .build();
        }

        SubscriptionTier tier = subscriptionTierResolver.resolveTier(clientMessage.getUserId());
        if (tier == SubscriptionTier.FREE) {
            log.info("Tutor access blocked for FREE tier userId={}", clientMessage.getUserId());
            return TutorServerMessage.builder()
                    .type("ERROR")
                    .content("이 계정은 Live Tutor를 사용할 수 없습니다. Basic 또는 Pro 구독을 활성화해 주세요.")
                    .problemId(clientMessage.getProblemId())
                    .userId(clientMessage.getUserId())
                    .triggerType(clientMessage.getTriggerType())
                    .build();
        }

        if (tier == SubscriptionTier.BASIC && "AUTO".equalsIgnoreCase(clientMessage.getTriggerType())) {
            log.debug("AUTO trigger ignored for BASIC tier userId={} problemId={}", clientMessage.getUserId(), clientMessage.getProblemId());
            return null;
        }

        String normalizedTriggerType = normalizeTriggerType(clientMessage.getTriggerType());
        String effectiveTriggerType = "QUESTION".equals(normalizedTriggerType) ? "USER" : normalizedTriggerType;
        String content = buildContent(effectiveTriggerType, clientMessage.getCode(), clientMessage.getMessage());
        String responseType = "USER".equals(effectiveTriggerType) || "AUTO".equals(effectiveTriggerType) ? "HINT" : "INFO";

        return TutorServerMessage.builder()
                .type(responseType)
                .content(content)
                .problemId(clientMessage.getProblemId())
                .userId(clientMessage.getUserId())
                .triggerType(clientMessage.getTriggerType())
                .build();
    }

    private String normalizeTriggerType(String triggerType) {
        if (triggerType == null) {
            return "UNKNOWN";
        }
        return triggerType.trim().toUpperCase(Locale.ROOT);
    }

    private String buildContent(String triggerType, String code, String question) {
        if ("AUTO".equals(triggerType)) {
            int lineCount = countLines(code);
            String[] autoTips = new String[]{
                    "입출력 예외 상황(빈 입력, 최대 입력 크기)에 대한 처리 여부를 다시 확인해 보세요.",
                    "시간 복잡도를 O(N log N) 이하로 유지할 수 있는지 검토해 보세요.",
                    "반례가 될 수 있는 정렬/중복/음수/0 처리 케이스를 점검해 보세요.",
                    "자료구조 선택이 적절한지 확인해 보세요. 큐/스택/우선순위큐로 단순화할 여지가 있는지 검토하세요.",
                    "부분 문제로 나눌 수 있는지, 반복되는 로직을 함수로 빼서 테스트하기 쉬운 구조인지 점검하세요."
            };
            int tipIndex = Math.abs(code != null ? code.hashCode() : 0) % autoTips.length;
            return String.format(
                    "자동 힌트: 현재 코드 줄 수는 %d줄입니다. %s",
                    lineCount,
                    autoTips[tipIndex]
            );
        }

        if ("USER".equals(triggerType)) {
            return buildUserResponse(question, code);
        }

        return "튜터 메시지: 지원되지 않는 트리거 타입입니다. AUTO 또는 USER 모드로 다시 시도해 주세요.";
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
                ? "질문이 비어 있습니다. 정확히 궁금한 부분(로직/복잡도/반례)을 한 줄로 정리해 주세요."
                : "질문: \"" + normalizedQuestion + "\"";

        return switch (variant) {
            case 0 -> String.format(
                    "%s%n- 현재 로직의 병목이 어디인지(루프/정렬/재귀) 표시하고, 그 부분의 복잡도를 적어보세요.%n- 입력 크기 대비 필요한 자료구조(예: 해시, 우선순위큐, 세그먼트 트리)를 다시 선택해 보세요.%n- 실패할 반례를 2~3개 가정하고 그 흐름을 코드로 따라가 보세요.",
                    questionSnippet
            );
            case 1 -> String.format(
                    "%s%n- 코드 줄 수는 대략 %d줄입니다. 핵심 함수(입력 파싱/핵심 로직/출력)를 분리해 테스트하기 쉽게 만드세요.%n- 시간/메모리 제한 대비 현재 접근이 충분한지, 더 단순한 자료구조로 대체할 수 있는지 검토하세요.%n- 예외 입력(빈 값, 최대값, 중복/정렬 상태)을 직접 콘솔에서 실행해 보세요.",
                    questionSnippet,
                    lineCount
            );
            default -> String.format(
                    "%s%n- 문제의 의도(정렬/그리디/DP/그래프 중 하나인지)와 현재 접근이 맞는지 먼저 확인하세요.%n- 상태 전이를 표나 주석으로 적어보면서, 중복 계산을 캐싱할 수 있는지 살펴보세요.%n- 풀이 순서를 단계별로 다시 적어 보며 누락된 엣지 케이스가 없는지 점검하세요.",
                    questionSnippet
            );
        };
    }

    // TODO: 이후 단계에서 실제 LLM 기반 구현(예: LlmTutorService)으로 교체하거나 별도 구현체를 추가할 수 있습니다.
}
