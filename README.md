# DonutTrade (DonutSMP-style /trade window)

Server plugin for **Paper or Spigot 1.20.4 and newer** (Java Edition servers). It does not work in single player or on a vanilla server.

## Use
- `/trade <player>` sends a request. The other player gets clickable [ACCEPT] / [DENY] (or `/trade accept`, `/trade deny`).
- Both players get a big chest window:
  - left 4x4: your offer (put items in, click them to take back, shift-click works)
  - right 4x4: their offer (read only, updates live)
  - bottom right: **ACCEPT**, **DENY**, **SEE THEIR ITEMS**
- When both press ACCEPT a short countdown starts (default 3s). Changing any items cancels both accepts.
- Closing the window, pressing DENY, or leaving cancels the trade and everyone gets their own items back.

Settings are in `plugins/DonutTrade/config.yml`.

## Get the .jar without installing anything
1. Make a free GitHub account and create a new repository.
2. Upload ALL files from this folder (including the hidden `.github` folder).
3. Open the **Actions** tab, wait for "Build plugin" to finish (about 1 minute).
4. Open the finished run, download **DonutTrade-jar**, unzip it.
5. Put `DonutTrade-1.0.0.jar` in your server's `plugins` folder and restart.

## Or build it yourself
Install JDK 17+ and Maven, then run `mvn package`. The jar appears in `target/`.
