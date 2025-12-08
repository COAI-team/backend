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
            JavaLexer lexer = new JavaLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            JavaParser parser = new JavaParser(tokens);

            ParseTree tree = parser.compilationUnit();

            FeatureExtractionListener listener = new FeatureExtractionListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree);

            return listener.getFeatures();

        } catch (Exception e) {
            log.error("Failed to parse Java code for syntactic analysis", e);
            return Map.of("error", e.getMessage());
        }
    }

    private static class FeatureExtractionListener extends JavaBaseListener {
        private int loopDepth = 0;
        private int maxLoopDepth = 0;
        private int complexity = 1;
        private boolean hasExceptionHandling = false;
        private final Map<String, Integer> apiUsage = new HashMap<>();

        @Override
        public void enterForControl(JavaParser.ForControlContext ctx) {
            loopDepth++;
            maxLoopDepth = Math.max(maxLoopDepth, loopDepth);
            complexity++;
        }

        @Override
        public void exitForControl(JavaParser.ForControlContext ctx) {
            loopDepth--;
        }

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

        @Override
        public void enterCatchClause(JavaParser.CatchClauseContext ctx) {
            hasExceptionHandling = true;
            complexity++;
        }

        @Override
        public void enterImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
            String importName = ctx.qualifiedName().getText();
            String[] parts = importName.split("\\.");
            if (parts.length >= 2) {
                String pkg = parts[0] + "." + parts[1];
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
