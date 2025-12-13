package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.LanguageDto;
import kr.or.kosa.backend.algorithm.dto.enums.LanguageType;
import kr.or.kosa.backend.algorithm.mapper.LanguageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 언어 서비스
 * DB 직접 조회 방식 (단순화)
 *
 * 변경사항 (2025-12-13):
 * - LanguageConstantService → LanguageService 리팩토링
 * - languageId (INT, Judge0 API ID)를 기준으로 조회
 * - pistonLanguage 필드 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageService {

    private final LanguageMapper languageMapper;

    /**
     * 언어 ID로 조회 (Judge0 API ID)
     * 채점 시 특정 언어의 제한 배수를 가져올 때 사용
     *
     * @param languageId 언어 ID (예: Python=100, Java=91)
     * @return 언어 정보, 없으면 null
     */
    public LanguageDto getById(Integer languageId) {
        LanguageDto language = languageMapper.selectById(languageId);

        if (language == null) {
            log.warn("언어 ID '{}'를 찾을 수 없습니다. 지원하지 않는 언어일 수 있습니다.", languageId);
        }

        return language;
    }

    /**
     * 언어명으로 조회 (표시용)
     *
     * @param languageName 언어명 (예: "Python", "Java")
     * @return 언어 정보, 없으면 null
     */
    public LanguageDto getByName(String languageName) {
        LanguageDto language = languageMapper.selectByName(languageName);

        if (language == null) {
            log.warn("언어 '{}'를 찾을 수 없습니다.", languageName);
        }

        return language;
    }

    /**
     * Piston 언어명으로 조회
     *
     * @param pistonLanguage Piston API 언어명 (예: "python", "java")
     * @return 언어 정보, 없으면 null
     */
    public LanguageDto getByPistonLanguage(String pistonLanguage) {
        return languageMapper.selectByPistonLanguage(pistonLanguage);
    }

    /**
     * 모든 언어 조회
     *
     * @return 전체 언어 리스트
     */
    public List<LanguageDto> getAllLanguages() {
        return languageMapper.selectAll();
    }

    /**
     * 언어 유형별 조회 (문제 타입에 따른 언어 목록 제공)
     *
     * @param languageType 언어 유형 (GENERAL 또는 DB)
     * @return 해당 유형의 언어 리스트
     */
    public List<LanguageDto> getLanguagesByType(LanguageType languageType) {
        return languageMapper.selectByType(languageType.name());
    }

    /**
     * 실제 시간 제한 계산 (편의 메서드)
     *
     * @param languageId    언어 ID
     * @param baseTimeLimit 문제의 기본 시간 제한 (ms)
     * @return 계산된 실제 시간 제한 (ms), 언어를 찾을 수 없으면 기본값 반환
     */
    public int calculateRealTimeLimit(Integer languageId, int baseTimeLimit) {
        LanguageDto language = getById(languageId);

        if (language == null) {
            log.warn("언어 ID '{}'를 찾을 수 없어 기본 시간 제한 사용: {}ms", languageId, baseTimeLimit);
            return baseTimeLimit;
        }

        return language.calculateRealTimeLimit(baseTimeLimit);
    }

    /**
     * 실제 메모리 제한 계산 (편의 메서드)
     *
     * @param languageId      언어 ID
     * @param baseMemoryLimit 문제의 기본 메모리 제한 (MB)
     * @return 계산된 실제 메모리 제한 (MB), 언어를 찾을 수 없으면 기본값 반환
     */
    public int calculateRealMemoryLimit(Integer languageId, int baseMemoryLimit) {
        LanguageDto language = getById(languageId);

        if (language == null) {
            log.warn("언어 ID '{}'를 찾을 수 없어 기본 메모리 제한 사용: {}MB", languageId, baseMemoryLimit);
            return baseMemoryLimit;
        }

        return language.calculateRealMemoryLimit(baseMemoryLimit);
    }

    /**
     * 언어 업데이트 (관리자 기능)
     *
     * @param language 업데이트할 언어 정보
     */
    @Transactional
    public void updateLanguage(LanguageDto language) {
        log.info("언어 업데이트 요청: ID={}, Name={}", language.getLanguageId(), language.getLanguageName());

        int updatedRows = languageMapper.update(language);

        if (updatedRows == 0) {
            throw new IllegalArgumentException(
                    "언어 ID '" + language.getLanguageId() + "'를 찾을 수 없습니다.");
        }

        log.info("언어 업데이트 완료: ID={}", language.getLanguageId());
    }

    /**
     * 새 언어 추가 (관리자 기능)
     *
     * @param language 추가할 언어 정보
     */
    @Transactional
    public void addLanguage(LanguageDto language) {
        log.info("새 언어 추가 요청: ID={}, Name={}", language.getLanguageId(), language.getLanguageName());

        languageMapper.insert(language);

        log.info("새 언어 추가 완료: ID={}", language.getLanguageId());
    }

    /**
     * 언어 삭제 (관리자 기능)
     * 주의: FK 제약 조건으로 인해 해당 언어로 제출된 기록이 있으면 삭제 불가
     *
     * @param languageId 삭제할 언어 ID
     */
    @Transactional
    public void deleteLanguage(Integer languageId) {
        log.info("언어 삭제 요청: ID={}", languageId);

        int deletedRows = languageMapper.deleteById(languageId);

        if (deletedRows == 0) {
            throw new IllegalArgumentException("언어 ID '" + languageId + "'를 찾을 수 없습니다.");
        }

        log.info("언어 삭제 완료: ID={}", languageId);
    }
}
