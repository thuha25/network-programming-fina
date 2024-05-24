package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

public class Client extends JFrame {

    // UI components
    private JTextField canBoCoiThiField;
    private JTextField phongThiField;
    private JButton browseButton1;
    private JButton browseButton2;
    private JButton submitButton;
    private JList<String> fileList;
    private DefaultListModel<String> fileListModel;
    private JButton viewButton;
    private JTable dataTable;
    private DefaultTableModel tableModel;
    private JLabel file1Label;
    private JLabel file2Label;
    private JButton nextButton;
    private JButton backButton;

    private static int count = 0;

    // Constants
    private static final int HEIGHT = 600;
    private static final int WIDTH = 800;

    // Server details
    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private Socket socket;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client().setVisible(true));
    }

    public Client() {
        setTitle("Client");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(173, 216, 230, 70));

        // Initialize UI components
        canBoCoiThiField = new JTextField(35);
        phongThiField = new JTextField(35);
        browseButton1 = new JButton("Browse...");
        browseButton2 = new JButton("Browse...");
        submitButton = new JButton("Submit");
        nextButton = new JButton("Next");
        backButton = new JButton("Back");

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        viewButton = new JButton("View");

        // Set layout for the main frame
        getContentPane().setLayout(new BorderLayout());

        // Input file panel
        JPanel filePanel = createFilePanel();
        getContentPane().add(filePanel, BorderLayout.NORTH);

        // Table model for displaying CSV data
        tableModel = new DefaultTableModel();

        // Received files panel
        JPanel listPanel = createListPanel();
        getContentPane().add(listPanel, BorderLayout.CENTER);
        loadFilesFromDataFolder();

        // Action listeners (unchanged from your code)
        browseButton1.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File("."));
            int returnValue = fileChooser.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                canBoCoiThiField.setText(selectedFile.getAbsolutePath());
            }
        });

        browseButton2.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File("."));
            int returnValue = fileChooser.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                phongThiField.setText(selectedFile.getAbsolutePath());
            }
        });

        nextButton.addActionListener(e -> {
            count++;
            sendCountToServer();
            tableModel.setRowCount(0);
        });

        backButton.addActionListener(e -> {
            count--;
            if (count < 0) {
                count = 0;
            }
            sendCountToServer();
            tableModel.setRowCount(0);
        });

        submitButton.addActionListener(e -> {
            String canBoCoiThiFilePath = canBoCoiThiField.getText();
            String phongThiFilePath = phongThiField.getText();
            if (!canBoCoiThiFilePath.isEmpty() && !phongThiFilePath.isEmpty()) {
                sendFilesToServer(canBoCoiThiFilePath, phongThiFilePath);
            } else {
                JOptionPane.showMessageDialog(null, "Please select both files", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        viewButton.addActionListener(e -> {
            String selectedFileName = fileList.getSelectedValue();
            if (selectedFileName != null) {
                viewCSVFile(selectedFileName);
            }
        });

        // Connect to server in a background thread
        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        try {
            socket = new Socket(HOST, PORT);
            new Thread(() -> {
                while (true) {
                    try {
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        // Receive the files
                        for (int i = 0; i < 2; i++) {
                            String fileName = in.readUTF();
                            long fileSize = in.readLong();

                            File file = new File("./client/data/" + fileName);
                            try (FileOutputStream fileOutputStream = new FileOutputStream(file);
                                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
                                            fileOutputStream)) {

                                byte[] buffer = new byte[4096];
                                long remainingBytes = fileSize;
                                int bytesRead;
                                while (remainingBytes > 0 && (bytesRead = in.read(buffer, 0,
                                        (int) Math.min(buffer.length, remainingBytes))) != -1) {
                                    bufferedOutputStream.write(buffer, 0, bytesRead);
                                    remainingBytes -= bytesRead;
                                }
                                bufferedOutputStream.flush();
                            }
                        }

                        // Update the file list
                        loadFilesFromDataFolder();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        break;
                    }
                }
            }).start();
        } catch (Exception e) {
            // Auto reconnect
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
            }
            connectToServer();
        }
    }

    private JPanel createFilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(240, 240, 240));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10); // Padding around components

        // Cán bộ coi thi (Examiner) Label and Text Field
        JLabel canBoCoiThiLabel = new JLabel("Cán bộ coi thi:");
        gbc.gridx = 0; // Column 0
        gbc.gridy = 0; // Row 0
        panel.add(canBoCoiThiLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2; // Span 2 columns for the text field
        gbc.fill = GridBagConstraints.HORIZONTAL; // Make the text field expand horizontally
        panel.add(canBoCoiThiField, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1; // Reset to 1 column
        gbc.fill = GridBagConstraints.NONE; // Reset fill
        panel.add(browseButton1, gbc);

        // Phòng thi (Exam Room) Label and Text Field
        JLabel phongThiLabel = new JLabel("Phòng thi:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(phongThiLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(phongThiField, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(browseButton2, gbc);

        // Generate Button
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        panel.add(submitButton, gbc);

        // Right Button
        gbc.gridx = 3;
        gbc.gridy = 2;
        panel.add(nextButton, gbc);

        // Left Button
        gbc.gridx = 2;
        gbc.gridy = 2;
        panel.add(backButton, gbc);

        TitledBorder fileBorder = BorderFactory.createTitledBorder("Input Files");
        fileBorder.setTitleJustification(TitledBorder.CENTER);
        panel.setBorder(fileBorder);
        return panel;
    }

    private JPanel createListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(220, 220, 220));

        // Panel for file labels and view buttons
        JPanel filePanel = new JPanel(new GridLayout(2, 2)); // 2 rows, 2 columns

        // File 1 components
        file1Label = new JLabel(fileListModel.size() > 0 ? fileListModel.get(0) : "");
        filePanel.add(file1Label);
        JButton viewFile1Button = new JButton("View File");
        filePanel.add(viewFile1Button);

        // File 2 components
        file2Label = new JLabel(fileListModel.size() > 1 ? fileListModel.get(1) : "");
        filePanel.add(file2Label);
        JButton viewFile2Button = new JButton("View File");
        filePanel.add(viewFile2Button);

        panel.add(filePanel, BorderLayout.NORTH);

        // Table for CSV data
        dataTable = new JTable(tableModel);
        // Get the table header
        JTableHeader header = dataTable.getTableHeader();
        header.setBackground(Color.WHITE);
        // Set custom header font (Bold)
        header.setFont(header.getFont().deriveFont(Font.BOLD)); //

        // Set custom row colors
        dataTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (row % 2 == 0) {
                    c.setBackground(table.getBackground());
                } else {
                    c.setBackground(new Color(82, 250, 166, 55)); // Odd rows
                }
                if (c instanceof JLabel) {
                    ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER); // Center align the cell text
                }
                return c;
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(dataTable);
        panel.add(tableScrollPane, BorderLayout.CENTER);

        TitledBorder listBorder = BorderFactory.createTitledBorder("Received Files and Data");
        listBorder.setTitleJustification(TitledBorder.CENTER);
        panel.setBorder(listBorder);

        // Action listeners for view buttons
        viewFile1Button.addActionListener(e -> {
            String fileName = file1Label.getText();
            if (!fileName.isEmpty()) {
                viewCSVFile(fileName);
            }
        });

        viewFile2Button.addActionListener(e -> {
            String fileName = file2Label.getText();
            if (!fileName.isEmpty()) {
                viewCSVFile(fileName);
            }
        });

        return panel;
    }

    private void sendFilesToServer(String canBoCoiThiFilePath, String phongThiFilePath) {
        if (socket == null || socket.isClosed()) {
            JOptionPane.showMessageDialog(null, "Not connected to server", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            var out = new DataOutputStream(socket.getOutputStream());
            File canBoCoiThiFile = new File(canBoCoiThiFilePath);
            File phongThiFile = new File(phongThiFilePath);

            sendFile(canBoCoiThiFile, out);
            sendFile(phongThiFile, out);

            canBoCoiThiField.setText("");
            phongThiField.setText("");

        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error generating files: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendCountToServer() {
        if (socket == null || socket.isClosed()) {
            JOptionPane.showMessageDialog(null, "Not connected to server", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            var out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("count");
            out.writeInt(count);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error generating files: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendFile(File file, DataOutputStream out) throws IOException {
        out.writeUTF(file.getName());
        long fileSize = file.length();
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
    }

    private void viewCSVFile(String fileName) {
        try {
            // 1. Construct the full file path
            File csvFile = new File("./client/data/" + fileName);

            // 2. Check if the file exists
            if (!csvFile.exists()) {
                JOptionPane.showMessageDialog(null, "CSV file not found.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 3. Read the CSV data directly
            try (BufferedReader in = new BufferedReader(new FileReader(csvFile))) {
                String line;
                String[] headersArray = in.readLine().split(",");
                Vector<String> headers = new Vector<>(Arrays.asList(headersArray));
                DefaultTableModel model = new DefaultTableModel(headers, 0);

                while ((line = in.readLine()) != null) {
                    String[] rowDataArray = line.split(",");
                    Vector<String> rowData = new Vector<>(Arrays.asList(rowDataArray));
                    model.addRow(rowData);
                }

                // Update the table model
                tableModel.setDataVector(model.getDataVector(), headers);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error viewing CSV file: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadFilesFromDataFolder() {
        File dataDir = new File("./client/data/");

        fileListModel.clear();

        // Ensure the directory exists
        if (dataDir.exists() && dataDir.isDirectory()) {
            // Get CSV files from the directory
            File[] csvFiles = dataDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".csv");
                }
            });

            // Populate the file list model
            if (csvFiles != null) {
                for (File file : csvFiles) {
                    fileListModel.addElement(file.getName());
                }
            }
        } else {
            // Handle the case where the directory is missing or not a directory
            JOptionPane.showMessageDialog(this, "Data directory not found or invalid.", "Error",
                    JOptionPane.ERROR_MESSAGE);
        }

        // Update the file labels
        if (fileListModel.size() >= 2) {
            file1Label.setText(fileListModel.get(0));
            file2Label.setText(fileListModel.get(1));
        }
    }
}