package livedayz.live;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * LiveHistogramVisualizer – a real‑time waterfall of RPL channel activity.
 *
 * Rows are added from the outside via {@link #pushRow(boolean[], int)}.
 * Channels that are active during the interval are drawn in gray, and when the
 * number of simultaneously active channels exceeds a fixed threshold they are
 * drawn in red, marking a network “event”.
 */
public class LiveHistogramVisualizer extends JPanel {

    private static final int TOTAL_CHANNELS = 656;
    private static final int IMG_WIDTH = TOTAL_CHANNELS;
    private static final int IMG_HEIGHT = 500;

    private BufferedImage image;
    private Graphics2D imageGraphics;

    private double noiseMultiplier = 1.2;
    private int noiseThreshold = 0;
    private long totalIntervals = 0;
    private double sumActive = 0;

    private static final Color ACTIVE_COLOR = new Color(255, 60, 60);
    private static final Color NOISE_COLOR = new Color(180, 180, 180);
    private static final Color INACTIVE_COLOR = new Color(10, 10, 15);

    public LiveHistogramVisualizer() {
        setBackground(new Color(10, 10, 15));
        image = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        imageGraphics = image.createGraphics();
        clearImage();
        setPreferredSize(new Dimension(IMG_WIDTH, IMG_HEIGHT));
    }

    private void clearImage() {
        imageGraphics.setColor(INACTIVE_COLOR);
        imageGraphics.fillRect(0, 0, IMG_WIDTH, IMG_HEIGHT);
    }

    /**
     * Adds a new row to the waterfall.
     *
     * @param row         channel activity for the interval (true = active)
     * @param activeCount number of active channels in this interval
     */
    public void pushRow(boolean[] row, int activeCount) {
        totalIntervals++;
        sumActive += activeCount;

        double averageActive = 50;
        noiseThreshold = (int) (averageActive * noiseMultiplier);
        if (noiseThreshold < 1) noiseThreshold = 1;

        boolean isEvent = activeCount > noiseThreshold;
        Color color = isEvent ? ACTIVE_COLOR : NOISE_COLOR;

        // Shift the image down and draw the new row at the top
        imageGraphics.copyArea(0, 0, IMG_WIDTH, IMG_HEIGHT - 1, 0, 1);
        for (int ch = 0; ch < TOTAL_CHANNELS; ch++) {
            imageGraphics.setColor(row[ch] ? color : INACTIVE_COLOR);
            imageGraphics.drawLine(ch, 0, ch, 0);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, getWidth(), IMG_HEIGHT, null);
    }

    public void setNoiseThreshold(int threshold) {
        this.noiseThreshold = threshold;
    }

    /**
     * Checks whether the given number of active channels exceeds the event threshold.
     */
    public boolean isEvent(int activeCount) {
        return activeCount > noiseThreshold;
    }
}