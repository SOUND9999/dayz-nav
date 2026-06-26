package livedayz.live;

import javax.swing.*;
import java.awt.*;

/**
 * NetworkActivityMeter – displays network load as a percentage.
 *
 * 1000 calls/sec = 20 %, 5000 calls/sec = 100 %.
 * The value is averaged over the last 3 seconds for smoothness.
 */
public class NetworkActivityMeter extends JPanel {

    private static final int BUFFER_SIZE = 3;                // seconds for averaging
    private static final int MAX_CALLS_PER_SEC = 5000;      // 100 % of the scale

    private final int[] callBuffer = new int[BUFFER_SIZE];
    private int bufferIndex = 0;
    private int bufferCount = 0;
    private int currentPercent = 0;
    private double avgCallsPerSec = 0;

    private static final Color BAR_BG = new Color(20, 20, 30);
    private static final Color BAR_BORDER = new Color(100, 100, 120);

    public NetworkActivityMeter() {
        setBackground(new Color(10, 10, 15));
        setPreferredSize(new Dimension(656 + 40, 40));
    }

    /**
     * Receives the number of RPC calls for the past second.
     */
    public void takeSample(int callsPerSecond) {
        callBuffer[bufferIndex] = callsPerSecond;
        bufferIndex = (bufferIndex + 1) % BUFFER_SIZE;
        if (bufferCount < BUFFER_SIZE) bufferCount++;

        // Average over the last bufferCount seconds
        int sum = 0;
        for (int i = 0; i < bufferCount; i++) {
            sum += callBuffer[i];
        }
        avgCallsPerSec = (double) sum / bufferCount;

        // Percentage of maximum
        currentPercent = (int) ((avgCallsPerSec / MAX_CALLS_PER_SEC) * 100);
        if (currentPercent > 100) currentPercent = 100;

        repaint();
    }

    public int getCurrentPercent() {
        return currentPercent;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int width = getWidth();
        int height = getHeight();
        int barWidth = width - 20;
        int barHeight = height - 10;
        int x = 10;
        int y = 5;

        // Bar background
        g.setColor(BAR_BG);
        g.fillRect(x, y, barWidth, barHeight);
        g.setColor(BAR_BORDER);
        g.drawRect(x, y, barWidth, barHeight);

        // Gradient fill
        int fillWidth = (int)(barWidth * currentPercent / 100.0);
        if (fillWidth > 0) {
            for (int i = 0; i < fillWidth; i++) {
                double t = (double) i / barWidth;
                Color color = interpolateColor(
                        new Color(0, 100, 255),  // blue (0%)
                        new Color(255, 30, 30),  // red (100%)
                        t
                );
                g.setColor(color);
                g.drawLine(x + i, y + 1, x + i, y + barHeight - 1);
            }
        }

        // Text: "45% (2250 calls/s)"
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        String text = currentPercent + "% (" + (int)avgCallsPerSec + " RPC/s)";
        FontMetrics fm = g.getFontMetrics();
        int textX = x + barWidth / 2 - fm.stringWidth(text) / 2;
        int textY = y + (barHeight + fm.getAscent() / 2) / 2;
        g.drawString(text, textX, textY);
    }

    private Color interpolateColor(Color c1, Color c2, double t) {
        int r = (int)(c1.getRed() * (1 - t) + c2.getRed() * t);
        int g = (int)(c1.getGreen() * (1 - t) + c2.getGreen() * t);
        int b = (int)(c1.getBlue() * (1 - t) + c2.getBlue() * t);
        return new Color(r, g, b);
    }
}