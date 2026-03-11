package com.infocaption.dashboard.model;

import java.io.Serializable;
import java.sql.Timestamp;

public class Module implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private Integer ownerUserId;
    private String moduleType;
    private String name;
    private String icon;
    private String description;
    private String category;
    private String entryFile;
    private String directoryName;
    private String badge;
    private String version;
    private String aiSpecText;
    private boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Module() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Integer ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getModuleType() { return moduleType; }
    public void setModuleType(String moduleType) { this.moduleType = moduleType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getEntryFile() { return entryFile; }
    public void setEntryFile(String entryFile) { this.entryFile = entryFile; }

    public String getDirectoryName() { return directoryName; }
    public void setDirectoryName(String directoryName) { this.directoryName = directoryName; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getAiSpecText() { return aiSpecText; }
    public void setAiSpecText(String aiSpecText) { this.aiSpecText = aiSpecText; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
