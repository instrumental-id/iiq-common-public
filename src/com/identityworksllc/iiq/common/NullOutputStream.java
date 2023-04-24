package com.identityworksllc.iiq.common;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that quietly swallows the input
 */
public class NullOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
        /* Do nothing */
    }
}
