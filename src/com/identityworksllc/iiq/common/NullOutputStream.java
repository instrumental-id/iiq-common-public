package com.identityworksllc.iiq.common;

import java.io.OutputStream;

/**
 * An output stream that quietly swallows the input
 */
public class NullOutputStream extends OutputStream {
    /**
     * Yum, data
     */
    @Override
    public void write(int b) {
        /* Do nothing */
    }
}
