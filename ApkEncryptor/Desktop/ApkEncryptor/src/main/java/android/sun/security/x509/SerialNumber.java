/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package android.sun.security.x509;

import android.sun.security.util.DerOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

/**
 * This class defines the SerialNumber class used by certificates.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class SerialNumber {
    private BigInteger serialNum;

    /**
     * The default constructor for this class using BigInteger.
     *
     * @param num the BigInteger number used to create the serial number.
     */
    public SerialNumber(BigInteger num) {
        serialNum = num;
    }

    /**
     * The default constructor for this class using int.
     *
     * @param num the BigInteger number used to create the serial number.
     */
    public SerialNumber(int num) {
        serialNum = BigInteger.valueOf(num);
    }

    /**
     * Create the object, decoding the values from the passed DER stream.
     *
     * @param in the DerInputStream to read the SerialNumber from.
     * @throws IOException on decoding errors.
     */
    public SerialNumber(android.sun.security.util.DerInputStream in) throws IOException {
        android.sun.security.util.DerValue derVal = in.getDerValue();
        construct(derVal);
    }

    /**
     * Create the object, decoding the values from the passed DerValue.
     *
     * @param val the DerValue to read the SerialNumber from.
     * @throws IOException on decoding errors.
     */
    public SerialNumber(android.sun.security.util.DerValue val) throws IOException {
        construct(val);
    }

    /**
     * Create the object, decoding the values from the passed stream.
     *
     * @param in the InputStream to read the SerialNumber from.
     * @throws IOException on decoding errors.
     */
    public SerialNumber(InputStream in) throws IOException {
        android.sun.security.util.DerValue derVal = new android.sun.security.util.DerValue(in);
        construct(derVal);
    }

    // Construct the class from the DerValue
    private void construct(android.sun.security.util.DerValue derVal) throws IOException {
        serialNum = derVal.getBigInteger();
        if (derVal.data.available() != 0) {
            throw new IOException("Excess SerialNumber data");
        }
    }

    /**
     * Return the SerialNumber as user readable string.
     */
    public String toString() {
        return ("SerialNumber: [" + android.sun.security.util.Debug.toHexString(serialNum) + "]");
    }

    /**
     * Encode the SerialNumber in DER form to the stream.
     *
     * @param out the DerOutputStream to marshal the contents to.
     * @throws IOException on errors.
     */
    public void encode(DerOutputStream out) throws IOException {
        out.putInteger(serialNum);
    }

    /**
     * Return the serial number.
     */
    public BigInteger getNumber() {
        return serialNum;
    }
}
