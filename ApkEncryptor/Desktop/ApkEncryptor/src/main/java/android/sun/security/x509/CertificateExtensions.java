/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

import android.sun.misc.HexDumpEncoder;
import android.sun.security.util.Debug;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.security.cert.CertificateException;
import java.util.*;

/**
 * This class defines the Extensions attribute for the Certificate.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see android.sun.security.x509.CertAttrSet
 */
public class CertificateExtensions implements android.sun.security.x509.CertAttrSet<android.sun.security.x509.Extension> {
    /**
     * Identifier for this attribute, to be used with the
     * get, set, delete methods of Certificate, x509 type.
     */
    public static final String IDENT = "x509.info.extensions";
    /**
     * name
     */
    public static final String NAME = "extensions";

    private static final Debug debug = android.sun.security.util.Debug.getInstance("x509");
    private static Class[] PARAMS = {Boolean.class, Object.class};
    private Hashtable<String, android.sun.security.x509.Extension> map = new Hashtable<String, android.sun.security.x509.Extension>();
    private boolean unsupportedCritExt = false;
    private Map<String, android.sun.security.x509.Extension> unparseableExtensions;

    /**
     * Default constructor.
     */
    public CertificateExtensions() {
    }

    /**
     * Create the object, decoding the values from the passed DER stream.
     *
     * @param in the DerInputStream to read the Extension from.
     * @throws IOException on decoding errors.
     */
    public CertificateExtensions(android.sun.security.util.DerInputStream in) throws IOException {
        init(in);
    }

    // helper routine
    private void init(android.sun.security.util.DerInputStream in) throws IOException {

        android.sun.security.util.DerValue[] exts = in.getSequence(5);

        for (int i = 0; i < exts.length; i++) {
            android.sun.security.x509.Extension ext = new android.sun.security.x509.Extension(exts[i]);
            parseExtension(ext);
        }
    }

    // Parse the encoded extension
    private void parseExtension(android.sun.security.x509.Extension ext) throws IOException {
        try {
            Class extClass = OIDMap.getClass(ext.getExtensionId());
            if (extClass == null) {   // Unsupported extension
                if (ext.isCritical()) {
                    unsupportedCritExt = true;
                }
                if (map.put(ext.getExtensionId().toString(), ext) == null) {
                    return;
                } else {
                    throw new IOException("Duplicate extensions not allowed");
                }
            }
            Constructor cons = ((Class<?>) extClass).getConstructor(PARAMS);

            Object[] passed = new Object[]{Boolean.valueOf(ext.isCritical()),
                    ext.getExtensionValue()};
            android.sun.security.x509.CertAttrSet certExt = (android.sun.security.x509.CertAttrSet) cons.newInstance(passed);
            if (map.put(certExt.getName(), (android.sun.security.x509.Extension) certExt) != null) {
                throw new IOException("Duplicate extensions not allowed");
            }
        } catch (InvocationTargetException invk) {
            Throwable e = invk.getTargetException();
            if (ext.isCritical() == false) {
                // ignore errors parsing non-critical extensions
                if (unparseableExtensions == null) {
                    unparseableExtensions = new HashMap<String, android.sun.security.x509.Extension>();
                }
                unparseableExtensions.put(ext.getExtensionId().toString(),
                        new UnparseableExtension(ext, e));
                if (debug != null) {
                    debug.println("Error parsing extension: " + ext);
                    e.printStackTrace();
                    HexDumpEncoder h = new HexDumpEncoder();
                    System.err.println(h.encodeBuffer(ext.getExtensionValue()));
                }
                return;
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw (IOException) new IOException(e.toString()).initCause(e);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw (IOException) new IOException(e.toString()).initCause(e);
        }
    }

    /**
     * Encode the extensions in DER form to the stream, setting
     * the context specific tag as needed in the X.509 v3 certificate.
     *
     * @param out the DerOutputStream to marshal the contents to.
     * @throws CertificateException on encoding errors.
     * @throws IOException          on errors.
     */
    public void encode(OutputStream out)
            throws CertificateException, IOException {
        encode(out, false);
    }

    /**
     * Encode the extensions in DER form to the stream.
     *
     * @param out       the DerOutputStream to marshal the contents to.
     * @param isCertReq if true then no context specific tag is added.
     * @throws CertificateException on encoding errors.
     * @throws IOException          on errors.
     */
    public void encode(OutputStream out, boolean isCertReq)
            throws CertificateException, IOException {
        android.sun.security.util.DerOutputStream extOut = new android.sun.security.util.DerOutputStream();
        Collection<android.sun.security.x509.Extension> allExts = map.values();
        Object[] objs = allExts.toArray();

        for (int i = 0; i < objs.length; i++) {
            if (objs[i] instanceof android.sun.security.x509.CertAttrSet)
                ((android.sun.security.x509.CertAttrSet) objs[i]).encode(extOut);
            else if (objs[i] instanceof android.sun.security.x509.Extension)
                ((android.sun.security.x509.Extension) objs[i]).encode(extOut);
            else
                throw new CertificateException("Illegal extension object");
        }

        android.sun.security.util.DerOutputStream seq = new android.sun.security.util.DerOutputStream();
        seq.write(android.sun.security.util.DerValue.tag_Sequence, extOut);

        android.sun.security.util.DerOutputStream tmp;
        if (!isCertReq) { // certificate
            tmp = new android.sun.security.util.DerOutputStream();
            tmp.write(android.sun.security.util.DerValue.createTag(android.sun.security.util.DerValue.TAG_CONTEXT, true, (byte) 3),
                    seq);
        } else
            tmp = seq; // pkcs#10 certificateRequest

        out.write(tmp.toByteArray());
    }

    /**
     * Set the attribute value.
     *
     * @param name the extension name used in the cache.
     * @param obj  the object to set.
     * @throws IOException if the object could not be cached.
     */
    public void set(String name, Object obj) throws IOException {
        if (obj instanceof android.sun.security.x509.Extension) {
            map.put(name, (android.sun.security.x509.Extension) obj);
        } else {
            throw new IOException("Unknown extension type.");
        }
    }

    /**
     * Get the attribute value.
     *
     * @param name the extension name used in the lookup.
     * @throws IOException if named extension is not found.
     */
    public Object get(String name) throws IOException {
        Object obj = map.get(name);
        if (obj == null) {
            throw new IOException("No extension found with name " + name);
        }
        return (obj);
    }

    /**
     * Delete the attribute value.
     *
     * @param name the extension name used in the lookup.
     * @throws IOException if named extension is not found.
     */
    public void delete(String name) throws IOException {
        Object obj = map.get(name);
        if (obj == null) {
            throw new IOException("No extension found with name " + name);
        }
        map.remove(name);
    }

    public String getNameByOid(android.sun.security.util.ObjectIdentifier oid) throws IOException {
        for (String name : map.keySet()) {
            if (map.get(name).getExtensionId().equals(oid)) {
                return name;
            }
        }
        return null;
    }

    /**
     * Return an enumeration of names of attributes existing within this
     * attribute.
     */
    public Enumeration<android.sun.security.x509.Extension> getElements() {
        return map.elements();
    }

    /**
     * Return a collection view of the extensions.
     *
     * @return a collection view of the extensions in this Certificate.
     */
    public Collection<android.sun.security.x509.Extension> getAllExtensions() {
        return map.values();
    }

    public Map<String, android.sun.security.x509.Extension> getUnparseableExtensions() {
        if (unparseableExtensions == null) {
            return Collections.emptyMap();
        } else {
            return unparseableExtensions;
        }
    }

    /**
     * Return the name of this attribute.
     */
    public String getName() {
        return NAME;
    }

    /**
     * Return true if a critical extension is found that is
     * not supported, otherwise return false.
     */
    public boolean hasUnsupportedCriticalExtension() {
        return unsupportedCritExt;
    }

    /**
     * Compares this CertificateExtensions for equality with the specified
     * object. If the <code>other</code> object is an
     * <code>instanceof</code> <code>CertificateExtensions</code>, then
     * all the entries are compared with the entries from this.
     *
     * @param other the object to test for equality with this
     *              CertificateExtensions.
     * @return true iff all the entries match that of the Other,
     * false otherwise.
     */
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof CertificateExtensions))
            return false;
        Collection<android.sun.security.x509.Extension> otherC =
                ((CertificateExtensions) other).getAllExtensions();
        Object[] objs = otherC.toArray();

        int len = objs.length;
        if (len != map.size())
            return false;

        android.sun.security.x509.Extension otherExt, thisExt;
        String key = null;
        for (int i = 0; i < len; i++) {
            if (objs[i] instanceof android.sun.security.x509.CertAttrSet)
                key = ((CertAttrSet) objs[i]).getName();
            otherExt = (Extension) objs[i];
            if (key == null)
                key = otherExt.getExtensionId().toString();
            thisExt = map.get(key);
            if (thisExt == null)
                return false;
            if (!thisExt.equals(otherExt))
                return false;
        }
        return this.getUnparseableExtensions().equals(
                ((CertificateExtensions) other).getUnparseableExtensions());
    }

    /**
     * Returns a hashcode value for this CertificateExtensions.
     *
     * @return the hashcode value.
     */
    public int hashCode() {
        return map.hashCode() + getUnparseableExtensions().hashCode();
    }

    /**
     * Returns a string representation of this <tt>CertificateExtensions</tt>
     * object in the form of a set of entries, enclosed in braces and separated
     * by the ASCII characters "<tt>,&nbsp;</tt>" (comma and space).
     * <p>Overrides to <tt>toString</tt> method of <tt>Object</tt>.
     *
     * @return a string representation of this CertificateExtensions.
     */
    public String toString() {
        return map.toString();
    }

}

class UnparseableExtension extends android.sun.security.x509.Extension {
    private String name;
    private Throwable why;

    public UnparseableExtension(android.sun.security.x509.Extension ext, Throwable why) {
        super(ext);

        name = "";
        try {
            Class extClass = android.sun.security.x509.OIDMap.getClass(ext.getExtensionId());
            if (extClass != null) {
                Field field = extClass.getDeclaredField("NAME");
                name = (String) (field.get(null)) + " ";
            }
        } catch (Exception e) {
            // If we cannot find the name, just ignore it
        }

        this.why = why;
    }

    @Override
    public String toString() {
        return super.toString() +
                "Unparseable " + name + "extension due to\n" + why + "\n\n" +
                new HexDumpEncoder().encodeBuffer(getExtensionValue());
    }
}
