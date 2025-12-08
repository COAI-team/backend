package kr.or.kosa.backend.codenose.service.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import kr.or.kosa.backend.codenose.service.PromptManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgenticWorkflowService {

    private final Generator generator;
    private final Critic critic;
    private final Refiner refiner;
    private final PromptManager promptManager;

    public AgenticWorkflowService(ChatLanguageModel chatLanguageModel, PromptManager promptManager) {
        this.generator = AiServices.create(Generator.class, chatLanguageModel);
        this.critic = AiServices.create(Critic.class, chatLanguageModel);
        this.refiner = AiServices.create(Refiner.class, chatLanguageModel);
        this.promptManager = promptManager;
    }

    public String executeWorkflow(String userCode, String systemPrompt) {
        System.out.println(
                "[TRACE] AgenticWorkflowService.executeWorkflow called with userCode length: " + userCode.length());
        log.info("Starting Agentic Workflow...");

        // Step 1: Generate Draft (JSON)
        // We pass the systemPrompt (which contains the JSON schema) to the generator
        String draft = generator.generate(userCode, systemPrompt);
        log.info("Draft generated.");

        // Step 2: Critic Loop
        int maxIterations = 2; // Keep it short for latency
        String currentJson = draft;
        String criticSystemPrompt = promptManager.getPrompt("CRITIC_SYSTEM_PROMPT");

        for (int i = 0; i < maxIterations; i++) {
            // Critic evaluates the JSON content
            String critique = critic.critique(currentJson, criticSystemPrompt);

            if (critique.toLowerCase().contains("approved")) {
                log.info("Code approved by Critic at iteration {}", i + 1);
                return currentJson;
            }

            log.info("Critic found issues: {}", critique);
            // Refiner updates the JSON based on critique
            currentJson = refiner.refine(currentJson, critique, systemPrompt);
            log.info("Code refined at iteration {}", i + 1);
        }

        log.warn("Max iterations reached. Returning last refined version.");
        return currentJson;
    }

    // --- Agent Interfaces ---

    interface Generator {
        @SystemMessage("{{systemPrompt}}")
        String generate(@UserMessage("Analyze this code:\n{{code}}") @V("code") String code,
                @V("systemPrompt") String systemPrompt);
    }

    interface Critic {
        @SystemMessage("{{systemPrompt}}")
        String critique(@UserMessage("Review this JSON analysis:\n{{json}}") @V("json") String json,
                @V("systemPrompt") String systemPrompt);
    }

    interface Refiner {
        @SystemMessage("{{systemPrompt}}\n\nIMPORTANT: Fix the JSON based on the feedback provided.")
        String refine(@UserMessage("Current JSON: {{json}}\nFeedback: {{feedback}}") @V("json") String json,
                @V("feedback") String feedback, @V("systemPrompt") String systemPrompt);
    }
}
