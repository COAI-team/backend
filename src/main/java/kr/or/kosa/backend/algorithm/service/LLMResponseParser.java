package kr.or.kosa.backend.algorithm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.request.ProblemGenerationRequestDto;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemSource;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM 응답 JSON 파서
 * AI가 생성한 JSON 응답을 기존 DTO로 변환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * 파싱 결과를 담는 레코드
     * 기존 DTO 재사용
     */
    public record ParsedResult(
            AlgoProblemDto problem,
            List<AlgoTestcaseDto> testCases,
            String optimalCode,
            String naiveCode,
            String expectedTimeComplexity
    ) {}

    /**
     * LLM 응답을 파싱하여 문제 데이터 추출
     *
     * @param aiResponse LLM의 원본 응답
     * @param request    문제 생성 요청 정보
     * @return 파싱된 결과
     * @throws JsonParseException JSON 파싱 실패 시
     */
    public ParsedResult parse(String aiResponse, ProblemGenerationRequestDto request) {
        String cleanedJson = null;
        try {
            // 디버그: 원본 응답 로그 (문제 발생 시 확인용)
            log.debug("LLM 원본 응답 (처음 500자): {}",
                    aiResponse != null && aiResponse.length() > 500
                            ? aiResponse.substring(0, 500) + "..."
                            : aiResponse);

            cleanedJson = sanitizeJson(aiResponse);

            // 디버그: 정제 후 JSON 로그
            log.debug("정제 후 JSON (처음 500자): {}",
                    cleanedJson.length() > 500
                            ? cleanedJson.substring(0, 500) + "..."
                            : cleanedJson);

            JsonNode root = objectMapper.readTree(cleanedJson);

            AlgoProblemDto problem = parseProblem(root, request);
            List<AlgoTestcaseDto> testCases = parseTestCases(root);

            String optimalCode = getText(root, "optimalCode");
            String naiveCode = getText(root, "naiveCode");
            String expectedTimeComplexity = getText(root, "expectedTimeComplexity");

            // 검증 코드 파싱 결과 로그 (디버그용)
            log.info("검증 코드 파싱 - optimalCode: {}, naiveCode: {}, timeComplexity: {}",
                    optimalCode != null ? optimalCode.length() + "자" : "null",
                    naiveCode != null ? naiveCode.length() + "자" : "null",
                    expectedTimeComplexity);

            // 검증 코드가 없는 경우 JSON 필드 존재 여부 추가 확인
            if (optimalCode == null && naiveCode == null) {
                log.warn("검증 코드 필드 없음 - JSON에 optimalCode 키 존재: {}, naiveCode 키 존재: {}",
                        root.has("optimalCode"),
                        root.has("naiveCode"));
            }

            log.info("JSON 파싱 완료 - 제목: {}, 테스트케이스: {}개",
                    problem.getAlgoProblemTitle(), testCases.size());

            return new ParsedResult(problem, testCases, optimalCode, naiveCode, expectedTimeComplexity);

        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패: {}", e.getMessage());
            log.error("파싱 실패한 JSON 전체:\n{}", cleanedJson);
            throw new JsonParseException("LLM 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * JSON 응답 정제
     * - 마크다운 코드 블록 제거
     * - JavaScript 스타일 문자열 연결 제거 ("str1" + "str2" → "str1str2")
     * - 문자열 값 내부의 줄바꿈을 \n으로 이스케이프
     * - 유효하지 않은 이스케이프 시퀀스 처리
     */
    public String sanitizeJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("빈 응답");
            return "{}";
        }

        // 마크다운 코드 블록 제거
        String cleaned = rawResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .replaceAll("```", "")
                .trim();

        if (cleaned.isEmpty()) {
            return "{}";
        }

        // Python 코드 표현식이 포함된 테스트케이스 제거
        String beforePythonRemoval = cleaned;
        cleaned = removePythonExpressions(cleaned);
        if (!beforePythonRemoval.equals(cleaned)) {
            log.warn("Python 표현식 제거로 JSON 변경됨 - 변경 전 길이: {}, 변경 후 길이: {}",
                    beforePythonRemoval.length(), cleaned.length());
        }
        log.debug("Python 표현식 제거 후 (처음 300자): {}",
                cleaned.length() > 300 ? cleaned.substring(0, 300) + "..." : cleaned);

        // JavaScript 스타일 문자열 연결 제거 ("str" + "str" 패턴)
        cleaned = removeStringConcatenation(cleaned);
        log.debug("문자열 연결 제거 후 (처음 300자): {}",
                cleaned.length() > 300 ? cleaned.substring(0, 300) + "..." : cleaned);

        // JSON 문자열 값 내부의 줄바꿈/탭을 이스케이프 처리
        cleaned = escapeNewlinesInJsonStrings(cleaned);
        log.debug("줄바꿈 이스케이프 후 (처음 300자): {}",
                cleaned.length() > 300 ? cleaned.substring(0, 300) + "..." : cleaned);

        // 유효하지 않은 JSON 이스케이프 시퀀스 수정
        String result = fixInvalidEscapes(cleaned);
        log.debug("최종 정제 완료 (처음 300자): {}",
                result.length() > 300 ? result.substring(0, 300) + "..." : result);

        return result;
    }

    /**
     * Python 코드 표현식이 포함된 테스트케이스 항목 제거
     * LLM이 큰 데이터를 Python 코드로 표현한 경우 해당 테스트케이스 제거
     * 예: ".join(str(x) for x in range(100))" 같은 패턴
     */
    private String removePythonExpressions(String json) {
        // Python 코드 패턴들: .join(, range(, for x in, f"{
        if (!json.contains(".join(") && !json.contains("range(") &&
            !json.contains(" for ") && !json.contains("f\"{")) {
            return json; // Python 표현식 없음
        }

        log.warn("Python 코드 표현식 감지됨 - 문제가 있는 테스트케이스 제거 시도");

        // testCases 배열 위치 찾기
        int testCasesStart = json.indexOf("\"testCases\"");
        if (testCasesStart == -1) {
            return json;
        }

        // testCases 배열 시작 '[' 찾기
        int arrayStart = json.indexOf('[', testCasesStart);
        if (arrayStart == -1) {
            return json;
        }

        // testCases 배열 끝 ']' 찾기 (중첩 괄호 고려)
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd == -1) {
            return json;
        }

        String beforeTestCases = json.substring(0, arrayStart + 1);
        String testCasesContent = json.substring(arrayStart + 1, arrayEnd);
        String afterTestCases = json.substring(arrayEnd);

        // 각 테스트케이스 객체를 파싱하여 Python 표현식이 있는 것 제거
        List<String> cleanedTestCases = new ArrayList<>();
        int pos = 0;
        while (pos < testCasesContent.length()) {
            // 공백 건너뛰기
            while (pos < testCasesContent.length() &&
                   Character.isWhitespace(testCasesContent.charAt(pos))) {
                pos++;
            }
            if (pos >= testCasesContent.length()) break;

            // '{' 찾기
            if (testCasesContent.charAt(pos) != '{') {
                pos++;
                continue;
            }

            // 객체 끝 '}' 찾기
            int objEnd = findMatchingBrace(testCasesContent, pos);
            if (objEnd == -1) break;

            String testCaseObj = testCasesContent.substring(pos, objEnd + 1);

            // Python 표현식 포함 여부 확인
            if (!containsPythonExpression(testCaseObj)) {
                cleanedTestCases.add(testCaseObj);
            } else {
                log.debug("Python 표현식 포함 테스트케이스 제거: {}...",
                        testCaseObj.length() > 100 ? testCaseObj.substring(0, 100) : testCaseObj);
            }

            pos = objEnd + 1;
            // 쉼표 건너뛰기
            while (pos < testCasesContent.length() &&
                   (Character.isWhitespace(testCasesContent.charAt(pos)) ||
                    testCasesContent.charAt(pos) == ',')) {
                pos++;
            }
        }

        // 정제된 testCases 재조립
        String cleanedArray = String.join(",\n    ", cleanedTestCases);
        return beforeTestCases + "\n    " + cleanedArray + "\n  " + afterTestCases;
    }

    /**
     * Python 표현식 포함 여부 확인
     */
    private boolean containsPythonExpression(String text) {
        // 문자열 값 외부의 Python 표현식 패턴 감지
        // ".join(, range(, for x in 이 따옴표 밖에 있으면 Python 코드
        return text.matches(".*\"[^\"]*\"\\s*\\.join\\s*\\(.*") ||
               text.matches(".*\"[^\"]*\"\\s*\\+\\s*\".*") ||  // 문자열 연결
               text.contains("range(") ||
               text.contains("f\"{") ||
               text.matches(".*\\sfor\\s+\\w+\\s+in\\s.*");
    }

    /**
     * 매칭되는 닫는 대괄호 찾기
     */
    private int findMatchingBracket(String text, int openPos) {
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = openPos + 1; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /**
     * 매칭되는 닫는 중괄호 찾기
     */
    private int findMatchingBrace(String text, int openPos) {
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = openPos + 1; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /**
     * JavaScript 스타일 문자열 연결 제거
     * "string1" + "string2" → "string1string2"
     * LLM이 멀티라인 코드를 문자열 연결로 표현할 경우 처리
     */
    private String removeStringConcatenation(String json) {
        // 패턴: "..." + "..." 또는 "..." +\n"..."
        // 반복적으로 제거 (중첩된 연결 처리)
        String result = json;
        String previous;
        int iterations = 0;
        int maxIterations = 100; // 무한 루프 방지

        do {
            previous = result;
            // "..." + "..." 패턴 (공백, 줄바꿈 포함)
            result = result.replaceAll("\"\\s*\\+\\s*\"", "");
            iterations++;
        } while (!result.equals(previous) && iterations < maxIterations);

        if (iterations > 1) {
            log.debug("문자열 연결 {}회 제거", iterations - 1);
        }

        return result;
    }

    /**
     * JSON 문자열 값 내부의 줄바꿈과 탭을 이스케이프 처리
     * 따옴표로 둘러싸인 문자열 내부만 처리
     */
    private String escapeNewlinesInJsonStrings(String json) {
        StringBuilder result = new StringBuilder();
        boolean insideString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                result.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                insideString = !insideString;
                result.append(c);
                continue;
            }

            // 문자열 내부에서 실제 줄바꿈/탭을 이스케이프
            if (insideString) {
                if (c == '\n') {
                    result.append("\\n");
                    continue;
                }
                if (c == '\r') {
                    result.append("\\r");
                    continue;
                }
                if (c == '\t') {
                    result.append("\\t");
                    continue;
                }
            }

            result.append(c);
        }

        return result.toString();
    }

    /**
     * 유효하지 않은 이스케이프 시퀀스를 이중 백슬래시로 변환
     * JSON 유효 이스케이프: ", \, /, b, f, n, r, t, uXXXX
     */
    private String fixInvalidEscapes(String json) {
        StringBuilder result = new StringBuilder();
        int fixedCount = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);

                if (isValidEscape(next)) {
                    result.append(c).append(next);
                    i++;
                } else if (next == 'u' && i + 5 < json.length()) {
                    String hex = json.substring(i + 2, i + 6);
                    if (hex.matches("[0-9a-fA-F]{4}")) {
                        result.append(json, i, i + 6);
                        i += 5;
                    } else {
                        result.append("\\\\");
                        fixedCount++;
                    }
                } else {
                    result.append("\\\\");
                    fixedCount++;
                }
            } else if (c == '\\') {
                result.append("\\\\");
                fixedCount++;
            } else {
                result.append(c);
            }
        }

        if (fixedCount > 0) {
            log.debug("이스케이프 시퀀스 {}개 수정", fixedCount);
        }

        return result.toString();
    }

    private boolean isValidEscape(char c) {
        return c == '"' || c == '\\' || c == '/' ||
               c == 'b' || c == 'f' || c == 'n' ||
               c == 'r' || c == 't';
    }

    /**
     * 문제 정보 파싱
     */
    private AlgoProblemDto parseProblem(JsonNode root, ProblemGenerationRequestDto request) {
        ProblemType problemType = "SQL".equalsIgnoreCase(request.getProblemType())
                ? ProblemType.SQL
                : ProblemType.ALGORITHM;

        String initScript = null;
        if (problemType == ProblemType.SQL) {
            initScript = getText(root, "initScript");
        }

        String title = getRequiredText(root, "title");
        String description = getText(root, "description");
        String constraints = getText(root, "constraints");
        String inputFormat = getText(root, "inputFormat");
        String outputFormat = getText(root, "outputFormat");
        String sampleInput = getText(root, "sampleInput");
        String sampleOutput = getText(root, "sampleOutput");
        String expectedTimeComplexity = getText(root, "expectedTimeComplexity");

        // 전체 설명 구성
        String fullDescription = buildFullDescription(
                description, inputFormat, outputFormat, constraints, sampleInput, sampleOutput);

        return AlgoProblemDto.builder()
                .algoProblemTitle(title)
                .algoProblemDescription(fullDescription)
                .algoProblemDifficulty(request.getDifficulty())
                .algoProblemSource(ProblemSource.AI_GENERATED)
                .problemType(problemType)
                .initScript(initScript)
                .constraints(constraints)
                .inputFormat(inputFormat)
                .outputFormat(outputFormat)
                .expectedTimeComplexity(expectedTimeComplexity)
                .timelimit(request.getTimeLimit() != null
                        ? request.getTimeLimit()
                        : getDefaultTimeLimit(request.getDifficulty()))
                .memorylimit(request.getMemoryLimit())
                .algoProblemTags(buildTagsJson(request.getTopic()))
                .algoProblemStatus(true)
                .algoCreatedAt(LocalDateTime.now())
                .algoUpdatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 테스트케이스 파싱
     * Code-First 방식: output이 없어도 input만 있으면 파싱 (output은 코드 실행으로 생성)
     */
    private List<AlgoTestcaseDto> parseTestCases(JsonNode root) {
        List<AlgoTestcaseDto> testCases = new ArrayList<>();

        // 샘플 입출력 (sampleInput/sampleOutput)
        String sampleInput = getText(root, "sampleInput");
        String sampleOutput = getText(root, "sampleOutput");

        if (sampleInput != null && sampleOutput != null) {
            testCases.add(AlgoTestcaseDto.builder()
                    .inputData(sampleInput)
                    .expectedOutput(sampleOutput)
                    .isSample(true)
                    .build());
        }

        // testCases 배열
        JsonNode testCasesNode = root.get("testCases");
        if (testCasesNode != null && testCasesNode.isArray()) {
            for (JsonNode tc : testCasesNode) {
                boolean isSample = tc.has("isSample") && tc.get("isSample").asBoolean(false);

                String input = getText(tc, "input");
                String output = getText(tc, "output");  // Code-First에서는 null일 수 있음

                // input은 필수
                if (input == null || input.isBlank()) {
                    log.debug("input이 없는 테스트케이스 건너뜀");
                    continue;
                }

                testCases.add(AlgoTestcaseDto.builder()
                        .inputData(input)
                        .expectedOutput(output)  // null이어도 OK (Code-First에서 나중에 생성)
                        .isSample(isSample)
                        .build());
            }
        }

        log.debug("테스트케이스 파싱 완료 - 총 {}개 (output 있음: {}개)",
                testCases.size(),
                testCases.stream().filter(tc -> tc.getExpectedOutput() != null).count());

        return testCases;
    }

    private String buildFullDescription(String description, String inputFormat,
                                         String outputFormat, String constraints,
                                         String sampleInput, String sampleOutput) {
        StringBuilder sb = new StringBuilder();

        if (description != null) {
            sb.append(description).append("\n\n");
        }

        if (inputFormat != null) {
            sb.append("**입력**\n").append(inputFormat).append("\n\n");
        }

        if (outputFormat != null) {
            sb.append("**출력**\n").append(outputFormat).append("\n\n");
        }

        if (constraints != null) {
            sb.append("**제한 사항**\n").append(constraints).append("\n\n");
        }

        if (sampleInput != null) {
            sb.append("**예제 입력**\n").append(sampleInput).append("\n\n");
        }

        if (sampleOutput != null) {
            sb.append("**예제 출력**\n").append(sampleOutput);
        }

        return sb.toString().trim();
    }

    private String getText(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private String getRequiredText(JsonNode node, String field) {
        String value = getText(node, field);
        if (value == null || value.isBlank()) {
            throw new JsonParseException("필수 필드 누락: " + field);
        }
        return value;
    }

    private Integer getDefaultTimeLimit(ProblemDifficulty difficulty) {
        return switch (difficulty) {
            case BRONZE -> 1000;
            case SILVER -> 2000;
            case GOLD -> 3000;
            case PLATINUM -> 5000;
        };
    }

    private String buildTagsJson(String topic) {
        if (topic == null || topic.isBlank()) {
            return "[]";
        }

        try {
            String[] tags = topic.split(",");
            List<String> tagList = new ArrayList<>();

            for (String tag : tags) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    tagList.add(trimmed);
                }
            }

            return objectMapper.writeValueAsString(tagList);
        } catch (Exception e) {
            log.warn("태그 JSON 변환 실패: {}", topic);
            return "[\"" + topic.replace("\"", "\\\"") + "\"]";
        }
    }

    /**
     * JSON 파싱 예외
     */
    public static class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }

        public JsonParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
