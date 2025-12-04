# ZenControl (Android Headless Agent)

> **A Tactile, Accessibility-First Interface & AI Agent for Android**

ZenControl is an advanced Android accessibility tool designed to operate a smartphone entirely without touch input. Originally engineered to bypass a broken digitizer, it has evolved into a **Headless Android Agent** that allows users to navigate, manage calls, control media, and execute system commands using only hardware volume keys and background AI triggers.

## 🚀 Key Features

### 1. Tactile Navigation (Headless UI)
* **Volume Key Mapping:** Uses a custom `VolumeKeyListener` to intercept hardware key events.
    * **Short Press:** Navigate lists or trigger actions.
    * **Long Press:** Execute primary commands (e.g., select item).
    * **Double Press:** Toggle special modes.
* **Custom Dialer:** Acts as the default system dialer (`TelecomManager`) to handle incoming/outgoing calls without UI interaction.

### 2. "Agentic" Tool Architecture
Unlike standard apps, ZenControl is built on a modular **Tool Use** architecture. The app exposes capabilities as `AiTool` instances that can be triggered locally or remotely:
* **SpotifyTool:** Deep integration with Spotify App Remote for playback control.
* **NotificationTool:** Intercepts notifications and uses Regex (`OtpParser`) to extract and read 2FA/OTP codes aloud via TTS.
* **TouchTool:** Uses Accessibility APIs to inject gestures (clicks/scrolls) programmatically.
* **ScreenScraper:** Captures screen content (text hierarchy) for AI context processing.

### 3. Remote AI Integration (Firebase)
* The app uploads its capabilities (available tools) to **Firebase Realtime Database**.
* It listens for remote commands, effectively allowing an external AI (like Google Genkit) to drive the phone remotely.

## 🛠️ Tech Stack

* **Language:** Kotlin
* **Architecture:** MVVM + Clean Architecture (Domain/Data layers)
* **UI:** Jetpack Compose (Material 3)
* **Dependency Injection:** Dagger Hilt
* **System APIs:**
    * `AccessibilityService` (Screen reading & gesture injection)
    * `NotificationListenerService` (OTP extraction)
    * `TextToSpeech` (TTS)
    * `RoleManager` (Default Dialer handling)
* **Backend:** Firebase (Firestore, Realtime DB)
* **Local DB:** Room Database

## 📱 How It Works

### The Volume Listener
The core navigation logic resides in `VolumeKeyListener.kt`. It intercepts key events before they reach the system (when the app is focused) or via Accessibility Services, translating patterns into internal `Command` objects.

### The Tool Executor
Commands are dispatched to the `ToolExecutor`, which validates parameters and runs the specific `AiTool`:


// Example: Converting a remote JSON command into a native Android action
val tool = toolStore.getTool("spotify")
toolExecutor.execute(tool, mapOf("action" to "next"), scope)


## 🔮 Future Roadmap

  * [ ] **Voice Command Layer:** Integrate local speech recognition to trigger `AiTools` via voice.
  * [ ] **LLM Integration:** Connect the `ScreenCapture` output directly to an on-device Gemini Nano model for context-aware assistance.
  * [ ] **Smart Home Bridge:** Add a tool to control Home Assistant devices via volume keys.
  * [ ] **Gestures V2:** Implement complex gesture chaining for the `TouchTool`.


## ⚙️ Setup & Installation

### Clone the Repo
git clone https://github.com/kkek-041405/ZenControl.git

### Firebase Setup
* Add your `google-services.json` to the `/app` folder.
* Ensure **Realtime Database** and **Firestore** are enabled.

### Spotify SDK
* The project requires the **spotify-app-remote AAR**.
* Ensure it is placed in `app/libs/`.

### Permissions
On first launch, grant:
* Accessibility
* Notification Access
* Default Dialer

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](https://www.google.com/search?q=LICENSE) file for details.
