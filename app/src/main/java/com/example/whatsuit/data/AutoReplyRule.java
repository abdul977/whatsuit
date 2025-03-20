package com.example.whatsuit.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "auto_reply_rules",
    indices = {
        @Index(value = {"packageName", "identifier", "identifierType"}, unique = true)
    }
)
public class AutoReplyRule {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private String packageName;
    private String identifier;      // Phone number for WhatsApp, exact title for others
    private String identifierType;  // "PHONE_NUMBER" or "TITLE"
    private boolean disabled;
    private long createdAt;

    public static final String TYPE_PHONE_NUMBER = "PHONE_NUMBER";
    public static final String TYPE_TITLE = "TITLE";

    // Required no-args constructor for Room
    public AutoReplyRule() {
    }

    public AutoReplyRule(String packageName, String identifier, String identifierType, boolean disabled) {
        this.packageName = packageName;
        this.identifier = identifier;
        this.identifierType = identifierType;
        this.disabled = disabled;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifierType() {
        return identifierType;
    }

    public void setIdentifierType(String identifierType) {
        this.identifierType = identifierType;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AutoReplyRule that = (AutoReplyRule) o;

        if (id != that.id) return false;
        if (disabled != that.disabled) return false;
        if (!packageName.equals(that.packageName)) return false;
        if (!identifier.equals(that.identifier)) return false;
        return identifierType.equals(that.identifierType);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + packageName.hashCode();
        result = 31 * result + identifier.hashCode();
        result = 31 * result + identifierType.hashCode();
        result = 31 * result + (disabled ? 1 : 0);
        return result;
    }
}
