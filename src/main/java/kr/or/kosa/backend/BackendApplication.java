package kr.or.kosa.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "kr.or.kosa.backend.user.mapper",
        "kr.or.kosa.backend.pay.repository",
        "kr.or.kosa.backend.freeboard.mapper",
        "kr.or.kosa.backend.freeboardLike.mapper",
        "kr.or.kosa.backend.tag.mapper",
        "kr.or.kosa.backend.freeComment.mapper",
        "kr.or.kosa.backend.algorithm.mapper",
        "kr.or.kosa.backend.codenose.mapper"
})
//@MapperScan("kr.or.kosa.backend")
public class BackendApplication {
    public static void main(String[] args) {

        // 1) .env 로드
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .load();

        // 2) 환경변수를 시스템 프로퍼티에 적용
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        // 3) Spring Boot 실행
        SpringApplication.run(BackendApplication.class, args);
    }
}