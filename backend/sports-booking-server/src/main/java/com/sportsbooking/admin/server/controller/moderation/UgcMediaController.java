package com.sportsbooking.admin.server.controller.moderation;

import com.sportsbooking.admin.biz.pojo.vo.UgcUploadVO;
import com.sportsbooking.admin.biz.pojo.vo.UgcMediaVO;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import com.sportsbooking.admin.biz.service.UgcMediaService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** UGC 独立上传与公开版本受控读取，不使用场馆封面目录。 */
@RestController
public class UgcMediaController {
    @Autowired
    private UgcMediaService service;

    @PostMapping(value = "/mini/content/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UgcUploadVO> upload(@RequestParam MultipartFile file,
                                           @RequestParam(required = false) Integer durationSeconds) {
        return ApiResponse.success(service.upload(AuthContext.getMiniUserId(), file, durationSeconds));
    }

    @GetMapping("/mini/content/media/posts/{mediaId}")
    public ResponseEntity<Resource> post(@PathVariable long mediaId) {
        return file(service.openPost(AuthContext.getMiniUserId(), mediaId));
    }

    @GetMapping("/mini/content/media/reviews/{mediaId}")
    public ResponseEntity<Resource> ownReview(@PathVariable long mediaId) {
        return file(service.openReview(AuthContext.getMiniUserId(), mediaId));
    }

    @GetMapping("/public/content/media/reviews/{mediaId}")
    public ResponseEntity<Resource> publicReview(@PathVariable long mediaId) {
        return file(service.openReview(null, mediaId));
    }

    @GetMapping("/platform/content-media/{type}/{revisionId}")
    public ApiResponse<List<UgcMediaVO>> preview(@PathVariable String type, @PathVariable long revisionId) {
        Principals.platform();
        return ApiResponse.success(service.listForModeration(type,revisionId));
    }

    @GetMapping("/platform/content-media/{type}/file/{mediaId}")
    public ResponseEntity<Resource> file(@PathVariable String type, @PathVariable long mediaId) {
        Principals.platform();
        return file(service.openForModeration(type,mediaId));
    }

    private ResponseEntity<Resource> file(UgcMediaService.StoredFile stored) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.mimeType()))
                .cacheControl(CacheControl.noStore()).body(new FileSystemResource(stored.path()));
    }
}
