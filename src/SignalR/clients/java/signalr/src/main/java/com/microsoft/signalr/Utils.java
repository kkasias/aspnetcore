// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the Apache License, Version 2.0. See License.txt in the project root for license information.

package com.microsoft.signalr;

import java.awt.List;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import org.msgpack.core.MessageUnpacker;

class Utils {
    public static String appendQueryString(String original, String queryStringValue) {
        if (original.contains("?")) {
            return original + "&" + queryStringValue;
        } else {
            return  original + "?" + queryStringValue;
        }
    }
    
    public static Object toPrimitive(Class<?> c, Object value) {
        if( Boolean.class == c) return ((Boolean) value).booleanValue();
        if( Byte.class == c) return ((Byte) value).byteValue();
        if( Short.class == c) return ((Short) value).shortValue();
        if( Integer.class == c) return ((Integer) value).intValue();
        if( Long.class == c) return ((Long) value).longValue();
        if( Float.class == c) return ((Float) value).floatValue();
        if( Double.class == c) return ((Double) value).doubleValue();
        return value;
    }
    
    public static int readLengthHeader(ByteBuffer bb) throws IOException {
        // The payload starts with a length prefix encoded as a VarInt. VarInts use the most significant bit
        // as a marker whether the byte is the last byte of the VarInt or if it spans to the next byte. Bytes
        // appear in the reverse order - i.e. the first byte contains the least significant bits of the value
        // Examples:
        // VarInt: 0x35 - %00110101 - the most significant bit is 0 so the value is %x0110101 i.e. 0x35 (53)
        // VarInt: 0x80 0x25 - %10000000 %00101001 - the most significant bit of the first byte is 1 so the
        // remaining bits (%x0000000) are the lowest bits of the value. The most significant bit of the second
        // byte is 0 meaning this is last byte of the VarInt. The actual value bits (%x0101001) need to be
        // prepended to the bits we already read so the values is %01010010000000 i.e. 0x1480 (5248)
        // We support payloads up to 2GB so the biggest number we support is 7fffffff which when encoded as
        // VarInt is 0xFF 0xFF 0xFF 0xFF 0x07 - hence the maximum length prefix is 5 bytes.
        
        int length = 0;
        int numBytes = 0;
        int maxLength = 5;
        byte curr;
        
        do {
            // If we run out of bytes before we finish reading the length header, the message is malformed
            if (bb.hasRemaining()) {
                curr = bb.get();
            } else {
                throw new RuntimeException("The length header was incomplete");
            }
            length = length | (curr & (byte) 0x7f) << (numBytes * 7);
            numBytes++;
        } while (numBytes < maxLength && (curr & (byte) 0x80) != 0);
        
        // Max header length is 5, and the maximum value of the 5th byte is 0x07
        if ((curr & (byte) 0x80) != 0 || (numBytes == maxLength && curr > (byte) 0x07)) {
            throw new RuntimeException("Messages over 2GB in size are not supported");
        }
        
        return length;
    }
    
    public static ArrayList<Byte> getLengthHeader(int length) {
        // This code writes length prefix of the message as a VarInt. Read the comment in
        // the readLengthHeader for details.
        
        ArrayList<Byte> header = new ArrayList<Byte>();
        do {
            byte curr = (byte) (length & 0x7f);
            length >>= 7;
            if (length > 0) {
                curr |= 0x80;
            }
            header.add(curr);
        } while (length > 0);
        
        return header;
    }
}