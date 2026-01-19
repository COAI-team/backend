package kr.or.kosa.backend.infra.s3;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// 공통 업로드 유틸리티
@Component
@RequiredArgsConstructor
public class S3Uploader {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    // 공통 업로드 메서드
    public String upload(MultipartFile file, String folder) throws IOException {

        // 파일명 생성
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String original = file.getOriginalFilename();
        String key = folder + "/" + timestamp + "_" + original;

        // 업로드 요청 생성
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(file.getContentType())
            .acl("public-read")
            .build();

        // S3 업로드 실행
        s3Client.putObject(
            putObjectRequest,
            RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        return getFileUrl(key);
    }

    // URL 생성
    public String getFileUrl(String key) {
        return "https://" + bucketName + ".s3.ap-northeast-2.amazonaws.com/" + key;
    }
}
