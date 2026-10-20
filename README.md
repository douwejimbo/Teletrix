# Teletrix

Teletrix is an Android client for [Matrix](https://matrix.org). It is built for everyday chatting: a room list, conversations, spaces, and the public rooms on your homeserver.

The app signs in to a homeserver, keeps the session on the device, and talks to Matrix through the Matrix Rust SDK. The interface is a dark chat app with four tabs: Chats, Spaces, Settings, and Profile.

## What you can do

**Chats.** The room list shows the room name, the latest message, and how many messages are unread since you last read that room. A magnifying glass opens search. Filters narrow the list to unread rooms, direct messages, or groups. Message bubbles stay as wide as the text. Images, video, audio, voice notes, and stickers open in a viewer. People appear with their display name and picture, and you can mention them with @.

**Read receipts.** People who have read a message show as small avatar bubbles on that message. Tap a bubble to see who they are. Once five or more people have read it, the bubbles collapse to a short "4+N read" summary.

**Spaces.** A space lists every room and space inside it, including ones you have not joined. Private rooms are not given a public name until you join. Joining a listed room opens it.

**Directory.** The directory lists rooms and spaces that your homeserver has published. Private rooms are not included, and the screen says so.

**Settings and profile.** Appearance is dark by default, with light, system, and an AMOLED black option. You can compare emoji to verify this session against another client. The profile screen shows the signed-in account.

## Sync

Teletrix syncs while the app is open. It does not keep a background sync after you leave it, so unread badges update when you next open the app. The count is the messages since you last read the room, and opening the room clears it.

## Requirements

- JDK 17 or newer (`JAVA_HOME` set)
- Android SDK, compileSdk 35
- Android 10 or newer (minSdk 29)

## Build a debug APK

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Build a signed release APK

1. Create a keystore once, for example with `keytool`.
2. Copy `keystore.properties.example` to `keystore.properties` and fill in the values. Do not commit `keystore.properties`.
3. Run:

```bash
./gradlew :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Project layout

| Module | Role |
| --- | --- |
| `:app` | Application shell, navigation, and dependency injection |
| `:matrix` | Matrix Rust SDK (`org.matrix.rustcomponents:sdk-android` 26.05.7): sync, rooms, timeline, encryption session |
| `:feature:auth` | Homeserver discovery and sign-in |
| `:feature:chat` | Room list, conversation, spaces, and the public directory |
| `:feature:media` | Viewer for images, video, audio, voice notes, and files |
| `:feature:settings` | Appearance, session verification, and profile |
| `:core:ui` | Theme, including dark, light, system, and AMOLED |
| `:data:db` | Local Room database with SQLCipher |
| `:data:media` | Playback service for audio in chats |
| `:data:push` | Placeholder for a UnifiedPush distributor. Push delivery is not wired up yet |

Application id: `com.nextgenmatrix.app`. Version 0.1.0.
