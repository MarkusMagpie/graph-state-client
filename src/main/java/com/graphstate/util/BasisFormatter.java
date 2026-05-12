package com.graphstate.util;

public final class BasisFormatter {
    private BasisFormatter() {}

    public static String format(int i, int n) {
        String binary = Integer.toBinaryString(i);
        String padded = String.format("%" + n + "s", binary).replace(' ', '0');
        return "|" + padded + ">";
    }
}