package com.memory;

import com.sun.management.OperatingSystemMXBean;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class Server2 {
    public static final int PORT = 6666;
    private static final int MAX_CLIENTS = 5;

    public static void main(String[] args) throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(MAX_CLIENTS);
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Server2 запущен на порту " + PORT);
            while (true) {
                Socket client = server.accept();
                pool.execute(() -> handleClient(client));
            }
        }
    }

    private static void handleClient(Socket client) {
        System.out.println("Клиент подключился: " + client.getRemoteSocketAddress());
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)
        ) {
            out.println("Добро пожаловать в Server2! Введите /getswap");
            String line;
            while ((line = in.readLine()) != null) {
                if ("/getswap".equalsIgnoreCase(line.trim())) {
                    long[] swap = readSwapInfo();
                    out.println("SwapTotal: " + formatBytes(swap[0]));
                    out.println("SwapFree : " + formatBytes(swap[1]));
                } else if ("/exit".equalsIgnoreCase(line.trim())) {
                    break;
                } else {
                    out.println("Неизвестная команда, введите /getswap или /exit");
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка общения с клиентом: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
            System.out.println("Клиент отключился: " + client.getRemoteSocketAddress());
        }
    }


    private static long[] readSwapInfo() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            OperatingSystemMXBean mx =
                    ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

            long total = mx.getTotalSwapSpaceSize();
            long free  = mx.getFreeSwapSpaceSize();
            return new long[]{ total, free };
        }

        long total = -1, free = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) {
                    total = parseKbLine(line) * 1024L;
                } else if (line.startsWith("SwapFree:")) {
                    free  = parseKbLine(line) * 1024L;
                }
                if (total >= 0 && free >= 0) break;
            }
        } catch (IOException e) {
            System.err.println("Не удалось прочитать /proc/meminfo: " + e.getMessage());
        }
        return new long[]{ total, free };
    }

    private static long parseKbLine(String line) {
        String[] parts = line.split("\\s+");
        return Long.parseLong(parts[1]);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024)
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        else if (bytes >= 1024L * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        else if (bytes >= 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        else
            return bytes + " B";
    }
}
