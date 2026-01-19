package kr.or.kosa.backend.infra.s3;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/upload")
public class FileUploadController {

    private final S3ImageService imageService;
    private final S3FileService fileService;

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) throws Exception {
        String url = imageService.uploadImage(file);
        return ResponseEntity.ok().body(url);
    }

    @PostMapping("/file")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) throws Exception {
        String url = fileService.uploadFile(file);
        return ResponseEntity.ok().body(url);
    }
}
