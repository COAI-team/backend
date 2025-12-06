package kr.or.kosa.backend.codenose.service.pipeline;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LogicOptimizerModule {

    private final ChatClient chatClient;

    public LogicOptimizerModule(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public PipelineContext optimizeLogic(PipelineContext context) {
        System.out.println("[TRACE] LogicOptimizerModule.optimizeLogic called with context: " + context);
        String prompt = """
                Optimize the following Java code for performance and readability.
                Ignore specific style conventions for now; focus on algorithmic efficiency and best practices.
                Output ONLY the optimized code snippet.

                Code:
                %s
                """.formatted(context.getOriginalCode());

        String optimized = chatClient.prompt(prompt).call().content();
        context.setOptimizedLogic(optimized);
        return context;
    }
}
