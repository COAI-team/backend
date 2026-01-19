package kr.or.kosa.backend.codenose.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프롬프트 매니저 (PromptManager)
 * 
 * 역할:
 * 외부 파일(prompts/prompts.st)에 정의된 여러 프롬프트 템플릿을 애플리케이션 시작 시 로드하고,
 * 필요한 곳에서 키(Key)를 통해 쉽게 가져다 쓸 수 있도록 관리하는 설정(Configuration) 클래스입니다.
 * 
 * 주요 기능:
 * 1. 서버 시작 시(@PostConstruct) 지정된 경로의 파일 내용을 읽어옵니다.
 * 2. 정규표현식을 사용하여 하나의 파일 안에 있는 여러 프롬프트를 구분하여 Map에 저장합니다.
 * 3. 서비스 로직에서 getPrompt(key)를 호출하면 해당 키에 매핑된 프롬프트 문자열을 반환합니다.
 */
@Slf4j
@Service
public class PromptManager {

    // 파싱된 프롬프트들을 키-값 쌍으로 메모리에 저장할 저장소
    private final Map<String, String> prompts = new HashMap<>();

    // 프롬프트 템플릿 파일의 경로 (resources 디렉토리 기준)
    private static final String PROMPT_FILE_PATH = "prompts/prompts.st";

    // 파일 내에서 각 프롬프트를 구분하는 구분자 패턴
    // 예: "=== SYSTEM_PROMPT ===" 형태의 헤더를 찾습니다.
    private static final Pattern DELIMITER_PATTERN = Pattern.compile("=== (\\w+) ===");

    /**
     * 프롬프트 로드 메서드
     * 
     * 애플리케이션 컨텍스트가 초기화된 직후(@PostConstruct) 자동으로 실행됩니다.
     * 파일 입출력(I/O)을 수행하여 프롬프트 파일의 전체 내용을 문자열로 읽어온 뒤,
     * 파싱 로직(parsePrompts)으로 넘깁니다.
     */
    @PostConstruct
    public void loadPrompts() {
        try {
            // ClassPathResource를 사용해 classpath 내의 리소스 파일을 가져옵니다.
            ClassPathResource resource = new ClassPathResource(PROMPT_FILE_PATH);

            // InputStream을 통해 파일 내용을 UTF-8 문자열로 변환합니다.
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            // 읽어온 전체 본문을 파싱하여 Map에 저장합니다.
            parsePrompts(content);

            log.info("성공적으로 {} 개의 프롬프트를 {} 에서 로드했습니다.", prompts.size(), PROMPT_FILE_PATH);
        } catch (IOException e) {
            log.error("{} 파일에서 프롬프트를 로드하는데 실패했습니다.", PROMPT_FILE_PATH, e);
            throw new RuntimeException("프롬프트 로드 실패", e);
        }
    }

    /**
     * 프롬프트 파싱 로직
     * 
     * 하나의 큰 문자열(파일 전체 내용)을 구분자("=== KEY ===") 기준으로 쪼개어
     * 키와 실제 프롬프트 내용으로 분리합니다.
     * 
     * 작동 원리:
     * 1. 정규표현식 매처(Matcher)가 구분자 패턴을 찾으며 순회합니다.
     * 2. 구분자를 발견할 때마다, 이전 구분자 끝부터 현재 구분자 시작 전까지의 내용을 '이전 키'의 값으로 저장합니다.
     * 3. 현재 발견된 키를 '현재 키'로 설정하고 위치를 갱신합니다.
     * 4. 마지막에 남은 내용을 마지막 키의 값으로 저장합니다.
     */
    private void parsePrompts(String content) {
        Matcher matcher = DELIMITER_PATTERN.matcher(content);
        int lastEnd = 0;
        String currentKey = null;

        while (matcher.find()) {
            // 새로운 구분자를 찾았을 때, 이전에 처리 중이던 키가 있다면 내용을 저장합니다.
            if (currentKey != null) {
                // lastEnd(이전 구분자 끝) ~ matcher.start(현재 구분자 시작) 사이가 프롬프트 내용입니다.
                String promptContent = content.substring(lastEnd, matcher.start()).trim();
                prompts.put(currentKey, promptContent);
            }
            // 정규식 그룹 1번((\w+))이 프롬프트의 키(Key)가 됩니다.
            currentKey = matcher.group(1);
            lastEnd = matcher.end(); // 다음 내용 추출을 위해 끝 인덱스 업데이트
        }

        // 반복문이 끝난 후, 마지막 키에 대한 내용이 남아있으므로 이를 저장합니다.
        if (currentKey != null) {
            String promptContent = content.substring(lastEnd).trim();
            prompts.put(currentKey, promptContent);
        }
    }

    /**
     * 프롬프트 조회 메서드
     * 
     * 외부 서비스에서 특정 키에 해당하는 프롬프트 내용을 요청할 때 사용합니다.
     * 키가 존재하지 않으면 경고 로그를 남기고 빈 문자열을 반환하여 NPE를 방지합니다.
     */
    public String getPrompt(String key) {
        if (!prompts.containsKey(key)) {
            log.warn("요청한 프롬프트 키 '{}'를 찾을 수 없습니다!", key);
            return "";
        }
        return prompts.get(key);
    }
}
