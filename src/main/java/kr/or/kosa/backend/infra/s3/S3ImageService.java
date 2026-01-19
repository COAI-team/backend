package kr.or.kosa.backend.infra.s3;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3ImageService {

    private final S3Uploader s3Uploader;

    // 확장자 검증
    private final List<String> IMAGE_TYPES = List.of(
        "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif"
    );

    public String uploadImage(MultipartFile file) throws IOException {

        if (!IMAGE_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        return s3Uploader.upload(file, "images");
    }
}
