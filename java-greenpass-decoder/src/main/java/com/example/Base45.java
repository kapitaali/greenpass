package com.example;

import java.util.HashMap;
import java.util.Map;

public class Base45 {
 
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    public static byte[] decode(String encodedString) {
        int remainderSize = encodedString.length() % 3;
        if (remainderSize == 1) {
            throw new IllegalArgumentException("wrong remainder length: " + remainderSize);
        }
        int wholeChunkCount = encodedString.length() / 3;
        byte[] result = new byte[wholeChunkCount * 2 + (remainderSize == 2 ? 1 : 0)];
        int resultIndex = 0;
        int wholeChunkLength = wholeChunkCount * 3;
        for (int i = 0; i < wholeChunkLength; ) {
            int c0 = ALPHABET.indexOf(encodedString.charAt(i++));
            int c1 = ALPHABET.indexOf(encodedString.charAt(i++));
            int c2 = ALPHABET.indexOf(encodedString.charAt(i++));
            if (c0 < 0 || c1 < 0 || c2 < 0) {
                throw new IllegalArgumentException("unsupported input character near pos: " + i);
            }
            int val = c0 + 45 * c1 + 45 * 45 * c2;
            if (val > 0xFFFF) {
                throw new IllegalArgumentException();
            }
            result[resultIndex++] = (byte) (val / 256);
            result[resultIndex++] = (byte) (val % 256);
        }

        if (remainderSize != 0) {
            int c0 = ALPHABET.indexOf(encodedString.charAt(encodedString.length() - 2));
            int c1 = ALPHABET.indexOf(encodedString.charAt(encodedString.length() - 1));
            result[resultIndex] = (byte) (c0 + 45 * c1);
        }
        return result;
    }

     
}

