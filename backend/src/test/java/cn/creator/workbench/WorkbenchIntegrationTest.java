package cn.creator.workbench;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import cn.creator.workbench.persistence.WorkspaceMapper;
import org.springframework.boot.test.context.SpringBootTest;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:workbenchtest;MODE=MySQL;DB_CLOSE_DELAY=-1","workbench.files-dir=${java.io.tmpdir}/creator-workbench-test-files"})
class WorkbenchIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired WorkspaceMapper mapper;
    final HttpClient http=HttpClient.newHttpClient();
    JsonObject load()throws Exception{return JsonParser.parseString(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/workspace")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject().getAsJsonObject("data");}
    HttpResponse<String> command(String type,JsonObject payload,long version)throws Exception{var body=new JsonObject();body.addProperty("type",type);body.add("payload",payload);body.addProperty("expectedVersion",version);return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/commands")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());}
    @Test void persistenceVersionConflictAndGatesAreEnforced()throws Exception{
        var initial=load();long version=initial.get("version").getAsLong();var p=new JsonObject();String id="test_"+UUID.randomUUID();p.addProperty("id",id);p.addProperty("title","集成测试作品");p.addProperty("template","SCREENCAST");var created=command("CREATE_PROJECT",p,version);assertEquals(200,created.statusCode(),created.body());var after=load();assertEquals(version+1,after.get("version").getAsLong());assertEquals(49,after.getAsJsonArray("projects").get(0).getAsJsonObject().getAsJsonObject("nodes").size());assertEquals(409,command("CREATE_PROJECT",p,version).statusCode());
        var result=new JsonObject();result.addProperty("projectId",id);result.addProperty("nodeId","N15");result.addProperty("text","不能越过立项");result.addProperty("complete",true);assertEquals(409,command("SAVE_NODE",result,version+1).statusCode());result.addProperty("nodeId","N02");result.addProperty("text","我的真实资源");assertEquals(200,command("SAVE_NODE",result,version+1).statusCode());assertEquals("我的真实资源",load().getAsJsonArray("projects").get(0).getAsJsonObject().getAsJsonObject("nodes").getAsJsonObject("N02").get("output").getAsString());
    }
    @Test void foreignBrowserOriginCannotWrite()throws Exception{
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/commands")).header("Origin","https://untrusted.example.invalid").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,response.statusCode());
    }
    @Test void defaultModelAndRecoverableTaskDeletionArePersistent()throws Exception{
        String modelId="model_"+UUID.randomUUID();
        var model=JsonParser.parseString("""
            {"name":"测试默认模型","provider":"test","protocol":"CUSTOM_HTTP","baseUrl":"https://provider.example.invalid","remoteModelId":"writer","credentialRef":"CREATOR_TEST_KEY","capabilities":["TEXT_REASONING"],"parameters":{}}
            """).getAsJsonObject();model.addProperty("id",modelId);
        var payload=new JsonObject();payload.add("model",model);
        assertEquals(200,command("SAVE_MODEL",payload,load().get("version").getAsLong()).statusCode());
        payload=new JsonObject();payload.addProperty("scope","GLOBAL");payload.addProperty("modelId",modelId);
        assertEquals(200,command("SET_DEFAULT_MODEL",payload,load().get("version").getAsLong()).statusCode());
        assertEquals(modelId,load().getAsJsonObject("globalBindings").get("$default").getAsString());
        String projectId=load().getAsJsonArray("projects").get(0).getAsJsonObject().get("id").getAsString();
        payload=new JsonObject();payload.addProperty("projectId",projectId);
        assertEquals(200,command("PLAN_PROJECT",payload,load().get("version").getAsLong()).statusCode());
        var before=load();String taskId=before.getAsJsonArray("tasks").get(0).getAsJsonObject().get("id").getAsString();
        payload=new JsonObject();payload.addProperty("id",taskId);
        assertEquals(200,command("DELETE_TASK",payload,before.get("version").getAsLong()).statusCode());
        var deleted=load();assertTrue(deleted.getAsJsonArray("tasks").get(0).getAsJsonObject().has("deletedAt"));assertEquals(before.get("projects"),deleted.get("projects"));
        assertEquals(200,command("RESTORE_TASK",payload,deleted.get("version").getAsLong()).statusCode());
        assertFalse(load().getAsJsonArray("tasks").get(0).getAsJsonObject().has("deletedAt"));
        payload.addProperty("id","missing-task");assertEquals(404,command("DELETE_TASK",payload,load().get("version").getAsLong()).statusCode());
    }
    @Test void readingLegacyWorkspaceArchivesRetiredNodeOnceWithVersionChange()throws Exception{
        var legacy=load();long version=legacy.get("version").getAsLong();
        var project=legacy.getAsJsonArray("projects").get(0).getAsJsonObject();
        var node=JsonParser.parseString("""
            {"status":"COMPLETED","output":"历史目标原件","draft":"历史目标原件","revision":3,"dependencies":{}}
            """).getAsJsonObject();
        project.getAsJsonObject("nodes").add("N01",node);
        project.getAsJsonObject("nodes").getAsJsonObject("N02").getAsJsonObject("dependencies").addProperty("N01",3);
        legacy.addProperty("version",version+1);assertEquals(1,mapper.updateWorkspace("local",legacy.toString(),version));
        var migrated=load();assertEquals(version+2,migrated.get("version").getAsLong());
        var result=migrated.getAsJsonArray("projects").get(0).getAsJsonObject();
        assertEquals("历史目标原件",result.getAsJsonObject("archivedNodes").getAsJsonObject("N01").get("output").getAsString());
        assertFalse(result.getAsJsonObject("nodes").has("N01"));assertEquals(migrated,load());
    }
    @Test void catalogAndHealthDoNotPretendExternalExecution()throws Exception{
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/catalog")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertEquals(49,JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("nodes").size());
        var health=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/health")).GET().build(),HttpResponse.BodyHandlers.ofString());assertFalse(JsonParser.parseString(health.body()).getAsJsonObject().getAsJsonObject("data").get("externalExecutionEnabled").getAsBoolean());
    }
}
