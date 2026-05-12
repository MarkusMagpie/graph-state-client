package com.graphstate.gui;

import com.graphstate.client.GraphStateClient;
import com.graphstate.util.BasisFormatter;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.List;

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

        // главная панель с вкладками
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.addTab("Построение состояния по графу", buildGraphPanel());
        tabbedPane.addTab("Проверка состояния на графовость", buildCheckGraphPanel());
        tabbedPane.addTab("Проверка состояния на стабилизаторность", buildCheckStabilizerPanel());
        tabbedPane.addTab("Анализ запутанности", buildEntanglementPanel());

        add(tabbedPane);
        add(statusScroll, BorderLayout.SOUTH);
    }

    private void logHttp(String method, String url, String params, int statusCode, long durationMs) {
        String msg = String.format("%s %s HTTP/1.1 | %s -> %d (%d ms)", method, url, params, statusCode, durationMs);
        SwingUtilities.invokeLater(() -> {
            statusArea.append(msg + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }

    private void logError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append("!) " + message + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }

    private void logInfo(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append("ℹ) " + message + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
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

        tableModel = new DefaultTableModel(new String[]{"Базисное состояние", "Амплитуда"}, 0);
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

//            logInfo("Построено состояние: 2^" + n + " = " + ampsList.size() + " амплитуд");
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
//            logInfo("Таблица для n = " + n + " сгенерирована. Количество базисов: " + (1 << n));
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

            String params = "n=" + n + ", signs=" + signs;
            long start = System.currentTimeMillis();

            try {
                GraphStateClient client = new GraphStateClient();
                Map<String, Object> response = client.checkGraphState(n, signs);
                long duration = System.currentTimeMillis() - start;
                int status = (int) response.get("_statusCode");
                logHttp("POST", "/check_graph_submit", params, status, duration);

                boolean isGraph = (boolean) response.get("is_graph"); // ключ из http ответа

                if (isGraph) {
                    List<List<Integer>> edges = (List<List<Integer>>) response.get("edges");
                    resultArea.setText("Состояние является графовым\nНайденный граф: " + edges);
//                    logInfo("Состояние является графовым. Найденный граф: " + edges.size());

                    // визуализация графа по ребрам из http ответа
                    graphPanel.setGraph(n, edges);
                } else {
                    resultArea.setText("Состояние не является графовым");
                    graphPanel.setGraph(0, null);
//                    logInfo("Состояние не графовое");
                }
            } catch (Exception ex) {
                logError("Ошибка при проверке: " + ex.getMessage());
                ex.printStackTrace();
            }
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
        });

        // обработчик для кнопки проверки стабилллизаторности
        checkBtn.addActionListener(e -> {
            int n = (Integer) nSpinner.getValue();
            int expectedRows = 1 << n;
            if (signTableModel.getRowCount() != expectedRows) {
                JOptionPane.showMessageDialog(panel,
                        "Пожалуйста, сначала сгенерируйте таблицу для выбранного n",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
//                logError("Пожалуйста, сначала сгенерируйте таблицу для выбранного n");
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
//                    logInfo("Состояние стабилизаторное, ранг " + response.get("rank"));
                } else {
                    sb.append("СОСТОЯНИЕ НЕ ЯВЛЯЕТСЯ СТАБИЛИЗАТОРНЫМ\n");
                    sb.append("Причина: ").append(response.get("reason")).append("\n");
                    // logInfo("Состояние не стабилизаторное: " + response.get("reason"));
                }

                resultArea.setText(sb.toString());
            } catch (Exception ex) {
//                logError("Ошибка при проверке: " + ex.getMessage());
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
        int maxHeight = 500;
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
            try {
                GraphStateClient client = new GraphStateClient();
                Map<String, Object> response = client.checkSeparability(n, signs);
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
                }
            } catch (Exception ex) {
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