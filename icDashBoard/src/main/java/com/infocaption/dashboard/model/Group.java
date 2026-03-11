package com.infocaption.dashboard.model;

import java.io.Serializable;
import java.sql.Timestamp;

public class Group implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private String icon;
    private String description;
    private boolean hidden;
    private String ssoDepartment;
    private Integer createdBy;
    private Timestamp createdAt;

    // Transient fields (not stored in DB, computed at query time)
    private int memberCount;
    private boolean member;    // Is current user a member?

    public Group() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public String getSsoDepartment() { return ssoDepartment; }
    public void setSsoDepartment(String ssoDepartment) { this.ssoDepartment = ssoDepartment; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public boolean isMember() { return member; }
    public void setMember(boolean member) { this.member = member; }
}
