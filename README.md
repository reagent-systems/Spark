# Spark - Local LLM Chat App for Android

Spark turns your Android phone into a powerful AI chat companion by running Large Language Models directly on your device. Chat privately with no cloud dependencies, and even use it as a local API server for your applications.

> ⚠️ **Early Development Notice**: Spark is in active development and things are moving fast! If you encounter any bugs or issues, please help us improve by:
> - 🐛 [Opening an issue](https://github.com/reagent-systems/Spark/issues)
> - 💬 Reporting it in our [Discord community](https://discord.reagent-systems.com/)
> 
> Your feedback is incredibly valuable in making Spark better for everyone!

## ✨ Features

- 💬 **Private Chat**: Have conversations with AI models running 100% on your device
- 🤖 **Multiple Models**: Choose from small (0.5GB) to powerful (4GB+) models
- 🌐 **Local API**: Use as a drop-in replacement for OpenAI's API in your apps
- 🎨 **Beautiful UI**: Modern Material You design with dark mode
- 🔄 **Easy Updates**: One-click model downloads from HuggingFace
- 🔒 **Full Privacy**: No data leaves your device

## 📱 Requirements

- Android 8.1 or newer
- 64-bit processor (ARMv8)
- Storage: 2GB+ free space
- RAM: 
  - Minimum: 3GB free RAM
  - Recommended: 6GB+ for larger models
  - High-end: 8GB+ for multimodal models

## 🤖 Available Models

### Multimodal Models
- **Gemma-3n-E2B-it** (3.0GB)
  - Vision + Text capabilities
  - 4096 token context
  - Needs 8GB RAM
- **Gemma-3n-E4B-it** (4.2GB)
  - Enhanced multimodal model
  - 4096 token context
  - Needs 9GB RAM

### Text Models
- **Gemma3-1B-IT** (0.5GB) ⭐
  - Fast & lightweight
  - 2048 token context
  - Only needs 4GB RAM
- **Qwen2.5-1.5B** (1.5GB)
  - Strong multilingual support
  - 1280 token context
  - Needs 6GB RAM
- **DeepSeek R1** (1.5GB)
  - Best for reasoning tasks
  - 1280 token context
  - Needs 6GB RAM

## 🚀 Getting Started

1. Install Spark from [Latest Release](https://github.com/your-username/spark/releases)
2. Open app and go to Models tab
3. Download a model (start with Gemma3-1B-IT for best compatibility)
4. Create a new chat and start talking!

## 🌐 Using the API

The API server lets you use Spark models in your own apps as a local alternative to OpenAI's API.

1. In Spark app:
   - Load your chosen model
   - Go to Server tab
   - Start server and note your phone's IP address

2. In your application:
   - Replace `localhost` with your phone's IP
   - Use standard OpenAI API format:

```bash
# List available models
GET http://<phone-ip>:8080/v1/models

# Chat completion
POST http://<phone-ip>:8080/v1/chat/completions
{
  "model": "gemma3-1b-it",
  "messages": [{"role": "user", "content": "Hello!"}]
}
```

## 🔗 Links

- [Discord Community](https://discord.reagent-systems.com/)
- [Report Issues](https://github.com/reagent-systems/Spark/issues)
- [Latest Release](https://github.com/reagent-systems/spark/releases)

## 📄 License

MIT License - see [LICENSE](LICENSE)

## 🙏 Built With

- [MediaPipe](https://developers.google.com/mediapipe/solutions/genai/llm_inference/android) - LLM inference
- [Ktor](https://ktor.io/) - API server
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI
