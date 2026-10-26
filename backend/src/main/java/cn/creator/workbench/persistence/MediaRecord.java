package cn.creator.workbench.persistence;

public class MediaRecord {
    private String id, originalName, storageName, mediaType, sha256;
    private long byteSize;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOriginalName(){return originalName;} public void setOriginalName(String v){originalName=v;}
    public String getStorageName(){return storageName;} public void setStorageName(String v){storageName=v;}
    public String getMediaType(){return mediaType;} public void setMediaType(String v){mediaType=v;}
    public String getSha256(){return sha256;} public void setSha256(String v){sha256=v;}
    public long getByteSize(){return byteSize;} public void setByteSize(long v){byteSize=v;}
}
