package com.identityworksllc.iiq.common;

import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

/**
 * Utilities to return hexadecimal hashes of strings
 */
public class HashUtilities {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Transforms the input byte array to Base64. This is intended as an input to
     * {@link #hash(String, String, Function)}, as the encoder.
     *
     * @param bytes The input bytes
     * @return A base64 string
     */
    public static String bytesToBase64(byte[] bytes) {
        return Base64.encodeBytes(bytes);
    }

    /**
     * Transforms the given byte array to hexadecimal. This is intended as an input to
     * {@link #hash(String, String, Function)}, as the encoder.
     *
     * @param bytes The input byte array
     * @return The output as a hexadecimal string
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Performs a hash of the given input with the given hash function. This implementation
     * takes care of the javax.crypto initialization for you.
     *
     * @param hashFunction The hash function to use
     * @param input The input string to hash
     * @return The hashed value as a hexadecimal string
     * @throws GeneralException if any cryptographic failures occur
     */
    public static String hash(String hashFunction, String input) throws GeneralException {
        return hash(hashFunction, input, HashUtilities::bytesToHex);
    }

    /**
     * Performs a hash of the given input with the given hash function. This method allows you
     * to replace the encoding implementation if you don't wish to receive your output as a
     * hexadecimal string.
     *
     * @param hashFunction The hash function to use
     * @param input The input string to hash
     * @param encoder A function to encode the byte array into a String if you wish to replace the hexadecimal implementation
     * @return The hashed value as a hexadecimal string
     * @throws GeneralException if any cryptographic failures occur
     */
    public static String hash(String hashFunction, String input, Function<byte[], String> encoder) throws GeneralException {
        try {
            MessageDigest md = MessageDigest.getInstance(hashFunction);
            md.update(input.getBytes());
            byte[] digest = md.digest();
            return encoder.apply(digest);
        } catch(NoSuchAlgorithmException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Hashes the input using the MD5 algorithm and returns it as a hex string
     * @param input The input string
     * @return The MD5 hash of the input string in hex
     * @throws GeneralException if any cryptographic failures occur
     */
    public static String md5(String input) throws GeneralException {
        return hash("MD5", input);
    }

    /**
     * Hashes the input using the SHA-256 algorithm and returns it as a hex string
     * @param input The input string
     * @return The SHA-256 hash of the input string in hex
     * @throws GeneralException if any cryptographic failures occur
     */
    public static String sha256(String input) throws GeneralException {
        return hash("SHA-256", input);
    }

    /**
     * Private utility constructor
     */
    private HashUtilities() {

    }
}
