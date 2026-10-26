package cn.creator.workbench.api;

import cn.creator.workbench.service.*;
import com.google.gson.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/v1/agent-runs",produces=MediaType.APPLICATION_JSON_VALUE)
public class AgentRunController {
    private final AgentRunService service;
    public AgentRunController(AgentRunService service){this.service=service;}
    @GetMapping("/readiness") public String readiness(){return ApiController.ok(service.readiness());}
    @GetMapping public String list(){return ApiController.ok(service.list());}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE) public String create(@RequestBody String body){return ApiController.ok(service.create(parse(body)));}
    @GetMapping("/{id}") public String detail(@PathVariable String id){return ApiController.ok(service.detail(id));}
    @PostMapping(value="/{id}/decision",consumes=MediaType.APPLICATION_JSON_VALUE) public String decide(@PathVariable String id,@RequestBody String body){return ApiController.ok(service.decide(id,parse(body)));}
    @PostMapping(value="/{id}/control",consumes=MediaType.APPLICATION_JSON_VALUE) public String control(@PathVariable String id,@RequestBody String body){return ApiController.ok(service.control(id,parse(body)));}
    private JsonObject parse(String body){if(body.length()>4000)throw new BusinessException(413,"请求过长");var data=JsonParser.parseString(body);if(!data.isJsonObject())throw new BusinessException(400,"请求必须为 JSON 对象");return data.getAsJsonObject();}
}
