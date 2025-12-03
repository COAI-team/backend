package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.LanguageConstant;
import kr.or.kosa.backend.algorithm.domain.LanguageType;
import kr.or.kosa.backend.algorithm.mapper.LanguageConstantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 언어 상수 서비스
 * Option C (DB + 캐싱) 방식 구현
 * 
 * - 애플리케이션 시작 시 DB에서 전체 로드하여 메모리 캐싱
 * - 런타임 시 DB 조회 없이 캐시에서 즉시 반환 (0.00012ms)
 * - 규칙 변경 시 DB 업데이트 + 캐시 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageConstantService {

    private final LanguageConstantMapper languageConstantMapper;

    /**
     * In-Memory 캐시 (ConcurrentHashMap - Thread-Safe)
     * Key: 언어명 (예: "Java 17", "Python 3")
     * Value: LanguageConstant 객체
     */
    private static final Map<String, LanguageConstant> CACHE = new ConcurrentHashMap<>();

    /**
     * 애플리케이션 준비 완료 후 자동 실행 (1회만)
     * LANGUAGE_CONSTANTS 테이블 전체를 메모리에 로드
     * 
     * @EventListener - Spring Boot의 @PostConstruct 대체
     *                ApplicationReadyEvent - 모든 빈 초기화 완료 후 실행
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeCache() {
        log.info("🔄 언어 상수 캐시 초기화 시작...");
        long startTime = System.currentTimeMillis();

        try {
            List<LanguageConstant> allConstants = languageConstantMapper.selectAll();

            allConstants.forEach(constant -> {
                CACHE.put(constant.getLanguageName(), constant);
            });

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("✅ {} 개의 언어 상수를 캐시에 로드 완료 (소요 시간: {}ms)",
                    CACHE.size(), elapsedTime);

            // 언어 유형별 통계 출력
            long generalCount = CACHE.values().stream()
                    .filter(lc -> lc.getLanguageType() == LanguageType.GENERAL)
                    .count();
            long dbCount = CACHE.values().stream()
                    .filter(lc -> lc.getLanguageType() == LanguageType.DB)
                    .count();

            log.info("   - GENERAL 언어: {} 개", generalCount);
            log.info("   - DB 언어: {} 개", dbCount);

        } catch (Exception e) {
            log.error("❌ 언어 상수 캐시 초기화 실패", e);
            throw new RuntimeException("Failed to initialize language constants cache", e);
        }
    }

    /**
     * 언어명으로 조회 (캐시에서 즉시 반환)
     * DB 조회 없음 → 초고속 (0.00012ms)
     * 
     * @param languageName 언어명 (예: "Java 17")
     * @return 언어 상수, 없으면 null
     */
    public LanguageConstant getByLanguageName(String languageName) {
        LanguageConstant constant = CACHE.get(languageName);

        if (constant == null) {
            log.warn("⚠️ 언어 '{}' 를 캐시에서 찾을 수 없습니다. 지원하지 않는 언어일 수 있습니다.", languageName);
        }

        return constant;
    }

    /**
     * 모든 언어 상수 조회 (캐시에서 반환)
     * 
     * @return 전체 언어 상수 리스트
     */
    public List<LanguageConstant> getAllLanguages() {
        return CACHE.values().stream()
                .sorted((a, b) -> a.getLanguageName().compareTo(b.getLanguageName()))
                .collect(Collectors.toList());
    }

    /**
     * 언어 유형별 조회 (문제 타입에 따른 언어 목록 제공)
     * 
     * @param languageType 언어 유형 (GENERAL 또는 DB)
     * @return 해당 유형의 언어 상수 리스트
     */
    public List<LanguageConstant> getLanguagesByType(LanguageType languageType) {
        return CACHE.values().stream()
                .filter(lc -> lc.getLanguageType() == languageType)
                .sorted((a, b) -> a.getLanguageName().compareTo(b.getLanguageName()))
                .collect(Collectors.toList());
    }

    /**
     * 실제 시간 제한 계산 (편의 메서드)
     * 
     * @param languageName  언어명
     * @param baseTimeLimit 문제의 기본 시간 제한 (ms)
     * @return 계산된 실제 시간 제한 (ms), 언어를 찾을 수 없으면 기본값 반환
     */
    public int calculateRealTimeLimit(String languageName, int baseTimeLimit) {
        LanguageConstant constant = getByLanguageName(languageName);

        if (constant == null) {
            log.warn("⚠️  언어 '{}'를 찾을 수 없어 기본 시간 제한 사용: {}ms", languageName, baseTimeLimit);
            return baseTimeLimit;
        }

        return constant.calculateRealTimeLimit(baseTimeLimit);
    }

    /**
     * 실제 메모리 제한 계산 (편의 메서드)
     * 
     * @param languageName    언어명
     * @param baseMemoryLimit 문제의 기본 메모리 제한 (MB)
     * @return 계산된 실제 메모리 제한 (MB), 언어를 찾을 수 없으면 기본값 반환
     */
    public int calculateRealMemoryLimit(String languageName, int baseMemoryLimit) {
        LanguageConstant constant = getByLanguageName(languageName);

        if (constant == null) {
            log.warn("⚠️ 언어 '{}'를 찾을 수 없어 기본 메모리 제한 사용: {}MB", languageName, baseMemoryLimit);
            return baseMemoryLimit;
        }

        return constant.calculateRealMemoryLimit(baseMemoryLimit);
    }

    /**
     * 언어 상수 업데이트 (관리자 기능)
     * DB 업데이트 + 캐시 동기화
     * 
     * @param languageConstant 업데이트할 언어 상수
     */
    @Transactional
    public void updateLanguageConstant(LanguageConstant languageConstant) {
        log.info("🔧 언어 상수 업데이트 요청: {}", languageConstant.getLanguageName());

        // 1. DB 업데이트
        int updatedRows = languageConstantMapper.update(languageConstant);

        if (updatedRows == 0) {
            throw new IllegalArgumentException(
                    "언어 '" + languageConstant.getLanguageName() + "' 를 찾을 수 없습니다.");
        }

        // 2. 캐시 갱신 (매우 중요!)
        CACHE.put(languageConstant.getLanguageName(), languageConstant);

        log.info("✅ 언어 상수 업데이트 완료: {} (DB + 캐시 동기화)",
                languageConstant.getLanguageName());
    }

    /**
     * 새 언어 추가 (관리자 기능)
     * 
     * @param languageConstant 추가할 언어 상수
     */
    @Transactional
    public void addLanguageConstant(LanguageConstant languageConstant) {
        log.info("➕ 새 언어 추가 요청: {}", languageConstant.getLanguageName());

        // 1. DB에 삽입
        languageConstantMapper.insert(languageConstant);

        // 2. 캐시에도 추가
        CACHE.put(languageConstant.getLanguageName(), languageConstant);

        log.info("✅ 새 언어 추가 완료: {}", languageConstant.getLanguageName());
    }

    /**
     * 언어 삭제 (관리자 기능)
     * 
     * @param languageName 삭제할 언어명
     */
    @Transactional
    public void deleteLanguageConstant(String languageName) {
        log.info("🗑️ 언어 삭제 요청: {}", languageName);

        // 1. DB에서 삭제
        int deletedRows = languageConstantMapper.deleteByLanguageName(languageName);

        if (deletedRows == 0) {
            throw new IllegalArgumentException("언어 '" + languageName + "' 를 찾을 수 없습니다.");
        }

        // 2. 캐시에서도 제거
        CACHE.remove(languageName);

        log.info("✅ 언어 삭제 완료: {}", languageName);
    }

    /**
     * 캐시 전체 재로드 (관리자 기능 또는 정기 갱신용)
     * 여러 서버 인스턴스 환경에서 동기화 목적으로 사용 가능
     */
    public void reloadCache() {
        log.info("🔄 언어 상수 캐시 전체 재로드 시작...");
        CACHE.clear();
        initializeCache();
    }

    /**
     * 캐시 크기 조회 (모니터링용)
     * 
     * @return 캐시에 저장된 언어 개수
     */
    public int getCacheSize() {
        return CACHE.size();
    }
}
