package livedayz.app;

import livedayz.live.EventDetector;
import livedayz.live.LiveHistogramVisualizer;
import livedayz.live.NetworkActivityMeter;
import org.pcap4j.core.*;
import org.pcap4j.packet.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

/**
 * DayZ NAV v1.0 (Network Activity Visualizer) – real-time RPL waterfall and audio event detection
 * for both server and client UDP traffic.
 */
public class DayZLiveCapture extends JFrame {

    // Visual components
    private final LiveHistogramVisualizer serverHistogram;
    private final NetworkActivityMeter serverMeter;
    private final LiveHistogramVisualizer clientHistogram;
    private final NetworkActivityMeter clientMeter;
    private final EventDetector eventDetector;

    // Panels for visibility control
    private final JPanel serverPanel;
    private final JPanel clientPanel;
    private final JPanel eventPanel;

    // Capture controls
    private final JComboBox<PcapNetworkInterface> nifComboBox;
    private final JTextField ipFilterField;
    private final JButton toggleCaptureBtn;
    private final JLabel statusLabel;

    // Checkboxes
    private final JCheckBox checkS;
    private final JCheckBox checkC;
    private final JCheckBox checkE;

    private PcapHandle handle;
    private volatile boolean capturing = false;
    private String serverIp = "";

    private final boolean[] serverRowBuffer = new boolean[656];
    private final boolean[] clientRowBuffer = new boolean[656];
    private int serverRpcCounter = 0;
    private int clientRpcCounter = 0;

    private Timer snapshotTimer;
    private Timer rpcMeterTimer;
    private static final int SNAPSHOT_INTERVAL_MS = 10;

    /** IPv4 address pattern */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    public DayZLiveCapture() {
        setTitle("DayZ NAV v1.0 (Network Activity Visualizer)");
        setSize(400, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Initialise components
        serverHistogram = new LiveHistogramVisualizer();
        serverMeter = new NetworkActivityMeter();
        clientHistogram = new LiveHistogramVisualizer();
        clientMeter = new NetworkActivityMeter();
        eventDetector = new EventDetector();

        // Assemble panels
        serverPanel = new JPanel(new BorderLayout());
        serverPanel.add(serverHistogram, BorderLayout.CENTER);
        serverPanel.add(serverMeter, BorderLayout.SOUTH);
        serverPanel.setBorder(BorderFactory.createTitledBorder("Server"));

        clientPanel = new JPanel(new BorderLayout());
        clientPanel.add(clientHistogram, BorderLayout.CENTER);
        clientPanel.add(clientMeter, BorderLayout.SOUTH);
        clientPanel.setBorder(BorderFactory.createTitledBorder("Client"));

        eventPanel = new JPanel(new BorderLayout());
        eventPanel.add(eventDetector, BorderLayout.CENTER);

        // Central layout
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(serverPanel);
        centerPanel.add(clientPanel);
        centerPanel.add(eventPanel);
        add(centerPanel, BorderLayout.CENTER);

        // Control panel
        JPanel controls = new JPanel(new GridLayout(3, 1));
        controls.setPreferredSize(new Dimension(1000, 100));

        nifComboBox = new JComboBox<>();
        try {
            for (PcapNetworkInterface nif : Pcaps.findAllDevs()) nifComboBox.addItem(nif);
        } catch (PcapNativeException ex) { ex.printStackTrace(); }
        nifComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PcapNetworkInterface) {
                    PcapNetworkInterface nif = (PcapNetworkInterface) value;
                    String desc = nif.getDescription();
                    setText((desc != null && !desc.isEmpty()) ? desc : nif.getName());
                }
                return this;
            }
        });

        ipFilterField = new JTextField(15);
        toggleCaptureBtn = new JButton("Start Capture");
        toggleCaptureBtn.addActionListener(e -> {
            if (!capturing) startCapture(); else stopCapture();
        });

        JPanel topRow = new JPanel();
        topRow.add(new JLabel("Interface:"));
        topRow.add(nifComboBox);

        JPanel botRow = new JPanel();
        botRow.add(new JLabel("Server IP:"));
        botRow.add(ipFilterField);
        botRow.add(toggleCaptureBtn);

        // Checkboxes S, C, E
        JPanel checkRow = new JPanel();
        checkS = new JCheckBox("Server", true);
        checkC = new JCheckBox("Client", true);
        checkE = new JCheckBox("EventDetector", true);
        checkRow.add(checkS);
        checkRow.add(checkC);
        checkRow.add(checkE);

        // Visibility handlers
        checkS.addItemListener(e -> serverPanel.setVisible(e.getStateChange() == ItemEvent.SELECTED));
        checkC.addItemListener(e -> clientPanel.setVisible(e.getStateChange() == ItemEvent.SELECTED));
        checkE.addItemListener(e -> eventPanel.setVisible(e.getStateChange() == ItemEvent.SELECTED));

        // Apply initial visibility
        serverPanel.setVisible(checkS.isSelected());
        clientPanel.setVisible(checkC.isSelected());
        eventPanel.setVisible(checkE.isSelected());

        controls.add(topRow);
        controls.add(botRow);
        controls.add(checkRow);
        add(controls, BorderLayout.NORTH);

        statusLabel = new JLabel("Status: Ready");
        add(statusLabel, BorderLayout.SOUTH);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopCapture();
                dispose();
            }
        });
    }

    private void startCapture() {
        PcapNetworkInterface nif = (PcapNetworkInterface) nifComboBox.getSelectedItem();
        if (nif == null) return;

        serverIp = ipFilterField.getText().trim();
        if (serverIp.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a server IP address!");
            return;
        }
        if (!IPV4_PATTERN.matcher(serverIp).matches()) {
            JOptionPane.showMessageDialog(this, "Invalid IPv4 address format.\nExpected: xxx.xxx.xxx.xxx");
            return;
        }

        final String filter = "udp and host " + serverIp;
        new Thread(() -> {
            try {
                handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
                handle.setFilter(filter, BpfProgram.BpfCompileMode.OPTIMIZE);
                capturing = true;
                startTimers();
                SwingUtilities.invokeLater(() -> {
                    toggleCaptureBtn.setText("Stop Capture");
                    statusLabel.setText("Status: Capturing...");
                });
                while (capturing) {
                    Packet packet = handle.getNextPacket();
                    if (packet != null) processPacket(packet);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                if (handle != null && handle.isOpen()) handle.close();
            }
        }).start();
    }

    private void stopCapture() {
        capturing = false;
        stopTimers();
        SwingUtilities.invokeLater(() -> {
            toggleCaptureBtn.setText("Start Capture");
            statusLabel.setText("Status: Stopped");
        });
    }

    private void processPacket(Packet packet) {
        UdpPacket udp = packet.get(UdpPacket.class);
        IpV4Packet ip = packet.get(IpV4Packet.class);
        if (udp == null || ip == null) return;
        String srcIp = ip.getHeader().getSrcAddr().getHostAddress();
        boolean isServer = srcIp.equals(serverIp);
        byte[] data = udp.getPayload().getRawData();
        for (int off = 0; off + 16 <= data.length; off += 16) {
            int objId = ((data[off + 1] & 0xFF) << 8) | (data[off + 2] & 0xFF);
            int baseId = objId / 100;
            int version = objId % 100;
            if (baseId >= 0 && baseId <= 655 && version >= 0 && version <= 99) {
                if (isServer) {
                    synchronized (serverRowBuffer) { serverRowBuffer[baseId] = true; }
                    serverRpcCounter++;
                } else {
                    synchronized (clientRowBuffer) { clientRowBuffer[baseId] = true; }
                    clientRpcCounter++;
                }
            }
        }
    }

    private void startTimers() {
        snapshotTimer = new Timer("SnapshotTimer", true);
        snapshotTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Server snapshot
                boolean[] serverSnap;
                synchronized (serverRowBuffer) {
                    serverSnap = Arrays.copyOf(serverRowBuffer, serverRowBuffer.length);
                    Arrays.fill(serverRowBuffer, false);
                }
                int serverActive = 0;
                for (boolean b : serverSnap) if (b) serverActive++;
                serverHistogram.pushRow(serverSnap, serverActive);

                boolean hasActivity = (serverActive > 0);
                boolean isEvent = serverHistogram.isEvent(serverActive);
                eventDetector.updateServer(hasActivity, isEvent);

                // Client snapshot
                boolean[] clientSnap;
                synchronized (clientRowBuffer) {
                    clientSnap = Arrays.copyOf(clientRowBuffer, clientRowBuffer.length);
                    Arrays.fill(clientRowBuffer, false);
                }
                int clientActive = 0;
                for (boolean b : clientSnap) if (b) clientActive++;
                clientHistogram.pushRow(clientSnap, clientActive);

                boolean clientHasActivity = (clientActive > 0);
                boolean clientIsEvent = clientHistogram.isEvent(clientActive);
                eventDetector.updateClient(clientHasActivity, clientIsEvent);
            }
        }, SNAPSHOT_INTERVAL_MS, SNAPSHOT_INTERVAL_MS);

        rpcMeterTimer = new Timer("RPCMeterTimer", true);
        rpcMeterTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int srv = serverRpcCounter; serverRpcCounter = 0;
                int clt = clientRpcCounter; clientRpcCounter = 0;
                SwingUtilities.invokeLater(() -> {
                    serverMeter.takeSample(srv);
                    clientMeter.takeSample(clt);
                });
            }
        }, 1000, 1000);
    }

    private void stopTimers() {
        if (snapshotTimer != null) { snapshotTimer.cancel(); snapshotTimer = null; }
        if (rpcMeterTimer != null) { rpcMeterTimer.cancel(); rpcMeterTimer = null; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { e.printStackTrace(); }
            new DayZLiveCapture().setVisible(true);
        });
    }
}
