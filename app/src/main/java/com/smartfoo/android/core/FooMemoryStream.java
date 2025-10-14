package com.smartfoo.android.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("unused")
public class FooMemoryStream
{
    @NonNull
    public static final byte[] EMPTY_BUFFER = new byte[0];
    public static final int    BLOCK_SIZE   = 256;

    @NonNull
    protected byte[] buffer   = EMPTY_BUFFER; // never null
    private   int    position = 0;
    private   int    length   = 0;

    public FooMemoryStream()
    {
        this(BLOCK_SIZE);
    }

    public FooMemoryStream(int capacity)
    {
        makeSpaceFor(capacity);
    }

    public synchronized void reset()
    {
        setLength(0);
        //setPosition(0);
    }

    public synchronized void clear()
    {
        this.buffer = EMPTY_BUFFER;
        reset();
    }

    public synchronized int getCapacity()
    {
        return this.buffer.length;
    }

    @NonNull
    public synchronized byte[] getBuffer()
    {
        return this.buffer;
    }

    public synchronized int getPosition()
    {
        return this.position;
    }

    public synchronized void setPosition(int position)
    {
        makeSpaceFor(position);
        this.position = position;
    }

    public synchronized int incPosition(int amount)
    {
        setPosition(getPosition() + amount);
        return getPosition();
    }

    public synchronized int getLength()
    {
        return this.length;
    }

    public synchronized void setLength(int length)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("length must be >= 0");
        }

        makeSpaceFor(length);

        // this.buffer can contain previously used data
        // if length > this.length, re-zero any length added to the end.
        FooArrays.fill(this.buffer, (byte) 0, this.length, length);

        this.length = length;

        if (this.position > this.length)
        {
            this.position = this.length;
        }
    }

    public synchronized int incLength(int amount)
    {
        setLength(getLength() + amount);
        return getPosition();
    }

    /** @noinspection UnusedReturnValue*/
    protected synchronized boolean makeSpaceFor(int size)
    {
        if (size <= this.buffer.length)
        {
            // already big enough, do nothing
            // this also handles the size <= 0 case
            return false;
        }

        int remainder = size % BLOCK_SIZE;
        size = size / BLOCK_SIZE * BLOCK_SIZE;
        if (remainder > 0)
        {
            size += BLOCK_SIZE;
        }
        if (size == 0)
        {
            return false;
        }

        byte[] tmp = new byte[size];

        // only need to copy the bytes in the array that are actually used
        System.arraycopy(this.buffer, 0, tmp, 0, this.length);

        this.buffer = tmp;

        // this.position and this.length remain unchanged

        return true;
    }

    public synchronized void write(@NonNull byte[] buffer, int offset, int length)
    {
        makeSpaceFor(this.position + length);
        System.arraycopy(buffer, offset, this.buffer, this.position, length);
        this.position += length;
        if (this.position > this.length)
        {
            this.length = this.position;
        }
    }

    public synchronized void writeInt8(byte value)
    {
        makeSpaceFor(this.position + 1);
        this.buffer[this.position] = value;
        this.position += 1;
        if (this.position > this.length)
        {
            this.length = this.position;
        }
    }

    public synchronized void writeUInt8(short value)
    {
        if ((value >> 8) != 0)
        {
            throw new IllegalArgumentException("value is not a uint8: 0x" + FooString.toHexString(value, 4));
        }
        makeSpaceFor(this.position + 1);
        this.buffer[this.position] = (byte) value;
        this.position += 1;
        if (this.position > this.length)
        {
            this.length = this.position;
        }
    }

    public synchronized void writeInt16(short value)
    {
        makeSpaceFor(this.position + 2);
        this.buffer[this.position] = (byte) (value >> 8);
        this.buffer[this.position + 1] = (byte) value;
        this.position += 2;
        if (this.position > this.length)
        {
            this.length = this.position;
        }
    }

    public synchronized void writeUInt16(int value)
    {
        if ((value >> 16) != 0)
        {
            throw new IllegalArgumentException("value is not a uint16: 0x" + FooString.toHexString(value, 8));
        }
        makeSpaceFor(this.position + 2);
        this.buffer[this.position] = (byte) (value >> 8);
        this.buffer[this.position + 1] = (byte) value;
        this.position += 2;
        if (this.position > this.length)
        {
            this.length = this.position;
        }
    }

    public synchronized void writeInt32(long value)
    {
        makeSpaceFor(this.position + 4);
        this.buffer[this.position] = (byte) (value >> 24);
        this.buffer[this.position + 1] = (byte) (value >> 16);
        this.buffer[this.position + 2] = (byte) (value >> 8);
        this.buffer[this.position + 3] = (byte) value;
        this.position += 4;
        if (this.position > this.length)
        {
            this.length = this.position;
        }
    }

    public synchronized void writeUInt32(long value)
    {
        if ((value >> 32) != 0)
        {
            throw new IllegalArgumentException("value is not a uint32: 0x" + FooString.toHexString(value, 16));
        }
        makeSpaceFor(this.position + 4);
        this.buffer[this.position] = (byte) (value >> 24);
        this.buffer[this.position + 1] = (byte) (value >> 16);
        this.buffer[this.position + 2] = (byte) (value >> 8);
        this.buffer[this.position + 3] = (byte) value;
        this.position += 4;
        if (this.position > this.length)
        {
            this.length = this.position;
        }
    }

    public synchronized void writeString(@Nullable String value)
    {
        if (value != null && !value.isEmpty())
        {
            byte[] b = FooString.getBytes(value);
            makeSpaceFor(this.position + b.length + 1);
            write(b, 0, b.length);
        }
        writeUInt8((short) 0);
        if (this.position > this.length)
        {
            this.length = this.position;
        }
    }

    protected static boolean checkOffset(int size, @NonNull byte[] buffer, int offset, int length)
    {
        return checkOffset(size, buffer, offset, length, true, true);
    }

    /** @noinspection SameParameterValue*/
    protected static boolean checkOffset(int size, @NonNull byte[] buffer, int offset, int length, //
                                         boolean checkParameters, boolean throwException)
    {
        if (checkParameters)
        {
            //noinspection ConstantValue
            if (buffer == null)
            {
                throw new IllegalArgumentException("buffer must not be null");
            }

            if (length > buffer.length)
            {
                throw new IllegalArgumentException(
                        "length(" + length + ") must be <= buffer.length(" + buffer.length + ')');
            }

            if (offset < 0 || offset >= length)
            {
                throw new IllegalArgumentException("offset(" + offset + ") must be >= 0 and < (length(" + length
                                                   + ") or buffer.length(" + buffer.length + "))");
            }
        }

        if (offset + size > length)
        {
            if (throwException)
            {
                throw new IndexOutOfBoundsException("attempted to read " + size + " bytes past offset(" + offset
                                                    + ") would exceed length(" + length + ')');
            }
            return false;
        }
        return true;
    }

    private static int unsignedByteToInt(byte value)
    {
        return value & 0xff;
    }

    private static int unsignedByteToInt(byte value, int leftShift)
    {
        return unsignedByteToInt(value) << leftShift;
    }

    public synchronized int read(@NonNull byte[] dest, int offset, int count)
    {
        count = Math.min(count, this.length - this.position);
        System.arraycopy(this.buffer, this.position, dest, offset, count);
        this.position += count;
        return count;
    }

    public synchronized byte readInt8()
    {
        checkOffset(1, this.buffer, this.position, this.length);
        return this.buffer[this.position++];
    }

    public synchronized short readUInt8()
    {
        checkOffset(1, this.buffer, this.position, this.length);
        return (short) unsignedByteToInt(this.buffer[this.position++]);
    }

    public synchronized short readInt16()
    {
        checkOffset(2, this.buffer, this.position, this.length);
        int value = unsignedByteToInt(this.buffer[this.position++], 8);
        value += unsignedByteToInt(this.buffer[this.position++]);
        return (short) value;
    }

    public synchronized int readUInt16()
    {
        checkOffset(2, this.buffer, this.position, this.length);
        int value = unsignedByteToInt(this.buffer[this.position++], 8);
        value += unsignedByteToInt(this.buffer[this.position++]);
        return value;
    }

    public synchronized int readInt32()
    {
        checkOffset(4, this.buffer, this.position, this.length);
        int value = unsignedByteToInt(this.buffer[this.position++], 24);
        value += unsignedByteToInt(this.buffer[this.position++], 16);
        value += unsignedByteToInt(this.buffer[this.position++], 8);
        value += unsignedByteToInt(this.buffer[this.position++]);
        return value;
    }

    public synchronized long readUInt32()
    {
        checkOffset(4, this.buffer, this.position, this.length);
        long value = unsignedByteToInt(this.buffer[this.position++], 24);
        value += unsignedByteToInt(this.buffer[this.position++], 16);
        value += unsignedByteToInt(this.buffer[this.position++], 8);
        value += unsignedByteToInt(this.buffer[this.position++]);
        return value;
    }

    @NonNull
    public synchronized String readString()
    {
        int index = this.position;
        while (checkOffset(1, this.buffer, this.position, this.length) && this.buffer[this.position] != 0)
        {
            this.position++;
        }
        this.position++; // null terminated
        return FooString.getString(this.buffer, index, this.position - index - 1);
    }

    @NonNull
    public synchronized String toDebugString()
    {
        return "(" + this.length + "):" + FooString.toHexString(this.buffer, 0, this.length);
    }

    @NonNull
    public static byte[] newBytes(byte value)
    {
        return new byte[]
                {
                        value
                };
    }

    @NonNull
    public static byte[] newBytes(short value)
    {
        return new byte[]
                {
                        (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF),
                        };
    }

    @NonNull
    public static byte[] newBytes(int value)
    {
        return new byte[]
                {
                        (byte) (value & 0xFF),
                        (byte) ((value >> 8) & 0xFF),
                        (byte) ((value >> 16) & 0xFF),
                        (byte) ((value >> 24) & 0xFF),
                        };
    }

    @NonNull
    public static byte[] newBytes(long value)
    {
        return new byte[]
                {
                        (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF), (byte) ((value >> 16) & 0xFF),
                        (byte) ((value >> 24) & 0xFF), (byte) ((value >> 32) & 0xFF), (byte) ((value >> 40) & 0xFF),
                        (byte) ((value >> 48) & 0xFF), (byte) ((value >> 56) & 0xFF),
                        };
    }
}
