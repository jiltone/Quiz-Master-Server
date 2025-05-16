import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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
        super("Examiner Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLayout(new BorderLayout());

        // 1. Create Components

        // Panel for name input
        JPanel namePanel = new JPanel();
        namePanel.setLayout(new FlowLayout());
        nameField = new JTextField(20);
        submitNameButton = new JButton("Submit Name");
        namePanel.add(new JLabel("Enter Your Name:"));
        namePanel.add(nameField);
        namePanel.add(submitNameButton);
        add(namePanel, BorderLayout.NORTH);

        questionLabel = new JLabel("Waiting for questions...");
        questionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(questionLabel, BorderLayout.CENTER);

        optionsPanel = new JPanel();
        optionsPanel.setLayout(new GridLayout(2, 2)); // 2 rows, 2 columns
        optionAButton = new JButton("A");
        optionBButton = new JButton("B");
        optionCButton = new JButton("C");
        optionDButton = new JButton("D");
        optionsPanel.add(optionAButton);
        optionsPanel.add(optionBButton);
        optionsPanel.add(optionCButton);
        optionsPanel.add(optionDButton);
        add(optionsPanel, BorderLayout.SOUTH);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        add(outputScrollPane, BorderLayout.EAST);

        // Disable options initially
        setOptionsEnabled(false);

        // 2. Event Listeners

        submitNameButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                examinerName = nameField.getText();
                if (examinerName != null && !examinerName.trim().isEmpty()) {
                    connectToServer(examinerName);
                    namePanel.setVisible(false); // Hide the name input panel
                } else {
                    JOptionPane.showMessageDialog(ExaminerClient.this, "Please enter a valid name.");
                }
            }
        });

        ActionListener optionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String answer = e.getActionCommand();
                sendAnswer(answer);
                setOptionsEnabled(false); // Disable options after answering
            }
        };

        optionAButton.addActionListener(optionListener);
        optionBButton.addActionListener(optionListener);
        optionCButton.addActionListener(optionListener);
        optionDButton.addActionListener(optionListener);

        setVisible(true);
    }

    private void connectToServer(String name) {
        try {
            socket = new Socket("127.0.0.1", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(name); // Send name to server
            outputArea.append("Connected to server as " + name + "\n");

            // Start a thread to listen for questions
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
                    String finalScore = in.readLine();
                    if (finalScore != null && finalScore.startsWith("FINAL_SCORE|")) {
                        outputArea.append("Quiz Ended! Your final score is: " + finalScore.substring(12) + "\n");
                        JOptionPane.showMessageDialog(this, "Quiz Ended! Your final score is: " + finalScore.substring(12));
                    } else {
                        outputArea.append("Quiz Ended! Your final score is: Unknown\n");
                    }
                    break;
                } else if (message.startsWith("RESULT|")) {
                    String result = message.substring(7); // Remove "RESULT|" prefix
                    outputArea.append("Result: " + result + "\n");
                } else if (message.startsWith("LEADERBOARD|")) {
                    String leaderboard = message.substring(12); // Remove "LEADERBOARD|" prefix
                    outputArea.append("Leaderboard:\n" + leaderboard.replace("|", "\n") + "\n");
                } else if (message.startsWith("FINAL_SCORE|")) {
                    String finalScore = message.substring(12); // Remove "FINAL_SCORE|" prefix
                    outputArea.append("Your final score: " + finalScore + "\n");
                } else {
                    displayQuestion(message);
                }
            }
        } catch (IOException e) {
            outputArea.append("Error receiving data from server: " + e.getMessage() + "\n");
            JOptionPane.showMessageDialog(this, "Error receiving data from server: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void displayQuestion(String questionData) {
        String[] parts = questionData.split("\\|"); // Split question data using "|"
        if (parts.length == 5) { // Expecting: question|A|B|C|D
            questionLabel.setText(parts[0].trim());
            optionAButton.setText("A) " + parts[1].trim());
            optionBButton.setText("B) " + parts[2].trim());
            optionCButton.setText("C) " + parts[3].trim());
            optionDButton.setText("D) " + parts[4].trim());

            setOptionsEnabled(true);
        } else {
            outputArea.append("Error: Invalid question format received: " + questionData + "\n");
        }
    }

    private void sendAnswer(String answer) {
        // Extract the option letter (e.g., "A" from "A) Berlin")
        String optionLetter = answer.trim().substring(0, 1).toUpperCase();
        out.println(optionLetter);
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