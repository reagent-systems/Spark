# Spark - Local LLM Chat App for Android

Spark lets you run Large Language Models directly on your Android device, providing a private and efficient chat experience with no cloud dependencies.

## ✨ Features

- 💬 **Chat Interface**: Multi-session support, real-time streaming, message history
- 🤖 **Model Management**: Download models from HuggingFace, memory optimization
- 🌐 **API Server**: OpenAI-compatible endpoints for local development
- 🎨 **Modern UI**: Material Design 3 with dark mode support
- 🔒 **Privacy**: All processing happens locally on your device

## 📱 Requirements

- Android 8.1+ (API 29+)
- ARMv8 (64-bit) processor
- RAM requirements vary by model (2-9GB)
- 2GB+ storage for models

## 🤖 Supported Models

### Multimodal Models (Text + Vision)
- **Gemma-3n-E2B-it** (3.0GB, INT4)
  - 4096 context length, requires 8GB RAM
  - Latest Google model with vision capabilities
- **Gemma-3n-E4B-it** (4.2GB, INT4)
  - 4096 context length, requires 9GB RAM
  - High-performance multimodal variant

### Text-Only Models
- **Gemma3-1B-IT** (0.5GB, INT4)
  - 2048 context length, requires 4GB RAM
  - Fast, mobile-optimized Google model
- **Qwen2.5-1.5B-Instruct** (1.5GB, INT8)
  - 1280 context length, requires 6GB RAM
  - Strong multilingual capabilities
- **DeepSeek R1 Distill** (1.5GB, INT8)
  - 1280 context length, requires 6GB RAM
  - Optimized for reasoning tasks

## 🚀 Quick Start

1. Install the app
2. Download a model from the catalog
3. Create a new chat session
4. Start chatting!

## 🌐 API Usage

```bash
# List models
GET http://localhost:8080/v1/models

# Chat completion
POST http://localhost:8080/v1/chat/completions
{
  "model": "model-id",
  "messages": [{"role": "user", "content": "Hello!"}]
}
```

## 📄 License

MIT License - see [LICENSE](LICENSE) file

## 🙏 Acknowledgments

Built with [MediaPipe](https://developers.google.com/mediapipe/solutions/genai/llm_inference/android), [Ktor](https://ktor.io/), and [Jetpack Compose](https://developer.android.com/jetpack/compose) 