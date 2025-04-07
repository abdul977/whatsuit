-- Add search indexes for global search functionality
-- Migration version: 16

-- Add index for conversation history search
CREATE INDEX IF NOT EXISTS idx_conversation_history_search 
ON conversation_history(message, response);

-- Add index for prompt templates search
CREATE INDEX IF NOT EXISTS idx_prompt_templates_search 
ON prompt_templates(name, template);

-- Create view for searchable conversations
DROP VIEW IF EXISTS v_searchable_conversations;
CREATE VIEW v_searchable_conversations AS
SELECT 
    ch.id,
    'conversation' as result_type,
    ch.message as search_title,
    ch.response as search_content,
    ch.timestamp as search_timestamp,
    n.id as notification_id
FROM conversation_history ch
LEFT JOIN notifications n ON ch.notification_id = n.id;

-- Create view for searchable templates
DROP VIEW IF EXISTS v_searchable_templates;
CREATE VIEW v_searchable_templates AS
SELECT 
    id,
    'template' as result_type,
    name as search_title,
    template as search_content,
    created_at as search_timestamp,
    is_active
FROM prompt_templates;

-- Create view for combined searchable content
DROP VIEW IF EXISTS v_global_search;
CREATE VIEW v_global_search AS
SELECT 
    id,
    result_type,
    search_title,
    search_content,
    search_timestamp,
    package_name,
    app_name,
    NULL as notification_id,
    NULL as is_active
FROM (
    SELECT 
        id,
        'notification' as result_type,
        title as search_title,
        content as search_content,
        timestamp as search_timestamp,
        package_name,
        app_name,
        NULL as notification_id,
        NULL as is_active
    FROM notifications
    
    UNION ALL
    
    SELECT 
        conv.id,
        conv.result_type,
        conv.search_title,
        conv.search_content,
        conv.search_timestamp,
        NULL as package_name,
        NULL as app_name,
        conv.notification_id,
        NULL as is_active
    FROM v_searchable_conversations conv
    
    UNION ALL
    
    SELECT 
        tmpl.id,
        tmpl.result_type,
        tmpl.search_title,
        tmpl.search_content,
        tmpl.search_timestamp,
        NULL as package_name,
        NULL as app_name,
        NULL as notification_id,
        tmpl.is_active
    FROM v_searchable_templates tmpl
);

-- Create indexes on the most commonly searched columns
CREATE INDEX IF NOT EXISTS idx_global_search_title 
ON notifications(title);

CREATE INDEX IF NOT EXISTS idx_global_search_content 
ON notifications(content);

CREATE INDEX IF NOT EXISTS idx_global_search_timestamp 
ON notifications(timestamp DESC);