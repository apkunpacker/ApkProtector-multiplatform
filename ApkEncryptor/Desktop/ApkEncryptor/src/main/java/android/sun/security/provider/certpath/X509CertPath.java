/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

package android.sun.security.provider.certpath;

import android.sun.security.pkcs.ContentInfo;
import android.sun.security.pkcs.PKCS7;
import android.sun.security.pkcs.SignerInfo;
import android.sun.security.util.DerInputStream;
import android.sun.security.util.DerOutputStream;
import android.sun.security.util.DerValue;
import android.sun.security.x509.AlgorithmId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.*;
import java.util.*;


/**
 * A {@link java.security.cert.CertPath CertPath} (certification path)
 * consisting exclusively of
 * {@link java.security.cert.X509Certificate X509Certificate}s.
 * <p>
 * By convention, X.509 <code>CertPath</code>s are stored from target
 * to trust anchor.
 * That is, the issuer of one certificate is the subject of the following
 * one. However, unvalidated X.509 <code>CertPath</code>s may not follow
 * this convention. PKIX <code>CertPathValidator</code>s will detect any
 * departure from this convention and throw a
 * <code>CertPathValidatorException</code>.
 *
 * @author Yassir Elley
 * @since 1.4
 */
public class X509CertPath extends CertPath {

    private static final long serialVersionUID = 4989800333263052980L;
    /**
     * The names of our encodings.  PkiPath is the default.
     */
    private static final String COUNT_ENCODING = "count";
    private static final String PKCS7_ENCODING = "PKCS7";
    private static final String PKIPATH_ENCODING = "PkiPath";
    /**
     * List of supported encodings
     */
    private static final Collection<String> encodingList;

    static {
        List<String> list = new ArrayList<String>(2);
        list.add(PKIPATH_ENCODING);
        list.add(PKCS7_ENCODING);
        encodingList = Collections.unmodifiableCollection(list);
    }

    /**
     * List of certificates in this chain
     */
    private List<X509Certificate> certs;

    /**
     * Creates an <code>X509CertPath</code> from a <code>List</code> of
     * <code>X509Certificate</code>s.
     * <p>
     * The certificates are copied out of the supplied <code>List</code>
     * object.
     *
     * @param certs a <code>List</code> of <code>X509Certificate</code>s
     * @throws CertificateException if <code>certs</code> contains an element
     *                              that is not an <code>X509Certificate</code>
     */
    public X509CertPath(List<? extends Certificate> certs) throws CertificateException {
        super("X.509");

        // Ensure that the List contains only X509Certificates
        for (Object obj : (List<?>) certs) {
            if (obj instanceof X509Certificate == false) {
                throw new CertificateException
                        ("List is not all X509Certificates: "
                                + obj.getClass().getName());
            }
        }

        // Assumes that the resulting List is thread-safe. This is true
        // because we ensure that it cannot be modified after construction
        // and the methods in the Sun JDK 1.4 implementation of ArrayList that
        // allow read-only access are thread-safe.
        this.certs = Collections.unmodifiableList(
                new ArrayList<X509Certificate>((List<X509Certificate>) certs));
    }

    /**
     * Creates an <code>X509CertPath</code>, reading the encoded form
     * from an <code>InputStream</code>. The data is assumed to be in
     * the default encoding.
     *
     * @param is the <code>InputStream</code> to read the data from
     * @throws CertificateException if an exception occurs while decoding
     */
    public X509CertPath(InputStream is) throws CertificateException {
        this(is, PKIPATH_ENCODING);
    }

    /**
     * Creates an <code>X509CertPath</code>, reading the encoded form
     * from an InputStream. The data is assumed to be in the specified
     * encoding.
     *
     * @param is       the <code>InputStream</code> to read the data from
     * @param encoding the encoding used
     * @throws CertificateException if an exception occurs while decoding or
     *                              the encoding requested is not supported
     */
    public X509CertPath(InputStream is, String encoding)
            throws CertificateException {
        super("X.509");

        if (PKIPATH_ENCODING.equals(encoding)) {
            certs = parsePKIPATH(is);
        } else if (PKCS7_ENCODING.equals(encoding)) {
            certs = parsePKCS7(is);
        } else {
            throw new CertificateException("unsupported encoding");
        }
    }

    /**
     * Parse a PKIPATH format CertPath from an InputStream. Return an
     * unmodifiable List of the certificates.
     *
     * @param is the <code>InputStream</code> to read the data from
     * @return an unmodifiable List of the certificates
     * @throws CertificateException if an exception occurs
     */
    private static List<X509Certificate> parsePKIPATH(InputStream is)
            throws CertificateException {
        List<X509Certificate> certList = null;
        CertificateFactory certFac = null;

        if (is == null) {
            throw new CertificateException("input stream is null");
        }

        try {
            DerInputStream dis = new DerInputStream(readAllBytes(is));
            DerValue[] seq = dis.getSequence(3);
            if (seq.length == 0) {
                return Collections.<X509Certificate>emptyList();
            }

            certFac = CertificateFactory.getInstance("X.509");
            certList = new ArrayList<X509Certificate>(seq.length);

            // append certs in reverse order (target to trust anchor)
            for (int i = seq.length - 1; i >= 0; i--) {
                certList.add((X509Certificate) certFac.generateCertificate
                        (new ByteArrayInputStream(seq[i].toByteArray())));
            }

            return Collections.unmodifiableList(certList);

        } catch (IOException ioe) {
            CertificateException ce = new CertificateException("IOException" +
                    " parsing PkiPath data: " + ioe);
            ce.initCause(ioe);
            throw ce;
        }
    }

    /**
     * Parse a PKCS#7 format CertPath from an InputStream. Return an
     * unmodifiable List of the certificates.
     *
     * @param is the <code>InputStream</code> to read the data from
     * @return an unmodifiable List of the certificates
     * @throws CertificateException if an exception occurs
     */
    private static List<X509Certificate> parsePKCS7(InputStream is)
            throws CertificateException {
        List<X509Certificate> certList;

        if (is == null) {
            throw new CertificateException("input stream is null");
        }

        try {
            if (is.markSupported() == false) {
                // Copy the entire input stream into an InputStream that does
                // support mark
                is = new ByteArrayInputStream(readAllBytes(is));
            }
            ;
            PKCS7 pkcs7 = new PKCS7(is);

            X509Certificate[] certArray = pkcs7.getCertificates();
            // certs are optional in PKCS #7
            if (certArray != null) {
                certList = Arrays.asList(certArray);
            } else {
                // no certs provided
                certList = new ArrayList<X509Certificate>(0);
            }
        } catch (IOException ioe) {
            throw new CertificateException("IOException parsing PKCS7 data: " +
                    ioe);
        }
        // Assumes that the resulting List is thread-safe. This is true
        // because we ensure that it cannot be modified after construction
        // and the methods in the Sun JDK 1.4 implementation of ArrayList that
        // allow read-only access are thread-safe.
        return Collections.unmodifiableList(certList);
    }

    /*
     * Reads the entire contents of an InputStream into a byte array.
     *
     * @param is the InputStream to read from
     * @return the bytes read from the InputStream
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
        int n;
        while ((n = is.read(buffer)) != -1) {
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }

    /**
     * Returns the encodings supported by this certification path, with the
     * default encoding first.
     *
     * @return an <code>Iterator</code> over the names of the supported
     * encodings (as Strings)
     */
    public static Iterator<String> getEncodingsStatic() {
        return encodingList.iterator();
    }

    /**
     * Returns the encoded form of this certification path, using the
     * default encoding.
     *
     * @return the encoded bytes
     * @throws CertificateEncodingException if an encoding error occurs
     */
    public byte[] getEncoded() throws CertificateEncodingException {
        // @@@ Should cache the encoded form
        return encodePKIPATH();
    }

    /**
     * Encode the CertPath using PKIPATH format.
     *
     * @return a byte array containing the binary encoding of the PkiPath object
     * @throws CertificateEncodingException if an exception occurs
     */
    private byte[] encodePKIPATH() throws CertificateEncodingException {

        ListIterator<X509Certificate> li = certs.listIterator(certs.size());
        try {
            DerOutputStream bytes = new DerOutputStream();
            // encode certs in reverse order (trust anchor to target)
            // according to PkiPath format
            while (li.hasPrevious()) {
                X509Certificate cert = li.previous();
                // check for duplicate cert
                if (certs.lastIndexOf(cert) != certs.indexOf(cert)) {
                    throw new CertificateEncodingException
                            ("Duplicate Certificate");
                }
                // get encoded certificates
                byte[] encoded = cert.getEncoded();
                bytes.write(encoded);
            }

            // Wrap the data in a SEQUENCE
            DerOutputStream derout = new DerOutputStream();
            derout.write(DerValue.tag_SequenceOf, bytes);
            return derout.toByteArray();

        } catch (IOException ioe) {
            CertificateEncodingException ce = new CertificateEncodingException
                    ("IOException encoding PkiPath data: " + ioe);
            ce.initCause(ioe);
            throw ce;
        }
    }

    /**
     * Encode the CertPath using PKCS#7 format.
     *
     * @return a byte array containing the binary encoding of the PKCS#7 object
     * @throws CertificateEncodingException if an exception occurs
     */
    private byte[] encodePKCS7() throws CertificateEncodingException {
        PKCS7 p7 = new PKCS7(new AlgorithmId[0],
                new ContentInfo(ContentInfo.DATA_OID, null),
                certs.toArray(new X509Certificate[certs.size()]),
                new SignerInfo[0]);
        DerOutputStream derout = new DerOutputStream();
        try {
            p7.encodeSignedData(derout);
        } catch (IOException ioe) {
            throw new CertificateEncodingException(ioe.getMessage());
        }
        return derout.toByteArray();
    }

    /**
     * Returns the encoded form of this certification path, using the
     * specified encoding.
     *
     * @param encoding the name of the encoding to use
     * @return the encoded bytes
     * @throws CertificateEncodingException if an encoding error occurs or
     *                                      the encoding requested is not supported
     */
    public byte[] getEncoded(String encoding)
            throws CertificateEncodingException {
        if (PKIPATH_ENCODING.equals(encoding)) {
            return encodePKIPATH();
        } else if (PKCS7_ENCODING.equals(encoding)) {
            return encodePKCS7();
        } else {
            throw new CertificateEncodingException("unsupported encoding");
        }
    }

    /**
     * Returns an iteration of the encodings supported by this certification
     * path, with the default encoding first.
     * <p>
     * Attempts to modify the returned <code>Iterator</code> via its
     * <code>remove</code> method result in an
     * <code>UnsupportedOperationException</code>.
     *
     * @return an <code>Iterator</code> over the names of the supported
     * encodings (as Strings)
     */
    public Iterator<String> getEncodings() {
        return getEncodingsStatic();
    }

    /**
     * Returns the list of certificates in this certification path.
     * The <code>List</code> returned must be immutable and thread-safe.
     *
     * @return an immutable <code>List</code> of <code>X509Certificate</code>s
     * (may be empty, but not null)
     */
    public List<X509Certificate> getCertificates() {
        return certs;
    }
}
