package com.smartfoo.android.core;

import androidx.annotation.NonNull;

import java.util.BitSet;

public class FooBitSet
{
    public static final byte BITS_PER_BYTE = 8;

    private final int    length;
    private final BitSet bitset;

    public FooBitSet(byte value)
    {
        length = BITS_PER_BYTE;
        bitset = new BitSet(length);

        // Walk through bytes and set the bits
        for (int j = 0; j < BITS_PER_BYTE; j++)
        {
            if ((value & (1 << j)) != 0)
            {
                bitset.set(BITS_PER_BYTE + j);
            }
        }
    }

    public FooBitSet(@NonNull byte[] bytes)
    {
        length = bytes.length * BITS_PER_BYTE;
        bitset = new BitSet(length);

        // Walk through bytes and set the bits
        for (int i = 0; i < bytes.length; i++)
        {
            for (int j = 0; j < BITS_PER_BYTE; j++)
            {
                if ((bytes[i] & (1 << j)) != 0)
                {
                    bitset.set(i * BITS_PER_BYTE + j);
                }
            }
        }
    }

    public int getLength()
    {
        return length;
    }

    public boolean get(int index)
    {
        return bitset.get(index);
    }
}
