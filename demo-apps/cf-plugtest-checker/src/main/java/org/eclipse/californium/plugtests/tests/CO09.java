/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
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
 ******************************************************************************/
package org.eclipse.californium.plugtests.tests;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.plugtests.TestClientAbstract;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;

/**
 * TD_COAP_OBS_09: Update of the observed resource
 */
public class CO09 extends TestClientAbstract {

	private static final String RESOURCE_URI = "/obs";
	private final ResponseCode EXPECTED_RESPONSE_CODE = ResponseCode.CONTENT;
	private final ResponseCode EXPECTED_RESPONSE_CODE_1 = ResponseCode.CHANGED;

	private int contentType = MediaTypeRegistry.TEXT_PLAIN;
	private String newValue = "New value";

	public CO09(String serverURI) {
		super(CO09.class.getSimpleName());

		// create the request
		Request request = new Request(Code.GET, Type.CON);
		// request.setToken(TokenManager.getInstance().acquireToken(false));
		request.setObserve();
		// set the parameters and execute the request
		executeRequest(request, serverURI, RESOURCE_URI);

	}

	@Override
	protected synchronized void executeRequest(Request request, String serverURI, String resourceUri) {

		// defensive check for slash
		if (!serverURI.endsWith("/") && !resourceUri.startsWith("/")) {
			resourceUri = "/" + resourceUri;
		}

		URI uri = null;
		try {
			uri = new URI(serverURI + resourceUri);
			setUseTcp(uri.getScheme());
		} catch (URISyntaxException use) {
			throw new IllegalArgumentException("Invalid URI: " + use.getMessage());
		}

		addContextObserver(request);
		request.setURI(uri);

		// for observing
		int observeLoop = 2;

		// print request info
		if (verbose) {
			System.out.println("Request for test " + this.testName + " sent");
			Utils.prettyPrint(request);
		}

		// execute the request
		try {
			Response response = null;
			boolean success = true;

			startObserve(request);

			System.out.println();
			System.out.println("**** TEST: " + testName + " ****");
			System.out.println("**** BEGIN CHECK ****");

			response = request.waitForResponse(6000);
			if (response != null) {
				success &= checkCode(EXPECTED_RESPONSE_CODE, response.getCode());
				success &= checkType(Type.ACK, response.getType());
				success &= hasContentType(response);
				success &= hasToken(response);
				success &= hasObserve(response);
			}
			else {
				System.out.println("FAIL: No notification after registration");
				success = false;
			}

			// receive multiple responses
			for (int l = 0; success && l < observeLoop; ++l) {
				response = waitForNotification(6000);

				// checking the response
				if (response != null) {
					System.out.println("Received notification " + l);

					// print response info
					if (verbose) {
						System.out.println("Response received");
						System.out.println("Time elapsed (ms): " + TimeUnit.NANOSECONDS.toMillis(response.getApplicationRttNanos()));
						Utils.prettyPrint(response);
					}

					success &= checkResponse(request, response);

					if (!hasObserve(response)) {
						break;
					}
				}
				else {
					System.out.println("FAIL: missing notification");
					success = false;
				}
			}

			// Client is requested to update the /obs resource on Server
			System.out.println("+++++ Sending PUT +++++");
			Request asyncRequest = new Request(Code.PUT, Type.CON);
			addContextObserver(asyncRequest);
			asyncRequest.setPayload(newValue);
			asyncRequest.getOptions().setContentFormat(contentType);
			asyncRequest.setURI(uri);
			asyncRequest.send();

			response = asyncRequest.waitForResponse(6000);

			// checking the response
			if (response != null) {
				success &= checkCode(EXPECTED_RESPONSE_CODE_1, response.getCode());
			}
			else {
				System.out.println("FAIL: missing response");
				success = false;
			}

			response = waitForNotification(6000);
			if (response != null) {
				success &= checkCode(EXPECTED_RESPONSE_CODE, response.getCode());
				success &= hasObserve(response);
				success &= hasContentType(response);
				success &= hasToken(response);
				success &= checkString(newValue, response.getPayloadString(), "payload");
			}
			else {
				System.out.println("FAIL: missing notification after PUT");
				success = false;
			}

			if (success) {
				System.out.println("**** TEST PASSED ****");
				addSummaryEntry(testName + ": PASSED");
			} else {
				System.out.println("**** TEST FAILED ****");
				addSummaryEntry(testName + ": --FAILED--");
			}

			tickOffTest();

		} catch (InterruptedException e) {
			System.err.println("Interupted during receive: " + e.getMessage());
			System.exit(-1);
		} finally {
			stopObservation();
		}
	}

	protected boolean checkResponse(Request request, Response response) {
		boolean success = true;

		success &= checkType(Type.CON, response.getType());
		success &= checkCode(EXPECTED_RESPONSE_CODE, response.getCode());
		success &= checkToken(request.getToken(), response.getToken());
		success &= hasContentType(response);
		success &= hasNonEmptyPayload(response);
		success &= hasObserve(response);

		return success;
	}
}
