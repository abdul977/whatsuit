# Gemini API Integration Usage Guide

## Overview
The Gemini API integration now supports personalized responses with conversation memory and customizable prompt templates. This ensures more contextual and consistent responses across notification threads.

## Features

### 1. API Configuration
- Access via:
  * Menu -> Gemini Configuration
  * FAB Menu -> Gemini Configuration
- Set your Gemini API key
- Configure maximum conversation history per thread

### 2. Prompt Templates
- Create and manage response templates
- Support for variables:
  * `{context}` - Previous conversation history
  * `{message}` - Current message
- Enforced 50-word limit for concise responses
- Multiple templates with one active at a time

### 3. Conversation Memory
- Maintains conversation history per notification thread
- Configurable history limit
- Automatic cleanup of old conversations
- Context-aware responses based on previous interactions

### 4. Response Generation
- Smart context incorporation
- Word limit enforcement
- Streaming response display
- Automatic memory management

## Setup Instructions

1. Launch the app and open Gemini Configuration
2. Enter your Gemini API key and test the connection
3. Customize the maximum history limit if desired
4. Create or modify prompt templates as needed
5. Enable auto-reply for desired apps

## Best Practices

1. Start with the default prompt template
2. Adjust history limit based on your needs
3. Regularly review and clean up old conversations
4. Test templates before setting them as active

## Technical Notes

- Uses Room database for persistent storage
- Automatic migration handles setup
- Thread-safe conversation management
- Efficient memory usage with configurable limits
- Integration with existing notification system