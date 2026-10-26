package cn.creator.workbench.persistence;

public class WorkspaceRecord {
    private String id;
    private String payload;
    private long revision;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
}
