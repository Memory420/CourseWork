package com.memory;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class Server1 extends Application {
    public static final int PORT = 5555;
    public static final int MAX_CLIENTS = 1;
    private ServerSocket serverSocket;
    private ExecutorService clientPool;
    private final AtomicInteger connectedClients = new AtomicInteger(0);
    private final List<PrintWriter> clientOutputs = new CopyOnWriteArrayList<>();
    private Stage mainStage;

    private final Semaphore clientSemaphore = new Semaphore(MAX_CLIENTS);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        mainStage = stage;
        Label label = new Label("JavaFX работает!");
        label.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Button exitButton = new Button("Выход");

        VBox vbox = new VBox(20, label, exitButton);
        vbox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(vbox, 300, 200);
        stage.setTitle("Проверка JavaFX");
        stage.setScene(scene);
        stage.setWidth(500);
        stage.setHeight(400);
        stage.centerOnScreen();

        addResizeFinishedListener(stage, (w, h) ->
                broadcast("Размер окна: " + w.intValue() + "×" + h.intValue(), null)
        );

        stage.show();

        startServer();
    }

    private void startServer() {
        clientPool = Executors.newFixedThreadPool(MAX_CLIENTS);
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("Сервер запущен на порту " + PORT);
                while (!serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    if (clientSemaphore.tryAcquire()) {
                        System.out.println("Клиент подключился: " + client.getInetAddress().getHostAddress());
                        System.out.println("[INFO] Кол-во подключений: " + connectedClients.incrementAndGet());

                        clientPool.execute(() -> {
                            try {
                                handleClient(client);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } finally {
                                clientSemaphore.release();
                                connectedClients.decrementAndGet();
                            }
                        });
                    } else {
                        System.out.println("Отклонено подключение от: " + client.getInetAddress().getHostAddress());
                        try (PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
                            out.println("Сервер переполнен. Попробуйте позже.");
                        } catch (IOException ignored) {}
                        client.close();
                    }
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleClient(Socket client) throws IOException {
        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
        clientOutputs.add(out);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            out.println("Сервер принял подключение");

            String clientAddress = client.getInetAddress().getHostAddress();
            int clientPort = client.getPort();
            String nickname = "[" + clientAddress + ":" + clientPort + "]";
            String line;

            while ((line = in.readLine()) != null) {
                System.out.println("От клиента " + nickname + ": " + line);

                handleCommand(line, out);

                broadcast("Клиент " + nickname + ": " + line, out);
            }

        } catch (IOException e) {
            System.out.println("Ошибка клиента: " + e.getMessage());

        } finally {
            clientOutputs.remove(out);
            out.close();

            int now = connectedClients.decrementAndGet();
            System.out.println("[INFO] Клиент отключился. Осталось подключений: " + now);

            try {
                client.close();
            } catch (IOException ignore) {}
        }
    }

    private void handleCommand(String command, PrintWriter out) {
        if (!command.startsWith("/")) return;

        List<String> details = Arrays.asList(command.trim().split("\\s+"));
        System.out.println("[CMD] " + details);

        switch (details.get(0)) {
            case "/help":
                out.println("/help");
                out.println("/ping");
                out.println("/getwindow");
                out.println("/rename <name>");
                break;
            case "/ping":
                broadcast("pong!", null);
                break;

            case "/getwindow":
                broadcast("Размер окна: "
                        + mainStage.getWidth() + "×" + mainStage.getHeight(), null);
                break;

            case "/rename":
                if (details.size() < 2) {
                    broadcast("Ошибка: /rename <новое имя>", null);
                } else {
                    String newTitle = details.get(1);
                    Platform.runLater(() -> mainStage.setTitle(newTitle));
                    broadcast("Имя окна изменено на " + newTitle, null);
                }
                break;

            default:
                broadcast("Неизвестная команда", null);
                break;
        }
    }


    private void broadcast(String message, PrintWriter sender) {
        if (sender == null) {
            System.out.println(message);
        }
        for (PrintWriter writer : clientOutputs) {
            if (writer == sender) continue;
            writer.println(message);
        }
    }

    private void addResizeFinishedListener(Stage stage, BiConsumer<Double, Double> onResized) {
        final double[] lastSize = { stage.getWidth(), stage.getHeight() };

        PauseTransition pause = new PauseTransition(Duration.millis(200));
        pause.setOnFinished(evt -> {
            double w = stage.getWidth(), h = stage.getHeight();
            if (w != lastSize[0] || h != lastSize[1]) {
                lastSize[0] = w;
                lastSize[1] = h;
                onResized.accept(w, h);
            }
        });

        ChangeListener<Number> listener = (obs, oldV, newV) -> pause.playFromStart();

        stage.widthProperty().addListener(listener);
        stage.heightProperty().addListener(listener);
    }

    private void shutdownServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("Сервер остановлен");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (clientPool != null) {
            clientPool.shutdownNow();
        }
    }
}
