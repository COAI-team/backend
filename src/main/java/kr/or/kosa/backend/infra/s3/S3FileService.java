package kr.or.kosa.backend.infra.s3;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3FileService {

    private final S3Uploader s3Uploader;

    public String uploadFile(MultipartFile file) throws IOException {
        return s3Uploader.upload(file, "files");
    }
}
