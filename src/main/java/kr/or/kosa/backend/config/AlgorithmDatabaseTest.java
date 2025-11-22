//package kr.or.kosa.backend.algorithm.config;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Map;
//
///**
// * 알고리즘 데이터베이스 연결 및 테이블 생성 테스트
// * 애플리케이션 시작 시 자동 실행
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class AlgorithmDatabaseTest implements CommandLineRunner {
//
//    private final JdbcTemplate jdbcTemplate;
//
//    @Override
//    public void run(String... args) {
//        log.info("🚀 === 알고리즘 데이터베이스 연결 테스트 시작 ===");
//
//        try {
//            // 1. 데이터베이스 연결 확인
//            testConnection();
//
//            // 2. 테이블 존재 확인
//            checkAlgorithmTables();
//
//            // 3. 샘플 데이터 확인
//            checkSampleData();
//
//            // 4. AUTO_INCREMENT 설정 확인
//            checkAutoIncrement();
//
//            log.info("✅ === 데이터베이스 테스트 완료: 모든 정상 ===");
//
//        } catch (Exception e) {
//            log.error("❌ 데이터베이스 테스트 실패: {}", e.getMessage(), e);
//            throw new RuntimeException("알고리즘 데이터베이스 설정 오류", e);
//        }
//    }
//
//    /**
//     * 데이터베이스 연결 테스트
//     */
//    private void testConnection() {
//        try {
//            String currentTime = jdbcTemplate.queryForObject("SELECT NOW()", String.class);
//            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
//            log.info("✅ 데이터베이스 연결 성공");
//            log.info("   📊 현재 데이터베이스: {}", dbName);
//            log.info("   🕒 현재 시각: {}", currentTime);
//        } catch (Exception e) {
//            log.error("❌ 데이터베이스 연결 실패", e);
//            throw e;
//        }
//    }
//
//    /**
//     * 알고리즘 관련 테이블 존재 확인
//     */
//    private void checkAlgorithmTables() {
//        String[] requiredTables = {
//                "ALGO_PROBLEMS",
//                "ALGO_TESTCASES",
//                "ALGO_SUBMISSIONS",
//                "FOCUS_SESSIONS",
//                "FOCUS_SUMMARY",
//                "GITHUB_COMMITS",
//                "VIOLATION_LOGS"
//        };
//
//        log.info("📋 알고리즘 테이블 존재 확인 시작...");
//
//        for (String tableName : requiredTables) {
//            try {
//                String sql = "SELECT COUNT(*) FROM " + tableName;
//                Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
//                log.info("✅ 테이블 '{}' 확인 완료 (레코드 수: {})", tableName, count);
//            } catch (Exception e) {
//                log.error("❌ 테이블 '{}' 존재하지 않음: {}", tableName, e.getMessage());
//                throw new RuntimeException("테이블 " + tableName + " 설정 필요", e);
//            }
//        }
//    }
//
//    /**
//     * 샘플 데이터 확인
//     */
//    private void checkSampleData() {
//        try {
//            // ALGO_PROBLEMS 테이블의 샘플 데이터 확인
//            List<Map<String, Object>> problems = jdbcTemplate.queryForList(
//                    """
//                    SELECT
//                        ALGO_PROBLEM_ID,
//                        ALGO_PROBLEM_TITLE,
//                        ALGO_PROBLEM_DIFFICULTY,
//                        ALGO_PROBLEM_SOURCE,
//                        ALGO_CREATED_AT
//                    FROM ALGO_PROBLEMS
//                    ORDER BY ALGO_PROBLEM_ID
//                    LIMIT 5
//                    """
//            );
//
//            if (problems.isEmpty()) {
//                log.warn("⚠️  샘플 문제 데이터가 없습니다.");
//                log.warn("   💡 DDL 스크립트의 샘플 데이터 삽입 부분이 실행되었는지 확인해주세요.");
//            } else {
//                log.info("✅ 샘플 문제 데이터 {} 건 확인", problems.size());
//                problems.forEach(problem ->
//                        log.info("   📝 문제 {}: {} ({}, {})",
//                                problem.get("ALGO_PROBLEM_ID"),
//                                problem.get("ALGO_PROBLEM_TITLE"),
//                                problem.get("ALGO_PROBLEM_DIFFICULTY"),
//                                problem.get("ALGO_PROBLEM_SOURCE"))
//                );
//            }
//
//            // ALGO_TESTCASES 테이블 확인
//            Integer testcaseCount = jdbcTemplate.queryForObject(
//                    "SELECT COUNT(*) FROM ALGO_TESTCASES", Integer.class);
//            log.info("✅ 샘플 테스트케이스 데이터 {} 건 확인", testcaseCount);
//
//        } catch (Exception e) {
//            log.error("❌ 샘플 데이터 확인 실패: {}", e.getMessage(), e);
//            throw e;
//        }
//    }
//
//    /**
//     * AUTO_INCREMENT 설정 확인
//     */
//    private void checkAutoIncrement() {
//        try {
//            String sql = """
//                SELECT
//                    TABLE_NAME,
//                    AUTO_INCREMENT,
//                    TABLE_COMMENT
//                FROM INFORMATION_SCHEMA.TABLES
//                WHERE TABLE_SCHEMA = DATABASE()
//                AND AUTO_INCREMENT IS NOT NULL
//                ORDER BY TABLE_NAME
//                """;
//
//            List<Map<String, Object>> tables = jdbcTemplate.queryForList(sql);
//
//            log.info("🔢 AUTO_INCREMENT 설정 확인:");
//            tables.forEach(table ->
//                    log.info("   🔹 {}: 다음ID={} ({})",
//                            table.get("TABLE_NAME"),
//                            table.get("AUTO_INCREMENT"),
//                            table.get("TABLE_COMMENT"))
//            );
//
//            if (tables.isEmpty()) {
//                log.warn("⚠️  AUTO_INCREMENT 설정된 테이블이 없습니다.");
//            }
//
//        } catch (Exception e) {
//            log.warn("⚠️  AUTO_INCREMENT 확인 실패: {}", e.getMessage());
//        }
//    }
//
//    /**
//     * 뷰(View) 존재 확인
//     */
//    private void checkViews() {
//        try {
//            String sql = """
//                SELECT
//                    TABLE_NAME as VIEW_NAME,
//                    TABLE_COMMENT
//                FROM INFORMATION_SCHEMA.VIEWS
//                WHERE TABLE_SCHEMA = DATABASE()
//                ORDER BY TABLE_NAME
//                """;
//
//            List<Map<String, Object>> views = jdbcTemplate.queryForList(sql);
//
//            if (!views.isEmpty()) {
//                log.info("👁️  생성된 뷰(View) 확인:");
//                views.forEach(view ->
//                        log.info("   🔸 {}: {}",
//                                view.get("VIEW_NAME"),
//                                view.get("TABLE_COMMENT"))
//                );
//            }
//
//        } catch (Exception e) {
//            log.debug("뷰 확인 중 오류 (정상): {}", e.getMessage());
//        }
//    }
//}