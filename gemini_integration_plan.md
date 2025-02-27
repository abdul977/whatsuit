# Personalized Gemini API Integration Plan

## 1. Database Enhancements

### New Tables Required
```sql
-- Store API configuration (single global key)
CREATE TABLE gemini_config (
    id INTEGER PRIMARY KEY CHECK (id = 1), -- Ensures single row
    api_key TEXT NOT NULL,
    model_name TEXT NOT NULL DEFAULT 'gemini-1.5-flash',
    max_history_per_thread INTEGER DEFAULT 10,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Store conversation history
CREATE TABLE conversation_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    notification_id INTEGER,
    message TEXT NOT NULL,
    response TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (notification_id) REFERENCES notifications(id)
);

-- Store prompt templates
CREATE TABLE prompt_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    template TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 2. UI Components

### API Configuration Screen
- Single API key input field
- History limit configuration
- Test connection button
- Save configuration button

### Conversation Settings
- Max history per thread slider
- Clear history option
- Export/backup options

### Conversation History View
- Timeline of past interactions
- Search functionality
- Filter by notification
- Clear individual thread history

## 3. Service Layer Updates

### Enhanced GeminiService
- Global API key configuration
- Configurable history limit
- Concise response generation (50-word limit)
- Memory-aware responses

### Key Components:
1. ConversationManager
   - Track conversation state
   - Enforce history limits
   - Auto-cleanup old conversations

2. PromptManager
   - Enforce 50-word limit
   - Concise response formatting
   - Context-aware prompting

3. ConfigurationManager
   - Global API key management
   - History limit settings
   - System preferences

## 4. Default Prompt Template

```
System: You are a concise assistant. Keep responses under 50 words, direct and straight to the point.
Context: Previous messages - {context}
User: {message}
Assistant: Please provide a clear, direct response under 50 words.
```

## 5. Implementation Phases

### Phase 1: Foundation
1. Database migrations
2. Basic API configuration
3. History limit settings

### Phase 2: Core Features
1. Conversation tracking
2. Response length limiting
3. History management

### Phase 3: UI Implementation
1. Settings screen
2. History view
3. Configuration options

### Phase 4: Testing & Polish
1. Response quality verification
2. Performance optimization
3. UI/UX improvements

## 6. Migration Strategy

1. Create new database tables
2. Preserve existing notification data
3. Set default history limits
4. Initialize prompt templates

## 7. Testing Strategy

1. Unit tests for word limit enforcement
2. History limit validation
3. API interaction tests
4. UI responsiveness testing
5. Migration verification

## Next Steps

1. Create database migrations
2. Implement API configuration UI
3. Add conversation history management
4. Develop concise prompt system
5. Build settings interface