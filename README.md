# KidPaint

A collaborative pixel-art drawing application built with a client-server architecture. Multiple users can connect to the same "studio", draw on a shared canvas in real time, and chat with each other — all within the same session.

---

## Features

- **Real-time collaborative drawing** — all strokes are broadcast to every connected client in the same studio
- **Multiple studios** — users can create a new studio or join an existing one
- **Three drawing tools:**
  - **Pen** — draw pixel-by-pixel with adjustable pen size (1–3)
  - **Bucket (fill)** — flood-fill a contiguous area with the selected colour
  - **Eraser** — paint pixels black
- **Colour picker** — pick any colour from a spectrum image (`color-spectrum.jpg`)
- **Chat panel** — send and receive text messages within your studio
- **Save / Load** — save the current canvas to a file and load it back later
- **Clear canvas** — wipe the entire studio canvas
- **Server discovery via UDP broadcast** — clients automatically discover the server on the local network; no manual IP entry required

---

## Project Structure

```
KidPaint Project/
├── KidPaint/               # Client application
│   ├── color-spectrum.jpg  # Colour picker image (must be in the working directory)
│   ├── bin/                # Compiled client .class files
│   └── src/
│       ├── KidPaint.java   # Entry point — launches the UI
│       ├── UI.java         # Main Swing GUI (paint panel, chat, tools, networking); defines the PaintMode enum (Pixel | Area)
│       └── ColorPicker.java# Colour-picker dialog (reads color-spectrum.jpg)
└── Server/                 # Server application
    ├── bin/                # Compiled server .class file
    └── src/
        └── Server.java     # TCP server + UDP discovery responder
```

---

## How It Works

### Server (`Server.java`)

- Listens for **TCP connections on port `12345`** to handle client sessions.
- Listens for **UDP broadcasts on port `55555`** to help clients discover the server automatically on a local network.
- When a client connects it:
  1. Sends the list of currently active studios to the client.
  2. Receives the client's chosen (or newly created) studio number.
  3. Replays the full drawing history of that studio to the new client so they see the current state of the canvas.
  4. Forwards subsequent messages (draw, bucket-fill, clear, chat) to all other clients in the same studio.
- Studio state (all paint operations) is kept in memory; it is lost when the server restarts.

### Client (`UI.java` / `KidPaint.java`)

- On startup the client:
  1. Asks for a **username**.
  2. Sends a **UDP broadcast** to discover the server; receives the server's IP and port in reply.
  3. Connects via TCP and receives the studio list.
  4. Shows the **studio lobby** — create a new studio or join an existing one.
- After joining a studio the main painting window appears with:
  - A **canvas** (grid of coloured circles, default 50 × 50 blocks at 16 px each).
  - A **toolbar** (colour swatch, Pen / Bucket / Eraser toggle buttons, pen-size cycling, Save, Load, Clear).
  - A **chat panel** on the right (text input + scrollable message history).
- Every mouse drag (pen/eraser) or fill operation is sent to the server as a typed binary message; the server relays it to all peers in the studio.

### Message Protocol (TCP)

| Type (`int`) | Direction | Payload |
|---|---|---|
| `0` | both | Chat message — name length, name bytes, message length, message bytes |
| `1` | both | Single pixel — colour (int), x (int), y (int) |
| `2` | both | Bucket fill — count (int), colour (int), then `count` × (x, y) pairs |
| `3` | server → client | Studio list — count (int), then `count` studio IDs |
| `3` | client → server | Clear canvas (same wire format as bucket fill) |

---

## Prerequisites

- **Java 8 or later** (JDK for compiling, JRE for running)
- Both the server and all clients must be on the **same local network** (UDP broadcast is used for discovery)

---

## Building

### Compile the Server

```bash
cd "KidPaint Project/Server"
javac -d bin src/Server.java
```

### Compile the Client

```bash
cd "KidPaint Project/KidPaint"
javac -d bin src/ColorPicker.java src/UI.java src/KidPaint.java
```

> **Note:** The `PaintMode` enum is defined inside `UI.java`, so there is no separate `PaintMode.java` to compile.

---

## Running

### 1. Start the Server

```bash
cd "KidPaint Project/Server"
java -cp bin Server
```

You should see:

```
Listening at port : 12345
```

### 2. Start a Client

Open a new terminal for **each** player (they can all be on different machines on the same network):

```bash
cd "KidPaint Project/KidPaint"
java -cp bin KidPaint
```

> The `color-spectrum.jpg` file must be in the **current working directory** when the client is launched (`KidPaint Project/KidPaint/`), because `ColorPicker` loads it with a relative path.

### 3. Connect and Draw

1. Enter your **username** and click **Submit**.
2. The client discovers the server automatically via UDP broadcast.
3. Either **Create New Studio** (enter canvas width and height, both ≥ 40) or select an existing studio number and click **Choose**.
4. Draw, fill, erase, chat, save, and load to your heart's content!

---

## Notes

- The server stores drawing history **in memory only**; restarting the server clears all studios.
- The UDP discovery broadcasts to `255.255.255.255`; make sure your firewall allows UDP on port `55555` and TCP on port `12345`.
- The client hard-codes UDP bind port `43215`; only one client instance can run per machine unless that port is made dynamic.
- Pen size cycles through 1 → 2 → 3 → 1 by clicking the **Pen** button.

