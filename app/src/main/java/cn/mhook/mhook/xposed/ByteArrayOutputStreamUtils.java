package cn.mhook.mhook.xposed;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class ByteArrayOutputStreamUtils extends OutputStream {
    
    private static final byte[] mNullByteArray = new byte[0];
    
    private byte[] mBuffer;
    
    private int mCount;

    
    public ByteArrayOutputStreamUtils() {
        this(32);
    }

    
    public ByteArrayOutputStreamUtils(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        }
        mBuffer = new byte[size];
    }

    public static int indexOf(byte[] array, byte b, int start, int indexRange) {
        if (array == null || array.length == 0 || start >= indexRange) {
            return -1;
        } else if (start < 0) {
            start = 0;
        }
        if (indexRange > array.length) {
            indexRange = array.length;
        }
        while (start < indexRange) {
            if (array[start] == b) {
                return start;
            }
            start++;
        }
        return -1;
    }

    public static int lastIndexOf(byte[] array, byte b, int startIndex, int indexRange) {
        if (array == null || array.length == 0 || indexRange > startIndex) {
            return -1;
        } else if (indexRange < 0) {
            indexRange = 0;
        }
        if (startIndex > array.length - 1) {
            startIndex = array.length - 1;
        }
        while (startIndex >= indexRange) {
            if (array[startIndex] == b) {
                return startIndex;
            }
            startIndex--;
        }
        return -1;
    }

    public static int indexOf(byte[] array, byte[] b, int start, int indexRange) {
        if (array == null || array.length == 0 || start > indexRange || b == null || b.length > array.length || b.length == 0 || indexRange - start + 1 < b.length) {
            return -1;
        } else if (start < 0) {
            start = 0;
        }
        if (indexRange > array.length) {
            indexRange = array.length;
        }
        int i, i2;
        for (i = start; i < indexRange; i++) {
            if (array[i] == b[0]) {
                if (indexRange - i < b.length) {
                    break;
                }
                for (i2 = 1; i2 < b.length; i2++) {
                    if (array[i + i2] != b[i2]) {
                        break;
                    }
                }
                if (i2 == b.length) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int lastIndexOf(byte[] array, byte[] b, int startIndex, int indexRange) {
        if (array == null || array.length == 0 || indexRange > startIndex || b == null || b.length > array.length || b.length == 0 || startIndex - indexRange + 1 < b.length) {
            return -1;
        } else if (indexRange < 0) {
            indexRange = 0;
        }
        if (startIndex > array.length) {
            startIndex = array.length;
        }
        int i, i2;
        for (i = startIndex == array.length ? array.length - 1 : startIndex; i >= indexRange; i--) {
            if (array[i] == b[0]) {
                if (i + b.length > startIndex) {
                    continue;
                }
                for (i2 = 1; i2 < b.length; i2++) {
                    if (array[i + i2] != b[i2]) {
                        break;
                    }
                }
                if (i2 == b.length) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int getSize() {
        return mCount;
    }

    public void setSize(int size) {
        if (size > mBuffer.length) {
            size = mBuffer.length;
        }
        mCount = size;
    }

    public int getBuffSize() {
        return mBuffer.length;
    }

    
    
    private void ensureCapacity(int minCapacity) {
        
        if (minCapacity - mBuffer.length > 0) {
            grow(minCapacity);
        }
    }

    
    private void grow(int minCapacity) {
        int oldCapacity = mBuffer.length;
        
        int newCapacity = oldCapacity << 1;
        
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        if (newCapacity < 0) {
            if (minCapacity < 0) {
                
                throw new OutOfMemoryError();
            }
            newCapacity = Integer.MAX_VALUE;
        }
        mBuffer = Arrays.copyOf(mBuffer, newCapacity);
    }

    
    public void write(int b) {
        ensureCapacity(mCount + 1);
        mBuffer[mCount] = (byte) b;
        mCount += 1;
    }

    
    @Override
    public void write(byte b[], int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) - b.length > 0)) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacity(mCount + len);
        System.arraycopy(b, off, mBuffer, mCount, len);
        mCount += len;
    }

    
    public void writeTo(OutputStream out) throws IOException {
        out.write(mBuffer, 0, mCount);
    }

    
    public void reset() {
        mCount = 0;
    }

    public byte toByteArray()[] {
        if (mCount == 0) {
            return mNullByteArray;
        }
        return Arrays.copyOf(mBuffer, mCount);
    }

    
    public int size() {
        return mCount;
    }

    public String toString() {
        return new String(mBuffer, 0, mCount);
    }

    public String toString(String charsetName) throws UnsupportedEncodingException {
        return new String(mBuffer, 0, mCount, charsetName);
    }

    @Deprecated
    public String toString(int hibyte) {
        return new String(mBuffer, hibyte, 0, mCount);
    }

    public void close() {
    }

    public void releaseCache() {
        mBuffer = mNullByteArray;
        mCount = 0;
    }

    public byte[] getBuff() {
        return mBuffer;
    }

    public void seekIndex(int index) {
        setSize(index);
    }

    public int getIndex() {
        return mCount;
    }

    public int indexOfBuff(byte b, int start) {
        return indexOf(mBuffer, b, start, mBuffer.length);
    }

    public int indexOfBuff(byte[] b, int start) {
        return indexOf(mBuffer, b, start, mBuffer.length);
    }

    public int indexOfBuff(byte b, int start, int end) {
        return indexOf(mBuffer, b, start, end);
    }

    public int indexOfBuff(byte[] b, int start, int end) {
        return indexOf(mBuffer, b, start, end);
    }

    public int lastIndexOfBuff(byte b, int start) {
        return lastIndexOf(mBuffer, b, 0, start);
    }

    public int lastIndexOfBuff(byte[] b, int start) {
        return lastIndexOf(mBuffer, b, 0, start);
    }

    public int lastIndexOfBuff(byte b, int start, int end) {
        return lastIndexOf(mBuffer, b, start, end);
    }

    public int lastIndexOfBuff(byte[] b, int start, int end) {
        return lastIndexOf(mBuffer, b, start, end);
    }
}
