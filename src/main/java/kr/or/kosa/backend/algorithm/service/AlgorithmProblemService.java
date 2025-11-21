package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알고리즘 문제 서비스 - Phase 1
 * 기본 조회 기능만 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AlgorithmProblemService {

    private final AlgorithmProblemMapper algorithmProblemMapper;

    /**
     * 문제 목록 조회 (페이징)
     * @param offset 시작 위치
     * @param limit 조회 개수
     * @return 문제 목록
     */
    public List<AlgoProblem> getProblems(int offset, int limit) {
        log.debug("문제 목록 조회 - offset: {}, limit: {}", offset, limit);

        try {
            List<AlgoProblem> problems = algorithmProblemMapper.selectProblems(offset, limit);
            log.debug("문제 목록 조회 완료 - 조회된 문제 수: {}", problems.size());

            return problems;

        } catch (Exception e) {
            log.error("문제 목록 조회 실패 - offset: {}, limit: {}", offset, limit, e);
            throw new RuntimeException("문제 목록 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 전체 문제 수 조회
     * @return 전체 문제 개수
     */
    public int getTotalProblemsCount() {
        log.debug("전체 문제 수 조회");

        try {
            int count = algorithmProblemMapper.countAllProblems();
            log.debug("전체 문제 수 조회 완료 - count: {}", count);

            return count;

        } catch (Exception e) {
            log.error("전체 문제 수 조회 실패", e);
            throw new RuntimeException("전체 문제 수 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 문제 상세 조회
     * @param problemId 문제 ID
     * @return 문제 정보
     */
    public AlgoProblem getProblemDetail(Long problemId) {
        log.debug("문제 상세 조회 - problemId: {}", problemId);

        if (problemId == null || problemId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 문제 ID입니다.");
        }

        try {
            AlgoProblem problem = algorithmProblemMapper.selectProblemById(problemId);

            if (problem == null) {
                throw new RuntimeException("존재하지 않는 문제입니다. ID: " + problemId);
            }

            log.debug("문제 상세 조회 완료 - problemId: {}, title: {}", problemId, problem.getAlgoProblemTitle());

            return problem;

        } catch (Exception e) {
            log.error("문제 상세 조회 실패 - problemId: {}", problemId, e);
            throw new RuntimeException("문제 상세 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 문제 존재 여부 확인
     * @param problemId 문제 ID
     * @return 존재 여부
     */
    public boolean existsProblem(Long problemId) {
        log.debug("문제 존재 여부 확인 - problemId: {}", problemId);

        if (problemId == null || problemId <= 0) {
            return false;
        }

        try {
            boolean exists = algorithmProblemMapper.existsProblemById(problemId);
            log.debug("문제 존재 여부 확인 완료 - problemId: {}, exists: {}", problemId, exists);

            return exists;

        } catch (Exception e) {
            log.error("문제 존재 여부 확인 실패 - problemId: {}", problemId, e);
            throw new RuntimeException("문제 존재 여부 확인 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 페이지 번호 검증
     * @param page 페이지 번호 (1부터 시작)
     * @param size 페이지 크기
     * @return 검증된 페이지 번호
     */
    public int validateAndNormalizePage(int page, int size) {
        if (page < 1) {
            log.warn("잘못된 페이지 번호: {}. 1로 설정합니다.", page);
            return 1;
        }

        // 최대 페이지 수 확인
        int totalCount = getTotalProblemsCount();
        int maxPage = (int) Math.ceil((double) totalCount / size);

        if (maxPage > 0 && page > maxPage) {
            log.warn("페이지 번호 초과: {}. 최대 페이지 {}로 설정합니다.", page, maxPage);
            return maxPage;
        }

        return page;
    }

    /**
     * 페이지 크기 검증
     * @param size 페이지 크기
     * @return 검증된 페이지 크기
     */
    public int validateAndNormalizeSize(int size) {
        if (size < 1) {
            log.warn("잘못된 페이지 크기: {}. 10으로 설정합니다.", size);
            return 10;
        }

        if (size > 100) {
            log.warn("페이지 크기 초과: {}. 100으로 제한합니다.", size);
            return 100;
        }

        return size;
    }
}