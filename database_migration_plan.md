# Database Migration Plan

## Current Situation

### Schema Mismatch Issue
The database is failing with error: "Expected identity hash: c918c613a9cbaf6a5535df0c21520b49, found: 0926fb41ac5becad4b60747038f53b30"

This is caused by a discrepancy between:
1. The template defined in Migration2To3.kt (original)
2. The template defined in PromptTemplate.kt (new)

### Current Database State
- Version: 7
- Has migrations: 1→2, 2→3, 3→4, 4→5, 5→6, 6→7
- Uses Room with schema export enabled
- Has proper configuration in build.gradle.kts

## Analysis

### Template Evolution
Original template (Migration2To3):
```
System: Generate a clear and concise response (maximum 50 words) that directly addresses the query.
Context: Previous conversation - {context}
User: {message}
Assistant: Provide a direct response under 50 words.
```

New template (PromptTemplate.kt):
```
System: You are a helpful messaging assistant that maintains conversation context and remembers user details.
Respond in a friendly, concise manner (maximum 50 words) while maintaining memory of previous conversations.

{context}

User: {message}
Assistant:
```

The new template is an improvement because it:
1. Better defines the assistant's role and personality
2. Emphasizes context maintenance
3. Has clearer formatting with proper spacing
4. Removes redundant instructions

## Migration Strategy

### Recommended Approach
1. Create Migration7To8 that:
   - Updates the default template text
   - Preserves existing template customizations
   - Updates schema version to 8

### Implementation Details
1. Create a new Kotlin migration file: Migration7To8.kt
2. Update AppDatabase version to 8
3. Add migration to database builder
4. Test migration path from version 1 through 8
5. Verify template updates don't affect existing conversations

### SQL Migration Script
```sql
UPDATE prompt_templates 
SET template = 'System: You are a helpful messaging assistant that maintains conversation context and remembers user details.
Respond in a friendly, concise manner (maximum 50 words) while maintaining memory of previous conversations.

{context}

User: {message}
Assistant:'
WHERE name = 'Default Concise Response' 
AND template = 'System: Generate a clear and concise response (maximum 50 words) that directly addresses the query.
Context: Previous conversation - {context}
User: {message}
Assistant: Provide a direct response under 50 words.';
```

## Testing Plan

### Pre-Migration Testing
1. Backup existing database
2. Verify current functionality
3. Document existing templates

### Migration Testing
1. Test upgrade path from version 1
2. Test upgrade path from version 7
3. Verify template updates
4. Check conversation history integrity

### Post-Migration Testing
1. Verify template functionality
2. Test conversation context
3. Validate response formatting
4. Check performance impact

## Rollback Plan

### Rollback Steps
1. Keep backup of pre-migration database
2. Prepare downgrade migration if needed
3. Test downgrade path
4. Document rollback procedures

## Next Steps
1. Review this migration plan
2. Implement in Code mode after approval
3. Execute testing plan
4. Monitor post-deployment metrics