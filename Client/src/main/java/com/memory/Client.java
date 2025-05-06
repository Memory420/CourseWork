package com.memory;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static final String HOST = "localhost";
    private static final int RETRY_DELAY_MS = 100;

    public static void main(String[] args) {
        System.out.println("Старт клиента...");
        System.out.print("Введите порт сервера: ");
        int currentServer = new Scanner(System.in).nextInt();
        Socket socket = null;
        int tries = 0;
        while (socket == null) {
            try {
                socket = new Socket(HOST, currentServer);
            } catch (IOException e) {
                if (++tries % 30 == 0) {
                    System.out.println("Попытка подключиться...");
                }
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }

        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println(in.readLine());

            Thread reader = new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        System.out.print("\r");
                        System.out.println(msg);
                        System.out.print("→ ");
                    }
                } catch (IOException e) {
                    System.err.println("Соединение прервано.");
                }
            }, "Server-Listener");
            reader.setDaemon(true);
            reader.start();

            System.out.print("→ ");

            System.out.print("Вводи текст (или 'exit' для выхода): ");
            String userInput;
            while (scanner.hasNextLine()) {
                userInput = scanner.nextLine();
                if ("exit".equalsIgnoreCase(userInput)) break;
                out.println(userInput);
                System.out.print("→ ");
            }

            System.out.println("Клиент завершил работу.");
        } catch (IOException e) {
            System.err.println("Ошибка работы с сокетом: " + e.getMessage());
        }
    }
}
