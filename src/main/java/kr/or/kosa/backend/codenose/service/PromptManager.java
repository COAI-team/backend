package kr.or.kosa.backend.codenose.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PromptManager {

    private final Map<String, String> prompts = new HashMap<>();
    private static final String PROMPT_FILE_PATH = "prompts/prompts.st";
    private static final Pattern DELIMITER_PATTERN = Pattern.compile("=== (\\w+) ===");

    @PostConstruct
    public void loadPrompts() {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_FILE_PATH);
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            parsePrompts(content);
            log.info("Successfully loaded {} prompts from {}", prompts.size(), PROMPT_FILE_PATH);
        } catch (IOException e) {
            log.error("Failed to load prompts from {}", PROMPT_FILE_PATH, e);
            throw new RuntimeException("Failed to load prompts", e);
        }
    }

    private void parsePrompts(String content) {
        Matcher matcher = DELIMITER_PATTERN.matcher(content);
        int lastEnd = 0;
        String currentKey = null;

        while (matcher.find()) {
            if (currentKey != null) {
                String promptContent = content.substring(lastEnd, matcher.start()).trim();
                prompts.put(currentKey, promptContent);
            }
            currentKey = matcher.group(1);
            lastEnd = matcher.end();
        }

        // Add the last prompt
        if (currentKey != null) {
            String promptContent = content.substring(lastEnd).trim();
            prompts.put(currentKey, promptContent);
        }
    }

    public String getPrompt(String key) {
        if (!prompts.containsKey(key)) {
            log.warn("Prompt key '{}' not found!", key);
            return "";
        }
        return prompts.get(key);
    }
}
