# Whatsuit App

![Whatsuit Logo](app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png)

Whatsuit is an intelligent Android application that enhances your messaging experience by providing AI-powered responses to notifications. Using Google's Gemini API, Whatsuit maintains conversation context and delivers personalized, concise responses.

## Features

### Gemini API Integration
- Seamless integration with Google's Gemini AI models
- Configurable API settings with secure key management
- Support for multiple model options

### Smart Conversation Management
- Maintains conversation history per notification thread
- Configurable history limits for optimal performance
- Context-aware responses based on previous interactions

### Customizable Prompt Templates
- Create and manage response templates
- Support for dynamic variables like `{context}` and `{message}`
- Enforced 50-word limit for concise responses

### User-Friendly Interface
- Intuitive configuration screens
- Conversation history viewer
- Template management tools

## Getting Started

### Prerequisites
- Android Studio Iguana or later
- Android SDK 34+
- Kotlin 1.9+
- Google Gemini API key

### Installation
1. Clone this repository
2. Open the project in Android Studio
3. Build and run on your device or emulator

```bash
git clone https://github.com/yourusername/whatsuit.git
cd whatsuit
```

## Configuration

To use Whatsuit, you'll need to configure your Gemini API key:

1. Launch the app
2. Navigate to Settings > Gemini Configuration
3. Enter your API key and test the connection
4. Customize prompt templates as needed

## Documentation

- [User Manual](USER_MANUAL.md) - Detailed guide for end users
- [Gemini Usage Guide](gemini_usage_guide.md) - How to use the Gemini integration

## Tech Stack

- **Language:** Kotlin
- **Architecture:** MVVM with Clean Architecture principles
- **UI:** Material Design components
- **Database:** Room for persistent storage
- **Concurrency:** Kotlin Coroutines and Flow
- **Dependency Injection:** Hilt/Dagger
- **API Integration:** Retrofit/Ktor

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
