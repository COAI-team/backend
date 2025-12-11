package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.ProblemValidationLogDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import kr.or.kosa.backend.algorithm.dto.request.ProblemListRequestDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemStatisticsResponseDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.mapper.ProblemValidationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlgorithmProblemService {

    private final AlgorithmProblemMapper algorithmProblemMapper;
    private final ProblemValidationLogMapper validationLogMapper;


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
    * @param offset     시작 위치
    * @param limit      조회 개수
    * @param difficulty 난이도 필터 (nullable)
    * @param source     출처 필터 (nullable)
    * @param keyword    검색어 (nullable)
    * @param topic      주제 필터 (nullable)
    * @param problemType 문제 유형 필터 (nullable)
    * @return 문제 목록
    */
    public List<AlgoProblemDto> getProblemsWithFilter(int offset, int limit, String difficulty, String source,
                                                      String keyword, String topic, String problemType) {
        log.debug("문제 목록 조회 (필터) - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}, topic: {}, problemType: {}",
                offset, limit, difficulty, source, keyword, topic, problemType);

        try {
            List<AlgoProblemDto> problems = algorithmProblemMapper.selectProblemsWithFilter(offset, limit, difficulty,
                    source, keyword, problemType);
            log.debug("문제 목록 조회 완료 - 조회된 문제 수: {}", problems.size());

            return problems;

        } catch (Exception e) {
            log.error("문제 목록 조회 실패 - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}, topic: {}, problemType: {}",
                    offset, limit, difficulty, source, keyword, topic, problemType, e);
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
     * @param problemType 문제 유형 필터 (nullable)
     * @return 필터링된 문제 개수
     */
    public int getTotalProblemsCountWithFilter(String difficulty, String source, String keyword, String topic, String problemType) {
        log.debug("전체 문제 수 조회 (필터) - difficulty: {}, source: {}, keyword: {}, topic: {}, problemType: {}",
                difficulty, source, keyword, topic, problemType);

        try {
            int count = algorithmProblemMapper.countProblemsWithFilter(difficulty, source, keyword, problemType);
            log.debug("전체 문제 수 조회 완료 - count: {}", count);

            return count;

        } catch (Exception e) {
            log.error("전체 문제 수 조회 실패 (필터) - difficulty: {}, source: {}, keyword: {}, topic: {}, problemType: {}",
                    difficulty, source, keyword, topic, problemType, e);
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
                    request.getTopic(),
                    request.getProblemType()  // problemType 추가
            );

            // 전체 개수 조회
            int totalCount = getTotalProblemsCountWithFilter(
                    request.getDifficulty(),
                    request.getSource(),
                    request.getKeyword(),
                    request.getTopic(),
                    request.getProblemType()  // problemType 추가
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
    public ProblemStatisticsResponseDto getProblemStatistics(Long userId) {
        log.debug("통계 정보 조회 - userId: {}", userId);

        try {
            // 전체 문제 수 조회
            int totalProblems = getTotalProblemsCount();

            // TODO: Mapper에 selectProblemStatistics 메서드 구현 필요
            // 현재는 기본값 반환 (merge 충돌 해결을 위한 임시 구현)
            return ProblemStatisticsResponseDto.builder()
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

            // 4. 검증 로그 저장 (검증 결과가 있거나 검증 코드가 있는 경우)
            boolean hasValidationResults = responseDto.getValidationResults() != null && !responseDto.getValidationResults().isEmpty();
            boolean hasValidationCode = responseDto.getOptimalCode() != null || responseDto.getNaiveCode() != null;

            if (hasValidationResults || hasValidationCode) {
                saveValidationLog(problem.getAlgoProblemId(), responseDto);
            }

            // 5. ResponseDto에 생성된 ID 설정
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

    /**
     * 검증 로그 저장
     *
     * @param problemId   문제 ID
     * @param responseDto 문제 생성 응답 (검증 결과 포함)
     */
    @Transactional
    private void saveValidationLog(Long problemId, ProblemGenerationResponseDto responseDto) {
        try {
            List<ValidationResultDto> validationResults = responseDto.getValidationResults();

            // 검증 결과 분석 (null-safe)
            String validationStatus;
            Double similarityScore = null;
            Boolean similarityValid = null;
            Integer optimalExecutionTime = null;
            Integer naiveExecutionTime = null;
            Double timeRatio = null;
            Boolean timeRatioValid = null;
            String optimalCodeResult = null;
            String naiveCodeResult = null;
            String failureReasons = null;

            if (validationResults != null && !validationResults.isEmpty()) {
                // 검증 결과가 있는 경우
                boolean allPassed = validationResults.stream().allMatch(ValidationResultDto::isPassed);
                validationStatus = allPassed ? "PASSED" : "FAILED";

                // 메타데이터에서 유사도 점수 추출
                for (ValidationResultDto result : validationResults) {
                    Map<String, Object> metadata = result.getMetadata();

                    if ("SimilarityChecker".equals(result.getValidatorName())) {
                        if (metadata != null && metadata.containsKey("maxSimilarity")) {
                            similarityScore = ((Number) metadata.get("maxSimilarity")).doubleValue();
                        }
                        similarityValid = result.isPassed();
                    }

                    if ("CodeExecutionValidator".equals(result.getValidatorName())) {
                        if (metadata != null && metadata.containsKey("optimalExecutionTime")) {
                            optimalExecutionTime = ((Number) metadata.get("optimalExecutionTime")).intValue();
                        }
                        optimalCodeResult = result.isPassed() ? "PASS" : "FAIL";
                    }

                    if ("TimeRatioValidator".equals(result.getValidatorName())) {
                        if (metadata != null && metadata.containsKey("naiveExecutionTime")) {
                            naiveExecutionTime = ((Number) metadata.get("naiveExecutionTime")).intValue();
                        }
                        if (metadata != null && metadata.containsKey("timeRatio")) {
                            timeRatio = ((Number) metadata.get("timeRatio")).doubleValue();
                        }
                        timeRatioValid = result.isPassed();
                        naiveCodeResult = result.isPassed() ? "PASS" : "FAIL";
                    }
                }

                // 실패 원인 수집 (JSON 형태)
                failureReasons = collectFailureReasons(validationResults);
            } else {
                // 검증 결과 없이 코드만 있는 경우 (PENDING 상태로 저장)
                validationStatus = "PENDING";
                log.info("검증 결과 없음 - PENDING 상태로 저장 (문제 ID: {})", problemId);
            }

            // 검증 로그 DTO 생성
            ProblemValidationLogDto validationLog = ProblemValidationLogDto.builder()
                    .algoProblemId(problemId)
                    .optimalCode(responseDto.getOptimalCode())
                    .naiveCode(responseDto.getNaiveCode())
                    .expectedTimeComplexity(responseDto.getProblem().getExpectedTimeComplexity())
                    .optimalCodeResult(optimalCodeResult)
                    .naiveCodeResult(naiveCodeResult)
                    .optimalExecutionTime(optimalExecutionTime)
                    .naiveExecutionTime(naiveExecutionTime)
                    .timeRatio(timeRatio)
                    .timeRatioValid(timeRatioValid)
                    .similarityScore(similarityScore)
                    .similarityValid(similarityValid)
                    .validationStatus(validationStatus)
                    .correctionAttempts(0)  // 저장 시점에서는 수정 시도 전
                    .failureReasons(failureReasons)
                    .createdAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .build();

            // DB 저장
            int result = validationLogMapper.insertValidationLog(validationLog);

            if (result > 0) {
                log.info("검증 로그 저장 완료 - 문제 ID: {}, 검증 상태: {}, 로그 ID: {}",
                        problemId, validationStatus, validationLog.getValidationId());
            } else {
                log.warn("검증 로그 저장 실패 - 문제 ID: {}", problemId);
            }

        } catch (Exception e) {
            log.error("검증 로그 저장 중 오류 발생 - problemId: {}", problemId, e);
            // 검증 로그 저장 실패가 전체 프로세스를 중단하지 않도록 예외를 던지지 않음
        }
    }

    /**
     * 실패 원인 수집 (JSON 형태)
     */
    private String collectFailureReasons(List<ValidationResultDto> validationResults) {
        List<String> failures = validationResults.stream()
                .filter(r -> !r.isPassed())
                .map(r -> String.format("{\"validator\":\"%s\",\"errors\":%s}",
                        r.getValidatorName(),
                        r.getErrors().stream()
                                .map(e -> "\"" + e.replace("\"", "\\\"") + "\"")
                                .collect(Collectors.joining(",", "[", "]"))))
                .collect(Collectors.toList());

        if (failures.isEmpty()) {
            return null;
        }

        return "[" + String.join(",", failures) + "]";
    }
}