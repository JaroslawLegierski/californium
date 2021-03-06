/*******************************************************************************
 * Copyright (c) 2022 Bosch IO GmbH and others.
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
 *    Bosch IO GmbH - initial implementation
 ******************************************************************************/
package org.eclipse.californium.core.server;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.EndpointContextMatcher;
import org.eclipse.californium.elements.PersistentComponent;
import org.eclipse.californium.elements.PersistentConnector;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.elements.UDPConnector;
import org.eclipse.californium.elements.category.Medium;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.rule.LoggingRule;
import org.eclipse.californium.elements.util.DataStreamReader;
import org.eclipse.californium.elements.util.DatagramWriter;
import org.eclipse.californium.elements.util.SerializationUtil;
import org.eclipse.californium.elements.util.StandardCharsets;
import org.eclipse.californium.rule.CoapNetworkRule;
import org.eclipse.californium.rule.CoapThreadsRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Verifies behavior of {@link ServersSerializationUtil}.
 */
@Category(Medium.class)
@SuppressWarnings("deprecation")
public class ServerSerializationTest {

	@ClassRule
	public static CoapNetworkRule network = new CoapNetworkRule(CoapNetworkRule.Mode.DIRECT,
			CoapNetworkRule.Mode.NATIVE);

	@Rule
	public CoapThreadsRule cleanup = new CoapThreadsRule();

	@Rule
	public LoggingRule logging = new LoggingRule();

	private DummyConnector connector1 = new DummyConnector(5684);
	private DummyConnector connector2 = new DummyConnector(5784);
	private DummyConnector connector3 = new DummyConnector(5884);
	private DummyConnector connector4 = new LableDummyConnector("test1", 5984);
	private DummyConnector connector5 = new LableDummyConnector("test2", 5994);

	private EncryptedServersSerializationUtil setup(boolean useDeprecatedFormat, Connector... connectors) {
		EncryptedServersSerializationUtil util = new EncryptedServersSerializationUtil(useDeprecatedFormat);
		Configuration config = network.createStandardTestConfig();
		CoapServer server = new CoapServer(config);
		for (Connector connector : connectors) {
			CoapEndpoint endpoint = CoapEndpoint.builder().setConfiguration(config).setConnector(connector).build();
			server.addEndpoint(endpoint);
		}
		util.add(server);
		return util;
	}
	
	private void resetConnectors() {
		connector1.data = null;
		connector2.data = null;
		connector3.data = null;
		connector4.data = null;
		connector5.data = null;
	}

	@Test
	public void testSaveAndLoad() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util.loadServers(in);
		assertArrayEquals(connector1.mark, connector1.data);
		assertArrayEquals(connector2.mark, connector2.data);
	}

	@Test
	public void testSaveSkipAndLoad() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util = setup(true, connector2);
		util.loadServers(in);
		assertThat(connector1.data, is(nullValue()));
		assertArrayEquals(connector2.mark, connector2.data);
	}

	@Test
	public void testSaveLoadAndSkip() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util = setup(true, connector1);
		util.loadServers(in);
		assertArrayEquals(connector1.mark, connector1.data);
		assertThat(connector2.data, is(nullValue()));
	}

	@Test
	public void testSaveAndLoadWithNonePersistentConnector() throws IOException {
		EncryptedServersSerializationUtil util = setup(true,
				connector1, new UDPConnector(null, network.createStandardTestConfig()));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util.loadServers(in);
		assertArrayEquals(connector1.mark, connector1.data);
		assertThat(connector2.data, is(nullValue()));
	}

	@Test
	public void testSaveAndLoadWithLabel() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector4);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util.loadServers(in);
		assertArrayEquals(connector1.mark, connector1.data);
		assertArrayEquals(connector4.mark, connector4.data);
	}

	@Test
	public void testSaveAndLoadWithFormatMigration() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector4, connector5);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		byte[] oldFormat = out.toByteArray();
		ByteArrayInputStream in = new ByteArrayInputStream(oldFormat);
		util = setup(false, connector1, connector4, connector5);
		util.loadServers(in);
		assertArrayEquals(connector1.mark, connector1.data);
		assertArrayEquals(connector4.mark, connector4.data);
		assertArrayEquals(connector5.mark, connector5.data);
		resetConnectors();
		out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		byte[] newFormat = out.toByteArray();
		in = new ByteArrayInputStream(newFormat);
		util.loadServers(in);
		assertThat(connector1.data, is(nullValue()));
		assertArrayEquals(connector4.mark, connector4.data);
		assertArrayEquals(connector5.mark, connector5.data);
		assertThat(oldFormat.length, is(not(newFormat.length)));
	}

	@Test
	public void testEncryptedSaveAndLoad() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, key, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util.loadServers(in, key);
		assertArrayEquals(connector1.mark, connector1.data);
		assertArrayEquals(connector2.mark, connector2.data);
	}

	@Test
	public void testEncryptedSaveSkipAndLoad() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, key, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util = setup(true, connector2);
		util.loadServers(in, key);
		assertThat(connector1.data, is(nullValue()));
		assertArrayEquals(connector2.mark, connector2.data);
	}

	@Test
	public void testEncryptedSaveLoadAndSkip() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, key, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util = setup(true, connector1);
		util.loadServers(in, key);
		assertArrayEquals(connector1.mark, connector1.data);
		assertThat(connector2.data, is(nullValue()));
	}

	@Test
	public void testEncryptedSaveLoadSkipAndLoad() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2, connector3);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, key, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util = setup(true, connector1, connector3);
		util.loadServers(in, key);
		assertArrayEquals(connector1.mark, connector1.data);
		assertThat(connector2.data, is(nullValue()));
	}

	@Test
	public void testEncryptedSaveAndLoadWrongKey() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, key, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		key = new SecretKeySpec("01234567".getBytes(), "PW");
		logging.setLoggingLevel("ERROR", ServersSerializationUtil.class);
		util.loadServers(in, key);
		assertThat(connector1.data, is(nullValue()));
		assertThat(connector2.data, is(nullValue()));
	}

	@Test
	public void testEncryptedSaveAndUnencryptedLoad() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, key, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		logging.setLoggingLevel("ERROR", ServersSerializationUtil.class);
		util.loadServers(in);
		assertThat(connector1.data, is(nullValue()));
		assertThat(connector2.data, is(nullValue()));
	}

	@Test
	public void testUnencryptedSaveAndEncryptedLoad() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector2);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		logging.setLoggingLevel("ERROR", ServersSerializationUtil.class);
		util.loadServers(in, key);
		assertThat(connector1.data, is(nullValue()));
		assertThat(connector2.data, is(nullValue()));
	}

	@Test
	public void testEncryptedSaveAndLoadWithLabel() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector4);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, key, 1000);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		util.loadServers(in, key);
		assertArrayEquals(connector1.mark, connector1.data);
		assertArrayEquals(connector4.mark, connector4.data);
	}

	@Test
	public void testEncryptedSaveAndLoadWithFormatMigration() throws IOException {
		EncryptedServersSerializationUtil util = setup(true, connector1, connector4, connector5);
		SecretKey key = new SecretKeySpec("1234567".getBytes(), "PW");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		util.saveServers(out, key, 1000);
		byte[] oldFormat = out.toByteArray();
		ByteArrayInputStream in = new ByteArrayInputStream(oldFormat);
		util = setup(false, connector1, connector4, connector5);
		util.loadServers(in, key);
		assertArrayEquals(connector1.mark, connector1.data);
		assertArrayEquals(connector4.mark, connector4.data);
		assertArrayEquals(connector5.mark, connector5.data);
		resetConnectors();
		out = new ByteArrayOutputStream();
		util.saveServers(out, 1000);
		byte[] newFormat = out.toByteArray();
		in = new ByteArrayInputStream(newFormat);
		util.loadServers(in);
		assertThat(connector1.data, is(nullValue()));
		assertArrayEquals(connector4.mark, connector4.data);
		assertArrayEquals(connector5.mark, connector5.data);
		assertThat(oldFormat.length, is(not(newFormat.length)));
	}

	private static class DummyConnector implements Connector, PersistentConnector {

		private InetSocketAddress address;

		private byte[] mark;

		private byte[] data;

		private DummyConnector(int port) {
			address = new InetSocketAddress(port);
			mark = String.format("dummy-%05d", port).getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public int saveConnections(OutputStream out, long maxQuietPeriodInSeconds) throws IOException {
			DatagramWriter writer = new DatagramWriter();
			int pos = SerializationUtil.writeStartItem(writer, 1, Short.SIZE);
			writer.writeVarBytes(mark, Byte.SIZE);
			SerializationUtil.writeFinishedItem(writer, pos, Short.SIZE);
			writer.writeTo(out);
			pos = SerializationUtil.writeStartItem(writer, 1, Short.SIZE);
			writer.writeVarBytes(mark, Byte.SIZE);
			SerializationUtil.writeFinishedItem(writer, pos, Short.SIZE);
			writer.writeTo(out);
			pos = SerializationUtil.writeStartItem(writer, 1, Short.SIZE);
			writer.writeVarBytes(mark, Byte.SIZE);
			SerializationUtil.writeFinishedItem(writer, pos, Short.SIZE);
			writer.writeTo(out);
			SerializationUtil.writeNoItem(out);
			data = null;
			return 3;
		}

		@Override
		public int loadConnections(InputStream in, long delta) throws IOException {
			DataStreamReader reader = new DataStreamReader(in);
			SerializationUtil.readStartItem(reader, 1, Short.SIZE);
			data = reader.readVarBytes(Byte.SIZE);
			int len = SerializationUtil.readStartItem(reader, 1, Short.SIZE);
			SerializationUtil.skipBits(reader, len * Byte.SIZE);
			len = SerializationUtil.readStartItem(reader, 1, Short.SIZE);
			SerializationUtil.skipBits(reader, len * Byte.SIZE);
			int version = reader.readNextByte() & 0xff;
			assertThat(version, is(SerializationUtil.NO_VERSION));
			return 3;
		}

		@Override
		public void start() throws IOException {
			// dummy
		}

		@Override
		public void stop() {
			// dummy
		}

		@Override
		public void destroy() {
			// dummy
		}

		@Override
		public void send(RawData msg) {
			// dummy
		}

		@Override
		public void setRawDataReceiver(RawDataChannel messageHandler) {
			// dummy
		}

		@Override
		public void setEndpointContextMatcher(EndpointContextMatcher matcher) {
			// dummy
		}

		@Override
		public InetSocketAddress getAddress() {
			// dummy
			return address;
		}

		@Override
		public String getProtocol() {
			return CoAP.PROTOCOL_DTLS;
		}

		@Override
		public boolean isRunning() {
			// dummy
			return false;
		}

		@Override
		public void processDatagram(DatagramPacket datagram) {
			// dummy
		}

	}

	private static class LableDummyConnector extends DummyConnector implements PersistentComponent {

		private final String label;

		LableDummyConnector(String label, int port) {
			super(port);
			this.label = label;
		}

		@Override
		public String getLabel() {
			return label;
		}

		@Override
		public int save(OutputStream out, long maxQuietPeriodInSeconds) throws IOException {
			return saveConnections(out, maxQuietPeriodInSeconds);
		}

		@Override
		public int load(InputStream in, long delta) throws IOException {
			return loadConnections(in, delta);
		}
	}
}
