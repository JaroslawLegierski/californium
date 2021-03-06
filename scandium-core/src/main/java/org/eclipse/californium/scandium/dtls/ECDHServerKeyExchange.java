/*******************************************************************************
 * Copyright (c) 2015, 2017 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Stefan Jucker - DTLS implementation
 *    Kai Hudalla (Bosch Software Innovations GmbH) - add accessor for peer address
 *    Kai Hudalla (Bosch Software Innovations GmbH) - move EC curve params to SupportedGroup enum
 *    Bosch Software Innovations GmbH - migrate to SLF4J
 ******************************************************************************/
package org.eclipse.californium.scandium.dtls;

import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;

import org.eclipse.californium.elements.util.DatagramReader;
import org.eclipse.californium.elements.util.DatagramWriter;
import org.eclipse.californium.elements.util.NoPublicAPI;
import org.eclipse.californium.elements.util.StringUtil;
import org.eclipse.californium.scandium.dtls.AlertMessage.AlertDescription;
import org.eclipse.californium.scandium.dtls.AlertMessage.AlertLevel;
import org.eclipse.californium.scandium.dtls.cipher.XECDHECryptography.SupportedGroup;

/**
/**
 * The server's ephemeral ECDH.
 * 
 * See <a href="https://tools.ietf.org/html/rfc4492#section-5.4" target="_blank">
 * RFC 4492, section 5.4 Server Key Exchange</a> for details regarding
 * the message format.
 * 
 * According <a href="https://tools.ietf.org/html/rfc8422#section-5.1.1" target="_blank">RFC
 * 8422, 5.1.1. Supported Elliptic Curves Extension</a> only "named curves" are
 * valid, the "prime" and "char2" curve descriptions are deprecated. Also only
 * "UNCOMPRESSED" as point format is valid, the other formats have been
 * deprecated.
 */
@NoPublicAPI
public abstract class ECDHServerKeyExchange extends ServerKeyExchange {

	private static final int CURVE_TYPE_BITS = 8;
	private static final int NAMED_CURVE_BITS = 16;
	private static final int PUBLIC_LENGTH_BITS = 8;

	/** The ECCurveType */
	// a named curve is used
	private static final int NAMED_CURVE = 3;

	/** ephemeral keys */
	private final SupportedGroup supportedGroup;

	private final byte[] encodedPoint;

	/**
	 * Called when reconstructing the byte array.
	 * 
	 * @param supportedGroup supported group (named curve)
	 * @param encodedPoint the encoded point on the curve (public key).
	 * @throws NullPointerException if one of the parameters are {@code null}
	 */
	protected ECDHServerKeyExchange(SupportedGroup supportedGroup, byte[] encodedPoint) {
		if (supportedGroup == null) {
			throw new NullPointerException("Supported group (curve) must not be null!");
		}
		if (encodedPoint == null) {
			throw new NullPointerException("encoded point must not be null!");
		}
		this.supportedGroup = supportedGroup;
		this.encodedPoint = encodedPoint;
	}

	protected int getNamedCurveLength() {
		return 4 + encodedPoint.length;
	}

	protected void writeNamedCurve(DatagramWriter writer) {
		// http://tools.ietf.org/html/rfc4492#section-5.4
		writer.write(NAMED_CURVE, CURVE_TYPE_BITS);
		writer.write(supportedGroup.getId(), NAMED_CURVE_BITS);
		writer.writeVarBytes(encodedPoint, PUBLIC_LENGTH_BITS);
	}

	protected static EcdhData readNamedCurve(final DatagramReader reader) throws HandshakeException {
		int curveType = reader.read(CURVE_TYPE_BITS);
		if (curveType != NAMED_CURVE) {
		throw new HandshakeException(
				String.format(
						"Curve type [%s] received in ServerKeyExchange message is unsupported",
						curveType),
				new AlertMessage(AlertLevel.FATAL, AlertDescription.HANDSHAKE_FAILURE));
		}
		int curveId = reader.read(NAMED_CURVE_BITS);
		SupportedGroup group = SupportedGroup.fromId(curveId);
		if (group == null || !group.isUsable()) {
			throw new HandshakeException(
				String.format("Server used unsupported elliptic curve (%d) for ECDH", curveId),
				new AlertMessage(AlertLevel.FATAL, AlertDescription.ILLEGAL_PARAMETER));
		}
		byte[] encodedPoint = reader.readVarBytes(PUBLIC_LENGTH_BITS);
		return new EcdhData(group, encodedPoint);
	}

	protected void updateSignatureForNamedCurve(Signature signature) throws SignatureException {
		int curveId = getSupportedGroup().getId();
		signature.update((byte) NAMED_CURVE);
		signature.update((byte) (curveId >> 8));
		signature.update((byte) curveId);
		signature.update((byte) encodedPoint.length);
		signature.update(encodedPoint);
	}

	/**
	 * Get supported group for ECDH.
	 * 
	 * @return supported group
	 */
	public SupportedGroup getSupportedGroup() {
		return supportedGroup;
	}

	/**
	 * Get encoded point
	 * 
	 * @return encoded point (public key).
	 */
	public byte[] getEncodedPoint() {
		return Arrays.copyOf(encodedPoint, encodedPoint.length);
	}

	@Override
	public String toString(int indent) {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString(indent));
		String indentation = StringUtil.indentation(indent + 1);
		sb.append(indentation).append("Diffie-Hellman public key: ");
		sb.append(supportedGroup.name()).append("-").append(StringUtil.byteArray2HexString(encodedPoint, StringUtil.NO_SEPARATOR, 16));
		sb.append(StringUtil.lineSeparator());

		return sb.toString();
	}

	/**
	 * Utility class to keep results of reading the supported group and the
	 * encoded point-
	 */
	protected static class EcdhData {

		public final SupportedGroup supportedGroup;
		public final byte[] encodedPoint;

		EcdhData(SupportedGroup supportedGroup, byte[] encodedPoint) {
			this.supportedGroup = supportedGroup;
			this.encodedPoint = encodedPoint;
		}
	}
}
