package com.graphstate.gui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GraphPanel extends JPanel {
    private List<List<Integer>> edges;
    private int vertexCount;

    public void setGraph(int vertexCount, List<List<Integer>> edges) {
        this.vertexCount = vertexCount;
        this.edges = edges;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (vertexCount == 0 || edges == null) return;
        int w = getWidth();
        int h = getHeight();
        int radius = Math.min(w, h) / 3;
        int cx = w / 2;
        int cy = h / 2;
        Point[] points = new Point[vertexCount];
        // расположим вершины по кругу
        for (int i = 0; i < vertexCount; i++) {
            double angle = 2 * Math.PI * i / vertexCount - Math.PI / 2; // начинаем сверху
            int x = cx + (int) (radius * Math.cos(angle));
            int y = cy + (int) (radius * Math.sin(angle));
            points[i] = new Point(x, y);
        }
        // рисуем рёбра
        g.setColor(Color.BLACK);
        for (List<Integer> edge : edges) {
            int u = edge.get(0) - 1;
            int v = edge.get(1) - 1;
            g.drawLine(points[u].x, points[u].y, points[v].x, points[v].y);
        }
        // рисуем вершины
        g.setColor(Color.BLUE);
        for (int i = 0; i < vertexCount; i++) {
            g.fillOval(points[i].x - 12, points[i].y - 12, 24, 24);
            g.setColor(Color.WHITE);
            g.drawString(String.valueOf(i + 1), points[i].x - 5, points[i].y + 5);
            g.setColor(Color.BLUE);
        }
    }
}