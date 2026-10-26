package cn.creator.workbench.service;

import cn.creator.workbench.api.WorkspaceCommand;
import cn.creator.workbench.persistence.WorkspaceMapper;
import com.google.gson.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** 单人工作区聚合：命令校验、版本控制、人工产物与拍板均在服务端再次检查。 */
@Service
public class WorkspaceService {
    private final WorkspaceMapper mapper;
    private final JsonObject catalog;
    private final String initial;
    private final Map<String,JsonObject> definitions=new LinkedHashMap<>();
    public WorkspaceService(WorkspaceMapper mapper) throws IOException {
        this.mapper=mapper;
        try(var input=new ClassPathResource("catalog.json").getInputStream()){catalog=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
        try(var input=new ClassPathResource("initial-state.json").getInputStream()){initial=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        catalog.getAsJsonArray("nodes").forEach(n->definitions.put(n.getAsJsonObject().get("id").getAsString(),n.getAsJsonObject()));
    }
    @Transactional
    public synchronized JsonObject load(){
        var record=mapper.findWorkspace("local");
        if(record==null){mapper.insertWorkspace("local",initial);return JsonParser.parseString(initial).getAsJsonObject();}
        var state=JsonParser.parseString(record.getPayload()).getAsJsonObject();
        if(archiveRetiredNode(state)){
            state.addProperty("version",record.getRevision()+1);
            if(mapper.updateWorkspace("local",state.toString(),record.getRevision())!=1)throw new BusinessException(409,"工作区迁移冲突，请刷新后重试。");
        }
        return state;
    }
    /** N01 已退役：保存历史产物与绑定，保留其余节点编号，迁移可重复执行。 */
    static boolean archiveRetiredNode(JsonObject state){
        boolean changed=false;
        for(var item:state.getAsJsonArray("projects")){
            var project=item.getAsJsonObject();var nodes=project.getAsJsonObject("nodes");
            if(nodes.has("N01")){ensureObject(project,"archivedNodes").add("N01",nodes.remove("N01"));changed=true;}
            var bindings=project.getAsJsonObject("nodeBindings");
            if(bindings.has("N01")){ensureObject(project,"archivedNodeBindings").add("N01",bindings.remove("N01"));changed=true;}
            for(var node:nodes.entrySet())if(node.getValue().getAsJsonObject().getAsJsonObject("dependencies").remove("N01")!=null)changed=true;
        }
        return changed;
    }
    public JsonObject catalog(){return catalog.deepCopy();}
    public void requireExecutable(JsonObject project,String nodeId){var reasons=blocking(project,definition(nodeId));if(!reasons.isEmpty())throw new BusinessException(409,reasons.getFirst());}
    @Transactional
    public synchronized JsonObject execute(WorkspaceCommand command){
        var state=load();long version=state.get("version").getAsLong();if(command.expectedVersion()!=version)throw new BusinessException(409,"工作区已在其他窗口更新，请刷新后重试。");
        var p=command.payload();
        switch(command.type()){
            case "CREATE_PROJECT" -> createProject(state,p);
            case "SAVE_NODE" -> saveNode(state,p);
            case "SKIP_NODE" -> skipNode(state,p);
            case "APPROVE_GATE" -> approve(state,p);
            case "SAVE_MODEL" -> saveModel(state,p);
            case "DELETE_MODEL" -> deleteModel(state,p);
            case "SET_BINDING" -> bind(state,p);
            case "SET_DEFAULT_MODEL" -> bindDefault(state,p);
            case "DELETE_TASK", "RESTORE_TASK" -> updateTaskVisibility(state,p,command.type().equals("DELETE_TASK"));
            case "ADD_IDEA" -> {var idea=new JsonObject();idea.addProperty("id",id("idea"));idea.addProperty("text",required(p,"text",2000));idea.addProperty("createdAt",now());prepend(state,"ideas",idea);}
            case "SAVE_PROFILE" -> {var profile=p.getAsJsonObject("profile");if(profile==null||profile.toString().length()>50000)throw bad("档案内容无效或过长");state.add("profile",profile.deepCopy());}
            case "ADD_ASSET" -> {var asset=p.getAsJsonObject("asset");if(asset==null)throw bad("缺少素材信息");String fileRef=required(asset,"fileRef",150);if(!fileRef.matches("/api/v1/files/[a-zA-Z0-9_-]+"))throw bad("只接受本机已上传文件引用");String fileId=fileRef.substring(fileRef.lastIndexOf('/')+1);if(mapper.findFile(fileId)==null)throw bad("素材原件不存在");prepend(state,"assets",asset.deepCopy());}
            case "ADD_LEDGER" -> {var amount=p.get("amount").getAsBigDecimal();if(amount.signum()<0||amount.doubleValue()>1e12)throw bad("金额无效");String kind=required(p,"kind",10);if(!Set.of("income","cost").contains(kind))throw bad("账目类型无效");var entry=new JsonObject();entry.addProperty("id",id("entry"));entry.addProperty("label",required(p,"label",200));entry.addProperty("amount",amount);entry.addProperty("kind",kind);entry.addProperty("createdAt",now());prepend(state,"ledger",entry);}
            case "PLAN_PROJECT" -> plan(state,p);
            default -> throw bad("不支持的操作");
        }
        state.addProperty("version",version+1);
        if(mapper.updateWorkspace("local",state.toString(),version)!=1)throw new BusinessException(409,"保存冲突，请刷新后重试。");
        return state;
    }
    private void createProject(JsonObject s,JsonObject data){
        var p=new JsonObject();String projectId=data.has("id")?required(data,"id",90):id("project");if(!projectId.matches("[a-zA-Z0-9_-]+"))throw bad("作品 ID 无效");if(find(s.getAsJsonArray("projects"),projectId)!=null)throw bad("作品 ID 已存在");
        p.addProperty("id",projectId);p.addProperty("title",required(data,"title",100));p.addProperty("brief",optional(data,"brief",10000));String template=data.has("template")?data.get("template").getAsString():"SCREENCAST";if(!Set.of("SCREENCAST","REAL_PERSON","AI_STORY").contains(template))throw bad("制作路线无效");p.addProperty("template",template);
        var platforms=data.has("platforms")?data.getAsJsonArray("platforms"):JsonParser.parseString("[\"DOUYIN\",\"XIAOHONGSHU\"]").getAsJsonArray();for(var platform:platforms)if(!Set.of("DOUYIN","XIAOHONGSHU").contains(platform.getAsString()))throw bad("平台无效");p.add("platforms",platforms.deepCopy());
        double budget=data.has("budget")?data.get("budget").getAsDouble():100;if(!Double.isFinite(budget)||budget<0||budget>1_000_000)throw bad("预算无效");p.addProperty("budget",budget);p.addProperty("sample",false);p.addProperty("poster","blue");p.addProperty("createdAt",now());
        var nodes=new JsonObject();for(String key:definitions.keySet()){var n=new JsonObject();n.addProperty("status","NOT_STARTED");n.addProperty("draft","");n.addProperty("output","");n.addProperty("revision",0);n.add("dependencies",new JsonObject());nodes.add(key,n);}p.add("nodes",nodes);p.add("approvals",new JsonObject());p.add("bindings",new JsonObject());p.add("nodeBindings",new JsonObject());prepend(s,"projects",p);
    }
    private void saveNode(JsonObject s,JsonObject p){
        var project=project(s,p);String key=required(p,"nodeId",5);var definition=definition(key);var node=project.getAsJsonObject("nodes").getAsJsonObject(key);String text=optional(p,"text",200000);boolean complete=bool(p,"complete");
        if(complete){var reasons=blocking(project,definition);if(!reasons.isEmpty())throw new BusinessException(409,reasons.getFirst());if(text.isBlank())throw bad("请先填写或导入本节点产物");}
        node.addProperty("draft",text);if(complete&&node.get("status").getAsString().equals("COMPLETED")&&node.get("output").getAsString().equals(text))return;if(complete){node.addProperty("output",text);node.addProperty("status","COMPLETED");node.addProperty("revision",node.get("revision").getAsInt()+1);var dependencies=new JsonObject();for(var dependency:definition.getAsJsonArray("dependsOn")){String dep=dependency.getAsString();dependencies.addProperty(dep,project.getAsJsonObject("nodes").getAsJsonObject(dep).get("revision").getAsInt());}node.add("dependencies",dependencies);invalidate(project,key);}else if(node.get("status").getAsString().equals("NOT_STARTED"))node.addProperty("status","DRAFT");node.addProperty("updatedAt",now());
    }
    private void invalidate(JsonObject project,String key){var changed=new HashSet<String>();changed.add(key);boolean again=true;while(again){again=false;for(var e:project.getAsJsonObject("nodes").entrySet()){var n=e.getValue().getAsJsonObject();if(changed.contains(e.getKey())||!n.get("status").getAsString().equals("COMPLETED"))continue;boolean affected=n.getAsJsonObject("dependencies").keySet().stream().anyMatch(changed::contains);if(affected){n.addProperty("status","NEEDS_REVALIDATION");changed.add(e.getKey());again=true;}}}}
    private void skipNode(JsonObject s,JsonObject p){String key=required(p,"nodeId",5);if(!Set.of("N25","N26").contains(key))throw bad("该节点不能直接跳过，请提交替代产物");var project=project(s,p);var node=project.getAsJsonObject("nodes").getAsJsonObject(key);node.addProperty("status","SKIPPED");node.addProperty("skipReason",required(p,"reason",1000));node.addProperty("revision",node.get("revision").getAsInt()+1);invalidate(project,key);}
    private void approve(JsonObject s,JsonObject p){var project=project(s,p);var gate=gate(required(p,"gateId",5));for(var after:gate.getAsJsonArray("after"))if(!project.getAsJsonObject("nodes").getAsJsonObject(after.getAsString()).get("status").getAsString().equals("COMPLETED"))throw new BusinessException(409,"请先完成对应阶段的审阅材料");for(var watched:gate.getAsJsonArray("watches")){var n=project.getAsJsonObject("nodes").getAsJsonObject(watched.getAsString());if(n.get("status").getAsString().equals("COMPLETED")&&!n.get("draft").getAsString().equals(n.get("output").getAsString()))throw new BusinessException(409,"审阅材料含未采用草稿，请先标记完成或恢复当前产物");}String fp=fingerprint(project,gate);if(!fp.equals(required(p,"fingerprint",20000)))throw new BusinessException(409,"内容版本已变化，请刷新后重新审阅");var approval=new JsonObject();approval.addProperty("id",gate.get("id").getAsString());approval.addProperty("fingerprint",fp);approval.addProperty("decision","APPROVED");approval.addProperty("at",now());project.getAsJsonObject("approvals").add(gate.get("id").getAsString(),approval);}
    private void saveModel(JsonObject s,JsonObject p){var model=p.getAsJsonObject("model");if(model==null)throw bad("缺少模型信息");required(model,"id",90);required(model,"name",80);required(model,"remoteModelId",200);String protocol=required(model,"protocol",80);if(!Set.of("CHAT_COMPLETIONS_COMPATIBLE","RESPONSES","ANTHROPIC_MESSAGES","GEMINI_GENERATE_CONTENT","CUSTOM_HTTP","LOCAL_GATEWAY").contains(protocol))throw bad("协议无效");
        URI uri;try{uri=URI.create(required(model,"baseUrl",1000));}catch(Exception e){throw bad("模型地址无效");}if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw bad("模型地址须为不含凭据的 HTTP(S) 地址");if(!required(model,"credentialRef",120).matches("[A-Z][A-Z0-9_]*"))throw bad("认证引用须为环境变量名称，请勿输入密钥");var caps=model.getAsJsonArray("capabilities");if(caps==null||caps.isEmpty())throw bad("至少选择一种能力");for(var c:caps)if(find(catalog.getAsJsonArray("capabilities"),c.getAsString())==null)throw bad("未知模型能力");if(model.get("parameters")==null||!model.get("parameters").isJsonObject())throw bad("参数必须为 JSON 对象");if(hasSensitiveKey(model.getAsJsonObject("parameters")))throw bad("参数中不能保存密钥、密码或认证头，请使用环境变量引用");if(uri.getQuery()!=null||uri.getFragment()!=null)throw bad("服务地址不能包含查询凭据或片段");model=model.deepCopy();model.addProperty("verified",false);model.addProperty("enabled",false);replace(s,"models",model);}
    private void deleteModel(JsonObject s,JsonObject p){String key=required(p,"id",90);var array=s.getAsJsonArray("models");for(int i=array.size()-1;i>=0;i--)if(array.get(i).getAsJsonObject().get("id").getAsString().equals(key))array.remove(i);clearBindings(s.getAsJsonObject("globalBindings"),key);for(var e:s.getAsJsonObject("agentBindings").entrySet())clearBindings(e.getValue().getAsJsonObject(),key);for(var pr:s.getAsJsonArray("projects")){var project=pr.getAsJsonObject();clearBindings(project.getAsJsonObject("bindings"),key);for(var e:project.getAsJsonObject("nodeBindings").entrySet())clearBindings(e.getValue().getAsJsonObject(),key);}}
    private void clearBindings(JsonObject map,String id){new ArrayList<>(map.keySet()).forEach(k->{if(map.get(k).getAsString().equals(id))map.remove(k);});}
    private void bindDefault(JsonObject state,JsonObject payload){
        String modelId=optional(payload,"modelId",90);
        if(!modelId.isEmpty()&&find(state.getAsJsonArray("models"),modelId)==null)throw bad("所选模型不存在");
        JsonObject target;
        switch(required(payload,"scope",20)){
            case "GLOBAL" -> target=state.getAsJsonObject("globalBindings");
            case "AGENT" -> {String role=required(payload,"agentId",80);if(find(catalog.getAsJsonArray("agents"),role)==null)throw bad("角色不存在");target=ensureObject(state.getAsJsonObject("agentBindings"),role);}
            case "PROJECT" -> target=project(state,payload).getAsJsonObject("bindings");
            case "NODE" -> {String node=required(payload,"nodeId",5);definition(node);target=ensureObject(project(state,payload).getAsJsonObject("nodeBindings"),node);}
            default -> throw bad("模型配置范围无效");
        }
        if(modelId.isEmpty())target.remove("$default");else target.addProperty("$default",modelId);
    }
    private void updateTaskVisibility(JsonObject state,JsonObject payload,boolean deleted){
        var task=find(state.getAsJsonArray("tasks"),required(payload,"id",90));
        if(task==null)throw new BusinessException(404,"任务不存在");
        if(deleted){if(!task.has("deletedAt"))task.addProperty("deletedAt",now());}else task.remove("deletedAt");
    }
    private void bind(JsonObject s,JsonObject p){String cap=required(p,"capability",80),model=optional(p,"modelId",90);if(find(catalog.getAsJsonArray("capabilities"),cap)==null)throw bad("能力无效");if(!model.isEmpty()){var m=find(s.getAsJsonArray("models"),model);if(m==null||!contains(m.getAsJsonArray("capabilities"),cap))throw bad("所选模型不支持该能力");}JsonObject target;switch(required(p,"scope",20)){case "GLOBAL"->target=s.getAsJsonObject("globalBindings");case "AGENT"->{String agent=required(p,"agentId",80);if(find(catalog.getAsJsonArray("agents"),agent)==null)throw bad("角色无效");target=ensureObject(s.getAsJsonObject("agentBindings"),agent);}case "PROJECT"->target=project(s,p).getAsJsonObject("bindings");case "NODE"->{String node=required(p,"nodeId",5);definition(node);target=ensureObject(project(s,p).getAsJsonObject("nodeBindings"),node);}default->throw bad("绑定范围无效");}if(model.isEmpty())target.remove(cap);else target.addProperty(cap,model);}
    private void plan(JsonObject s,JsonObject p){var project=project(s,p);String detail="当前没有可自动推进的节点，请检查待审阅或依赖。";String nextId=null;for(var n:definitions.values()){var status=project.getAsJsonObject("nodes").getAsJsonObject(n.get("id").getAsString()).get("status").getAsString();if(!Set.of("COMPLETED","SKIPPED").contains(status)&&blocking(project,n).isEmpty()){nextId=n.get("id").getAsString();detail=nextId+" "+n.get("name").getAsString()+" 已具备业务前提。"+(bool(p,"execute")?"当前版本尚未接入自动执行器，请手工提交产物；没有调用模型或产生费用。":"可继续填写产物或配置该节点模型。");break;}}
        for(var g:catalog.getAsJsonArray("gates")){var gate=g.getAsJsonObject();boolean ready=true;for(var after:gate.getAsJsonArray("after"))ready&=project.getAsJsonObject("nodes").getAsJsonObject(after.getAsString()).get("status").getAsString().equals("COMPLETED");if(ready&&!approved(project,gate)){detail="下一步需要人工拍板："+gate.get("name").getAsString()+"。";break;}}
        var task=new JsonObject();task.addProperty("id",id("task"));task.addProperty("projectId",project.get("id").getAsString());if(nextId!=null)task.addProperty("nodeId",nextId);task.addProperty("title",bool(p,"execute")?"自动推进检查":"制作计划检查");task.addProperty("status",bool(p,"execute")?"BLOCKED":"COMPLETED");task.addProperty("detail",detail);task.addProperty("createdAt",now());prepend(s,"tasks",task);}
    private List<String> blocking(JsonObject project,JsonObject node){var reasons=new ArrayList<String>();for(var dep:node.getAsJsonArray("dependsOn")){String key=dep.getAsString(),status=project.getAsJsonObject("nodes").getAsJsonObject(key).get("status").getAsString();if(!Set.of("COMPLETED","SKIPPED").contains(status))reasons.add("等待 "+key+" "+definitions.get(key).get("name").getAsString());}for(var g:catalog.getAsJsonArray("gates")){var gate=g.getAsJsonObject();if(contains(gate.getAsJsonArray("blocks"),node.get("id").getAsString())&&!approved(project,gate))reasons.add("等待"+gate.get("name").getAsString()+"（"+gate.get("id").getAsString()+"）");}return reasons;}
    private boolean approved(JsonObject project,JsonObject gate){var a=project.getAsJsonObject("approvals").getAsJsonObject(gate.get("id").getAsString());return a!=null&&"APPROVED".equals(a.get("decision").getAsString())&&fingerprint(project,gate).equals(a.get("fingerprint").getAsString());}
    private String fingerprint(JsonObject project,JsonObject gate){var parts=new ArrayList<String>();project.getAsJsonArray("platforms").forEach(p->parts.add(p.getAsString()));StringBuilder b=new StringBuilder(project.get("id").getAsString()+"|"+String.join(",",parts)+"|");var versions=new ArrayList<String>();gate.getAsJsonArray("watches").forEach(k->{var n=project.getAsJsonObject("nodes").getAsJsonObject(k.getAsString());versions.add(k.getAsString()+"@"+n.get("revision").getAsInt()+":"+n.get("status").getAsString());});return b+String.join("|",versions);}
    private JsonObject project(JsonObject s,JsonObject p){var project=find(s.getAsJsonArray("projects"),required(p,"projectId",90));if(project==null)throw new BusinessException(404,"作品不存在");return project;}
    private JsonObject definition(String id){var node=definitions.get(id);if(node==null)throw bad("节点不存在");return node;}
    private JsonObject gate(String id){var found=find(catalog.getAsJsonArray("gates"),id);if(found==null)throw bad("确认节点不存在");return found;}
    private static JsonObject find(JsonArray list,String id){for(var e:list){var o=e.getAsJsonObject();if(o.get("id").getAsString().equals(id))return o;}return null;}
    private static boolean contains(JsonArray list,String value){for(var e:list)if(e.getAsString().equals(value))return true;return false;}
    private static JsonObject ensureObject(JsonObject obj,String key){if(!obj.has(key))obj.add(key,new JsonObject());return obj.getAsJsonObject(key);}
    private static void prepend(JsonObject s,String key,JsonObject value){var array=new JsonArray();array.add(value);s.getAsJsonArray(key).forEach(array::add);s.add(key,array);}
    private static void replace(JsonObject s,String key,JsonObject value){var arr=s.getAsJsonArray(key);for(int i=0;i<arr.size();i++)if(arr.get(i).getAsJsonObject().get("id").equals(value.get("id"))){arr.set(i,value);return;}arr.add(value);}
    private static boolean hasSensitiveKey(JsonElement element){if(element.isJsonObject()){for(var entry:element.getAsJsonObject().entrySet()){if(entry.getKey().matches("(?i).*(api[_-]?key|authorization|password|secret|access[_-]?token).*"))return true;if(hasSensitiveKey(entry.getValue()))return true;}}else if(element.isJsonArray()){for(var item:element.getAsJsonArray())if(hasSensitiveKey(item))return true;}return false;}
    private static String required(JsonObject p,String key,int max){String value=optional(p,key,max);if(value.isBlank())throw bad("请填写 "+key);return value.trim();}
    private static String optional(JsonObject p,String key,int max){String value=p.has(key)&&!p.get(key).isJsonNull()?p.get(key).getAsString():"";if(value.length()>max)throw bad(key+" 内容过长");return value;}
    private static boolean bool(JsonObject p,String key){return p.has(key)&&p.get(key).getAsBoolean();}
    private static String id(String prefix){return prefix+"_"+UUID.randomUUID();}
    private static String now(){return Instant.now().toString();}
    private static BusinessException bad(String message){return new BusinessException(400,message);}
}
