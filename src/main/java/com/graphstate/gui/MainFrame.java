package com.graphstate.gui;

import com.graphstate.client.GraphStateClient;
import com.graphstate.util.AppLogger;
import com.graphstate.util.BasisFormatter;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class MainFrame extends JFrame {
    private JTextField verticesField;
    private JTextField edgesField;
    private JTable amplitudesTable;
    private DefaultTableModel tableModel;
    private JTextArea statusArea;

    public MainFrame() {
        setTitle("Клиент для работы с графовыми состояниями");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(null);
        initUI();
    }

    public void initUI() {
        statusArea = new JTextArea(5, 80);
        statusArea.setEditable(false);
        statusArea.setBackground(new Color(240, 240, 240));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(BorderFactory.createTitledBorder("HTTP логи: "));
        statusScroll.setPreferredSize(new Dimension(0, 120));

        JButton clearScreenBtn = new JButton("Очистить HTTP логи");
        clearScreenBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(MainFrame.this,
                    "Очистить отображаемые HTTP логи?",
                    "Подтверждение", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {

                statusArea.setText("");
            }
        });

        JButton clearDbBtn = new JButton("Очистить БД логов");
        clearDbBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(MainFrame.this,
                    "Удалить все записи из базы данных логов?\nЭто действие необратимо.",
                    "Подтверждение", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {

                AppLogger.clearDatabase();
                JOptionPane.showMessageDialog(MainFrame.this, "База данных логов очищена");
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearScreenBtn);
        buttonPanel.add(clearDbBtn);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusScroll, BorderLayout.CENTER);
        statusPanel.add(buttonPanel, BorderLayout.EAST);

        // главная панель с вкладками
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.addTab("Построение состояния по графу", buildGraphPanel());
        tabbedPane.addTab("Проверка состояния на графовость", buildCheckGraphPanel());
        tabbedPane.addTab("LC-орбита", buildLCOrbitPanel());
        tabbedPane.addTab("Проверка состояния на стабилизаторность", buildCheckStabilizerPanel());
        tabbedPane.addTab("Анализ запутанности", buildEntanglementPanel());

        add(tabbedPane, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void logHttp(String method, String url, String params, int status, long durationMs) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(String.format("%s %s HTTP/1.1 | %s -> %d (%d ms)\n", method, url, params, status, durationMs));
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
        AppLogger.logHttp(method, url, params, status, durationMs);
    }

    private void displayInfo(String message) {
//        SwingUtilities.invokeLater(() -> {
//            statusArea.append("ℹ) " + message + "\n");
//            statusArea.setCaretPosition(statusArea.getDocument().getLength());
//        });
        AppLogger.logInfo(message);
    }

    private void displayError(String message) {
//        SwingUtilities.invokeLater(() -> {
//            statusArea.append("!) " + message + "\n");
//            statusArea.setCaretPosition(statusArea.getDocument().getLength());
//        });
        AppLogger.logError(message);
    }

    public JPanel buildGraphPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Вершины: "), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        verticesField = new JTextField("1,2,3,4");
        inputPanel.add(verticesField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        inputPanel.add(new JLabel("Ребра: "), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        edgesField = new JTextField("1-2 2-3 3-4");
        inputPanel.add(edgesField, gbc);

        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        JButton buildBtn = new JButton("Построить состояние");
        inputPanel.add(buildBtn, gbc);

        panel.add(inputPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"Базисное состояние", "Амплитуда"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        amplitudesTable = new JTable(tableModel);
        amplitudesTable.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(amplitudesTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Амплитуды графового состояния"));
        panel.add(scrollPane, BorderLayout.CENTER);

        buildBtn.addActionListener(e -> buildState());

        return panel;
    }

    public void buildState() {
        String vertices = verticesField.getText().trim();
        String edges = edgesField.getText().trim();

        if (vertices.isEmpty() || edges.isEmpty()) {
//            logError("Поля вершин и рёбер не могут быть пустыми");
            JOptionPane.showMessageDialog(MainFrame.this,
                    "Поля вершин и рёбер не могут быть пустыми",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        tableModel.setRowCount(0);

        String params = "vertices=" + vertices + ", edges=" + edges;
        long start = System.currentTimeMillis();

        try {
            GraphStateClient client = new GraphStateClient();
            Map<String, Object> result = client.buildState(vertices, edges); // http response

            long duration = System.currentTimeMillis() - start;
            int status = (int) result.get("_statusCode");
            logHttp("POST", "/build_state", params, status, duration);
            if (status != 200) {
//                logError("Сервер вернул статус " + status);
                JOptionPane.showMessageDialog(MainFrame.this,
                        "Сервер вернул статус " + status,
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Object ampsObj = result.get("amplitudes"); // из json ответа извлекаю ключ
            if (!(ampsObj instanceof List)) {
//                logError("Поле 'amplitudes' не является списком");
                JOptionPane.showMessageDialog(MainFrame.this,
                        "Поле 'amplitudes' не является списком",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<?> ampsList = (List<?>) ampsObj;
            int n = (int) Math.round(Math.log(ampsList.size()) / Math.log(2)); // число вершин
            for (int i = 0; i < ampsList.size(); i++) {
                String basisStr = BasisFormatter.format(i, n);
                tableModel.addRow(new Object[]{basisStr, ampsList.get(i).toString()});
            }

            displayInfo("Построено состояние: 2^" + n + " = " + ampsList.size() + " амплитуд");
        } catch (Exception ex) {
//            logError("Ошибка: " + ex.getMessage());
            JOptionPane.showMessageDialog(MainFrame.this,
                    "Ошибка: " + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    public JPanel buildCheckGraphPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // панель управления
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        JSpinner nSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 7, 1));
        JButton genTableBtn = new JButton("Сгенерировать таблицу");
        JButton checkBtn = new JButton("Проверить графовость");
        JButton loadCsvBtn = new JButton("Загрузить CSV");
//        loadCsvBtn.setBackground(Color.lightGray);
        JButton exportCsvBtn = new JButton("Экспортировать CSV");
//        exportCsvBtn.setBackground(Color.lightGray);

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("количество кубитов (n): "));
        row1.add(nSpinner);
        row1.add(genTableBtn);
        row1.add(checkBtn);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(loadCsvBtn);
        row2.add(exportCsvBtn);

        controlPanel.add(row1);
        controlPanel.add(row2);

        panel.add(controlPanel, BorderLayout.NORTH);

        // таблица знаков
        DefaultTableModel signTableModel = new DefaultTableModel(new String[]{"Базисное состояния", "Знак"}, 0);
        JTable signTable = new JTable(signTableModel);
        signTable.setRowHeight(25);

        JComboBox<String> comboBox = new JComboBox<>(new String[]{"+", "-"});
        signTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(comboBox));
        JScrollPane tableScroll = new JScrollPane(signTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Знаки амплитуд"));

        panel.add(tableScroll, BorderLayout.CENTER);

        // область результата: текст + граф
        JPanel resultPanel = new JPanel(new BorderLayout());
        JTextArea resultArea = new JTextArea(5, 40);
        resultArea.setEditable(false);
        resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        // панель графа
        GraphPanel graphPanel = new GraphPanel();
        graphPanel.setPreferredSize(new Dimension(300, 200));
        graphPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        resultPanel.add(graphPanel, BorderLayout.SOUTH);

        panel.add(resultPanel, BorderLayout.SOUTH);

        // действие при генерации таблицы
        genTableBtn.addActionListener(e -> {
            int n = (Integer) nSpinner.getValue();
            tableGeneration(signTableModel, n);

            resultArea.setText("");
            graphPanel.setGraph(0, null);

//            statusArea.setText("Таблица для n = " + n + " сгенерирована. Количество базисов: " + (1 << n));
            displayInfo("Таблица для n = " + n + " сгенерирована. Количество базисов: " + (1 << n));
        });

        checkBtn.addActionListener(e -> {
            int n = (Integer) nSpinner.getValue();
            int expectedRows = (1 << n);
            if (signTableModel.getRowCount() != expectedRows) {
                JOptionPane.showMessageDialog(panel,
                        "Пожалуйста, сначала сгенерируйте таблицу для выбранного n",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
//                logError("Пожалуйста, сначала сгенерируйте таблицу для выбранного n");
                return;
            }

            List<String> signs = new java.util.ArrayList<>();
            signs.add(0, "+");
            for (int row = 1; row < signTableModel.getRowCount(); row++) {
                signs.add((String) signTableModel.getValueAt(row, 1));
            }

            checkBtn.setEnabled(false);
            resultArea.setText("Проверка...");
            graphPanel.setGraph(0, null);

            SwingWorker<Map<String, Object>, Void> worker = new SwingWorker<>() {
                @Override
                public Map<String, Object> doInBackground() throws Exception {
                    long start = System.currentTimeMillis();
                    GraphStateClient client = new GraphStateClient();
                    Map<String, Object> response = client.checkGraphState(n, signs);
                    long duration = System.currentTimeMillis() - start;
                    response.put("_duration", duration); // + длительность в ответ

                    return response;
                }

                @Override
                protected void done() {
                    try {
                        Map<String, Object> response = get();
                        long duration = (long) response.get("_duration");
                        int status = (int) response.get("_statusCode");
                        String params = "n=" + n + ", signs=" + signs;
                        logHttp("POST", "/check_graph_submit", params, status, duration);

                        boolean isGraph = (boolean) response.get("is_graph");
                        if (isGraph) {
                            List<List<Integer>> vertices = (List<List<Integer>>) response.get("vertices");
                            List<List<Integer>> edges = (List<List<Integer>>) response.get("edges");
                            resultArea.setText("Состояние является графовым\nG = (V=" + vertices + ", E=" + edges + ")");
                            graphPanel.setGraph(n, edges);
                            displayInfo("Состояние графовое, найдено рёбер: " + edges.size());
                        } else {
                            resultArea.setText("Состояние не является графовым");
                            graphPanel.setGraph(0, null);
                             displayInfo("Состояние не графовое");
                        }
                    } catch (Exception ex) {
//                        resultArea.setText("Ошибка: " + ex.getMessage());
                         displayError("Ошибка при проверке графовости: " + ex.getMessage());
                        JOptionPane.showMessageDialog(MainFrame.this,
                        "Ошибка при проверке состояния на графовость: ",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    } finally {
                        checkBtn.setEnabled(true);
                    }
                }
            };
            worker.execute();
        });

        loadCsvBtn.addActionListener(ev ->
                loadSignsFromCSV(signTable, signTableModel, nSpinner));

        exportCsvBtn.addActionListener(ev ->
                exportSignsToCSV(signTable, signTableModel, (Integer) nSpinner.getValue()));

        genTableBtn.doClick();
        return panel;
    }

    private void generateTableForN(int n, DefaultTableModel signTableModel) {
        int rows = 1 << n;
        signTableModel.setRowCount(0);
        for (int i = 0; i < rows; i++) {
            String basisStr = BasisFormatter.format(i, n);
            signTableModel.addRow(new Object[]{basisStr, "+"});
        }
    }

    public void loadSignsFromCSV(JTable signTable, DefaultTableModel signTableModel, JSpinner nSpinner) {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(signTable.getParent()) == JFileChooser.APPROVE_OPTION) {
            try (BufferedReader br = new BufferedReader(new FileReader(fileChooser.getSelectedFile()))) {
                String line;
                boolean firstLine = true;
                List<String> signs = new ArrayList<>();
                while ((line = br.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    } // пропуск заголовка

                    if (line.trim().isEmpty()) continue;

                    String[] parts = line.split(",");
                    if (parts.length < 2) continue;
                    String ampStr = parts[1].trim();
                    char sign = ampStr.startsWith("-") ? '-' : '+';
                    signs.add(String.valueOf(sign));
                }

                // число кубитов n по количеству знаков
                int numRows = signs.size();
                int newN = (int) Math.round(Math.log(numRows) / Math.log(2));
                if ((1 << newN) != numRows) {
                    JOptionPane.showMessageDialog(signTable.getParent(),
                            "Некорректное количество знаков: " + numRows + " (должно быть степенью 2)");
                    return;
                }

                nSpinner.setValue(newN);

                generateTableForN(newN, signTableModel);

                for (int i = 0; i < signs.size(); i++) {
                    signTableModel.setValueAt(signs.get(i), i, 1);
                }
                JOptionPane.showMessageDialog(signTable.getParent(), "CSV загружен успешно, n = " + newN);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(signTable.getParent(), "Ошибка: " + ex.getMessage());
            }
        }
    }

    public void exportSignsToCSV(JTable signTable, DefaultTableModel signTableModel, int n) {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(signTable.getParent()) == JFileChooser.APPROVE_OPTION) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileChooser.getSelectedFile()))) {
                bw.write("базис,амплитуда\n");
                double norm = 1.0 / Math.sqrt(1 << n);
                for (int i = 0; i < signTableModel.getRowCount(); i++) {
                    String basis = (String) signTableModel.getValueAt(i, 0);
                    String sign = (String) signTableModel.getValueAt(i, 1);
                    double amp = sign.equals("+") ? norm : -norm;
                    bw.write(basis + ",(" + amp + "+0j)\n");
                }
                JOptionPane.showMessageDialog(signTable.getParent(), "CSV файл экспортирован успешно");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(signTable.getParent(), "Ошибка: " + ex.getMessage());
            }
        }
    }

    // --------------------------------------------------------------- "LC-орбита"
    private JPanel buildLCOrbitPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // панель ввода графа
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Вершины: "), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        JTextField verticesField = new JTextField("1,2,3,4");
        inputPanel.add(verticesField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        inputPanel.add(new JLabel("Ребра: "), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        JTextField edgesField = new JTextField("1-2 2-3 3-4");
        inputPanel.add(edgesField, gbc);

        gbc.gridx = 2; gbc.gridy = 2; gbc.gridwidth = 1;
        JButton computeBtn = new JButton("Вычислить LC-орбиту");
        inputPanel.add(computeBtn, gbc);

        panel.add(inputPanel, BorderLayout.NORTH);

        // область вывода результата = список состояний
        JTextArea resultArea = new JTextArea(15, 80);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("LC-орбита"));
        panel.add(scrollPane, BorderLayout.CENTER);

        computeBtn.addActionListener(e -> {
            String verticesStr = verticesField.getText().trim();
            String edgesStr = edgesField.getText().trim();
            if (verticesStr.isEmpty() || edgesStr.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Поля не должны быть пустыми",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<Integer> vertices = Arrays.stream(verticesStr.split(","))
                    .map(String::trim).map(Integer::parseInt).collect(Collectors.toList());
            List<List<Integer>> edges = Arrays.stream(edgesStr.split(" "))
                    .map(pair -> pair.split("-"))
                    .map(parts -> List.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
                    .collect(Collectors.toList());

            computeBtn.setEnabled(false);
            resultArea.setText("Вычисление LC-орбиты...");

            SwingWorker<Map<String, Object>, Void> worker = new SwingWorker<>() {
                private long duration;
                private int statusCode;

                @Override
                protected Map<String, Object> doInBackground() throws Exception {
//                    return new GraphStateClient().lcOrbit(vertices, edges);
                    long start = System.currentTimeMillis();
                    Map<String, Object> response = new GraphStateClient().lcOrbit(vertices, edges);
                    duration = System.currentTimeMillis() - start;
                    statusCode = (int) response.getOrDefault("_statusCode", 500);
                    response.put("_duration", duration);
                    response.put("_statusCode", statusCode);
                    return response;
                }

                @Override
                protected void done() {
                    try {
                        Map<String, Object> response = get();

                        long duration = (long) response.get("_duration");
                        int status = (int) response.get("_statusCode");
                        String params = "vertices=" + verticesStr + ", edges=" + edgesStr;
                        logHttp("POST", "/lc_orbit", params, status, duration);

                        int size = (int) response.get("orbit_size");
                        List<Map<String, Object>> states = (List<Map<String, Object>>) response.get("orbit_states");

                        StringBuilder sb = new StringBuilder();
                        sb.append("Размер орбиты: ").append(size).append("\n\n");
                        for (int i = 0; i < states.size(); i++) {
                            Map<String, Object> state = states.get(i);
                            int n = (int) state.get("n");
                            List<String> signs = (List<String>) state.get("signs");
                            sb.append("  состояние ").append(i+1).append(":\n");
                            sb.append("  ").append(formatStateString(signs, n)).append("\n\n");
                        }
                        resultArea.setText(sb.toString());
                        displayInfo("LC-орбита вычислена, размер: " + size);
                    } catch (Exception ex) {
                        resultArea.setText("Ошибка при вычислении LC-орбиты: " + ex.getMessage());
                        displayError("Ошибка при вычислении LC-орбиты: " + ex.getMessage());
                        ex.printStackTrace();
                    } finally {
                        computeBtn.setEnabled(true);
                    }
                }
            };
            worker.execute();
        });

        return panel;
    }

    private String formatStateString(List<String> signs, int n) {
        int numStates = 1 << n;
        StringBuilder sb = new StringBuilder();
        sb.append("|\u03C8> = (1/\u221A").append(numStates).append(")(");

        for (int i = 0; i < numStates; i++) {
            String sign = signs.get(i);
            if (sign.equals("+") && i > 0) sb.append(" + ");
            else if (sign.equals("-")) sb.append(" - ");
            String basis = BasisFormatter.format(i, n);
            sb.append(basis);
        }
        sb.append(")");

        return sb.toString();
    }

    // --------------------------------------------------------------- "Проверка состояния на стабилизаторность"
    public JPanel buildCheckStabilizerPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // панель для управллвения
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Количество кубитов (n): "));
        JSpinner nSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 7, 1));
        row1.add(nSpinner);
        JButton genTableBtn = new JButton("Сгенерировать таблицу");
        row1.add(genTableBtn);
        JButton checkBtn = new JButton("Проверить стабилизаторность");
        row1.add(checkBtn);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadCsvBtn = new JButton("Загрузить CSV");
        JButton exportCsvBtn = new JButton("Экспортировать CSV");
        row2.add(loadCsvBtn);
        row2.add(exportCsvBtn);
        controlPanel.add(row1);
        controlPanel.add(row2);
        panel.add(controlPanel, BorderLayout.NORTH);

        // таблица знаков
        DefaultTableModel signTableModel = new DefaultTableModel(new String[]{"Базисное состояние", "Знак"}, 0);
        JTable signTable = new JTable(signTableModel);
        signTable.setRowHeight(25);
        JComboBox<String> comboBox = new JComboBox<>(new String[]{"+", "-", "0"});
        signTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(comboBox));
        JScrollPane tableScroll = new JScrollPane(signTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Знаки амплитуд: "));
        panel.add(tableScroll, BorderLayout.CENTER);

        // область для результата
        JPanel resultPanel = new JPanel(new BorderLayout());
        JTextArea resultArea = new JTextArea(15, 50);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Результат"));
        resultPanel.add(resultScroll, BorderLayout.CENTER);
        panel.add(resultPanel, BorderLayout.SOUTH);

        // обработчик для кнопки генерации таблицы
        genTableBtn.addActionListener(e -> {
            int n = (Integer) nSpinner.getValue();
            tableGeneration(signTableModel, n);
            resultArea.setText("");

            displayInfo("Таблица для n = " + n + " сгенерирована. Количество базисов: " + (1 << n));
        });

        // обработчик для кнопки проверки стабилллизаторности
        checkBtn.addActionListener(e -> {
            int n = (Integer) nSpinner.getValue();
            int expectedRows = 1 << n;
            if (signTableModel.getRowCount() != expectedRows) {
                JOptionPane.showMessageDialog(panel,
                        "Пожалуйста, сначала сгенерируйте таблицу для выбранного n",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                displayError("Пожалуйста, сначала сгенерируйте таблицу для выбранного n");
                return;
            }

            List<String> signs = new ArrayList<>();
            for (int row = 0; row < signTableModel.getRowCount(); row++) {
                signs.add((String) signTableModel.getValueAt(row, 1));
            }

            String params = "n=" + n + ", signs=" + signs;
            long start = System.currentTimeMillis();
            // отправка запроса серверу
            try {
                GraphStateClient client = new GraphStateClient();
                Map<String, Object> response = client.checkStabilizer(n, signs);
                long duration = System.currentTimeMillis() - start;
                int status = (int) response.getOrDefault("_statusCode", 500);
                logHttp("POST", "/check_stabilizer_submit", params, status, duration);

                if (status != 200) {
//                    logError("Сервер вернул ошибку " + status);
                    resultArea.setText("Сервер вернул ошибку " + status);
                    return;
                }

                boolean isStab = (boolean) response.get("is_stabilizer");

                StringBuilder sb = new StringBuilder();

                if (isStab) {
                    sb.append("СОСТОЯНИЕ ЯВЛЯЕТСЯ СТАБИЛИЗАТОРНЫМ\n\n");
                    sb.append("Генераторы: ").append(response.get("generators")).append("\n");
                    sb.append("Ранг: ").append(response.get("rank")).append("\n");
                    List<?> opsList = (List<?>) response.get("ops_and_vecs");
                    sb.append("Найденные операторы: ").append(opsList).append("\n");
                    sb.append("Количество операторов: ").append(opsList.size()).append("\n");

                    sb.append("\nОператоры:\n");
                    for (Object op : opsList) {
                        sb.append(op.toString()).append("\n");
                    }
                    displayInfo("Состояние стабилизаторное, ранг " + response.get("rank"));
                } else {
                    sb.append("СОСТОЯНИЕ НЕ ЯВЛЯЕТСЯ СТАБИЛИЗАТОРНЫМ\n");
                    sb.append("Причина: ").append(response.get("reason")).append("\n");
                    displayInfo("Состояние не стабилизаторное: " + response.get("reason"));
                }

                resultArea.setText(sb.toString());
            } catch (Exception ex) {
                displayError("Ошибка при проверке: " + ex.getMessage());
                resultArea.setText("Ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        loadCsvBtn.addActionListener(ev ->
                loadSignsFromCSV(signTable, signTableModel, nSpinner)
        );

        exportCsvBtn.addActionListener(
                ev -> exportSignsToCSV(signTable, signTableModel, (Integer) nSpinner.getValue())
        );

        genTableBtn.doClick();

        return panel;
    }

    private void tableGeneration(DefaultTableModel signTableModel, Integer n) {
        int rows = 1 << n;
        signTableModel.setRowCount(0);
        for (int i = 0; i < rows; i++) {
            String basisStr = BasisFormatter.format(i, n);
            signTableModel.addRow(new Object[]{basisStr, "+"});
        }
    }

    private ImageIcon scaleImage(byte[] imageBytes, int maxWidth, int maxHeight) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            int origWidth = original.getWidth();
            int origHeight = original.getHeight();
            double scaleX = (double) maxWidth / origWidth;
            double scaleY = (double) maxHeight / origHeight;
            double scale = Math.min(scaleX, scaleY);
            int newWidth = (int) Math.round(origWidth * scale);
            int newHeight = (int) Math.round(origHeight * scale);
            Image scaled = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);

            return new ImageIcon(scaled);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public JPanel buildEntanglementPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // панель для управллвения
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Количество кубитов (n): "));
        JSpinner nSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 7, 1));
        row1.add(nSpinner);
        JButton genTableBtn = new JButton("Сгенерировать таблицу");
        row1.add(genTableBtn);
        JButton checkBtn = new JButton("Проверить полную стабилизаторность");
        row1.add(checkBtn);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadCsvBtn = new JButton("Загрузить CSV");
        JButton exportCsvBtn = new JButton("Экспортировать CSV");
        row2.add(loadCsvBtn);
        row2.add(exportCsvBtn);
        controlPanel.add(row1);
        controlPanel.add(row2);

        panel.add(controlPanel, BorderLayout.NORTH);

        // таблица знаков
        DefaultTableModel signTableModel = new DefaultTableModel(new String[]{"Базисное состояние", "Знак"}, 0);
        JTable signTable = new JTable(signTableModel);
        signTable.setRowHeight(25);
        JComboBox<String> comboBox = new JComboBox<>(new String[]{"+", "-"});
        signTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(comboBox));
        JScrollPane tableScroll = new JScrollPane(signTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Знаки амплитуд"));
        tableScroll.setPreferredSize(new Dimension(300, 0));

        // панель результатов справа
        JPanel resultPanel = new JPanel(new BorderLayout());
        JTextArea resultArea = new JTextArea(8, 20);
        resultArea.setPreferredSize(new Dimension(0, 200));
        resultArea.setEditable(false);
        resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        JLabel pyramidLabel = new JLabel();
        int maxWidth = 620;
        int maxHeight = 350;
        pyramidLabel.setPreferredSize(new Dimension(maxWidth, maxHeight));
        pyramidLabel.setHorizontalAlignment(SwingConstants.CENTER);
        // полоса прокрутки для изображения если оно большое
        JScrollPane imageScroll = new JScrollPane(pyramidLabel);
        resultPanel.add(imageScroll, BorderLayout.SOUTH);

        // панель результатов в JScrollPane (если текст + картинка слишком высокие)
        JScrollPane resultScrollPane = new JScrollPane(resultPanel);
        resultScrollPane.setBorder(null);
        resultScrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

        // горизонтальный сплит: таблица слева, результат справа
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, resultScrollPane);
        splitPane.setContinuousLayout(true);
        panel.add(splitPane, BorderLayout.CENTER);

        // обработчик для кнопки генерации таблицы
        genTableBtn.addActionListener(e -> {
            int n = (Integer) nSpinner.getValue();
            tableGeneration(signTableModel, n);

            resultArea.setText("");
            pyramidLabel.setIcon(null);
        });


        checkBtn.addActionListener(e -> {
            int n = (Integer) nSpinner.getValue();
            int rows = 1 << n;
            if (signTableModel.getRowCount() != rows) {
                JOptionPane.showMessageDialog(panel,
                        "Пожалуйста, сначала сгенерируйте таблицу для выбранного n",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<String> signs = new ArrayList<>();
            for (int row = 0; row < rows; row++) {
                signs.add((String) signTableModel.getValueAt(row, 1));
            }

            String params = "n=" + n + ", signs=" + signs;
            long start = System.currentTimeMillis();
            try {
                GraphStateClient client = new GraphStateClient();
                Map<String, Object> response = client.checkSeparability(n, signs);

                long duration = System.currentTimeMillis() - start;
                int status = (int) response.getOrDefault("_statusCode", 500);
                logHttp("POST", "/check_separable_submit", params, status, duration);

                if (status != 200) {
//                    logError("Сервер вернул ошибку " + status);
                    resultArea.setText("Ошибка сервера: статус " + status);
                    return;
                }

                boolean isSep = (boolean) response.get("is_separable");
                StringBuilder sb = new StringBuilder();
                if (isSep) {
                    sb.append("Состояние полностью сепарабельно\n\n");
                    sb.append("Разложение на однокубитные состояния:\n");

                    List<?> t = (List<?>) response.get("t");
                    if (t != null && t.size() == n) {
                        for (int i = 0; i < n; i++) {
                            Number val = (Number) t.get(i);
                            char signChar = val.doubleValue() == 1.0 ? '+' : '-';
                            sb.append("    кубит ").append(i+1).append(": |0> + ").append(signChar).append("|1>\n");
                        }
                    } else {
                        sb.append("Не удалось получить разложение на однокубитные состояния\n");
                    }
                     displayInfo("Состояние полностью сепарабельно");
                } else {
                    sb.append("Состояние запутано\n");
                    if (response.containsKey("mismatches")) {
                        List<Integer> mismatches = (List<Integer>) response.get("mismatches");
                        if (!mismatches.isEmpty()) {
                            sb.append("Несовпадения в базисных состояниях:\n");
                            for (int mask : mismatches) {
                                sb.append("  ").append(BasisFormatter.format(mask, n)).append("\n");
                            }
                        }
                    }
                    displayInfo("Состояние запутано");
                }
                resultArea.setText(sb.toString());

                if (response.containsKey("image")) {
                    String base64Image = (String) response.get("image");
                    byte[] imageBytes = Base64.getDecoder().decode(base64Image);
//                    ImageIcon icon = new ImageIcon(imageBytes);
//                    масштабирование изображения так чтобы оно не превышало maxWidth, maxHeight
                    ImageIcon icon = scaleImage(imageBytes, maxWidth, maxHeight);
                    pyramidLabel.setIcon(icon);
                } else {
                    pyramidLabel.setIcon(null);
//                    logError("Изображение не получено");
                }
            } catch (Exception ex) {
                // logError("Ошибка: " + ex.getMessage());
                resultArea.setText("Ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        loadCsvBtn.addActionListener(ev -> loadSignsFromCSV(signTable, signTableModel, nSpinner));
        exportCsvBtn.addActionListener(ev -> exportSignsToCSV(signTable, signTableModel, (Integer) nSpinner.getValue()));

        genTableBtn.doClick();

        return panel;
    }
}