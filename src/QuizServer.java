package src;
import java.io.*;
import java.net.*;
import java.util.*;

public class QuizServer {
    private static Map<String, Integer> scores = new HashMap<>();
    private static List<Question> questions = new ArrayList<>();
    private static List<Socket> examiners = new ArrayList<>();
    private static List<Socket> admins = new ArrayList<>();
    private static int completedExaminers = 0;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started on port 12345");

            while (true) {
                Socket clientSocket = serverSocket.accept(); // Accept incoming client connection
                new Thread(new ClientHandler(clientSocket)).start(); // Handle each client in a new thread
            }
        } catch (IOException e) {
            System.err.println("Error starting the server: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        }

        @Override
        public void run() {
            try {
                String clientType = in.readLine(); // First message determines client type

                if (clientType.equals("Admin")) {
                    handleAdmin();
                } else {
                    handleExaminer(clientType);
                }
            } catch (IOException e) {
                System.err.println("Client connection error: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close(); // Ensure the socket is closed when the client disconnects
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void handleAdmin() throws IOException {
            System.out.println("Admin connected.");
            admins.add(clientSocket); // Add the admin to the list of connected clients
            while (true) {
                String questionData = in.readLine();
                if (questionData == null || questionData.equalsIgnoreCase("exit")) break;

                if (questionData.equalsIgnoreCase("DONE")) {
                    System.out.println("Admin added all questions to the quiz.");
                    break;
                }

                try {
                    String[] questionsArray = questionData.split("\n");
                    for (String questionLine : questionsArray) {
                        String[] parts = questionLine.split(";");
                        if (parts.length != 6) {
                            out.println("Error: Invalid question format. Use format: question;A;B;C;D;CorrectOption");
                            continue;
                        }

                        Question question = new Question(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
                        questions.add(question);
                        System.out.println("Question added: " + question.question);
                    }
                    out.println("Questions added successfully!");

                    // Send the questions to all connected examiners
                    sendQuestionsToExaminers();
                } catch (Exception e) {
                    out.println("Error adding questions: " + e.getMessage());
                }
            }
        }

        private void handleExaminer(String examinerName) throws IOException {
            if (examinerName == null || examinerName.trim().isEmpty()) {
                System.out.println("Invalid examiner name. Disconnecting client.");
                return;
            }

            System.out.println("Examiner connected: " + examinerName);
            scores.put(examinerName, 0); // Initialize the examiner's score
            examiners.add(clientSocket); // Add the examiner to the list of connected clients

            try {
                for (Question question : questions) {
                    // Send the question and all options to the examiner
                    out.println(question.toString());

                    String answer = in.readLine(); // Read the examiner's answer
                    if (answer == null || answer.isEmpty()) {
                        out.println("Invalid answer. Moving to the next question.");
                        continue;
                    }

                    // Extract the option letter (e.g., "A" from "A) Berlin")
                    String optionLetter = answer.trim().substring(0, 1).toUpperCase();

                    // Validate the answer
                    boolean isCorrect = question.correct.trim().equalsIgnoreCase(optionLetter);
                    if (isCorrect) {
                        scores.put(examinerName, scores.get(examinerName) + 1); // Update the score
                    }

                    // Send the result back to the examiner
                    String resultMessage = examinerName + " gave answer as " + optionLetter + ") " + getAnswerOption(optionLetter, question) +
                            (isCorrect ? " - Correct!" : " - Incorrect.");
                    out.println("RESULT|" + resultMessage); // Send feedback about correctness

                    // Log the result on the server's side
                    System.out.println(resultMessage);
                }

                // Send the final score to the examiner
                int finalScore = scores.get(examinerName);
                out.println("FINAL_SCORE|" + finalScore + "/" + questions.size());

                // Notify the examiner that the quiz has ended
                out.println("END");

                // Increment the count of completed examiners
                completedExaminers++;

                // If all examiners have completed the quiz, send the leaderboard and final scores
                if (completedExaminers == examiners.size()) {
                    System.out.println("All examiners have completed the quiz. Sending final scores to admin...");
                    sendLeaderboard(); // Send leaderboard to all examiners
                    sendFinalScoresToAdmins(); // Send final scores to admins
                    closeAllSockets(); // Close all sockets
                }
            } catch (IOException e) {
                System.err.println("Error handling examiner: " + e.getMessage());
                examiners.remove(clientSocket); // Remove disconnected examiner
            } finally {
                try {
                    clientSocket.close(); // Ensure the socket is closed when the client disconnects
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void sendQuestionToExaminers(Question question) {
            String questionMessage = question.toString();
            Iterator<Socket> iterator = examiners.iterator();
            while (iterator.hasNext()) {
                Socket examiner = iterator.next();
                try {
                    PrintWriter examinerOut = new PrintWriter(examiner.getOutputStream(), true);
                    examinerOut.println(questionMessage); // Send the question to each examiner
                } catch (IOException e) {
                    System.err.println("Error sending question to examiner: " + e.getMessage());
                    iterator.remove(); // Remove disconnected examiner
                }
            }
        }

        private void sendQuestionsToExaminers() {
            for (Question question : questions) {
                sendQuestionToExaminers(question);
            }
        }

        private void sendLeaderboard() {
            StringBuilder leaderboard = new StringBuilder("Leaderboard:\n");
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                leaderboard.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }

            Iterator<Socket> iterator = examiners.iterator();
            while (iterator.hasNext()) {
                Socket examiner = iterator.next();
                try {
                    if (!examiner.isClosed()) { // Check if the socket is still open
                        PrintWriter examinerOut = new PrintWriter(examiner.getOutputStream(), true);
                        examinerOut.println("LEADERBOARD|" + leaderboard.toString().trim());
                    } else {
                        iterator.remove(); // Remove disconnected examiner
                    }
                } catch (IOException e) {
                    System.err.println("Error sending leaderboard: " + e.getMessage());
                    iterator.remove(); // Remove disconnected examiner
                }
            }
        }

        private void sendFinalScoresToAdmins() {
            StringBuilder finalScores = new StringBuilder();
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                finalScores.append(entry.getKey()).append(": ").append(entry.getValue()).append("/").append(questions.size()).append("\n");
            }

            Iterator<Socket> iterator = admins.iterator();
            while (iterator.hasNext()) {
                Socket admin = iterator.next();
                try {
                    if (!admin.isClosed()) { // Check if the socket is still open
                        PrintWriter adminOut = new PrintWriter(admin.getOutputStream(), true);
                        adminOut.println("FINAL_SCORES|" + finalScores.toString().trim());
                        System.out.println("Final scores sent to admin: " + finalScores.toString().trim()); // Log the final scores
                    } else {
                        iterator.remove(); // Remove disconnected admin
                    }
                } catch (IOException e) {
                    System.err.println("Error sending final scores to admin: " + e.getMessage());
                    iterator.remove(); // Remove disconnected admin
                }
            }
        }

        private void closeAllSockets() {
            // Close all examiner sockets
            for (Socket examiner : examiners) {
                try {
                    examiner.close();
                } catch (IOException e) {
                    System.err.println("Error closing examiner socket: " + e.getMessage());
                }
            }

            // Close all admin sockets
            for (Socket admin : admins) {
                try {
                    admin.close();
                } catch (IOException e) {
                    System.err.println("Error closing admin socket: " + e.getMessage());
                }
            }
        }

        private String getAnswerOption(String answer, Question question) {
            switch (answer.trim().toUpperCase()) {
                case "A": return question.optionA;
                case "B": return question.optionB;
                case "C": return question.optionC;
                case "D": return question.optionD;
                default: return "Invalid Answer";
            }
        }
    }

    static class Question {
        String question, optionA, optionB, optionC, optionD, correct;

        public Question(String question, String optionA, String optionB, String optionC, String optionD, String correct) {
            this.question = question;
            this.optionA = optionA;
            this.optionB = optionB;
            this.optionC = optionC;
            this.optionD = optionD;
            this.correct = correct;
        }

        @Override
        public String toString() {
            // Use a delimiter that's unlikely to appear in the question or answers
            return question + "|" + optionA + "|" + optionB + "|" + optionC + "|" + optionD;
        }
    }
}