/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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

package android.sun.security.provider;

import android.sun.misc.BASE64Decoder;
import android.sun.security.pkcs.PKCS7;
import android.sun.security.pkcs.ParsingException;
import android.sun.security.provider.certpath.X509CertPath;
import android.sun.security.provider.certpath.X509CertificatePair;
import android.sun.security.util.Cache;
import android.sun.security.util.DerValue;
import android.sun.security.x509.X509CRLImpl;
import android.sun.security.x509.X509CertImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.*;
import java.util.*;

/**
 * This class defines a certificate factory for X.509 v3 certificates &
 * certification paths, and X.509 v2 certificate revocation lists (CRLs).
 *
 * @author Jan Luehe
 * @author Hemma Prafullchandra
 * @author Sean Mullan
 * @see java.security.cert.CertificateFactorySpi
 * @see java.security.cert.Certificate
 * @see java.security.cert.CertPath
 * @see java.security.cert.CRL
 * @see java.security.cert.X509Certificate
 * @see java.security.cert.X509CRL
 * @see android.sun.security.x509.X509CertImpl
 * @see X509CRLImpl
 */

public class X509Factory extends CertificateFactorySpi {

    public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERT = "-----END CERTIFICATE-----";

    private static final int ENC_MAX_LENGTH = 4096 * 1024; // 4 MB MAX

    private static final Cache certCache = Cache.newSoftMemoryCache(750);
    private static final Cache crlCache = Cache.newSoftMemoryCache(750);

    /**
     * Read from the stream until length bytes have been read or EOF has
     * been reached. Return the number of bytes actually read.
     */
    private static int readFully(InputStream in, ByteArrayOutputStream bout,
                                 int length) throws IOException {
        int read = 0;
        byte[] buffer = new byte[2048];
        while (length > 0) {
            int n = in.read(buffer, 0, length < 2048 ? length : 2048);
            if (n <= 0) {
                break;
            }
            bout.write(buffer, 0, n);
            read += n;
            length -= n;
        }
        return read;
    }

    /**
     * Return an interned X509CertImpl for the given certificate.
     * If the given X509Certificate or X509CertImpl is already present
     * in the cert cache, the cached object is returned. Otherwise,
     * if it is a X509Certificate, it is first converted to a X509CertImpl.
     * Then the X509CertImpl is added to the cache and returned.
     * <p>
     * Note that all certificates created via generateCertificate(InputStream)
     * are already interned and this method does not need to be called.
     * It is useful for certificates that cannot be created via
     * generateCertificate() and for converting other X509Certificate
     * implementations to an X509CertImpl.
     */
    public static synchronized X509CertImpl intern(X509Certificate c)
            throws CertificateException {
        if (c == null) {
            return null;
        }
        boolean isImpl = c instanceof X509CertImpl;
        byte[] encoding;
        if (isImpl) {
            encoding = ((X509CertImpl) c).getEncodedInternal();
        } else {
            encoding = c.getEncoded();
        }
        X509CertImpl newC = (X509CertImpl) getFromCache(certCache, encoding);
        if (newC != null) {
            return newC;
        }
        if (isImpl) {
            newC = (X509CertImpl) c;
        } else {
            newC = new X509CertImpl(encoding);
            encoding = newC.getEncodedInternal();
        }
        addToCache(certCache, encoding, newC);
        return newC;
    }

    /**
     * Return an interned X509CRLImpl for the given certificate.
     * For more information, see intern(X509Certificate).
     */
    public static synchronized X509CRLImpl intern(X509CRL c)
            throws CRLException {
        if (c == null) {
            return null;
        }
        boolean isImpl = c instanceof X509CRLImpl;
        byte[] encoding;
        if (isImpl) {
            encoding = ((X509CRLImpl) c).getEncodedInternal();
        } else {
            encoding = c.getEncoded();
        }
        X509CRLImpl newC = (X509CRLImpl) getFromCache(crlCache, encoding);
        if (newC != null) {
            return newC;
        }
        if (isImpl) {
            newC = (X509CRLImpl) c;
        } else {
            newC = new X509CRLImpl(encoding);
            encoding = newC.getEncodedInternal();
        }
        addToCache(crlCache, encoding, newC);
        return newC;
    }

    /**
     * Get the X509CertImpl or X509CRLImpl from the cache.
     */
    private static synchronized Object getFromCache(Cache cache,
                                                    byte[] encoding) {
        Object key = new Cache.EqualByteArray(encoding);
        Object value = cache.get(key);
        return value;
    }

    /**
     * Add the X509CertImpl or X509CRLImpl to the cache.
     */
    private static synchronized void addToCache(Cache cache, byte[] encoding,
                                                Object value) {
        if (encoding.length > ENC_MAX_LENGTH) {
            return;
        }
        Object key = new Cache.EqualByteArray(encoding);
        cache.put(key, value);
    }

    /**
     * Returns an ASN.1 SEQUENCE from a stream, which might be a BER-encoded
     * binary block or a PEM-style BASE64-encoded ASCII data. In the latter
     * case, it's de-BASE64'ed before return.
     * <p>
     * After the reading, the input stream pointer is after the BER block, or
     * after the newline character after the -----END SOMETHING----- line.
     *
     * @param is the InputStream
     * @throws IOException If any parsing error
     * @returns byte block or null if end of stream
     */
    private static byte[] readOneBlock(InputStream is) throws IOException {

        // The first character of a BLOCK.
        int c = is.read();
        if (c == -1) {
            return null;
        }
        if (c == DerValue.tag_Sequence) {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(2048);
            bout.write(c);
            readBERInternal(is, bout, c);
            return bout.toByteArray();
        } else {
            // Read BASE64 encoded data, might skip info at the beginning
            char[] data = new char[2048];
            int pos = 0;

            // Step 1: Read until header is found
            int hyphen = (c == '-') ? 1 : 0;   // count of consequent hyphens
            int last = (c == '-') ? -1 : c;    // the char before hyphen
            while (true) {
                int next = is.read();
                if (next == -1) {
                    // We accept useless data after the last block,
                    // say, empty lines.
                    return null;
                }
                if (next == '-') {
                    hyphen++;
                } else {
                    hyphen = 0;
                    last = next;
                }
                if (hyphen == 5 && (last == -1 || last == '\r' || last == '\n')) {
                    break;
                }
            }

            // Step 2: Read the rest of header, determine the line end
            int end;
            StringBuffer header = new StringBuffer("-----");
            while (true) {
                int next = is.read();
                if (next == -1) {
                    throw new IOException("Incomplete data");
                }
                if (next == '\n') {
                    end = '\n';
                    break;
                }
                if (next == '\r') {
                    next = is.read();
                    if (next == -1) {
                        throw new IOException("Incomplete data");
                    }
                    if (next == '\n') {
                        end = '\n';
                    } else {
                        end = '\r';
                        data[pos++] = (char) next;
                    }
                    break;
                }
                header.append((char) next);
            }

            // Step 3: Read the data
            while (true) {
                int next = is.read();
                if (next == -1) {
                    throw new IOException("Incomplete data");
                }
                if (next != '-') {
                    data[pos++] = (char) next;
                    if (pos >= data.length) {
                        data = Arrays.copyOf(data, data.length + 1024);
                    }
                } else {
                    break;
                }
            }

            // Step 4: Consume the footer
            StringBuffer footer = new StringBuffer("-");
            while (true) {
                int next = is.read();
                // Add next == '\n' for maximum safety, in case endline
                // is not consistent.
                if (next == -1 || next == end || next == '\n') {
                    break;
                }
                if (next != '\r') footer.append((char) next);
            }

            checkHeaderFooter(header.toString(), footer.toString());

            BASE64Decoder decoder = new BASE64Decoder();
            return decoder.decodeBuffer(new String(data, 0, pos));
        }
    }

    private static void checkHeaderFooter(String header,
                                          String footer) throws IOException {
        if (header.length() < 16 || !header.startsWith("-----BEGIN ") ||
                !header.endsWith("-----")) {
            throw new IOException("Illegal header: " + header);
        }
        if (footer.length() < 14 || !footer.startsWith("-----END ") ||
                !footer.endsWith("-----")) {
            throw new IOException("Illegal footer: " + footer);
        }
        String headerType = header.substring(11, header.length() - 5);
        String footerType = footer.substring(9, footer.length() - 5);
        if (!headerType.equals(footerType)) {
            throw new IOException("Header and footer do not match: " +
                    header + " " + footer);
        }
    }

    /**
     * Read one BER data block. This method is aware of indefinite-length BER
     * encoding and will read all of the sub-sections in a recursive way
     *
     * @param is   Read from this InputStream
     * @param bout Write into this OutputStream
     * @param tag  Tag already read (-1 mean not read)
     * @throws IOException Any parsing error
     * @returns The current tag, used to check EOC in indefinite-length BER
     */
    private static int readBERInternal(InputStream is,
                                       ByteArrayOutputStream bout, int tag) throws IOException {

        if (tag == -1) {        // Not read before the call, read now
            tag = is.read();
            if (tag == -1) {
                throw new IOException("BER/DER tag info absent");
            }
            if ((tag & 0x1f) == 0x1f) {
                throw new IOException("Multi octets tag not supported");
            }
            bout.write(tag);
        }

        int n = is.read();
        if (n == -1) {
            throw new IOException("BER/DER length info ansent");
        }
        bout.write(n);

        int length;

        if (n == 0x80) {        // Indefinite-length encoding
            if ((tag & 0x20) != 0x20) {
                throw new IOException(
                        "Non constructed encoding must have definite length");
            }
            while (true) {
                int subTag = readBERInternal(is, bout, -1);
                if (subTag == 0) {   // EOC, end of indefinite-length section
                    break;
                }
            }
        } else {
            if (n < 0x80) {
                length = n;
            } else if (n == 0x81) {
                length = is.read();
                if (length == -1) {
                    throw new IOException("Incomplete BER/DER length info");
                }
                bout.write(length);
            } else if (n == 0x82) {
                int highByte = is.read();
                int lowByte = is.read();
                if (lowByte == -1) {
                    throw new IOException("Incomplete BER/DER length info");
                }
                bout.write(highByte);
                bout.write(lowByte);
                length = (highByte << 8) | lowByte;
            } else if (n == 0x83) {
                int highByte = is.read();
                int midByte = is.read();
                int lowByte = is.read();
                if (lowByte == -1) {
                    throw new IOException("Incomplete BER/DER length info");
                }
                bout.write(highByte);
                bout.write(midByte);
                bout.write(lowByte);
                length = (highByte << 16) | (midByte << 8) | lowByte;
            } else { // ignore longer length forms
                throw new IOException("Invalid BER/DER data (too huge?)");
            }
            if (readFully(is, bout, length) != length) {
                throw new IOException("Incomplete BER/DER data");
            }
        }
        return tag;
    }

    /**
     * Generates an X.509 certificate object and initializes it with
     * the data read from the input stream <code>is</code>.
     *
     * @param is an input stream with the certificate data.
     * @return an X.509 certificate object initialized with the data
     * from the input stream.
     * @throws CertificateException on parsing errors.
     */
    public Certificate engineGenerateCertificate(InputStream is)
            throws CertificateException {
        if (is == null) {
            // clear the caches (for debugging)
            certCache.clear();
            X509CertificatePair.clearCache();
            throw new CertificateException("Missing input stream");
        }
        try {
            byte[] encoding = readOneBlock(is);
            if (encoding != null) {
                X509CertImpl cert = (X509CertImpl) getFromCache(certCache, encoding);
                if (cert != null) {
                    return cert;
                }
                cert = new X509CertImpl(encoding);
                addToCache(certCache, cert.getEncodedInternal(), cert);
                return cert;
            } else {
                throw new IOException("Empty input");
            }
        } catch (IOException ioe) {
            throw (CertificateException) new CertificateException
                    ("Could not parse certificate: " + ioe.toString()).initCause(ioe);
        }
    }

    /**
     * Generates a <code>CertPath</code> object and initializes it with
     * the data read from the <code>InputStream</code> inStream. The data
     * is assumed to be in the default encoding.
     *
     * @param inStream an <code>InputStream</code> containing the data
     * @return a <code>CertPath</code> initialized with the data from the
     * <code>InputStream</code>
     * @throws CertificateException if an exception occurs while decoding
     * @since 1.4
     */
    public CertPath engineGenerateCertPath(InputStream inStream)
            throws CertificateException {
        if (inStream == null) {
            throw new CertificateException("Missing input stream");
        }
        try {
            byte[] encoding = readOneBlock(inStream);
            if (encoding != null) {
                return new X509CertPath(new ByteArrayInputStream(encoding));
            } else {
                throw new IOException("Empty input");
            }
        } catch (IOException ioe) {
            throw new CertificateException(ioe.getMessage());
        }
    }

    /**
     * Generates a <code>CertPath</code> object and initializes it with
     * the data read from the <code>InputStream</code> inStream. The data
     * is assumed to be in the specified encoding.
     *
     * @param inStream an <code>InputStream</code> containing the data
     * @param encoding the encoding used for the data
     * @return a <code>CertPath</code> initialized with the data from the
     * <code>InputStream</code>
     * @throws CertificateException if an exception occurs while decoding or
     *                              the encoding requested is not supported
     * @since 1.4
     */
    public CertPath engineGenerateCertPath(InputStream inStream,
                                           String encoding) throws CertificateException {
        if (inStream == null) {
            throw new CertificateException("Missing input stream");
        }
        try {
            byte[] data = readOneBlock(inStream);
            if (data != null) {
                return new X509CertPath(new ByteArrayInputStream(data), encoding);
            } else {
                throw new IOException("Empty input");
            }
        } catch (IOException ioe) {
            throw new CertificateException(ioe.getMessage());
        }
    }

    /**
     * Generates a <code>CertPath</code> object and initializes it with
     * a <code>List</code> of <code>Certificate</code>s.
     * <p>
     * The certificates supplied must be of a type supported by the
     * <code>CertificateFactory</code>. They will be copied out of the supplied
     * <code>List</code> object.
     *
     * @param certificates a <code>List</code> of <code>Certificate</code>s
     * @return a <code>CertPath</code> initialized with the supplied list of
     * certificates
     * @throws CertificateException if an exception occurs
     * @since 1.4
     */
    public CertPath
    engineGenerateCertPath(List<? extends Certificate> certificates)
            throws CertificateException {
        return (new X509CertPath(certificates));
    }

    /**
     * Returns an iteration of the <code>CertPath</code> encodings supported
     * by this certificate factory, with the default encoding first.
     * <p>
     * Attempts to modify the returned <code>Iterator</code> via its
     * <code>remove</code> method result in an
     * <code>UnsupportedOperationException</code>.
     *
     * @return an <code>Iterator</code> over the names of the supported
     * <code>CertPath</code> encodings (as <code>String</code>s)
     * @since 1.4
     */
    public Iterator<String> engineGetCertPathEncodings() {
        return (X509CertPath.getEncodingsStatic());
    }

    /**
     * Returns a (possibly empty) collection view of X.509 certificates read
     * from the given input stream <code>is</code>.
     *
     * @param is the input stream with the certificates.
     * @return a (possibly empty) collection view of X.509 certificate objects
     * initialized with the data from the input stream.
     * @throws CertificateException on parsing errors.
     */
    public Collection<? extends java.security.cert.Certificate>
    engineGenerateCertificates(InputStream is)
            throws CertificateException {
        if (is == null) {
            throw new CertificateException("Missing input stream");
        }
        try {
            return parseX509orPKCS7Cert(is);
        } catch (IOException ioe) {
            throw new CertificateException(ioe);
        }
    }

    /**
     * Generates an X.509 certificate revocation list (CRL) object and
     * initializes it with the data read from the given input stream
     * <code>is</code>.
     *
     * @param is an input stream with the CRL data.
     * @return an X.509 CRL object initialized with the data
     * from the input stream.
     * @throws CRLException on parsing errors.
     */
    public CRL engineGenerateCRL(InputStream is)
            throws CRLException {
        if (is == null) {
            // clear the cache (for debugging)
            crlCache.clear();
            throw new CRLException("Missing input stream");
        }
        try {
            byte[] encoding = readOneBlock(is);
            if (encoding != null) {
                X509CRLImpl crl = (X509CRLImpl) getFromCache(crlCache, encoding);
                if (crl != null) {
                    return crl;
                }
                crl = new X509CRLImpl(encoding);
                addToCache(crlCache, crl.getEncodedInternal(), crl);
                return crl;
            } else {
                throw new IOException("Empty input");
            }
        } catch (IOException ioe) {
            throw new CRLException(ioe.getMessage());
        }
    }

    /**
     * Returns a (possibly empty) collection view of X.509 CRLs read
     * from the given input stream <code>is</code>.
     *
     * @param is the input stream with the CRLs.
     * @return a (possibly empty) collection view of X.509 CRL objects
     * initialized with the data from the input stream.
     * @throws CRLException on parsing errors.
     */
    public Collection<? extends java.security.cert.CRL> engineGenerateCRLs(
            InputStream is) throws CRLException {
        if (is == null) {
            throw new CRLException("Missing input stream");
        }
        try {
            return parseX509orPKCS7CRL(is);
        } catch (IOException ioe) {
            throw new CRLException(ioe.getMessage());
        }
    }

    /*
     * Parses the data in the given input stream as a sequence of DER
     * encoded X.509 certificates (in binary or base 64 encoded format) OR
     * as a single PKCS#7 encoded blob (in binary or base64 encoded format).
     */
    private Collection<? extends java.security.cert.Certificate>
    parseX509orPKCS7Cert(InputStream is)
            throws CertificateException, IOException {
        Collection<X509CertImpl> coll = new ArrayList<>();
        byte[] data = readOneBlock(is);
        if (data == null) {
            return new ArrayList<>(0);
        }
        try {
            PKCS7 pkcs7 = new PKCS7(data);
            X509Certificate[] certs = pkcs7.getCertificates();
            // certs are optional in PKCS #7
            if (certs != null) {
                return Arrays.asList(certs);
            } else {
                // no crls provided
                return new ArrayList<>(0);
            }
        } catch (ParsingException e) {
            while (data != null) {
                coll.add(new X509CertImpl(data));
                data = readOneBlock(is);
            }
        }
        return coll;
    }

    /*
     * Parses the data in the given input stream as a sequence of DER encoded
     * X.509 CRLs (in binary or base 64 encoded format) OR as a single PKCS#7
     * encoded blob (in binary or base 64 encoded format).
     */
    private Collection<? extends java.security.cert.CRL>
    parseX509orPKCS7CRL(InputStream is)
            throws CRLException, IOException {
        Collection<X509CRLImpl> coll = new ArrayList<>();
        byte[] data = readOneBlock(is);
        if (data == null) {
            return new ArrayList<>(0);
        }
        try {
            PKCS7 pkcs7 = new PKCS7(data);
            X509CRL[] crls = pkcs7.getCRLs();
            // CRLs are optional in PKCS #7
            if (crls != null) {
                return Arrays.asList(crls);
            } else {
                // no crls provided
                return new ArrayList<>(0);
            }
        } catch (ParsingException e) {
            while (data != null) {
                coll.add(new X509CRLImpl(data));
                data = readOneBlock(is);
            }
        }
        return coll;
    }
}
