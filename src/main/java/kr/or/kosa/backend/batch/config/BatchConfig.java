package kr.or.kosa.backend.batch.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Spring Batch 공통 설정
 *
 * Spring Boot 3.x + Spring Batch 5.x 버전에서는
 * @EnableBatchProcessing 어노테이션 없이도 자동 설정이 작동합니다.
 *
 * 커스텀 설정이 필요한 경우에만 @EnableBatchProcessing을 사용하며,
 * 이 경우 DefaultBatchConfiguration을 상속받아 구현합니다.
 */
@Configuration
public class BatchConfig {

    /**
     * 비동기 Job 실행을 위한 JobLauncher 설정
     *
     * 기본 JobLauncher는 동기 방식이므로,
     * 스케줄러에서 비동기 실행이 필요한 경우 이 Bean을 사용합니다.
     *
     * @param jobRepository Job 메타데이터 저장소
     * @return 비동기 JobLauncher
     */
    @Bean(name = "asyncJobLauncher")
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor());
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }

    /**
     * 배치 작업의 청크(Chunk) 크기 설정
     * 대용량 데이터 처리 시 한 번에 처리할 데이터 건수
     *
     * 일반적으로 100~1000 사이의 값을 사용하며,
     * 데이터 특성과 메모리 상황에 따라 조정이 필요합니다.
     */
    public static final int CHUNK_SIZE = 100;

    /**
     * 배치 재시작 시 건너뛸 레코드 수
     * Job이 실패 후 재시작될 때 이미 처리된 데이터를 건너뜁니다.
     */
    public static final int SKIP_LIMIT = 10;
}