package Milling;

import com.fazecast.jSerialComm.SerialPort;
import org.apache.logging.log4j.*;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.io.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MillMonitor extends JFrame {

    private final int BAUD = 9600;
    private final int ENDLINE_VAL = 10;
    private final int EXPECTED_INPUT_LEN = 16;
    private InputStream inputStream;
    private List<JLabel> labels = new ArrayList<>();
    private SerialPort arduinoPort = null;

    private static final Logger logger = LogManager.getLogger(MillMonitor.class);

    public MillMonitor() {
//        logger.error("Test1");
        setPreferredSize(new Dimension(800, 100));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(new GridLayout(1, 8));
        setTitle("MillMonitor");
        for (int i = 0; i < 8; i++) {
            JLabel label = new JLabel("Mill " + i);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
            add(label);
            labels.add(label);
        }
        pack();
        setVisible(true);
        findActiveSerial();
        listenToSerial();
    }

    public void findActiveSerial() {
        // Get the correct serial port.
        arduinoPort = null;
        while (arduinoPort == null) { // Keep looking until we find it.
            SerialPort[] serialPorts = SerialPort.getCommPorts();

            // No longer relying on serial port names to identify. Too diverse between OS.
            // Instead sending a message which should elicit a specific response from the correct device.
            List<Integer> readVals = new ArrayList<>();
            for (SerialPort sp : serialPorts) {
                System.out.println("Found: " + sp.getSystemPortName() + ", " + sp.getDescriptivePortName() + ", " + sp.getPortDescription() + ", Baud: " + sp.getBaudRate());

                if (sp.getDescriptivePortName().toLowerCase().contains("bluetooth")) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                final Duration timeout = Duration.ofSeconds(4);
                ExecutorService executor = Executors.newSingleThreadExecutor();

                final Future<String> handler = executor.submit(() -> {

                    sp.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
//                    sp.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 1000, 1000);
//                    sp.clearDTR();
//                    sp.clearRTS();
                    sp.setBaudRate(BAUD);
                    sp.openPort();

                    Thread.sleep(500);
                    try {
                        InputStream inputStream = sp.getInputStream();

                        while (true) {
                            Thread.sleep(50);

                            while (inputStream.available() > 0) {
                                int v = inputStream.read();
                                if (v != ENDLINE_VAL) {
                                    readVals.add(v);
                                } else {
                                    System.out.println(readVals.size());

                                    boolean allInRange = readVals.stream().allMatch(s -> s == 0 || s == 1);
                                    if (readVals.size() == 16 && allInRange) {
                                        System.out.println("found the arduino");
                                        return "found";
                                    }
                                    readVals.clear();
                                }
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        System.out.println("IOException caught and ignored.");
                    }
                    return "unfound";
                });

                try {
                    handler.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException | InterruptedException | ExecutionException e) {
                    handler.cancel(true);
                }

                executor.shutdownNow();
                try {
                    if (!handler.isCancelled() && handler.get().equals("found")) {
                        arduinoPort = sp;
                        InputStream in = arduinoPort.getInputStream();
                        while (arduinoPort.bytesAvailable() > 0) {
                            in.read();
                        }
                        break;
                    } else {
                        sp.closePort();
                    }
                } catch (InterruptedException | ExecutionException | IOException e) {
                    System.out.println("ignoring an error when finishing looking at a specific port.");
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
        }

        inputStream = arduinoPort.getInputStream();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void parseReceived(List<Integer> values) {

        StringBuilder sb = new StringBuilder();
        sb.append('\t');
        for (int i = 0; i < values.size(); i += 2) {
            int v1 = values.get(i);
            int v2 = values.get(i + 1);

            JLabel l = labels.get(i / 2);
            switch (v1 + 10 * v2) {
                case 0:
                    sb.append("offline\t");
                    l.setBackground(Color.LIGHT_GRAY);
                    break;
                case 1:
                    // Input 1 is on.
                    sb.append("red\t");
                    l.setBackground(Color.RED);
                    break;
                case 10:
                    // Input 2 is on.
                    sb.append("green\t");
                    l.setBackground(Color.GREEN);
                    break;
                case 11:
                    // Both are on.
                    sb.append("yellow\t");
                    l.setBackground(Color.YELLOW);
                    break;
                default:
                    throw new IllegalStateException("Not valid combination. " + (v1 + 10 * v2));
            }
        }
        logger.info(sb.toString());
    }

    private void listenToSerial() {
        List<Integer> readValues = new ArrayList<>(24);
        while (true) {
            try {
                if (inputStream.available() > 0) {
                    while (inputStream.available() > 0) {
                        int val = inputStream.read();
                        if (val == ENDLINE_VAL) {
                            // Reject partially-completed strings of values.
                            System.out.println();
                            if (readValues.size() != EXPECTED_INPUT_LEN) {
                                System.out.println("Unexpected completed message. Contained " + readValues.size() + " elements.");
                            } else {
                                parseReceived(readValues);
                            }
                            readValues.clear();
                        } else {
                            readValues.add(val);
                            System.out.print(val);
                        }
                    }
                }
            } catch (IOException e) {
                // When a disconnection occurs, then go back to searching for it.
                System.out.println("Port disconnected.");
//                try {
//                    inputStream.close();
//                } catch (IOException ignored) {
//                }

                arduinoPort.closePort();
                for (JLabel label : labels) {
                    label.setBackground(Color.BLACK);
                }
                findActiveSerial();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new MillMonitor();
    }
}
