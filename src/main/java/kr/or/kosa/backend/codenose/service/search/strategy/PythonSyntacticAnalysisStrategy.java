package kr.or.kosa.backend.codenose.service.search.strategy;

import kr.or.kosa.backend.codenose.parser.PythonLexer;
import kr.or.kosa.backend.codenose.parser.PythonParser;
import kr.or.kosa.backend.codenose.parser.PythonParserBaseListener;
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
public class PythonSyntacticAnalysisStrategy implements SyntacticAnalysisStrategy {

    @Override
    public boolean supports(String language) {
        return "python".equalsIgnoreCase(language) || "py".equalsIgnoreCase(language);
    }

    @Override
    public Map<String, Object> extractFeatures(String code) {
        try {
            PythonLexer lexer = new PythonLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            PythonParser parser = new PythonParser(tokens);

            ParseTree tree = parser.file_input();

            FeatureExtractionListener listener = new FeatureExtractionListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);

            return listener.getFeatures();

        } catch (Exception e) {
            log.error("Failed to parse Python code for syntactic analysis", e);
            return Map.of("error", e.getMessage());
        }
    }

    private static class FeatureExtractionListener extends PythonParserBaseListener {
        private int loopDepth = 0;
        private int maxLoopDepth = 0;
        private int complexity = 1;
        private boolean hasExceptionHandling = false;
        private final Map<String, Integer> apiUsage = new HashMap<>();

        @Override
        public void enterFor_stmt(PythonParser.For_stmtContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        @Override
        public void exitFor_stmt(PythonParser.For_stmtContext ctx) {
            loopDepth--;
        }

        @Override
        public void enterWhile_stmt(PythonParser.While_stmtContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        @Override
        public void exitWhile_stmt(PythonParser.While_stmtContext ctx) {
            loopDepth--;
        }

        @Override
        public void enterIf_stmt(PythonParser.If_stmtContext ctx) {
            complexity++; // Basic if count
            // Elifs are handled in recursion in some grammars, or linear list.
            // In this grammar, elif_stmt is a separate rule.
        }

        @Override
        public void enterElif_stmt(PythonParser.Elif_stmtContext ctx) {
            complexity++;
        }

        @Override
        public void enterTry_stmt(PythonParser.Try_stmtContext ctx) {
            hasExceptionHandling = true;
        }

        @Override
        public void enterExcept_block(PythonParser.Except_blockContext ctx) {
            complexity++;
        }

        @Override
        public void enterImport_name(PythonParser.Import_nameContext ctx) {
            // import A, B
            // dotted_as_names -> dotted_as_name -> dotted_name
            String text = ctx.dotted_as_names().getText();
            // Simplification: just take the text, splitting complex imports properly
            // requires more work
            for (String part : text.split(",")) {
                apiUsage.merge(part.trim(), 1, Integer::sum);
            }
        }

        @Override
        public void enterImport_from(PythonParser.Import_fromContext ctx) {
            // from X import Y
            if (ctx.dotted_name() != null) {
                String module = ctx.dotted_name().getText();
                apiUsage.merge(module, 1, Integer::sum);
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
