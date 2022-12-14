/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 * This class defines the certificate policy set ASN.1 object.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class CertificatePolicySet {

    private final Vector<android.sun.security.x509.CertificatePolicyId> ids;

    /**
     * The default constructor for this class.
     *
     * @param ids the sequence of CertificatePolicyId's.
     */
    public CertificatePolicySet(Vector<android.sun.security.x509.CertificatePolicyId> ids) {
        this.ids = ids;
    }

    /**
     * Create the object from the DerValue.
     *
     * @param in the passed DerInputStream.
     * @throws IOException on decoding errors.
     */
    public CertificatePolicySet(android.sun.security.util.DerInputStream in) throws IOException {
        ids = new Vector<android.sun.security.x509.CertificatePolicyId>();
        android.sun.security.util.DerValue[] seq = in.getSequence(5);

        for (int i = 0; i < seq.length; i++) {
            android.sun.security.x509.CertificatePolicyId id = new android.sun.security.x509.CertificatePolicyId(seq[i]);
            ids.addElement(id);
        }
    }

    /**
     * Return printable form of the object.
     */
    public String toString() {
        String s = "CertificatePolicySet:[\n"
                + ids.toString()
                + "]\n";

        return (s);
    }

    /**
     * Encode the policy set to the output stream.
     *
     * @param out the DerOutputStream to encode the data to.
     */
    public void encode(DerOutputStream out) throws IOException {
        android.sun.security.util.DerOutputStream tmp = new android.sun.security.util.DerOutputStream();

        for (int i = 0; i < ids.size(); i++) {
            ids.elementAt(i).encode(tmp);
        }
        out.write(android.sun.security.util.DerValue.tag_Sequence, tmp);
    }

    /**
     * Return the sequence of CertificatePolicyIds.
     *
     * @return A List containing the CertificatePolicyId objects.
     */
    public List<CertificatePolicyId> getCertPolicyIds() {
        return Collections.unmodifiableList(ids);
    }
}
