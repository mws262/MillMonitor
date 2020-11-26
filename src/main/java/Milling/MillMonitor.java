package Milling;

import com.fazecast.jSerialComm.SerialPort;
import org.apache.logging.log4j.*;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MillMonitor extends JFrame {

    private final int BAUD = 9600;
    private final int ENDLINE_VAL = 10;
    private final int EXPECTED_INPUT_LEN = 16;
    private InputStream inputStream;
    private List<JLabel> labels = new ArrayList<>();

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
        SerialPort arduinoPort = null;
        while (arduinoPort == null) { // Keep looking until we find it.
            SerialPort[] serialPorts = SerialPort.getCommPorts();
            for (SerialPort sp : serialPorts) {
                System.out.println("Found: " + sp.getSystemPortName() + ", " + sp.getDescriptivePortName() + ", " + sp.getPortDescription());
                if (sp.getPortDescription().contains("USB-Based Serial Port") || sp.getPortDescription().contains(
                        "Arduino")) {
                    if (arduinoPort == null) {
                        arduinoPort = sp;
                    } else {
                        System.out.println("Multiple USB-serial ports found. May have identified the wrong one.");
                    }
                }
            }
            // If not found, then wait and try again.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Open the port when the correct one is found.
        arduinoPort.openPort();
        arduinoPort.setBaudRate(BAUD);
        inputStream = arduinoPort.getInputStream();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void parseReceived(List<Integer> values) {

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

    public void listenToSerial() {
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
                            // System.out.print(val);
                        }
                    }
                }
            } catch (IOException e) {
                // When a disconnection occurs, then go back to searching for it.
                System.out.println("Port disconnected.");
                for (JLabel label : labels) {
                    label.setBackground(Color.BLACK);
                }
                findActiveSerial();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new MillMonitor();
    }
}
