# Gemini Integration Technical Specification

## Database Migration (Version 2 to 3)

```sql
-- Create gemini_config table
CREATE TABLE gemini_config (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    api_key TEXT NOT NULL,
    model_name TEXT NOT NULL DEFAULT 'gemini-1.5-flash',
    max_history_per_thread INTEGER DEFAULT 10,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create conversation_history table
CREATE TABLE conversation_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    notification_id INTEGER,
    message TEXT NOT NULL,
    response TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (notification_id) REFERENCES notifications(id)
);

-- Create prompt_templates table
CREATE TABLE prompt_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    template TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## New Class Specifications

### 1. GeminiConfig Entity
```kotlin
@Entity(tableName = "gemini_config")
data class GeminiConfig(
    @PrimaryKey
    val id: Int = 1,
    val apiKey: String,
    val modelName: String = "gemini-1.5-flash",
    val maxHistoryPerThread: Int = 10,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 2. ConversationHistory Entity
```kotlin
@Entity(tableName = "conversation_history")
data class ConversationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notificationId: Long,
    val message: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

### 3. PromptTemplate Entity
```kotlin
@Entity(tableName = "prompt_templates")
data class PromptTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val template: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 4. GeminiDao Interface
```kotlin
@Dao
interface GeminiDao {
    @Query("SELECT * FROM gemini_config WHERE id = 1")
    suspend fun getConfig(): GeminiConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: GeminiConfig)

    @Query("SELECT * FROM conversation_history WHERE notification_id = :notificationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getConversationHistory(notificationId: Long, limit: Int): List<ConversationHistory>

    @Insert
    suspend fun insertConversation(history: ConversationHistory)

    @Query("SELECT * FROM prompt_templates WHERE is_active = 1")
    suspend fun getActiveTemplate(): PromptTemplate?
}
```

### 5. Enhanced GeminiService
```kotlin
class GeminiService(
    private val context: Context,
    private val geminiDao: GeminiDao
) {
    private var generativeModel: GenerativeModel? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    suspend fun initialize() {
        val config = geminiDao.getConfig()
        if (config != null) {
            generativeModel = GenerativeModel(
                modelName = config.modelName,
                apiKey = config.apiKey
            )
        }
    }

    suspend fun generateReply(
        notificationId: Long,
        message: String,
        callback: ResponseCallback
    ) {
        val config = geminiDao.getConfig() ?: return
        val history = geminiDao.getConversationHistory(
            notificationId,
            config.maxHistoryPerThread
        )
        val template = geminiDao.getActiveTemplate()
        
        // Generate context-aware prompt
        val contextualPrompt = buildPrompt(message, history, template)
        
        // Generate response with 50-word limit
        // Implementation details in next section
    }
}
```

## UI Components Required

1. `GeminiConfigActivity.kt` - API key and settings management
2. `activity_gemini_config.xml` - Configuration UI layout
3. `ConversationHistoryAdapter.kt` - Display conversation history
4. `item_conversation.xml` - Conversation item layout

## Next Implementation Steps

1. Switch to Code mode to implement database migrations
2. Create necessary entity classes and DAOs
3. Enhance GeminiService with new functionality
4. Implement UI components
5. Add configuration screens
6. Test and validate implementation

Would you like to proceed with switching to Code mode to begin the implementation?