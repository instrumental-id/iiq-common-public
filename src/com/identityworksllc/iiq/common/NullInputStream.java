package com.identityworksllc.iiq.common;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream primarily for testing purposes that always contains the given
 * number of zeroes.
 */
public class NullInputStream extends InputStream {
    private int size;

    /**
     * Constructs a new NullInputStream with a default input size of 0. The
     * first read will always produce an EOF.
     */
    public NullInputStream() {
        this(0);
    }

    /**
     * Constructs a new NullInputStream with the specified input size.
     * @param size The number of bytes to simulate in the input
     */
    public NullInputStream(int size) {
        this.size = size;
    }

    /**
     * "Reads" one byte from the NullInputStream, returning 0 if we have not exhausted the
     * given pool of bytes (specified by 'size') and -1 (EOF) if we have.
     *
     * @return 0 or -1
     * @throws IOException never, but the interface requires it
     */
    @Override
    public int read() throws IOException {
        if (size-- > 0) {
            return 0;
        }
        return -1;
    }
}
