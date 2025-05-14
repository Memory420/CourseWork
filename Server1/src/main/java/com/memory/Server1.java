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
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class Server1 extends Application {
    public static final int PORT = 5555;
    public static final int MAX_CLIENTS = 5;

    private ServerSocket serverSocket;
    private ExecutorService clientPool;
    private final Semaphore clientSemaphore = new Semaphore(MAX_CLIENTS);
    private Stage mainStage;

    private final AtomicInteger activeClients = new AtomicInteger(0);
    private final List<ClientHandler> handlers = new CopyOnWriteArrayList<>();
    private final List<PrintWriter> resizeSubscribers = new CopyOnWriteArrayList<>();

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.mainStage = stage;
        Label label = new Label("Server1");
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Button stopBtn = new Button("Остановить сервер");
        stopBtn.setOnAction(e -> stopServer());

        VBox root = new VBox(10, label, stopBtn);
        root.setAlignment(Pos.CENTER);
        stage.setScene(new Scene(root, 400, 200));
        stage.setTitle("Server1");
        stage.show();

        addResizeListener((w, h) -> {
            String msg = String.format("Размер окна: %d×%d", w.intValue(), h.intValue());
            broadcastToSubscribers(msg);
        });

        startServer();
    }

    private void startServer() {
        clientPool = Executors.newCachedThreadPool();
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("Server1 listening on port " + PORT);
            } catch (BindException be) {
                System.err.println("Не удалось запустить сервер: порт " + PORT + " уже занят. Возможно, сервер уже запущен.");
                Platform.runLater(Platform::exit);
                return;
            } catch (IOException ioe) {
                System.err.println("Ошибка при создании ServerSocket: " + ioe.getMessage());
                Platform.runLater(Platform::exit);
                return;
            }

            while (!serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    if (!clientSemaphore.tryAcquire()) {
                        try (PrintWriter w = new PrintWriter(client.getOutputStream(), true)) {
                            w.println(timestamp() + " Сервер переполнен. Попробуйте позже.");
                        }
                        client.close();
                        continue;
                    }
                    activeClients.incrementAndGet();
                    ClientHandler h = new ClientHandler(client);
                    handlers.add(h);
                    clientPool.execute(h);
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }
        }, "Server-Acceptor").start();
    }


    @Override
    public void stop() {
        stopServer();
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out = new PrintWriter(socket.getOutputStream(), true);
                send("Подключено. /subscribe, /unsubscribe, /getwindow, /rename <name>, /exit");
                String id = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                String line;
                while ((line = in.readLine()) != null) {
                    String cmd = line.trim();
                    System.out.println("[" + id + "] -> " + cmd);
                    if ("/exit".equalsIgnoreCase(cmd)) {
                        send("До свидания!");
                        break;
                    }
                    handle(cmd);
                }
            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void handle(String cmd) {
            if (!cmd.startsWith("/")) {
                send("Неверная команда. Введите /help");
                return;
            }
            String[] parts = cmd.split("\\s+", 2);
            String base = parts[0].toLowerCase();
            switch (base) {
                case "/subscribe":
                    if (!resizeSubscribers.contains(out)) resizeSubscribers.add(out);
                    send("Подписка на resize оформлена");
                    break;
                case "/unsubscribe":
                    resizeSubscribers.remove(out);
                    send("Подписка отменена");
                    break;
                case "/getwindow":
                    send(String.format("Размер окна: %d×%d",
                            (int)mainStage.getWidth(), (int)mainStage.getHeight()));
                    break;
                case "/rename":
                    if (parts.length < 2 || parts[1].isBlank()) {
                        send("Ошибка: имя не может быть пустым");
                    } else {
                        String name = parts[1];
                        try {
                            Platform.runLater(() -> mainStage.setTitle(name));
                            send("Успех: заголовок изменён на '" + name + "'");
                        } catch (Exception ex) {
                            send("Ошибка изменения заголовка: " + ex.getMessage());
                        }
                    }
                    break;
                case "/help":
                    send("Команды: /subscribe, /unsubscribe, /getwindow, /rename <name>, /exit");
                    break;
                default:
                    send("Неизвестная команда: " + base);
            }
        }

        private void send(String msg) {
            out.println(timestamp() + " " + msg);
        }

        private void disconnect() {
            if (out != null) resizeSubscribers.remove(out);
            handlers.remove(this);
            clientSemaphore.release();
            activeClients.decrementAndGet();
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("Client disconnected. Active: " + activeClients.get());
        }
    }

    private void broadcastToSubscribers(String msg) {
        String withTs = timestamp() + " " + msg;
        for (PrintWriter pw : new CopyOnWriteArrayList<>(resizeSubscribers)) {
            pw.println(withTs);
        }
    }

    private void stopServer() {
        try { serverSocket.close(); clientPool.shutdownNow(); } catch (IOException ignored) {}
        Platform.exit();
    }

    private void addResizeListener(BiConsumer<Double, Double> lst) {
        PauseTransition pt = new PauseTransition(Duration.millis(200));
        ChangeListener<Number> cl = (obs, o, n) -> pt.playFromStart();
        pt.setOnFinished(e -> lst.accept(mainStage.getWidth(), mainStage.getHeight()));
        mainStage.widthProperty().addListener(cl);
        mainStage.heightProperty().addListener(cl);
    }

    private static String timestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FMT);
    }
}
