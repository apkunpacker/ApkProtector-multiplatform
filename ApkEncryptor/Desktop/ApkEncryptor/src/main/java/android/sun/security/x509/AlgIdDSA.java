/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

import android.sun.security.util.DerValue;

import java.io.IOException;
import java.math.BigInteger;
import java.security.ProviderException;
import java.security.interfaces.DSAParams;


/**
 * This class identifies DSS/DSA Algorithm variants, which are distinguished
 * by using different algorithm parameters <em>P, Q, G</em>.  It uses the
 * NIST/IETF standard DER encoding.  These are used to implement the Digital
 * Signature Standard (DSS), FIPS 186.
 *
 * <P><em><b>NOTE:</b>  DSS/DSA Algorithm IDs may be created without these
 * parameters.  Use of DSS/DSA in modes where parameters are
 * either implicit (e.g. a default applicable to a site or a larger scope),
 * or are derived from some Certificate Authority's DSS certificate, is
 * not supported directly.  The application is responsible for creating a key
 * containing the required parameters prior to using the key in cryptographic
 * operations.  The follwoing is an example of how this may be done assuming
 * that we have a certificate called <code>currentCert</code> which doesn't
 * contain DSS/DSA parameters and we need to derive DSS/DSA parameters
 * from a CA's certificate called <code>caCert</code>.
 * <p>
 * <code><pre>
 * // key containing parameters to use
 * DSAPublicKey cAKey = (DSAPublicKey)(caCert.getPublicKey());
 * // key without parameters
 * DSAPublicKey nullParamsKey = (DSAPublicKey)(currentCert.getPublicKey());
 *
 * DSAParams cAKeyParams = cAKey.getParams();
 * KeyFactory kf = KeyFactory.getInstance("DSA");
 * DSAPublicKeySpec ks = new DSAPublicKeySpec(nullParamsKey.getY(),
 *                                            cAKeyParams.getP(),
 *                                            cAKeyParams.getQ(),
 *                                            cAKeyParams.getG());
 * DSAPublicKey usableKey = kf.generatePublic(ks);
 * </pre></code>
 *
 * @author David Brownell
 * @see java.security.interfaces.DSAParams
 * @see java.security.interfaces.DSAPublicKey
 * @see java.security.KeyFactory
 * @see java.security.spec.DSAPublicKeySpec
 */
public final
class AlgIdDSA extends AlgorithmId implements DSAParams {

    private static final long serialVersionUID = 3437177836797504046L;

    /*
     * The three unsigned integer parameters.
     */
    private BigInteger p, q, g;

    /**
     * Default constructor.  The OID and parameters must be
     * deserialized before this algorithm ID is used.
     */
    // XXX deprecated for general use
    public AlgIdDSA() {
    }

    AlgIdDSA(DerValue val) throws IOException {
        super(val.getOID());
    }

    /**
     * Construct an AlgIdDSA from an X.509 encoded byte array.
     */
    public AlgIdDSA(byte[] encodedAlg) throws IOException {
        super(new android.sun.security.util.DerValue(encodedAlg).getOID());
    }

    /**
     * Constructs a DSS/DSA Algorithm ID from unsigned integers that
     * define the algorithm parameters.  Those integers are encoded
     * as big-endian byte arrays.
     *
     * @param p the DSS/DSA paramter "P"
     * @param q the DSS/DSA paramter "Q"
     * @param g the DSS/DSA paramter "G"
     */
    public AlgIdDSA(byte p[], byte q[], byte g[])
            throws IOException {
        this(new BigInteger(1, p),
                new BigInteger(1, q),
                new BigInteger(1, g));
    }

    /**
     * Constructs a DSS/DSA Algorithm ID from numeric parameters.
     * If all three are null, then the parameters portion of the algorithm id
     * is set to null.  See note in header regarding use.
     *
     * @param p the DSS/DSA paramter "P"
     * @param q the DSS/DSA paramter "Q"
     * @param g the DSS/DSA paramter "G"
     */
    public AlgIdDSA(BigInteger p, BigInteger q, BigInteger g) {
        super(DSA_oid);

        if (p != null || q != null || g != null) {
            if (p == null || q == null || g == null)
                throw new ProviderException("Invalid parameters for DSS/DSA" +
                        " Algorithm ID");
            try {
                this.p = p;
                this.q = q;
                this.g = g;
                initializeParams();

            } catch (IOException e) {
                /* this should not happen */
                throw new ProviderException("Construct DSS/DSA Algorithm ID");
            }
        }
    }

    /**
     * Returns the DSS/DSA parameter "P"
     */
    public BigInteger getP() {
        return p;
    }

    /**
     * Returns the DSS/DSA parameter "Q"
     */
    public BigInteger getQ() {
        return q;
    }

    /**
     * Returns the DSS/DSA parameter "G"
     */
    public BigInteger getG() {
        return g;
    }

    /**
     * Returns "DSA", indicating the Digital Signature Algorithm (DSA) as
     * defined by the Digital Signature Standard (DSS), FIPS 186.
     */
    public String getName() {
        return "DSA";
    }


    /*
     * For algorithm IDs which haven't been created from a DER encoded
     * value, "params" must be created.
     */
    private void initializeParams()
            throws IOException {
        android.sun.security.util.DerOutputStream out = new android.sun.security.util.DerOutputStream();

        out.putInteger(p);
        out.putInteger(q);
        out.putInteger(g);
        params = new android.sun.security.util.DerValue(android.sun.security.util.DerValue.tag_Sequence, out.toByteArray());
    }

    /**
     * Parses algorithm parameters P, Q, and G.  They're found
     * in the "params" member, which never needs to be changed.
     */
    protected void decodeParams()
            throws IOException {
        if (params == null)
            throw new IOException("DSA alg params are null");
        if (params.tag != android.sun.security.util.DerValue.tag_Sequence)
            throw new IOException("DSA alg parsing error");

        params.data.reset();

        this.p = params.data.getBigInteger();
        this.q = params.data.getBigInteger();
        this.g = params.data.getBigInteger();

        if (params.data.available() != 0)
            throw new IOException("AlgIdDSA params, extra=" +
                    params.data.available());
    }


    /*
     * Returns a formatted string describing the parameters.
     */
    public String toString() {
        return paramsToString();
    }

    /*
     * Returns a string describing the parameters.
     */
    protected String paramsToString() {
        if (params == null)
            return " null\n";
        else
            return
                    "\n    p:\n" + android.sun.security.util.Debug.toHexString(p) +
                            "\n    q:\n" + android.sun.security.util.Debug.toHexString(q) +
                            "\n    g:\n" + android.sun.security.util.Debug.toHexString(g) +
                            "\n";
    }
}
