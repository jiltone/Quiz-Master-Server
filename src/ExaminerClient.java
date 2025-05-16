package src;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ExaminerClient extends JFrame {
    private JLabel questionLabel;
    private JPanel optionsPanel;
    private JButton optionAButton, optionBButton, optionCButton, optionDButton;
    private JTextArea outputArea;
    private JTextField nameField;
    private JButton submitNameButton;
    private String examinerName;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ExaminerClient() {
        super("Quiz Examiner");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
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
                Color color1 = new Color(74, 144, 226);
                Color color2 = new Color(41, 128, 185);
                GradientPaint gp = new GradientPaint(0, 0, color1, 0, h, color2);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
            }
        };
        mainPanel.setLayout(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Name input panel
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        namePanel.setOpaque(false);
        JLabel nameLabel = new JLabel("Enter Your Name:");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 18));
        nameField = new JTextField(20);
        nameField.setFont(new Font("Arial", Font.PLAIN, 16));
        submitNameButton = createStyledButton("Start Quiz", new Color(46, 204, 113));
        namePanel.add(nameLabel);
        namePanel.add(nameField);
        namePanel.add(submitNameButton);
        mainPanel.add(namePanel, BorderLayout.NORTH);

        // Question panel
        JPanel questionPanel = new JPanel(new BorderLayout(10, 10));
        questionPanel.setOpaque(false);
        questionLabel = new JLabel("Waiting for questions...", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 20));
        questionLabel.setForeground(Color.WHITE);
        questionPanel.add(questionLabel, BorderLayout.NORTH);

        optionsPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        optionsPanel.setOpaque(false);
        optionAButton = createOptionButton("A");
        optionBButton = createOptionButton("B");
        optionCButton = createOptionButton("C");
        optionDButton = createOptionButton("D");
        optionsPanel.add(optionAButton);
        optionsPanel.add(optionBButton);
        optionsPanel.add(optionCButton);
        optionsPanel.add(optionDButton);
        questionPanel.add(optionsPanel, BorderLayout.CENTER);
        mainPanel.add(questionPanel, BorderLayout.CENTER);

        // Output area
        outputArea = new JTextArea(10, 30);
        outputArea.setFont(new Font("Arial", Font.PLAIN, 16));
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(255, 255, 255, 230));
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.WHITE), "Quiz Progress",
            0, 0, new Font("Arial", Font.PLAIN, 14), Color.WHITE));
        mainPanel.add(outputScrollPane, BorderLayout.EAST);

        setOptionsEnabled(false);
        add(mainPanel);

        // Event Listeners
        submitNameButton.addActionListener(e -> {
            examinerName = nameField.getText();
            if (examinerName != null && !examinerName.trim().isEmpty()) {
                connectToServer(examinerName);
                namePanel.setVisible(false);
            } else {
                JOptionPane.showMessageDialog(this, "Please enter a valid name.");
            }
        });

        ActionListener optionListener = e -> {
            String optionLetter = e.getActionCommand(); // Use the action command directly ("A", "B", "C", or "D")
            sendAnswer(optionLetter);
            setOptionsEnabled(false);
        };

        optionAButton.addActionListener(optionListener);
        optionBButton.addActionListener(optionListener);
        optionCButton.addActionListener(optionListener);
        optionDButton.addActionListener(optionListener);

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

    private JButton createOptionButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 18));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(52, 152, 219));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.WHITE, 2),
            BorderFactory.createEmptyBorder(10, 20, 10, 20)));
        button.setOpaque(true);
        button.setActionCommand(text); // Set action command to "A", "B", "C", or "D"
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(82, 182, 249));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(52, 152, 219));
            }
        });
        return button;
    }

    private void connectToServer(String name) {
        try {
            socket = new Socket("127.0.0.1", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(name);
            outputArea.append("Connected as " + name + "\n");
            new Thread(this::receiveQuestions).start();
        } catch (IOException e) {
            outputArea.append("Error connecting to server: " + e.getMessage() + "\n");
            JOptionPane.showMessageDialog(this, "Error connecting to server: " + e.getMessage());
        }
    }

    private void receiveQuestions() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("END")) {
                    outputArea.append("Quiz Ended!\n");
                    break;
                } else if (message.startsWith("RESULT|")) {
                    outputArea.append(message.substring(7) + "\n");
                } else if (message.startsWith("LEADERBOARD|")) {
                    outputArea.append("Leaderboard:\n" + message.substring(12).replace("|", "\n") + "\n");
                } else if (message.startsWith("FINAL_SCORE|")) {
                    outputArea.append("Your final score: " + message.substring(12) + "\n");
                    JOptionPane.showMessageDialog(this, "Quiz Ended! Your score: " + message.substring(12));
                } else {
                    displayQuestion(message);
                }
            }
        } catch (IOException e) {
            outputArea.append("Error receiving data: " + e.getMessage() + "\n");
        } finally {
            closeConnection();
        }
    }

    private void displayQuestion(String questionData) {
        String[] parts = questionData.split("\\|");
        if (parts.length == 5) {
            questionLabel.setText("<html><center>" + parts[0].trim() + "</center></html>");
            optionAButton.setText("<html>A) " + parts[1].trim() + "</html>");
            optionBButton.setText("<html>B) " + parts[2].trim() + "</html>");
            optionCButton.setText("<html>C) " + parts[3].trim() + "</html>");
            optionDButton.setText("<html>D) " + parts[4].trim() + "</html>");
            setOptionsEnabled(true);
        } else {
            outputArea.append("Error: Invalid question format: " + questionData + "\n");
        }
    }

    private void sendAnswer(String optionLetter) {
        out.println(optionLetter); // Send the option letter ("A", "B", "C", or "D") directly
        outputArea.append("Answer sent: " + optionLetter + "\n");
    }

    private void setOptionsEnabled(boolean enabled) {
        optionAButton.setEnabled(enabled);
        optionBButton.setEnabled(enabled);
        optionCButton.setEnabled(enabled);
        optionDButton.setEnabled(enabled);
    }

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            outputArea.append("Error closing connection: " + e.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ExaminerClient::new);
    }
}