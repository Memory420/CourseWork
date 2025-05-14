package com.memory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Графический клиент для взаимодействия с серверами Server1 и Server2.
 * Позволяет подключаться к разным портам, отправлять команды, очищать консоль и отключаться.
 */
public class ClientGUI extends Application {
    private static final String HOST = "localhost";

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private TextArea logArea;
    private TextField portField;
    private TextField commandField;
    private Button connectButton;
    private Button sendButton;
    private Button clearButton;
    private Button disconnectButton;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Memory Client GUI");

        // Лог
        logArea = new TextArea();
        logArea.setEditable(false);

        // Поле для порта и кнопка подключения
        portField = new TextField();
        portField.setPromptText("Порт сервера (5555 или 6666)");
        connectButton = new Button("Подключиться");
        connectButton.setOnAction(e -> connectToServer());

        // Кнопка отключения
        disconnectButton = new Button("Отключиться");
        disconnectButton.setDisable(true);
        disconnectButton.setOnAction(e -> sendExitCommand());

        // Кнопка очистки логов
        clearButton = new Button("Очистить консоль");
        clearButton.setOnAction(e -> logArea.clear());

        HBox connectionBox = new HBox(10, new Label("Порт:"), portField, connectButton, disconnectButton, clearButton);
        connectionBox.setPadding(new Insets(10));

        // Поле ввода команды и кнопка отправки
        commandField = new TextField();
        commandField.setPromptText("Введите команду или параметр");
        commandField.setDisable(true);

        sendButton = new Button("Отправить");
        sendButton.setDisable(true);
        sendButton.setOnAction(e -> sendCommand());

        HBox commandBox = new HBox(10, commandField, sendButton);
        commandBox.setPadding(new Insets(10));

        VBox centerBox = new VBox(10, connectionBox, logArea, commandBox);

        BorderPane root = new BorderPane();
        root.setCenter(centerBox);

        Scene scene = new Scene(root, 700, 450);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void connectToServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            appendLog("Неверный порт: " + portField.getText());
            return;
        }
        connectButton.setDisable(true);
        appendLog("Попытка подключения к порту " + port + "...");

        new Thread(() -> {
            try {
                socket = new Socket(HOST, port);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                Platform.runLater(() -> {
                    appendLog("Успешно подключено к серверу на порту " + port);
                    sendButton.setDisable(false);
                    commandField.setDisable(false);
                    disconnectButton.setDisable(false);
                });

                listenServer();
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    appendLog("Ошибка при подключении: " + ex.getMessage());
                    connectButton.setDisable(false);
                });
            }
        }, "Connect-Thread").start();
    }

    private void listenServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String msg = line;
                Platform.runLater(() -> appendLog("< Сервер: " + msg));
            }
        } catch (IOException e) {
            Platform.runLater(() -> appendLog("Соединение прервано."));
        } finally {
            Platform.runLater(this::disconnect);
        }
    }

    private void sendCommand() {
        String cmd = commandField.getText().trim();
        if (cmd.isEmpty() || out == null) return;
        out.println(cmd);
        appendLog("> Отправлено: " + cmd);
        commandField.clear();
        if ("/exit".equalsIgnoreCase(cmd)) {
            disconnect();
        }
    }

    private void sendExitCommand() {
        if (out != null) {
            out.println("/exit");
            appendLog("> Отправлено: /exit");
        }
        disconnect();
    }

    /**
     * Закрывает все ресурсы и возвращает UI в исходное состояние.
     */
    private void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        in = null;
        out = null;
        appendLog("Отключено от сервера.");
        connectButton.setDisable(false);
        sendButton.setDisable(true);
        commandField.setDisable(true);
        disconnectButton.setDisable(true);
    }

    private void appendLog(String message) {
        logArea.appendText(message + "\n");
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }
}