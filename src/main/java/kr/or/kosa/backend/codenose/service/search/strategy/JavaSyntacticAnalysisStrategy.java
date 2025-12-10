package kr.or.kosa.backend.codenose.service.search.strategy;

import kr.or.kosa.backend.codenose.parser.JavaBaseListener;
import kr.or.kosa.backend.codenose.parser.JavaLexer;
import kr.or.kosa.backend.codenose.parser.JavaParser;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Java 구문 분석 전략 (JavaSyntacticAnalysisStrategy)
 * 
 * 역할:
 * ANTLR4로 생성된 Java 파서(`JavaParser`, `JavaLexer`)를 사용하여
 * Java 코드의 구조적 특징(루프 중첩 깊이, 순환 복잡도, 예외 처리 여부, API 사용 등)을 추출합니다.
 */
@Slf4j
@Component
public class JavaSyntacticAnalysisStrategy implements SyntacticAnalysisStrategy {

    @Override
    public boolean supports(String language) {
        return "java".equalsIgnoreCase(language);
    }

    @Override
    public Map<String, Object> extractFeatures(String code) {
        try {
            // ANTLR Lexer & Parser 초기화
            JavaLexer lexer = new JavaLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            JavaParser parser = new JavaParser(tokens);

            // 파스 트리 생성 (CompilationUnit 시작점)
            ParseTree tree = parser.compilationUnit();

            // 리스너 기반으로 트리 순회하며 특징 추출
            FeatureExtractionListener listener = new FeatureExtractionListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);

            return listener.getFeatures();

        } catch (Exception e) {
            log.error("Java 코드 구문 분석 실패", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 내부 리스너 클래스
     * AST(Abstract Syntax Tree)를 순회하면서 실제 메트릭을 계산합니다.
     */
    private static class FeatureExtractionListener extends JavaBaseListener {
        private int loopDepth = 0;
        private int maxLoopDepth = 0;
        private int complexity = 1; // 기본 복잡도
        private boolean hasExceptionHandling = false;
        private final Map<String, Integer> apiUsage = new HashMap<>();

        // for 루프 진입
        @Override
        public void enterForControl(JavaParser.ForControlContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++; // 분기점 발생으로 복잡도 증가
        }

        // for 루프 탈출
        @Override
        public void exitForControl(JavaParser.ForControlContext ctx) {
            loopDepth--;
        }

        // 일반 구문(Statement) 진입 - while, do-while, if 체크
        @Override
        public void enterStatement(JavaParser.StatementContext ctx) {
            if (ctx.WHILE() != null || ctx.DO() != null) {
                loopDepth++;
                maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
                complexity++;
            }
            if (ctx.IF() != null) {
                complexity++;
            }
        }

        @Override
        public void exitStatement(JavaParser.StatementContext ctx) {
            if (ctx.WHILE() != null || ctx.DO() != null) {
                loopDepth--;
            }
        }

        // try-catch 블록 진입
        @Override
        public void enterCatchClause(JavaParser.CatchClauseContext ctx) {
            hasExceptionHandling = true;
            complexity++;
        }

        // import 구문 분석 (API 사용량 추적)
        @Override
        public void enterImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
            String importName = ctx.qualifiedName().getText();
            String[] parts = importName.split("\\.");
            if (parts.length >= 2) {
                String pkg = parts[0] + "." + parts[1]; // 예: java.util
                apiUsage.merge(pkg, 1, Integer::sum);
            }
        }

        public Map<String, Object> getFeatures() {
            Map<String, Object> features = new HashMap<>();
            features.put("max_loop_depth", maxLoopDepth);
            features.put("cyclomatic_complexity", complexity);
            features.put("has_exception_handling", hasExceptionHandling);
            features.put("api_usage", apiUsage);
            return features;
        }
    }
}
