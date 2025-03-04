package com.example.whatsuit.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface KeywordActionDao {
    @Query("SELECT * FROM keyword_actions WHERE :message LIKE '%' || keyword || '%' AND isEnabled = 1 LIMIT 1")
    KeywordActionEntity findMatchingKeyword(String message);
    
    @Insert
    long insert(KeywordActionEntity action);
    
    @Update
    void update(KeywordActionEntity action);
    
    @Delete
    void delete(KeywordActionEntity action);
    
    @Query("SELECT * FROM keyword_actions ORDER BY createdAt DESC")
    LiveData<List<KeywordActionEntity>> getAllKeywordActions();
    
    @Query("SELECT * FROM keyword_actions WHERE isEnabled = 1 ORDER BY createdAt DESC")
    LiveData<List<KeywordActionEntity>> getActiveKeywordActions();
    
    @Query("UPDATE keyword_actions SET isEnabled = :enabled WHERE id = :id")
    void updateEnabled(long id, boolean enabled);
}
