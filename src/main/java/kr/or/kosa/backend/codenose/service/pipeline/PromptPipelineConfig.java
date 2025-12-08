package kr.or.kosa.backend.codenose.service.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;

@Configuration
public class PromptPipelineConfig {

    @Bean
    public MessageChannel pipelineInputChannel() {
        return new DirectChannel();
    }

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

    @MessagingGateway
    public interface PromptPipelineGateway {
        @Gateway(requestChannel = "pipelineInputChannel")
        PipelineContext executePipeline(PipelineContext initialContext);
    }
}
