package kr.or.kosa.backend.codenose.service.pipeline;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FinalSynthesizerModule {

    private final ChatClient chatClient;
    private final Mustache.Compiler mustacheCompiler;

    public FinalSynthesizerModule(ChatClient.Builder builder, Mustache.Compiler mustacheCompiler) {
        this.chatClient = builder.build();
        this.mustacheCompiler = mustacheCompiler;
    }

    public PipelineContext synthesize(PipelineContext context) {
        System.out.println("[TRACE] FinalSynthesizerModule.synthesize called with context: " + context);
        String promptTemplate = """
                You are a code synthesizer.
                Apply the following Style Rules to the Optimized Logic.

                Style Rules:
                {{styleRules}}

                Optimized Logic:
                {{optimizedLogic}}

                Output the final Java code.
                """;

        Template tmpl = mustacheCompiler.compile(promptTemplate);
        String prompt = tmpl.execute(Map.of(
                "styleRules", context.getStyleRules(),
                "optimizedLogic", context.getOptimizedLogic()));

        String finalCode = chatClient.prompt(prompt).call().content();
        context.setFinalResult(finalCode);
        return context;
    }
}
