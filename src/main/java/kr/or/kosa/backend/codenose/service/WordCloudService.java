package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kennycason.kumo.CollisionMode;
import com.kennycason.kumo.WordCloud;
import com.kennycason.kumo.WordFrequency;
import com.kennycason.kumo.bg.PixelBoundaryBackground;
import com.kennycason.kumo.font.scale.LinearFontScalar;
import com.kennycason.kumo.nlp.FrequencyAnalyzer;
import com.kennycason.kumo.palette.ColorPalette;
import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordCloudService {

    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;

    public String generateWordCloud(Long userId, int year, int month) {
        try {
            // 1. Fetch data for the given month
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDateTime startDateTime = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime endDateTime = yearMonth.atEndOfMonth().atTime(23, 59, 59);

            List<CodeResultDTO> results = analysisMapper.findCodeResultsByUserIdAndDateRange(
                    userId,
                    Timestamp.valueOf(startDateTime),
                    Timestamp.valueOf(endDateTime));

            if (results.isEmpty()) {
                return null; // No data
            }

            // 2. Aggregate frequencies
            Map<String, Integer> frequencyMap = new HashMap<>();
            for (CodeResultDTO result : results) {
                String codeSmellsJson = result.getCodeSmells();
                if (codeSmellsJson != null && !codeSmellsJson.isEmpty()) {
                    try {
                        JsonNode root = objectMapper.readTree(codeSmellsJson);
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                String name = node.path("name").asText();
                                if (!name.isEmpty()) {
                                    frequencyMap.put(name, frequencyMap.getOrDefault(name, 0) + 1);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse code smells JSON for analysisId: {}", result.getAnalysisId());
                    }
                }
            }

            if (frequencyMap.isEmpty()) {
                return null;
            }

            List<WordFrequency> wordFrequencies = frequencyMap.entrySet().stream()
                    .map(entry -> new WordFrequency(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // 3. Configure Kumo WordCloud
            Dimension dimension = new Dimension(600, 600);
            WordCloud wordCloud = new WordCloud(dimension, CollisionMode.PIXEL_PERFECT);
            wordCloud.setPadding(2);
            wordCloud.setBackground(new com.kennycason.kumo.bg.CircleBackground(300)); // Default fallback
                                                                                       // fallback
            wordCloud.setColorPalette(new ColorPalette(new Color(0x4055F1), new Color(0x408DF1), new Color(0x40AAF1),
                    new Color(0x40C5F1), new Color(0x40D3F1), new Color(0xFFFFFF)));
            wordCloud.setFontScalar(new LinearFontScalar(10, 40));

            // 4. Load Random Mask
            try {
                int randomMaskIndex = new Random().nextInt(2) + 1; // 1 or 2
                String maskPath = "codenose/wordcloudbackgrounds/mask" + randomMaskIndex + ".png";
                ClassPathResource maskResource = new ClassPathResource(maskPath);
                if (maskResource.exists()) {
                    try (InputStream is = maskResource.getInputStream()) {
                        wordCloud.setBackground(new PixelBoundaryBackground(is));
                    }
                } else {
                    log.warn("Mask file not found: {}", maskPath);
                    // Fallback to circle if mask not found
                    wordCloud.setBackground(new com.kennycason.kumo.bg.CircleBackground(300));
                }
            } catch (Exception e) {
                log.error("Failed to load mask image", e);
                wordCloud.setBackground(new com.kennycason.kumo.bg.CircleBackground(300));
            }

            // 5. Build Word Cloud
            wordCloud.build(wordFrequencies);

            // 6. Convert to Base64
            BufferedImage bufferedImage = wordCloud.getBufferedImage();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            return Base64.getEncoder().encodeToString(imageBytes);

        } catch (Exception e) {
            log.error("Error generating word cloud", e);
            throw new RuntimeException("Failed to generate word cloud", e);
        }
    }
}
