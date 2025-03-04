# analyze_code Tool

## Description
Request to analyze code files and relationships. This tool examines code structure, dependencies, and patterns within specified files or directories.

## Parameters
- path: (required) The path of the file or directory to analyze (relative to current working directory)
- scope: (required) The type of analysis to perform ("structure", "dependencies", "patterns", or "all")
- include_pattern: (optional) File pattern to include (e.g., "*.java", "*.kt")

## Usage
```xml
<analyze_code>
<path>app/src/main/java/com/example/whatsuit/data</path>
<scope>all</scope>
<include_pattern>*.java</include_pattern>
</analyze_code>
```

Example Response:
```json
{
  "files_analyzed": ["AppDatabase.java", "NotificationDao.java"],
  "structure": {
    "classes": ["AppDatabase", "NotificationDao"],
    "interfaces": ["NotificationDao"],
    "dependencies": ["Room", "LiveData"]
  },
  "relationships": {
    "AppDatabase": ["NotificationDao", "GeminiDao"],
    "NotificationDao": ["NotificationEntity"]
  },
  "patterns": {
    "architectural": ["Repository Pattern", "DAO Pattern"],
    "queries": ["Smart Grouping", "Time-based Filtering"]
  }
}
```

## Notes
- The tool analyzes code without modifying it
- Works with any programming language
- Can analyze single files or entire directories
- Provides insights about code structure and relationships
- Helps understand dependencies and patterns before making changes

Like other tools:
- Returns analysis results immediately
- Can be used by any mode
- Works alongside read_file, search_files etc.
- Maintains consistent format with other tools