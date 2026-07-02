**TrueMusic** turns music into part of the game world itself with real in-world speaker blocks, synchronized playback across all players, and support for pretty much any audio source. This mod is inspired by Phonon.

## Features

- **Speaker Blocks** - place in-world speakers that play custom audio directly in the game.
- **Real-time Sync** — all players hear the same audio at the same time, no desync.
- **Universal URL Support** — YouTube, Bilibili, or any other URL via yt-dlp integration.
- **Spatial Audio** — sound emanates from the speaker's location, getting quieter with distance and accounting for direction.
- **Playlist System** — build playlists, switch between them from the speaker or via commands, and control queue and repeat behavior.
- **Music player** — the ability to listen to music on your own and, if you wish, play it for everyone

## Requirements

TrueMusic relies on yt-dlp and ffmpeg for external audio sources.

On first launch, the mod will attempt to install them automatically into the game's root folder or the modpack's root folder. If auto-installation fails for any reason (no network access, restricted environment, etc.), install yt-dlp and ffmpeg manually and make sure they're available in your PATH.

## Commands

| Command                       | Description                 |
|-------------------------------|-----------------------------|
| `/truemusic add <name> <url>` | Download and register audio |
| `/truemusic list`             | View registered audio       |
| `/truemusic remove <name>`    | Remove audio                |
| `/truemusic reload`           | Reload configuration        |

## Usage

1. Place a Speaker block
2. Right-click to open the control GUI
3. Select a track and press play

## In future
- Redstone support
- Sophisticated backpaks support
