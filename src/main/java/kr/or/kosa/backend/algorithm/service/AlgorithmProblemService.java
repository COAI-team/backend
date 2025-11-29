package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import kr.or.kosa.backend.algorithm.dto.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.dto.ProblemListRequestDto;
import kr.or.kosa.backend.algorithm.dto.ProblemListResponseDto;
import kr.or.kosa.backend.algorithm.dto.ProblemStatisticsDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알고리즘 문제 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AlgorithmProblemService {

    private final AlgorithmProblemMapper algorithmProblemMapper;

    // ===== 기존 메서드들 =====

    /**
     * 문제 목록 조회 (페이징)
     *
     * @param offset 시작 위치
     * @param limit  조회 개수
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
     *
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
     * 문제 목록 조회 (필터 포함)
     *
     * @param offset     시작 위치
     * @param limit      조회 개수
     * @param difficulty 난이도 필터 (nullable)
     * @param source     출처 필터 (nullable)
     * @param keyword    검색어 (nullable)
     * @return 문제 목록
     */
    public List<AlgoProblem> getProblemsWithFilter(int offset, int limit, String difficulty, String source,
                                                   String keyword) {
        log.debug("문제 목록 조회 (필터) - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}",
                offset, limit, difficulty, source, keyword);

        try {
            List<AlgoProblem> problems = algorithmProblemMapper.selectProblemsWithFilter(offset, limit, difficulty,
                    source, keyword);
            log.debug("문제 목록 조회 완료 - 조회된 문제 수: {}", problems.size());

            return problems;

        } catch (Exception e) {
            log.error("문제 목록 조회 실패 - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}",
                    offset, limit, difficulty, source, keyword, e);
            throw new RuntimeException("문제 목록 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 전체 문제 수 조회 (필터 포함)
     *
     * @param difficulty 난이도 필터 (nullable)
     * @param source     출처 필터 (nullable)
     * @param keyword    검색어 (nullable)
     * @return 필터링된 문제 개수
     */
    public int getTotalProblemsCountWithFilter(String difficulty, String source, String keyword) {
        log.debug("전체 문제 수 조회 (필터) - difficulty: {}, source: {}, keyword: {}", difficulty, source, keyword);

        try {
            int count = algorithmProblemMapper.countProblemsWithFilter(difficulty, source, keyword);
            log.debug("전체 문제 수 조회 완료 - count: {}", count);

            return count;

        } catch (Exception e) {
            log.error("전체 문제 수 조회 실패 (필터) - difficulty: {}, source: {}, keyword: {}", difficulty, source, keyword, e);
            throw new RuntimeException("전체 문제 수 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 문제 상세 조회
     *
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
     *
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
     *
     * @param page 페이지 번호 (1부터 시작)
     * @param size 페이지 크기
     * @return 검증된 페이지 번호
     */
    public int validateAndNormalizePage(int page, int size) {
        if (page < 1) {
            log.warn("잘못된 페이지 번호: {}. 1로 설정합니다.", page);
            return 1;
        }

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
     *
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

    /**
     * AI 생성 문제를 DB에 저장
     *
     * @param responseDto AI 생성 결과
     * @param userId      생성자 ID (null 가능)
     * @return 저장된 문제 ID
     */
    @Transactional
    public Long saveGeneratedProblem(ProblemGenerationResponseDto responseDto, Long userId) {
        try {
            log.info("AI 생성 문제 저장 시작 - 제목: {}", responseDto.getProblem().getAlgoProblemTitle());

            AlgoProblem problem = responseDto.getProblem();
            problem.setAlgoCreater(userId);

            int insertResult = algorithmProblemMapper.insertProblem(problem);

            if (insertResult == 0) {
                throw new RuntimeException("문제 저장 실패");
            }

            log.info("문제 저장 완료 - ID: {}, 제목: {}",
                    problem.getAlgoProblemId(), problem.getAlgoProblemTitle());

            if (responseDto.getTestCases() != null && !responseDto.getTestCases().isEmpty()) {
                saveTestcases(problem.getAlgoProblemId(), responseDto.getTestCases());
            }

            responseDto.setProblemId(problem.getAlgoProblemId());

            return problem.getAlgoProblemId();

        } catch (Exception e) {
            log.error("AI 생성 문제 저장 중 오류 발생", e);
            throw new RuntimeException("AI 생성 문제 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 테스트케이스 일괄 저장
     */
    @Transactional
    private void saveTestcases(Long problemId, List<AlgoTestcase> testcases) {
        try {
            int savedCount = 0;

            for (AlgoTestcase testcase : testcases) {
                testcase.setAlgoProblemId(problemId);

                int result = algorithmProblemMapper.insertTestcase(testcase);

                if (result == 0) {
                    throw new RuntimeException("테스트케이스 저장 실패 - 문제 ID: " + problemId);
                }

                savedCount++;
            }

            log.info("테스트케이스 저장 완료 - 문제 ID: {}, 저장 개수: {}", problemId, savedCount);

        } catch (Exception e) {
            log.error("테스트케이스 저장 중 오류 발생", e);
            throw new RuntimeException("테스트케이스 저장 실패: " + e.getMessage(), e);
        }
    }

    // ===== 새로 추가되는 메서드들 =====

    /**
     * 문제 목록 조회 (고급 필터링 + 통계)
     *
     * @param request 문제 목록 조회 요청 Dto
     * @return 문제 목록과 페이징 정보
     */
    public Map<String, Object> getProblemListWithStats(ProblemListRequestDto request) {
        log.info("문제 목록 조회 시작 - 필터: {}", request);

        try {
            // 전체 개수 조회
            int totalCount = algorithmProblemMapper.countProblemList(request);

            // 문제 목록 조회
            List<ProblemListResponseDto> problems = algorithmProblemMapper.selectProblemList(request);

            // 페이지 정보 계산
            int page = request.getPage() != null ? request.getPage() : 1;
            int size = request.getSize() != null ? request.getSize() : 10;
            int totalPages = (int) Math.ceil((double) totalCount / size);
            boolean hasNext = page < totalPages;
            boolean hasPrevious = page > 1;

            log.info("문제 목록 조회 완료 - 총 {}개, 페이지 {}/{}", totalCount, page, totalPages);

            // 응답 데이터 구성
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problems", problems);
            responseData.put("totalCount", totalCount);
            responseData.put("totalPages", totalPages);
            responseData.put("currentPage", page);
            responseData.put("hasNext", hasNext);
            responseData.put("hasPrevious", hasPrevious);

            return responseData;
        } catch (Exception e) {
            log.error("문제 목록 조회 실패", e);
            throw new RuntimeException("문제 목록 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 통계 정보 조회
     *
     * @param userId 사용자 ID (null 가능)
     * @return 통계 정보
     */
    public ProblemStatisticsDto getProblemStatistics(Long userId) {
        log.info("통계 정보 조회 - userId: {}", userId);

        try {
            ProblemStatisticsDto statistics = algorithmProblemMapper.selectProblemStatistics(userId);

            log.info("통계 정보 조회 완료 - {}", statistics);

            return statistics;
        } catch (Exception e) {
            log.error("통계 정보 조회 실패", e);
            throw new RuntimeException("통계 정보 조회 중 오류가 발생했습니다.", e);
        }
    }
}