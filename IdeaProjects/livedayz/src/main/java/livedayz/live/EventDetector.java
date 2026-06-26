package livedayz.live;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;

/**
 * EventDetector – audio event detection panel for DayZ traffic.
 *
 * Monitors server and client RPC bursts and plays different sound profiles.
 * Server events trigger a short beep or a Geiger‑like click depending on the
 * selected profile; prolonged server events (≥500 ms) generate a continuous
 * siren sound.  Client events can optionally play the Morse SOS sequence.
 */
public class EventDetector extends JPanel {

    // -----------------------------------------------------------------------
    // Sound profiles for server traffic
    // -----------------------------------------------------------------------
    public enum SoundProfile { BEEP, GEIGER }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------
    private static final double BASE_FREQ = 440.0;
    private static final double MAX_FREQ = 500.0;
    private static final double FREQ_STEP = -10.0;
    private static final long RESET_DELAY_MS = 500;
    private static final long LONG_EVENT_MS = 500;

    // -----------------------------------------------------------------------
    // Server state
    // -----------------------------------------------------------------------
    private double serverFreq = BASE_FREQ;
    private long serverLastEventTime = 0;
    private long serverEventStartTime = 0;
    private boolean serverEventActive = false;
    private boolean serverLongEvent = false;

    // -----------------------------------------------------------------------
    // Client state
    // -----------------------------------------------------------------------
    private boolean clientEventActive = false;

    // -----------------------------------------------------------------------
    // Sound flags and settings
    // -----------------------------------------------------------------------
    private boolean soundEnabled = true;
    private int volume = 25;                     // 25 % by default
    private SoundProfile currentProfile = SoundProfile.GEIGER;  // Geiger by default

    private volatile boolean playingSiren = false;
    private Thread sirenThread = null;

    // for client SOS
    private volatile boolean playingSOS = false;
    private boolean morzeEnabled = true;         // client sound on by default

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public EventDetector() {
        setBackground(new Color(238, 238, 238));
        setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));

        // Volume slider
        add(new JLabel("Vol:"));
        JSlider volumeSlider = new JSlider(0, 100, volume);
        volumeSlider.setPreferredSize(new Dimension(150, 20));
        volumeSlider.addChangeListener(e -> volume = volumeSlider.getValue());
        add(volumeSlider);

        // Profile radio buttons (server)
        JRadioButton beepBtn = new JRadioButton("Beep");
        JRadioButton geigerBtn = new JRadioButton("Geiger", true);
        ButtonGroup profileGroup = new ButtonGroup();
        profileGroup.add(beepBtn);
        profileGroup.add(geigerBtn);
        beepBtn.addActionListener(e -> { if (beepBtn.isSelected()) currentProfile = SoundProfile.BEEP; });
        geigerBtn.addActionListener(e -> { if (geigerBtn.isSelected()) currentProfile = SoundProfile.GEIGER; });
        add(beepBtn);
        add(geigerBtn);

        // Global sound checkbox
        JCheckBox soundCheck = new JCheckBox("Sound", soundEnabled);
        soundCheck.addActionListener(e -> soundEnabled = soundCheck.isSelected());
        add(soundCheck);

        // Client SOS (Morze) checkbox
        JCheckBox morzeCheck = new JCheckBox("Morze (client SOS)", morzeEnabled);
        morzeCheck.addActionListener(e -> morzeEnabled = morzeCheck.isSelected());
        add(morzeCheck);

        setPreferredSize(new Dimension(656, 60));
    }

    // -----------------------------------------------------------------------
    // Compatibility entry-point (uses server logic)
    // -----------------------------------------------------------------------
    public void update(boolean hasActivity, boolean isEvent) {
        updateServer(hasActivity, isEvent);
    }

    // -----------------------------------------------------------------------
    // Called every 10 ms from the main capture timer – server traffic
    // -----------------------------------------------------------------------
    public void updateServer(boolean hasActivity, boolean isEvent) {
        long now = System.currentTimeMillis();

        if (isEvent && !serverEventActive) {
            serverEventActive = true;
            serverEventStartTime = now;
            serverLongEvent = false;
        } else if (!isEvent && serverEventActive) {
            serverEventActive = false;
            serverLongEvent = false;
            stopContinuous();
        }

        // Switch to continuous tone when the event persists beyond LONG_EVENT_MS
        if (serverEventActive && !serverLongEvent && (now - serverEventStartTime >= LONG_EVENT_MS)) {
            serverLongEvent = true;
            if (currentProfile == SoundProfile.BEEP) {
                startContinuous();
            }
        }

        // Short event – play an individual sound
        if (isEvent && !serverLongEvent) {
            if (now - serverLastEventTime < RESET_DELAY_MS) {
                serverFreq = Math.min(MAX_FREQ, serverFreq + FREQ_STEP);
            } else {
                serverFreq = BASE_FREQ;
            }
            serverLastEventTime = now;
            playServerSound(serverFreq);
        } else if (!isEvent && !serverEventActive && (now - serverLastEventTime >= RESET_DELAY_MS)) {
            serverFreq = BASE_FREQ;
        }
    }

    // -----------------------------------------------------------------------
    // Called every 10 ms from the main capture timer – client traffic
    // -----------------------------------------------------------------------
    public void updateClient(boolean hasActivity, boolean isEvent) {
        if (!morzeEnabled || !soundEnabled) return;

        if (isEvent && !clientEventActive) {
            clientEventActive = true;
            playSOS();
        } else if (!isEvent && clientEventActive) {
            clientEventActive = false;
        }
    }

    // -----------------------------------------------------------------------
    // Helper to dispatch the right sound for the server
    // -----------------------------------------------------------------------
    private void playServerSound(double freq) {
        if (!soundEnabled) return;
        switch (currentProfile) {
            case BEEP  -> playBeep(freq);
            case GEIGER -> playGeiger();
        }
    }

    // =======================================================================
    // Sound generation methods
    // =======================================================================

    // ------------------------------ Beep -----------------------------------
    private void playBeep(double freq) {
        new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(8000, 8, 1, true, true);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                line.start();
                int samples = 8000 / 10;            // 100 ms
                byte[] buffer = new byte[samples];
                for (int i = 0; i < samples; i++) {
                    double angle = 2.0 * Math.PI * i / (8000 / freq);
                    buffer[i] = (byte) (Math.sin(angle) * volume * 1.27);
                }
                line.write(buffer, 0, buffer.length);
                line.drain();
                line.stop();
                line.close();
            } catch (Exception ignored) { }
        }).start();
    }

    // ------------------------------ Geiger ---------------------------------
    private void playGeiger() {
        new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(8000, 8, 1, true, true);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                line.start();
                int duration = 8000 / 200;          // ~5 ms
                byte[] buffer = new byte[duration];
                int half = duration / 2;
                for (int i = 0; i < duration; i++) {
                    buffer[i] = (i < half) ? (byte)(volume * 1.27) : (byte)(-volume * 1.27);
                }
                line.write(buffer, 0, buffer.length);
                line.drain();
                line.stop();
                line.close();
            } catch (Exception ignored) { }
        }).start();
    }

    // -------------------- Tone generator helper ----------------------------
    private byte[] generateTone(int samples, double freq) {
        byte[] buf = new byte[samples];
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i / (8000 / freq);
            buf[i] = (byte) (Math.sin(angle) * volume * 1.27);
        }
        return buf;
    }

    // -------------------- SOS for client -----------------------------------
    private void playSOS() {
        if (playingSOS) return;
        playingSOS = true;
        new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(8000, 8, 1, true, true);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                line.start();

                int dotLen  = 8000 * 100 / 1000;   // 100 ms
                int dashLen = 8000 * 300 / 1000;   // 300 ms
                int gapLen  = 8000 * 100 / 1000;   // 100 ms
                int charGap = 8000 * 300 / 1000;   // 300 ms

                byte[] dot   = generateTone(dotLen, 800);
                byte[] dash  = generateTone(dashLen, 800);
                byte[] silence    = new byte[gapLen];
                byte[] charSilence = new byte[charGap];

                // S
                writeSequence(line, dot, silence, dot, silence, dot);
                line.write(charSilence, 0, charSilence.length);
                // O
                writeSequence(line, dash, silence, dash, silence, dash);
                line.write(charSilence, 0, charSilence.length);
                // S
                writeSequence(line, dot, silence, dot, silence, dot);

                line.drain();
                line.stop();
                line.close();
            } catch (Exception ignored) { }
            playingSOS = false;
        }).start();
    }

    private void writeSequence(SourceDataLine line, byte[]... parts) {
        for (byte[] part : parts) {
            line.write(part, 0, part.length);
        }
    }

    // -------------------- Continuous siren (Beep only) ---------------------
    private void startContinuous() {
        if (!soundEnabled || currentProfile != SoundProfile.BEEP) return;
        stopContinuous();
        playingSiren = true;
        sirenThread = new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(8000, 8, 1, true, true);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                line.start();
                byte[] buffer = new byte[1024];
                double freq = MAX_FREQ;
                double dFreq = 2;
                while (playingSiren) {
                    for (int i = 0; i < buffer.length; i++) {
                        freq += dFreq;
                        if (freq > 880 || freq < 220) dFreq = -dFreq;
                        double angle = 2.0 * Math.PI * i / (8000 / freq);
                        buffer[i] = (byte) (Math.sin(angle) * volume * 1.27);
                    }
                    line.write(buffer, 0, buffer.length);
                }
                line.drain();
                line.stop();
                line.close();
            } catch (Exception ignored) { }
        });
        sirenThread.start();
    }

    private void stopContinuous() {
        playingSiren = false;
        if (sirenThread != null) {
            sirenThread.interrupt();
            sirenThread = null;
        }
    }
}