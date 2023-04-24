package com.identityworksllc.iiq.common;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream primarily for testing purposes that always contains the given
 * number of zeroes.
 */
public class NullInputStream extends InputStream {
    private int size;

    public NullInputStream() {
        this(0);
    }

    public NullInputStream(int size) {
        this.size = size;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() throws IOException {
        if (size-- > 0) {
            return 0;
        }
        return -1;
    }
}
