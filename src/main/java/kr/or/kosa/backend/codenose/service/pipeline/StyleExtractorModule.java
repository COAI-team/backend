package kr.or.kosa.backend.codenose.service.pipeline;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class StyleExtractorModule {

    private final ChatClient chatClient;

    public StyleExtractorModule(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public PipelineContext extractStyle(PipelineContext context) {
        System.out.println("[TRACE] StyleExtractorModule.extractStyle called with context: " + context);
        if (context.getUserContext() == null || context.getUserContext().isEmpty()) {
            context.setStyleRules("Follow standard Java conventions.");
            return context;
        }

        String prompt = """
                Extract explicit coding style rules from the following user history.
                Focus on naming conventions, error handling patterns, and library preferences.
                Output ONLY the rules as a bulleted list.

                History:
                %s
                """.formatted(context.getUserContext());

        String rules = chatClient.prompt(prompt).call().content();
        context.setStyleRules(rules);
        return context;
    }
}
