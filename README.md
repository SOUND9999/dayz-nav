<img width="476" height="863" alt="nav1" src="https://github.com/user-attachments/assets/e42466d6-ca50-4953-a982-7e3f148e6398" />
<img width="1839" height="807" alt="nav2" src="https://github.com/user-attachments/assets/446a2233-a004-47c9-b63c-99a77f374938" />


# DayZ NAV v1.0 – Network Activity Visualizer

**DayZ NAV** is a passive, real‑time network traffic visualizer for DayZ.  
It captures UDP packets, extracts RPC‑block information, and displays the activity
of all 656 RPL channels as a live waterfall histogram – separately for **server**
and **client** traffic.  An optional audio detector provides immediate feedback
when network activity spikes.

**No game modification, injection, or overlay is used.**  The tool only
looks at the network packets that DayZ already sends and receives, making it
compliant with BattlEye on most community servers.

---

## Quick overview

- **Server waterfall** – shows object loading, NPC state updates, and
  global world sync within a ~500 m radius.
- **Client waterfall** – reveals player‑to‑player data exchange (inventory,
  equipment) and vehicle driving bursts.
- **Audio EventDetector** – Geiger‑like clicks for server events, Morse SOS
  for client events (can be muted).
- **RPC call meter** – network load in calls per second (0‑100 %).

---

## System requirements

- **Java 21** or later (earlier versions may work but are untested)
- **pcap4j** (the project uses the library automatically via Gradle)
- **Npcap** (Windows) or **libpcap** (Linux / macOS)  
  → Npcap can be downloaded from [npcap.org](https://npcap.org/)

---

## Building and running

### 1. Clone the repository
```bash
git clone https://github.com/SOUND9999/dayz-nav.git
cd dayz-nav
```

### 2. Build with Gradle
```bash
./gradlew build
```

### 3. Start the capture
```bash
./gradlew run
```
or execute the generated fat JAR directly:
```bash
java -jar build/libs/dayz-nav-1.0.jar
```

### 4. Configure the interface and IP

- Select the network interface that carries your DayZ traffic.
- Enter the **server IP** (the IP of the DayZ server you are connected to).
- Press **Start Capture**.
- Use the checkboxes **Server**, **Client**, **EventDetector** to show/hide panels.

---

## How it works

1. The program captures **UDP packets** between your PC and the DayZ server.
2. It parses the 16‑byte **RPC blocks** contained in the payload.
3. Every 10 ms it records which of the **656 RPL channels** were active and
   pushes a new row into the waterfall histograms.
4. The **EventDetector** compares the current number of active channels with
   a fixed threshold (~70 % of 656) and plays sounds when the threshold is
   exceeded.

---

## Interpretation guide

| Observation | Meaning |
|-------------|---------|
| Server waterfall becomes dense (30‑50 % points) | Entering a town or area with many objects / NPCs |
| Continuous server bursts (red rows) | Large world updates (base loading, zombie horde) |
| Client waterfall suddenly becomes dense | Another player is nearby – their inventory data is being exchanged |
| Client traffic spikes heavily | You (or another player) are driving a vehicle |
| Geiger sound speeds up | The server activity is increasing – possible danger ahead |
| Morse SOS sounds | Client traffic burst detected – another player may be visible |

---

## Safety note

DayZ NAV is a **passive network monitor**.  It does not:

- Read or modify game memory
- Interact with the DayZ client process
- Overlay any graphics on the game screen

It simply analyses the same UDP packets that your network card already sees.
Nevertheless, always respect the rules of the server you play on.

---

## License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file
for details.

---

## Acknowledgments

- [pcap4j](https://github.com/kaitoy/pcap4j) – the Java packet capture library
- The DayZ community for endless inspiration

---

*Use wisely and enjoy the apocalypse!*
