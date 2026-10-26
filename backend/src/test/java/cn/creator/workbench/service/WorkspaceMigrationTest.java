package cn.creator.workbench.service;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMigrationTest {
    @Test void retiredNodeIsArchivedWithoutLosingContentAndMigrationIsIdempotent(){
        var state=JsonParser.parseString("""
            {"projects":[{"nodes":{"N01":{"output":"历史目标","revision":3,"dependencies":{}},"N02":{"output":"能力清单","dependencies":{"N01":3}}},"nodeBindings":{"N01":{"$default":"old-model"}}}]}
            """).getAsJsonObject();
        assertTrue(WorkspaceService.archiveRetiredNode(state));
        var project=state.getAsJsonArray("projects").get(0).getAsJsonObject();
        assertFalse(project.getAsJsonObject("nodes").has("N01"));
        assertEquals("历史目标",project.getAsJsonObject("archivedNodes").getAsJsonObject("N01").get("output").getAsString());
        assertEquals("old-model",project.getAsJsonObject("archivedNodeBindings").getAsJsonObject("N01").get("$default").getAsString());
        assertFalse(project.getAsJsonObject("nodes").getAsJsonObject("N02").getAsJsonObject("dependencies").has("N01"));
        assertFalse(WorkspaceService.archiveRetiredNode(state));
    }
}
