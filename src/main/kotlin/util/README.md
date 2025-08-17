# Util Package

This package contains utility functions and helpers for audio processing and error handling.

## Planned Components

- `AudioConverter.kt` - FFmpeg integration for audio format conversion
- `ErrorHandler.kt` - Global error handling and admin notifications
- `FileUtils.kt` - Temporary file management and cleanup
- `Extensions.kt` - Kotlin extension functions

## Audio Processing

- OGG/Opus to WAV PCM 16 kHz mono conversion
- Audio duration validation (≤90 seconds)
- Temporary file management
- FFmpeg process execution

## Error Handling

- Admin notification via Telegram DM
- Error logging and stack trace formatting
- User-friendly Russian error messages
- Graceful failure handling

## Status

🚧 **Under Development** - This package will be implemented throughout Phase 4 of the project.

See `docs/spec/todo.md` sections 12, 15 for implementation details.