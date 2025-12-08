package kr.or.kosa.backend.codenose.service.search.strategy;

import kr.or.kosa.backend.codenose.parser.JavaScriptLexer;
import kr.or.kosa.backend.codenose.parser.JavaScriptParser;
import kr.or.kosa.backend.codenose.parser.JavaScriptParserBaseListener;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JavaScriptSyntacticAnalysisStrategy implements SyntacticAnalysisStrategy {

    @Override
    public boolean supports(String language) {
        return "javascript".equalsIgnoreCase(language) || "js".equalsIgnoreCase(language)
                || "ts".equalsIgnoreCase(language);
    }

    @Override
    public Map<String, Object> extractFeatures(String code) {
        try {
            JavaScriptLexer lexer = new JavaScriptLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            JavaScriptParser parser = new JavaScriptParser(tokens);

            ParseTree tree = parser.program();

            FeatureExtractionListener listener = new FeatureExtractionListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);

            return listener.getFeatures();

        } catch (Exception e) {
            log.error("Failed to parse JavaScript code for syntactic analysis", e);
            return Map.of("error", e.getMessage());
        }
    }

    private static class FeatureExtractionListener extends JavaScriptParserBaseListener {
        private int loopDepth = 0;
        private int maxLoopDepth = 0;
        private int complexity = 1;
        private boolean hasExceptionHandling = false;
        private final Map<String, Integer> apiUsage = new HashMap<>();

        @Override
        public void enterDoStatement(JavaScriptParser.DoStatementContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        @Override
        public void exitDoStatement(JavaScriptParser.DoStatementContext ctx) {
            loopDepth--;
        }

        @Override
        public void enterWhileStatement(JavaScriptParser.WhileStatementContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        @Override
        public void exitWhileStatement(JavaScriptParser.WhileStatementContext ctx) {
            loopDepth--;
        }

        @Override
        public void enterForStatement(JavaScriptParser.ForStatementContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        @Override
        public void exitForStatement(JavaScriptParser.ForStatementContext ctx) {
            loopDepth--;
        }

        @Override
        public void enterForInStatement(JavaScriptParser.ForInStatementContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        @Override
        public void exitForInStatement(JavaScriptParser.ForInStatementContext ctx) {
            loopDepth--;
        }

        @Override
        public void enterForOfStatement(JavaScriptParser.ForOfStatementContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        @Override
        public void exitForOfStatement(JavaScriptParser.ForOfStatementContext ctx) {
            loopDepth--;
        }

        @Override
        public void enterIfStatement(JavaScriptParser.IfStatementContext ctx) {
            complexity++;
        }

        @Override
        public void enterSwitchStatement(JavaScriptParser.SwitchStatementContext ctx) {
            complexity++;
        }

        @Override
        public void enterCaseClause(JavaScriptParser.CaseClauseContext ctx) {
            complexity++;
        }

        @Override
        public void enterCatchProduction(JavaScriptParser.CatchProductionContext ctx) {
            hasExceptionHandling = true;
            complexity++;
        }

        @Override
        public void enterImportStatement(JavaScriptParser.ImportStatementContext ctx) {
            // Simplification: trying to extract module name if implicit
            // JavaScript imports are complex. We will count import statements generally.
        }

        @Override
        public void enterImportFrom(JavaScriptParser.ImportFromContext ctx) {
            String moduleName = ctx.StringLiteral().getText();
            moduleName = moduleName.replace("'", "").replace("\"", "");
            apiUsage.merge(moduleName, 1, Integer::sum);
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
