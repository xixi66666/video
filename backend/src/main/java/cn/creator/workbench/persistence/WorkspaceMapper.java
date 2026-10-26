package cn.creator.workbench.persistence;

import org.apache.ibatis.annotations.Param;

public interface WorkspaceMapper {
    WorkspaceRecord findWorkspace(@Param("id") String id);
    int insertWorkspace(@Param("id") String id, @Param("payload") String payload);
    int updateWorkspace(@Param("id") String id, @Param("payload") String payload, @Param("expectedRevision") long expectedRevision);
    int insertFile(MediaRecord record);
    MediaRecord findFile(@Param("id") String id);
}
