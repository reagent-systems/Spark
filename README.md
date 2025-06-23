# Spark - Local LLM Android App

Spark is an Android application that allows you to run Large Language Models (LLMs) locally on your device and expose them via API endpoints, similar to LM Studio. Built with Google's MediaPipe LLM Inference API, it provides a complete solution for on-device AI inference.

## Features

### 🤖 Model Management
- Load and manage multiple LLM models
- Support for MediaPipe-compatible models (Gemma, Phi-2, etc.)
- Real-time model status monitoring
- Easy model loading/unloading

### 💬 Chat Interface
- Interactive chat sessions with loaded models
- Multiple concurrent chat sessions
- Real-time response generation
- Clean, modern Material Design 3 UI

### 🌐 API Server
- OpenAI-compatible REST API endpoints
- Custom Spark API endpoints
- CORS support for web applications
- Real-time server status monitoring

### 📱 Modern Android Architecture
- Clean Architecture with MVVM pattern
- Jetpack Compose UI
- Kotlin Coroutines for async operations
- Material Design 3

## Supported Models

The app supports MediaPipe-compatible models including:

- **Gemma 2B/7B** - Google's open-source language model
- **Gemma-2 2B** - Latest version with improved performance
- **Phi-2** - Microsoft's efficient 2.7B parameter model
- **Custom models** - Any MediaPipe-compatible `.task` file

## Setup Instructions

### Prerequisites

1. **Android Device Requirements:**
   - Android 10+ (API level 29+)
   - High-end device recommended (Pixel 8, Samsung S23+)
   - At least 4GB RAM (8GB+ recommended)
   - Sufficient storage for models (2-8GB per model)

2. **Development Environment:**
   - Android Studio Hedgehog or later
   - Kotlin 1.9+
   - Gradle 8.0+

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/spark-llm-android.git
   cd spark-llm-android
   ```

2. **Open in Android Studio:**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build and install:**
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Model Setup

1. **Download a compatible model:**
   ```bash
   # Example: Download Gemma-2 2B (4-bit quantized)
   # Follow the MediaPipe model conversion guide
   ```

2. **Push model to device:**
   ```bash
   adb shell mkdir -p /data/local/tmp/llm/
   adb push your-model.task /data/local/tmp/llm/
   ```

3. **Add model in app:**
   - Open the Models tab
   - Tap the "+" button
   - Enter model details and file path

## Usage

### Managing Models

1. **Adding Models:**
   - Go to the "Models" tab
   - Tap the floating action button (+)
   - Fill in model name, description, and file path
   - Tap "Add"

2. **Loading Models:**
   - Find your model in the list
   - Tap "Load" to load it into memory
   - Wait for the loading process to complete

3. **Unloading Models:**
   - Tap "Unload" on a loaded model
   - This frees up memory for other models

### Chat Interface

1. **Creating Chat Sessions:**
   - Go to the "Chat" tab
   - Tap the "+" button
   - Enter a chat name and select a loaded model
   - Tap "Create"

2. **Chatting:**
   - Type your message in the input field
   - Tap the send button
   - Wait for the AI response

### API Server

1. **Starting the Server:**
   - Go to the "Server" tab
   - Tap "Start Server"
   - Note the server URL (typically `http://localhost:8080`)

2. **API Endpoints:**

   **OpenAI-Compatible:**
   - `GET /v1/models` - List loaded models
   - `POST /v1/chat/completions` - Chat completions

   **Spark Custom:**
   - `GET /spark/models` - List all models
   - `POST /spark/models/{id}/load` - Load a model
   - `POST /spark/models/{id}/unload` - Unload a model

3. **Example API Usage:**
   ```bash
   # List models
   curl http://localhost:8080/v1/models
   
   # Chat completion
   curl -X POST http://localhost:8080/v1/chat/completions \
     -H "Content-Type: application/json" \
     -d '{
       "model": "your-model-id",
       "messages": [
         {"role": "user", "content": "Hello!"}
       ],
       "temperature": 0.7,
       "max_tokens": 100
     }'
   ```

## Architecture

```
app/
├── data/
│   ├── models/           # Data models
│   └── repository/       # Repository implementations
├── domain/
│   ├── models/           # Domain models
│   └── repository/       # Repository interfaces
├── network/
│   ├── api/              # API models
│   └── server/           # Ktor server implementation
├── presentation/
│   ├── ui/
│   │   ├── components/   # Reusable UI components
│   │   └── screens/      # Screen composables
│   └── viewmodel/        # ViewModels
└── utils/                # Utility classes
```

## API Reference

### OpenAI-Compatible Endpoints

#### List Models
```http
GET /v1/models
```

Response:
```json
{
  "object": "list",
  "data": [
    {
      "id": "model-id",
      "object": "model",
      "created": 1234567890,
      "owned_by": "spark"
    }
  ]
}
```

#### Chat Completions
```http
POST /v1/chat/completions
Content-Type: application/json

{
  "model": "model-id",
  "messages": [
    {"role": "user", "content": "Hello!"}
  ],
  "temperature": 0.7,
  "max_tokens": 100,
  "top_k": 40
}
```

### Custom Endpoints

#### Load Model
```http
POST /spark/models/{modelId}/load
```

#### Unload Model
```http
POST /spark/models/{modelId}/unload
```

## Performance Tips

1. **Device Optimization:**
   - Close other apps to free up RAM
   - Use high-end devices for better performance
   - Ensure sufficient storage space

2. **Model Selection:**
   - Start with smaller models (2B parameters)
   - Use quantized versions for better performance
   - Consider your device's capabilities

3. **Memory Management:**
   - Unload unused models
   - Monitor memory usage
   - Restart app if performance degrades

## Troubleshooting

### Common Issues

1. **Model fails to load:**
   - Check file path is correct
   - Ensure model is MediaPipe-compatible
   - Verify sufficient memory available

2. **Server won't start:**
   - Check if port is already in use
   - Verify network permissions
   - Restart the app

3. **Slow responses:**
   - Use a more powerful device
   - Try a smaller model
   - Close other applications

### Debug Mode

Enable debug logging by setting `Log.d` tags in the code to see detailed information about model loading and inference.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Google MediaPipe](https://developers.google.com/mediapipe) for the LLM inference API
- [Ktor](https://ktor.io/) for the HTTP server
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for the modern UI

## Support

For support and questions:
- Open an issue on GitHub
- Check the [MediaPipe documentation](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
- Review the troubleshooting section above

---

**Note:** This app is optimized for high-end Android devices. Performance may vary on lower-end devices. 