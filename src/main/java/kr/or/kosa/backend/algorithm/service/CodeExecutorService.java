package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.LanguageDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 코드 실행 서비스 추상화 레이어
 * 환경에 따라 Judge0 또는 Piston API를 선택하여 사용
 *
 * - 로컬 개발 (ARM64 Mac): Piston API 사용
 * - 배포 서버 (x86_64 Linux): Judge0 셀프호스팅 사용
 *
 * 변경사항 (2025-12-13):
 * - languageId (Integer)를 받아서 LanguageService로 조회
 * - Judge0: languageId 직접 사용 (LANGUAGES.LANGUAGE_ID = Judge0 API ID)
 * - Piston: pistonLanguage 사용 (LANGUAGES.PISTON_LANGUAGE)
 */
@Service
@Slf4j
public class CodeExecutorService {

    private final Judge0Service judge0Service;
    private final PistonService pistonService;
    private final LanguageService languageService;

    @Value("${code-executor.provider:piston}") // judge0, piston
    private String provider;

    public CodeExecutorService(Judge0Service judge0Service, PistonService pistonService, LanguageService languageService) {
        this.judge0Service = judge0Service;
        this.pistonService = pistonService;
        this.languageService = languageService;
    }

    /**
     * 코드 채점 실행
     * 설정된 provider에 따라 Judge0 또는 Piston 사용
     *
     * @param sourceCode  제출할 소스 코드
     * @param languageId  언어 ID (LANGUAGES.LANGUAGE_ID)
     * @param testCases   AlgoTestcaseDto 목록
     * @param timeLimit   시간 제한 (ms)
     * @param memoryLimit 메모리 제한 (KB)
     * @return 채점 결과
     */
    public CompletableFuture<TestRunResponseDto> judgeCode(
            String sourceCode,
            Integer languageId,
            List<AlgoTestcaseDto> testCases,
            Integer timeLimit,
            Integer memoryLimit) {

        // 언어 정보 조회
        LanguageDto language = languageService.getById(languageId);
        if (language == null) {
            throw new IllegalArgumentException("지원하지 않는 언어 ID입니다: " + languageId);
        }

        log.info("코드 실행 요청 - provider: {}, languageId: {}, languageName: {}, testCases: {}",
                provider, languageId, language.getLanguageName(), testCases.size());

        if ("judge0".equalsIgnoreCase(provider)) {
            log.debug("Judge0 서비스 사용 - languageId: {}", languageId);
            // Judge0는 languageId를 직접 사용 (LANGUAGES.LANGUAGE_ID = Judge0 API ID)
            return judge0Service.judgeCode(sourceCode, languageId, testCases, timeLimit, memoryLimit);
        } else {
            log.debug("Piston 서비스 사용 - pistonLanguage: {}", language.getPistonLanguage());
            // Piston은 pistonLanguage 사용 (LANGUAGES.PISTON_LANGUAGE)
            String pistonLanguage = language.getPistonLanguage();
            if (pistonLanguage == null || pistonLanguage.isBlank()) {
                throw new IllegalArgumentException(
                        "Piston API에서 지원하지 않는 언어입니다: " + language.getLanguageName() +
                        " (PISTON_LANGUAGE가 설정되지 않음)");
            }
            return pistonService.judgeCode(sourceCode, pistonLanguage, testCases, timeLimit, memoryLimit);
        }
    }

    /**
     * 현재 사용 중인 provider 반환
     */
    public String getCurrentProvider() {
        return provider;
    }

    /**
     * Judge0 사용 여부
     */
    public boolean isUsingJudge0() {
        return "judge0".equalsIgnoreCase(provider);
    }

    /**
     * Piston 사용 여부
     */
    public boolean isUsingPiston() {
        return "piston".equalsIgnoreCase(provider);
    }
}
