package cn.creator.workbench.persistence;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AgentRunMapper {
    @Select("SELECT * FROM agent_run WHERE id=#{id}") AgentRunRecord find(String id);
    @Select("SELECT * FROM agent_run ORDER BY created_at DESC LIMIT 200") List<AgentRunRecord> list();
    @Insert("INSERT INTO agent_run(id,project_id,node_id,payload,input_fingerprint,state,created_at) VALUES(#{id},#{projectId},#{nodeId},#{payload},#{inputFingerprint},#{state},#{createdAt})")
    void insert(AgentRunRecord record);
    @Update("UPDATE agent_run SET state=#{state} WHERE id=#{id}") void state(@Param("id") String id,@Param("state") String state);
    @Update("UPDATE agent_run SET deleted_at=#{at} WHERE id=#{id}") void visibility(@Param("id") String id,@Param("at") String at);
}
