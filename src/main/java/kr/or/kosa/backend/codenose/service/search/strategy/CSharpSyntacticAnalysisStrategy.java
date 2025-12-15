package kr.or.kosa.backend.codenose.service.search.strategy;

import kr.or.kosa.backend.codenose.parser.CSharpLexer;
import kr.or.kosa.backend.codenose.parser.CSharpParser;
import kr.or.kosa.backend.codenose.parser.CSharpParserBaseListener;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * C# 구문 분석 전략 (CSharpSyntacticAnalysisStrategy)
 *
 * 역할:
 * ANTLR4로 생성된 C# 파서(`CSharpParser`, `CSharpLexer`)를 사용하여
 * C# 코드의 구조적 특징(루프 중첩 깊이, 순환 복잡도, 예외 처리 여부, API 사용 등)을 추출합니다.
 */
@Slf4j
@Component
public class CSharpSyntacticAnalysisStrategy implements SyntacticAnalysisStrategy {

    @Override
    public boolean supports(String language) {
        return "csharp".equalsIgnoreCase(language) || "cs".equalsIgnoreCase(language);
    }

    @Override
    public Map<String, Object> extractFeatures(String code) {
        try {
            // ANTLR Lexer & Parser 초기화
            CSharpLexer lexer = new CSharpLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CSharpParser parser = new CSharpParser(tokens);

            // 파스 트리 생성 (compilation_unit 시작점)
            ParseTree tree = parser.compilation_unit();

            // 리스너 기반으로 트리 순회하며 특징 추출
            FeatureExtractionListener listener = new FeatureExtractionListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);

            return listener.getFeatures();

        } catch (Exception e) {
            log.error("C# 코드 구문 분석 실패", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 내부 리스너 클래스
     * AST(Abstract Syntax Tree)를 순회하면서 실제 메트릭을 계산합니다.
     */
    private static class FeatureExtractionListener extends CSharpParserBaseListener {
        private int loopDepth = 0;
        private int maxLoopDepth = 0;
        private int complexity = 1; // 기본 복잡도
        private boolean hasExceptionHandling = false;
        private final Map<String, Integer> apiUsage = new HashMap<>();

        // Loop statements: while, do, for, foreach

        @Override
        public void enterWhileStatement(CSharpParser.WhileStatementContext ctx) {
            enterLoop();
        }

        @Override
        public void exitWhileStatement(CSharpParser.WhileStatementContext ctx) {
            exitLoop();
        }

        @Override
        public void enterDoStatement(CSharpParser.DoStatementContext ctx) {
            enterLoop();
        }

        @Override
        public void exitDoStatement(CSharpParser.DoStatementContext ctx) {
            exitLoop();
        }

        @Override
        public void enterForStatement(CSharpParser.ForStatementContext ctx) {
            enterLoop();
        }

        @Override
        public void exitForStatement(CSharpParser.ForStatementContext ctx) {
            exitLoop();
        }

        @Override
        public void enterForeachStatement(CSharpParser.ForeachStatementContext ctx) {
            enterLoop();
        }

        @Override
        public void exitForeachStatement(CSharpParser.ForeachStatementContext ctx) {
            exitLoop();
        }

        private void enterLoop() {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        private void exitLoop() {
            loopDepth--;
        }

        // Selection statements: if, switch
        @Override
        public void enterIfStatement(CSharpParser.IfStatementContext ctx) {
            complexity++;
        }

        @Override
        public void enterSwitchStatement(CSharpParser.SwitchStatementContext ctx) {
            complexity++;
        }

        // Catch blocks for exception handling
        @Override
        public void enterSpecific_catch_clause(CSharpParser.Specific_catch_clauseContext ctx) {
            hasExceptionHandling = true;
            complexity++;
        }

        @Override
        public void enterGeneral_catch_clause(CSharpParser.General_catch_clauseContext ctx) {
            hasExceptionHandling = true;
            complexity++;
        }

        // Using directives for API usage tracking
        @Override
        public void enterUsingNamespaceDirective(CSharpParser.UsingNamespaceDirectiveContext ctx) {
            // using System.Collections.Generic;
            // namespace_or_type_name -> getText() might correspond to
            // "System.Collections.Generic"
            String namespace = ctx.namespace_or_type_name().getText();
            String[] parts = namespace.split("\\.");
            if (parts.length >= 2) {
                String pkg = parts[0] + "." + parts[1];
                apiUsage.merge(pkg, 1, Integer::sum);
            } else {
                apiUsage.merge(parts[0], 1, Integer::sum);
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
