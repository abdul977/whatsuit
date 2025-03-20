package com.example.whatsuit.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AutoReplyRuleDao {
    @Insert
    long insert(AutoReplyRule rule);

    @Update
    void update(AutoReplyRule rule);

    @Delete
    void delete(AutoReplyRule rule);

    @Query("SELECT * FROM auto_reply_rules " +
           "WHERE packageName = :packageName " +
           "AND identifier = :identifier " +
           "AND identifierType = :identifierType " +
           "LIMIT 1")
    AutoReplyRule findRule(String packageName, String identifier, String identifierType);

    @Query("SELECT EXISTS(SELECT 1 FROM auto_reply_rules " +
           "WHERE packageName = :packageName " +
           "AND identifier = :identifier " +
           "AND identifierType = :identifierType " +
           "AND disabled = 1 " +
           "LIMIT 1)")
    boolean isAutoReplyDisabled(String packageName, String identifier, String identifierType);

    @Query("SELECT * FROM auto_reply_rules WHERE packageName = :packageName")
    List<AutoReplyRule> getRulesForApp(String packageName);

    @Query("UPDATE auto_reply_rules " +
           "SET disabled = :disabled " +
           "WHERE packageName = :packageName " +
           "AND identifier = :identifier " +
           "AND identifierType = :identifierType")
    void updateAutoReplyDisabled(String packageName, String identifier, String identifierType, boolean disabled);

    @Query("DELETE FROM auto_reply_rules WHERE packageName = :packageName")
    void deleteAllForApp(String packageName);
}
