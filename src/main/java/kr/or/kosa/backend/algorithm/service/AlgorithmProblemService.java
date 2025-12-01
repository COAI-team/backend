package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import kr.or.kosa.backend.algorithm.dto.*;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlgorithmProblemService {

    private final AlgorithmProblemMapper algorithmProblemMapper;

    /**
     * 문제 목록 조회 (필터 포함) - V1
     *
     * @param offset     시작 위치
     * @param limit      조회 개수
     * @param difficulty 난이도 필터 (nullable)
     * @param source     출처 필터 (nullable)
     * @param keyword    검색어 (nullable)
     * @param topic      주제 필터 (nullable)
     * @return 문제 목록
     */
    public List<AlgoProblem> getProblemsWithFilter(int offset, int limit, String difficulty,
                                                   String source, String keyword, String topic) {
        log.debug("문제 목록 조회 (필터) - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}, topic: {}",
                offset, limit, difficulty, source, keyword, topic);

        try {
            List<AlgoProblem> problems = algorithmProblemMapper.selectProblemsWithFilter(
                    offset, limit, difficulty, source, keyword, topic);
            log.debug("문제 목록 조회 완료 - 조회된 문제 수: {}", problems.size());

            return problems;

        } catch (Exception e) {
            log.error("문제 목록 조회 실패 - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}, topic: {}",
                    offset, limit, difficulty, source, keyword, topic, e);
            throw new RuntimeException("문제 목록 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 전체 문제 수 조회 (필터 포함) - V1
     *
     * @param difficulty 난이도 필터 (nullable)
     * @param source     출처 필터 (nullable)
     * @param keyword    검색어 (nullable)
     * @param topic      주제 필터 (nullable)
     * @return 필터링된 문제 개수
     */
    public int getTotalProblemsCountWithFilter(String difficulty, String source, String keyword, String topic) {
        log.debug("전체 문제 수 조회 (필터) - difficulty: {}, source: {}, keyword: {}, topic: {}",
                difficulty, source, keyword, topic);

        try {
            int count = algorithmProblemMapper.countProblemsWithFilter(difficulty, source, keyword, topic);
            log.debug("전체 문제 수 조회 완료 - count: {}", count);

            return count;

        } catch (Exception e) {
            log.error("전체 문제 수 조회 실패 (필터) - difficulty: {}, source: {}, keyword: {}, topic: {}",
                    difficulty, source, keyword, topic, e);
            throw new RuntimeException("전체 문제 수 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 문제 목록 조회 V2 (고급 필터링 + 통계)
     *
     * @param request 문제 목록 요청 DTO
     * @return 문제 목록 및 통계 정보
     */
    public Map<String, Object> getProblemListWithStats(ProblemListRequestDto request) {
        log.debug("문제 목록 조회 V2 - request: {}", request);

        try {
            // 페이징 계산
            int offset = (request.getPage() - 1) * request.getSize();

            // 문제 목록 조회
            List<ProblemListResponseDto> problems = algorithmProblemMapper.selectProblemList(
                    request.getUserId(),
                    offset,
                    request.getSize(),
                    request.getDifficulty(),
                    request.getSource(),
                    request.getLanguage(),
                    request.getKeyword(),
                    request.getTopic(),  // topic 추가
                    request.getStatus(),
                    request.getSortBy()
            );

            // 전체 개수 조회
            int totalCount = algorithmProblemMapper.countProblemList(
                    request.getUserId(),
                    request.getDifficulty(),
                    request.getSource(),
                    request.getLanguage(),
                    request.getKeyword(),
                    request.getTopic(),  // topic 추가
                    request.getStatus()
            );

            // 페이징 정보 계산
            int totalPages = (int) Math.ceil((double) totalCount / request.getSize());
            boolean hasNext = request.getPage() < totalPages;
            boolean hasPrevious = request.getPage() > 1;

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problems", problems);
            responseData.put("totalCount", totalCount);
            responseData.put("totalPages", totalPages);
            responseData.put("currentPage", request.getPage());
            responseData.put("pageSize", request.getSize());
            responseData.put("hasNext", hasNext);
            responseData.put("hasPrevious", hasPrevious);

            log.debug("문제 목록 조회 V2 완료 - 조회된 문제 수: {}, 전체 문제 수: {}", problems.size(), totalCount);

            return responseData;

        } catch (Exception e) {
            log.error("문제 목록 조회 V2 실패 - request: {}", request, e);
            throw new RuntimeException("문제 목록 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 통계 정보 조회
     *
     * @param userId 사용자 ID (nullable)
     * @return 통계 정보
     */
    public ProblemStatisticsDto getProblemStatistics(Long userId) {
        log.debug("통계 정보 조회 - userId: {}", userId);

        try {
            ProblemStatisticsDto statistics = algorithmProblemMapper.selectProblemStatistics(userId);
            log.debug("통계 정보 조회 완료 - statistics: {}", statistics);

            return statistics;

        } catch (Exception e) {
            log.error("통계 정보 조회 실패 - userId: {}", userId, e);
            throw new RuntimeException("통계 정보 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 문제 상세 조회
     *
     * @param problemId 문제 ID
     * @return 문제 상세 정보
     */
    public AlgoProblem getProblemDetail(Long problemId) {
        log.debug("문제 상세 조회 - problemId: {}", problemId);

        try {
            AlgoProblem problem = algorithmProblemMapper.selectProblemById(problemId);

            if (problem == null) {
                log.warn("문제를 찾을 수 없음 - problemId: {}", problemId);
                throw new RuntimeException("문제를 찾을 수 없습니다.");
            }

            log.debug("문제 상세 조회 완료 - problem: {}", problem);
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

        try {
            boolean exists = algorithmProblemMapper.existsProblemById(problemId);
            log.debug("문제 존재 여부 확인 완료 - exists: {}", exists);

            return exists;

        } catch (Exception e) {
            log.error("문제 존재 여부 확인 실패 - problemId: {}", problemId, e);
            throw new RuntimeException("문제 존재 여부 확인 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 전체 문제 수 조회
     *
     * @return 전체 문제 수
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
     * AI 생성 문제 저장
     *
     * @param aiResponse AI 생성 응답
     * @param userId     사용자 ID (nullable)
     * @return 생성된 문제 ID
     */
    @Transactional
    public Long saveGeneratedProblem(ProblemGenerationResponseDto aiResponse, Long userId) {
        log.debug("AI 생성 문제 저장 - userId: {}", userId);

        try {
            AlgoProblem problem = aiResponse.getProblem();

            // 문제 생성자 설정
            if (userId != null) {
                problem.setAlgoCreater(userId);
            }

            // 문제 저장
            algorithmProblemMapper.insertProblem(problem);
            Long problemId = problem.getAlgoProblemId();

            // 테스트케이스 저장
            if (aiResponse.getTestCases() != null && !aiResponse.getTestCases().isEmpty()) {
                for (AlgoTestcase testcase : aiResponse.getTestCases()) {
                    testcase.setAlgoProblemId(problemId);
                    algorithmProblemMapper.insertTestcase(testcase);
                }
                log.debug("테스트케이스 저장 완료 - 개수: {}", aiResponse.getTestCases().size());
            }

            log.debug("AI 생성 문제 저장 완료 - problemId: {}", problemId);
            return problemId;

        } catch (Exception e) {
            log.error("AI 생성 문제 저장 실패 - userId: {}", userId, e);
            throw new RuntimeException("AI 생성 문제 저장 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 테스트케이스 생성
     *
     * @param testcase 테스트케이스 정보
     * @return 생성된 테스트케이스 ID
     */
    @Transactional
    public Long createTestcase(AlgoTestcase testcase) {
        log.debug("테스트케이스 생성 - testcase: {}", testcase);

        try {
            algorithmProblemMapper.insertTestcase(testcase);
            Long testcaseId = testcase.getTestcaseId();

            log.debug("테스트케이스 생성 완료 - testcaseId: {}", testcaseId);
            return testcaseId;

        } catch (Exception e) {
            log.error("테스트케이스 생성 실패 - testcase: {}", testcase, e);
            throw new RuntimeException("테스트케이스 생성 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 샘플 테스트케이스 조회
     *
     * @param problemId 문제 ID
     * @return 샘플 테스트케이스 목록
     */
    public List<AlgoTestcase> getSampleTestCases(Long problemId) {
        log.debug("샘플 테스트케이스 조회 - problemId: {}", problemId);

        try {
            List<AlgoTestcase> testCases = algorithmProblemMapper.selectSampleTestCasesByProblemId(problemId);
            log.debug("샘플 테스트케이스 조회 완료 - 조회된 테스트케이스 수: {}", testCases.size());

            return testCases;

        } catch (Exception e) {
            log.error("샘플 테스트케이스 조회 실패 - problemId: {}", problemId, e);
            throw new RuntimeException("샘플 테스트케이스 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 모든 테스트케이스 조회
     *
     * @param problemId 문제 ID
     * @return 전체 테스트케이스 목록
     */
    public List<AlgoTestcase> getAllTestCases(Long problemId) {
        log.debug("전체 테스트케이스 조회 - problemId: {}", problemId);

        try {
            List<AlgoTestcase> testCases = algorithmProblemMapper.selectTestCasesByProblemId(problemId);
            log.debug("전체 테스트케이스 조회 완료 - 조회된 테스트케이스 수: {}", testCases.size());

            return testCases;

        } catch (Exception e) {
            log.error("전체 테스트케이스 조회 실패 - problemId: {}", problemId, e);
            throw new RuntimeException("전체 테스트케이스 조회 중 오류가 발생했습니다.", e);
        }
    }
}