package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
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
     * @param topic      주제 필터 (nullable)
     * @return 문제 목록
     */
    public List<AlgoProblemDto> getProblemsWithFilter(int offset, int limit, String difficulty, String source,
            String keyword, String topic) {
        log.debug("문제 목록 조회 (필터) - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}, topic: {}",
                offset, limit, difficulty, source, keyword, topic);

        try {
            List<AlgoProblemDto> problems = algorithmProblemMapper.selectProblemsWithFilter(offset, limit, difficulty,
                    source, keyword);
            log.debug("문제 목록 조회 완료 - 조회된 문제 수: {}", problems.size());

            return problems;

        } catch (Exception e) {
            log.error("문제 목록 조회 실패 - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}, topic: {}",
                    offset, limit, difficulty, source, keyword, topic, e);
            throw new RuntimeException("문제 목록 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 전체 문제 수 조회 (필터 포함)
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
            int count = algorithmProblemMapper.countProblemsWithFilter(difficulty, source, keyword);
            log.debug("전체 문제 수 조회 완료 - count: {}", count);

            return count;

        } catch (Exception e) {
            log.error("전체 문제 수 조회 실패 (필터) - difficulty: {}, source: {}, keyword: {}, topic: {}",
                    difficulty, source, keyword, topic, e);
            throw new RuntimeException("전체 문제 수 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 문제 목록 조회 (V2 - 통계 포함)
     *
     * @param request 문제 목록 조회 요청 DTO
     * @return 문제 목록 및 페이징 정보
     */
    public Map<String, Object> getProblemListWithStats(ProblemListRequestDto request) {
        log.debug("문제 목록 조회 (V2) - request: {}", request);

        try {
            // 문제 목록 조회
            List<AlgoProblemDto> problems = getProblemsWithFilter(
                    request.getOffset(),
                    request.getLimit(),
                    request.getDifficulty(),
                    request.getSource(),
                    request.getKeyword(),
                    request.getTopic()
            );

            // 전체 개수 조회
            int totalCount = getTotalProblemsCountWithFilter(
                    request.getDifficulty(),
                    request.getSource(),
                    request.getKeyword(),
                    request.getTopic()
            );

            // 페이징 정보 계산
            int totalPages = (int) Math.ceil((double) totalCount / request.getLimit());

            // 응답 데이터 구성
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problems", problems);
            responseData.put("totalCount", totalCount);
            responseData.put("currentPage", request.getPage());
            responseData.put("pageSize", request.getLimit());
            responseData.put("totalPages", totalPages);
            responseData.put("hasNext", request.getPage() < totalPages);
            responseData.put("hasPrevious", request.getPage() > 1);

            log.debug("문제 목록 조회 완료 - totalCount: {}, problems: {}", totalCount, problems.size());

            return responseData;

        } catch (Exception e) {
            log.error("문제 목록 조회 실패 (V2)", e);
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
            // 전체 문제 수 조회
            int totalProblems = getTotalProblemsCount();

            // TODO: Mapper에 selectProblemStatistics 메서드 구현 필요
            // 현재는 기본값 반환 (merge 충돌 해결을 위한 임시 구현)
            return ProblemStatisticsDto.builder()
                    .totalProblems(totalProblems)
                    .solvedProblems(0)      // TODO: 사용자별 해결 문제 수 조회
                    .averageAccuracy(0.0)   // TODO: 평균 정답률 계산
                    .totalAttempts(0)       // TODO: 총 응시자 수 조회
                    .build();

        } catch (Exception e) {
            log.error("통계 정보 조회 실패 - userId: {}", userId, e);
            throw new RuntimeException("통계 정보 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 문제 상세 조회
     *
     * @param problemId 문제 ID
     * @return 문제 정보
     */
    public AlgoProblemDto getProblemDetail(Long problemId) {
        log.debug("문제 상세 조회 - problemId: {}", problemId);

        if (problemId == null || problemId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 문제 ID입니다.");
        }

        try {
            AlgoProblemDto problem = algorithmProblemMapper.selectProblemById(problemId);

            if (problem == null) {
                throw new RuntimeException("존재하지 않는 문제입니다. ID: " + problemId);
            }

            // 테스트케이스 조회 및 설정
            List<AlgoTestcaseDto> testcases = algorithmProblemMapper.selectTestCasesByProblemId(problemId);
            problem.setTestcases(testcases);

            log.debug("문제 상세 조회 완료 - problemId: {}, title: {}, testcases: {}",
                    problemId, problem.getAlgoProblemTitle(), testcases != null ? testcases.size() : 0);

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

    // ===== AI 생성 문제 저장 메서드 =====
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

            // 1. 문제 엔티티 준비
            AlgoProblemDto problem = responseDto.getProblem();
            problem.setAlgoCreater(userId); // 생성자 ID 설정

            // 2. 문제 저장 (AUTO_INCREMENT로 ID 자동 생성)
            int insertResult = algorithmProblemMapper.insertProblem(problem);

            if (insertResult == 0) {
                throw new RuntimeException("문제 저장 실패");
            }

            log.info("문제 저장 완료 - ID: {}, 제목: {}",
                    problem.getAlgoProblemId(), problem.getAlgoProblemTitle());

            // 3. 테스트케이스 저장
            if (responseDto.getTestCases() != null && !responseDto.getTestCases().isEmpty()) {
                saveTestcases(problem.getAlgoProblemId(), responseDto.getTestCases());
            }

            // 4. ResponseDto에 생성된 ID 설정
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
    private void saveTestcases(Long problemId, List<AlgoTestcaseDto> testcases) {
        try {
            int savedCount = 0;

            for (AlgoTestcaseDto testcase : testcases) {
                testcase.setAlgoProblemId(problemId);

                int result = algorithmProblemMapper.insertTestcase(testcase);

                if (result == 0) {
                    throw new RuntimeException("테스트케이스 저장 실패 - 문제 ID: " + problemId);
                }

                savedCount++;
            }

            log.info("테스트케이스 저장 완료 - 문제 ID: {}, 저장 개수: {}", problemId, savedCount);

        } catch (Exception e) {
            log.error("전체 테스트케이스 조회 실패 - problemId: {}", problemId, e);
            throw new RuntimeException("전체 테스트케이스 조회 중 오류가 발생했습니다.", e);
        }
    }
}