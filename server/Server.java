package server;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class Server {

    // Files to be used by the Python script
    static String SUPERVISOR_FILE = "./server/data/canbocoithi.csv";
    static String ROOM_FILE = "./server/data/phongthi.csv";
    static String LOBBY_DEVISION_FILE = "./server/result/danhsachgiamsat.csv";
    static String ROOM_DEVISION_FILE = "./server/result/danhsachphancong.csv";

    // Python file to run
    static String PYTHON_FILE = "app.py";
    static String CURRENT_DIR = System.getProperty("user.dir") + "/server";

    // UI components
    static JTextPane logPane;

    // Main
    public static void main(String[] args) {
        // Set up the UI in the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });

        // Start the server
        try (ServerSocket server = new ServerSocket(8080)) {
            appendLog("Server started on port 8080");
            while (true) {
                Socket soc = server.accept();
                new Thread(() -> handleClient(soc)).start();
            }
        } catch (Exception ex) {
            appendLog("Server error: " + ex.toString());
        }
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Server Log");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        logPane = new JTextPane();
        logPane.setEditable(false);
        Color lightBlue = new Color(173, 216, 230, 70); // Light blue color
        logPane.setBackground(lightBlue);

        JScrollPane scrollPane = new JScrollPane(logPane);
        scrollPane.setBorder(new MatteBorder(6, 6, 6, 6, lightBlue)); // Padding all

        frame.getContentPane().add(scrollPane);
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);
    }

    private static void handleClient(Socket socket) {
        var address = socket.getInetAddress();
        String logPrefix = String.format("Client(%s:%d)", address.getHostAddress(), socket.getPort());
        appendLog(String.format("%s connected", logPrefix));
        while (true) {
            try {
                int count = 0;
                var in = new DataInputStream(socket.getInputStream());
                // Receive the count
                String check = in.readUTF();
                if (check.equalsIgnoreCase("count")) {
                    count = in.readInt();
                    appendLog(String.format("%s sent count: %d", logPrefix, count));
                } else {
                    // Receive the files
                    for (int i = 0; i < 2; i++) {

                        String fileName = i == 0 ? check : in.readUTF();
                        long fileSize = in.readLong();

                        File file = new File(i == 0 ? SUPERVISOR_FILE : ROOM_FILE);
                        try (var fileOutputStream = new FileOutputStream(file);
                                var bufferedOutputStream = new BufferedOutputStream(fileOutputStream)) {

                            byte[] buffer = new byte[4096];
                            long remainingBytes = fileSize;
                            int bytesRead;
                            while (remainingBytes > 0 && (bytesRead = in.read(buffer, 0,
                                    (int) Math.min(buffer.length, remainingBytes))) != -1) {
                                bufferedOutputStream.write(buffer, 0, bytesRead);
                                remainingBytes -= bytesRead;
                            }
                            bufferedOutputStream.flush();
                            appendLog(String.format("%s sent file: %s", logPrefix, fileName));
                        }
                    }
                }

                // Run the Python script
                runPythonScript(PYTHON_FILE, count, CURRENT_DIR, true);

                // Send the result files
                var out = new DataOutputStream(socket.getOutputStream());
                for (int i = 0; i < 2; i++) {
                    String path = i == 0 ? LOBBY_DEVISION_FILE : ROOM_DEVISION_FILE;
                    File file = new File(path);
                    String fileName = file.getName();
                    long fileSize = file.length();

                    out.writeUTF(fileName);
                    out.writeLong(fileSize);

                    try (var fileInputStream = new FileInputStream(file);
                            var bufferedInputStream = new BufferedInputStream(fileInputStream)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        out.flush();
                    }
                    appendLog(String.format("Sent back to %s: %s", logPrefix, fileName));
                }
            } catch (EOFException ex) {
                appendLog(String.format("%s disconnected", logPrefix));
                break;
            } catch (Exception ex) {
                appendLog(String.format("%s error: %s", logPrefix, ex.toString()));
                break;
            }
        }
    }

    private static void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = logPane.getStyledDocument();

                // Style for timestamp
                Style timestampStyle = doc.addStyle("timestampStyle", null);
                StyleConstants.setForeground(timestampStyle, Color.blue); // Light blue color

                // Style for log message
                Style messageStyle = doc.addStyle("messageStyle", null);
                StyleConstants.setForeground(messageStyle, Color.BLACK);

                // Add the timestamp
                String timestamp = String.format("[%s] ",
                        (new SimpleDateFormat("dd-MM-yyyy HH:mm:ss")).format(new Date()));
                doc.insertString(doc.getLength(), timestamp, timestampStyle);

                // Add the log message
                doc.insertString(doc.getLength(), message + "\n", messageStyle);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    static void runPythonScript(String scriptName, int count, String currentDir, boolean isPython3)
            throws Exception {
        String logPrefix = scriptName;
        try {
            // Command to run the Python script
            String[] command = { isPython3 ? "python3" : "python", scriptName, String.valueOf(count) };
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(currentDir));
            Process p = pb.start();

            // Reading the output of the Python script
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                appendLog(String.format("%s Output: %s", logPrefix, line));
            }

            // Reading the error stream of the Python script
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                appendLog(String.format("%s Error: %s", logPrefix, errorLine));
            }

            // Wait for the process to complete
            int exitCode = p.waitFor();
            if (exitCode == 0)
                appendLog(String.format("%s: Generate file successfully", logPrefix));
            else {
                appendLog(String.format("%s Exited with code: %d", logPrefix, exitCode));
                throw new Exception(String.format("%s Exited with code: %d", logPrefix, exitCode));
            }
        } catch (IOException | InterruptedException e) {
            appendLog(String.format("%s Script error: %s", logPrefix, e.toString()));
        }
    }
}
