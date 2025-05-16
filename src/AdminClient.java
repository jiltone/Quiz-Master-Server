package src;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class AdminClient extends JFrame {
    private JTextArea questionTextArea;
    private JButton sendButton;
    private JButton doneButton;
    private JTextArea outputArea;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public AdminClient() {
        super("Admin Control Panel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);

        // Main panel with gradient background
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                Color color1 = new Color(66, 135, 245);
                Color color2 = new Color(34, 87, 122);
                GradientPaint gp = new GradientPaint(0, 0, color1, 0, h, color2);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
            }
        };
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Quiz Admin Dashboard", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Question input area
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setOpaque(false);
        
        JLabel instructionLabel = new JLabel("Enter questions (format: question;A;B;C;D;correct):");
        instructionLabel.setForeground(Color.WHITE);
        instructionLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        inputPanel.add(instructionLabel, BorderLayout.NORTH);

        questionTextArea = new JTextArea(10, 40);
        questionTextArea.setFont(new Font("Arial", Font.PLAIN, 16));
        questionTextArea.setLineWrap(true);
        questionTextArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(questionTextArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.WHITE), "Questions Input",
            0, 0, new Font("Arial", Font.PLAIN, 14), Color.WHITE));
        inputPanel.add(scrollPane, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setOpaque(false);
        
        sendButton = createStyledButton("Send Questions", new Color(46, 204, 113));
        doneButton = createStyledButton("Finish Quiz", new Color(231, 76, 60));
        buttonPanel.add(sendButton);
        buttonPanel.add(doneButton);

        inputPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // Output area
        outputArea = new JTextArea(8, 40);
        outputArea.setFont(new Font("Arial", Font.PLAIN, 16));
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(255, 255, 255, 230));
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.WHITE), "Server Feedback",
            0, 0, new Font("Arial", Font.PLAIN, 14), Color.WHITE));
        mainPanel.add(outputScrollPane, BorderLayout.SOUTH);

        add(mainPanel);

        // Event Listeners
        sendButton.addActionListener(e -> sendQuestionsToServer());
        doneButton.addActionListener(e -> sendDoneToServer());

        // Connect to server
        try {
            socket = new Socket("127.0.0.1", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("Admin");
            new Thread(this::receiveServerMessages).start();
        } catch (IOException ex) {
            outputArea.append("Error connecting to server: " + ex.getMessage() + "\n");
            sendButton.setEnabled(false);
            doneButton.setEnabled(false);
        }

        setVisible(true);
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 16));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(160, 45));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });
        return button;
    }

    private void sendQuestionsToServer() {
        String questions = questionTextArea.getText();
        out.println(questions);
        questionTextArea.setText("");
        outputArea.append("Questions sent to server\n");
    }

    private void sendDoneToServer() {
        out.println("DONE");
        outputArea.append("Quiz setup completed\n");
    }

    private void receiveServerMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("FINAL_SCORES|")) {
                    String finalScores = message.substring(13);
                    outputArea.append("Final Scores:\n" + finalScores + "\n");
                } else {
                    outputArea.append(message + "\n");
                }
            }
        } catch (IOException e) {
            outputArea.append("Error reading from server: " + e.getMessage() + "\n");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                outputArea.append("Error closing socket: " + e.getMessage() + "\n");
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AdminClient::new);
    }
}