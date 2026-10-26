package cn.creator.workbench.api;

import cn.creator.workbench.service.MediaService;
import com.google.gson.JsonObject;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/files")
public class MediaController {
    private final MediaService service;
    private static final Set<String> INLINE=Set.of("image/png","image/jpeg","image/webp","image/gif","video/mp4","video/webm","audio/mpeg","audio/wav","audio/ogg");
    public MediaController(MediaService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public String upload(@RequestParam("file")MultipartFile file)throws IOException{var record=service.save(file);var data=new JsonObject();data.addProperty("fileRef","/api/v1/files/"+record.getId());data.addProperty("sha256",record.getSha256());data.addProperty("size",record.getByteSize());return ApiController.ok(data);}
    @GetMapping("/{id}")
    public ResponseEntity<Resource> file(@PathVariable String id){var record=service.find(id);boolean inline=INLINE.contains(record.getMediaType());return ResponseEntity.ok().contentType(inline?MediaType.parseMediaType(record.getMediaType()):MediaType.APPLICATION_OCTET_STREAM).contentLength(record.getByteSize()).header(HttpHeaders.CONTENT_DISPOSITION,(inline?ContentDisposition.inline():ContentDisposition.attachment()).filename(record.getOriginalName(),StandardCharsets.UTF_8).build().toString()).header("Content-Security-Policy","default-src 'none'; sandbox").body(service.resource(record));}
}
