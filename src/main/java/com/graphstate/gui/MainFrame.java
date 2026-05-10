package com.graphstate.gui;

import com.graphstate.client.GraphStateClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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
        // главная панель с вкладками
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Построение состояния по графу", buildGraphPanel());
        tabbedPane.addTab("Проверка состояния на графовость", buildCheckGraphPanel());
        tabbedPane.addTab("Проверка состояния на стабилизаторность", new JLabel("в разработке", SwingConstants.CENTER));
        tabbedPane.addTab("Анализ запутанности", new JLabel("в разработке", SwingConstants.CENTER));

        add(tabbedPane);
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

        // строка для сообщений об ошибках
        statusArea = new JTextArea(2, 40);
        statusArea.setEditable(false);
        statusArea.setBackground(new Color(240, 240, 240));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(BorderFactory.createTitledBorder("Статус"));
        statusScroll.setPreferredSize(new Dimension(0, 60));
        panel.add(statusScroll, BorderLayout.SOUTH);

        buildBtn.addActionListener(e -> buildState());

        return panel;
    }

    public void buildState() {
        String vertices = verticesField.getText().trim();
        String edges = edgesField.getText().trim();

        if (vertices.isEmpty() || edges.isEmpty()) {
            statusArea.setText("ошибка: поля вершин и ребер не могут быть пустыми");
            return;
        }

        tableModel.setRowCount(0);

        try {
            GraphStateClient client = new GraphStateClient();
            Map<String, Object> result = client.buildState(vertices, edges); // http response

            Object ampsObj = result.get("amplitudes"); // из json ответа извлекаю ключ
            if (!(ampsObj instanceof List ampsList)) {
                statusArea.setText("ошибка: поле 'amplitudes' не является списком.");
                return;
            }

            int n = (int) Math.round(Math.log(ampsList.size()) / Math.log(2)); // число вершин
            for (int i = 0; i < ampsList.size(); i++) {
                String binary = Integer.toBinaryString(i);
                String padded = String.format("%" + n + "s", binary).replace(' ', '0');
                String basisStr = "|" + padded + ">";
                tableModel.addRow(new Object[]{basisStr, ampsList.get(i).toString()});
            }
            statusArea.setText("Графовое состояние успешно построено. Всего амплитуд: " + "2^" + n + " = " + ampsList.size());
        } catch (Exception ex) {
            statusArea.setText("Ошибка: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private JPanel buildCheckGraphPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // панель управления
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("количество кубитов (n): "));
        JSpinner nSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 7, 1));
        controlPanel.add(nSpinner);
        JButton genTableBtn = new JButton("Сгенерировать таблицу");
        controlPanel.add(genTableBtn);
        JButton checkBtn = new JButton("Проверить графовость");
        controlPanel.add(checkBtn);
        panel.add(controlPanel, BorderLayout.NORTH);

        // таблица знаков
        DefaultTableModel signTableModel = new DefaultTableModel(new String[]{"Базис", "Знак"}, 0);
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
            int rows = 1 << n;
            signTableModel.setRowCount(0);
            for (int i = 0; i < rows; i++) {
                String binary = Integer.toBinaryString(i);
                String padded = String.format("%" + n + "s", binary).replace(' ', '0');
                String basisStr = "|" + padded + ">";
                signTableModel.addRow(new Object[]{basisStr, "+"});
            }
            resultArea.setText("");
            graphPanel.setGraph(0, null);
        });

        checkBtn.addActionListener(e -> {
            int n = (Integer) nSpinner.getValue();
            int expectedRows = (1 << n);
            if (signTableModel.getRowCount() != expectedRows) {
                JOptionPane.showMessageDialog(panel, "Сначала сгенерируйте таблицу для выбранного n", "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<String> signs = new java.util.ArrayList<>();
            signs.add(0, "+");
            for (int row = 1; row < signTableModel.getRowCount(); row++) {
                signs.add((String) signTableModel.getValueAt(row, 1));
            }

            // отправка запроса
            try {
                GraphStateClient client = new GraphStateClient();
                Map<String, Object> response = client.checkGraphState(n, signs);
                boolean isGraph = (boolean) response.get("is_graph"); // ключ из http ответа

                if (isGraph) {
                    List<List<Integer>> edges = (List<List<Integer>>) response.get("edges");
                    resultArea.setText("Состояние является графовым\nНайденный граф: " + edges);

                    // визуализация графа по edges
                    graphPanel.setGraph(n, edges);
                } else {
                    resultArea.setText("Состояние не является графовым");
                    graphPanel.setGraph(0, null);
                }
            } catch (Exception ex) {
                resultArea.setText("Ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        return panel;
    }
}