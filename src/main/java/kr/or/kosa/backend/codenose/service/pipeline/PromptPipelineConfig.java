package kr.or.kosa.backend.codenose.service.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

/**
 * 프롬프트 파이프라인 설정 (PromptPipelineConfig)
 * 
 * 역할:
 * Spring Integration을 사용하여 코드 최적화를 위한 파이프라인 흐름을 정의합니다.
 * 파이프라인 단계: 스타일 추출 -> 로직 최적화 -> 최종 코드 합성
 */
@Configuration
public class PromptPipelineConfig {

    /**
     * 파이프라인 입력 채널 정의
     * 
     * @return DirectChannel
     */
    @Bean
    public MessageChannel pipelineInputChannel() {
        return new DirectChannel();
    }

    /**
     * 프롬프트 처리 파이프라인 흐름 정의 (IntegrationFlow)
     * 
     * 1. extractStyle: 사용자 과거 이력에서 스타일 규칙 추출
     * 2. optimizeLogic: 코드의 알고리즘 및 로직 효율성 최적화
     * 3. synthesize: 스타일과 최적화된 로직을 결합하여 최종 코드 생성
     * 
     * @param styleExtractor   스타일 추출 모듈
     * @param logicOptimizer   로직 최적화 모듈
     * @param finalSynthesizer 최종 합성 모듈
     * @return IntegrationFlow 객체
     */
    @Bean
    public IntegrationFlow promptPipelineFlow(
            StyleExtractorModule styleExtractor,
            LogicOptimizerModule logicOptimizer,
            FinalSynthesizerModule finalSynthesizer) {

        return IntegrationFlow.from(pipelineInputChannel())
                .handle(styleExtractor, "extractStyle")
                .handle(logicOptimizer, "optimizeLogic")
                .handle(finalSynthesizer, "synthesize")
                .get();
    }

    /**
     * 파이프라인 진입점 게이트웨이 (PromptPipelineGateway)
     * 
     * 외부에서 이 인터페이스를 호출하여 파이프라인을 실행합니다.
     */
    @MessagingGateway
    public interface PromptPipelineGateway {
        @Gateway(requestChannel = "pipelineInputChannel")
        PipelineContext executePipeline(PipelineContext initialContext);
    }
}
