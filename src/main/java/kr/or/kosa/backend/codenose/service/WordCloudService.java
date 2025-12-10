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
import kr.or.kosa.backend.codenose.dto.CodeResultDTO;
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

/**
 * 워드 클라우드 서비스 (WordCloudService)
 * 
 * 역할:
 * Kumo 라이브러리를 사용하여, 사용자의 코드 분석 결과에서 자주 등장한 문제점(Code Smell)들을
 * 시각적인 워드 클라우드 이미지로 생성합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordCloudService {

    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;

    /**
     * 월별 워드 클라우드 이미지 생성
     * 
     * 1. 해당 년/월의 사용자 분석 기록을 조회합니다.
     * 2. 분석 결과(JSON Code Smell)를 파싱하여 키워드 빈도수를 계산합니다.
     * 3. Kumo 라이브러리를 사용해 워드 클라우드 이미지를 생성합니다.
     * (랜덤 마스크 이미지를 적용하여 모양을 다양화합니다)
     * 4. 생성된 이미지를 Base64 문자열로 변환하여 반환합니다.
     * 
     * @param userId 사용자 ID
     * @param year   조회할 연도
     * @param month  조회할 월
     * @return Base64 인코딩된 PNG 이미지 데이터 (데이터가 없으면 null)
     */
    public String generateWordCloud(Long userId, int year, int month) {
        try {
            // 1. 해당 월의 데이터 조회 기간 설정
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDateTime startDateTime = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime endDateTime = yearMonth.atEndOfMonth().atTime(23, 59, 59);

            List<CodeResultDTO> results = analysisMapper.findCodeResultsByUserIdAndDateRange(
                    userId,
                    Timestamp.valueOf(startDateTime),
                    Timestamp.valueOf(endDateTime));

            if (results.isEmpty()) {
                return null; // 데이터 없음
            }

            // 2. 빈도수 집계 (Aggregation)
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
                        log.warn("Code Smell JSON 파싱 실패 analysisId: {}", result.getAnalysisId());
                    }
                }
            }

            if (frequencyMap.isEmpty()) {
                return null;
            }

            List<WordFrequency> wordFrequencies = frequencyMap.entrySet().stream()
                    .map(entry -> new WordFrequency(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // 3. Kumo 라이브러리 설정 (워드 클라우드 옵션)
            Dimension dimension = new Dimension(600, 600);
            WordCloud wordCloud = new WordCloud(dimension, CollisionMode.PIXEL_PERFECT);
            wordCloud.setPadding(2);
            wordCloud.setBackground(new com.kennycason.kumo.bg.CircleBackground(300)); // 기본 배경 (원형)

            // 색상 팔레트 설정 (파란색 계열)
            wordCloud.setColorPalette(new ColorPalette(new Color(0x4055F1), new Color(0x408DF1), new Color(0x40AAF1),
                    new Color(0x40C5F1), new Color(0x40D3F1), new Color(0xFFFFFF)));
            wordCloud.setFontScalar(new LinearFontScalar(10, 40));

            // 4. 랜덤 마스크 이미지 적용 (모양 변경)
            try {
                int randomMaskIndex = new Random().nextInt(2) + 1; // 1 또는 2
                String maskPath = "codenose/wordcloudbackgrounds/mask" + randomMaskIndex + ".png";
                ClassPathResource maskResource = new ClassPathResource(maskPath);
                if (maskResource.exists()) {
                    try (InputStream is = maskResource.getInputStream()) {
                        wordCloud.setBackground(new PixelBoundaryBackground(is));
                    }
                } else {
                    log.warn("마스크 파일을 찾을 수 없음: {}", maskPath);
                    // Fallback to circle if mask not found
                    wordCloud.setBackground(new com.kennycason.kumo.bg.CircleBackground(300));
                }
            } catch (Exception e) {
                log.error("마스크 이미지 로드 실패 (기본 원형 사용)", e);
                wordCloud.setBackground(new com.kennycason.kumo.bg.CircleBackground(300));
            }

            // 5. 워드 클라우드 생성 (Build)
            wordCloud.build(wordFrequencies);

            // 6. Base64 변환
            BufferedImage bufferedImage = wordCloud.getBufferedImage();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            return Base64.getEncoder().encodeToString(imageBytes);

        } catch (Exception e) {
            log.error("워드 클라우드 생성 중 오류 발생", e);
            throw new RuntimeException("워드 클라우드 생성 실패", e);
        }
    }
}
