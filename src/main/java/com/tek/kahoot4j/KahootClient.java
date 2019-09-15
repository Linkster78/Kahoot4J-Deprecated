package com.tek.kahoot4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.websocket.ContainerProvider;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.cometd.websocket.client.WebSocketTransport;
import org.glassfish.tyrus.client.ClientManager;
import org.json.JSONObject;

/**
 * KahootClient, handles connections and contains
 * the websocket/data related to it.
 * 
 * @author RedstoneTek
 */
public class KahootClient {
	
	private static final String HANDSHAKE_ENDPOINT = "https://kahoot.it/reserve/session/%d/?%d";
	private static final String REGISTER_ENDPOINT = "wss://kahoot.it/cometd/%d/%s";
	private static final String WS_CONTROLLER = "/service/controller";
	private static final String WS_PLAYER = "/service/player";
	private static final String USER_AGENT = "Kahoot4J/1.0";
	
	private HttpClient httpClient;
	private BayeuxClient wsClient;
	private ClientManager webSocketContainer;
	private org.eclipse.jetty.client.HttpClient lpClient;
	private ClientTransport wsTransport, lpTransport;
	private String sessionToken;
	private String sessionId;
	private String challenge;
	
	private int pin;
	private String name;
	
	public KahootClient(int pin, String name) {
		this(HttpClients.createMinimal(), pin, name);
	}
	
	public KahootClient(HttpClient client, int pin, String name) {
		this.httpClient = client;
		this.pin = pin;
		this.name = name;
	}
	
	public void connect() throws IOException, InvalidKahootException, ChallengeFailedException {
		httpHandshake();
		solveChallenge();
		setupWebSocket();
		login();
	}
	
	private void httpHandshake() throws IOException, InvalidKahootException {
		HttpGet handshakeRequest = new HttpGet(String.format(HANDSHAKE_ENDPOINT, pin, System.currentTimeMillis()));
		HttpResponse handshakeResponse = httpClient.execute(handshakeRequest);
		
		if(handshakeResponse.getStatusLine().getStatusCode() == 404) {
			throw new InvalidKahootException(pin);
		} else {
			HttpEntity responseEntity = handshakeResponse.getEntity();
			BufferedReader reader = new BufferedReader(new InputStreamReader(responseEntity.getContent()));
			StringBuffer buffer = new StringBuffer();
			String line;
			
			while((line = reader.readLine()) != null) {
				buffer.append(line);
			}
			
			JSONObject response = new JSONObject(buffer.toString());
			challenge = response.getString("challenge");
			
			Header sessionTokenHeader = handshakeResponse.getHeaders("x-kahoot-session-token")[0];
			sessionToken = sessionTokenHeader.getValue();
		}
	}
	
	/*
	 * Challenge explanation:
	 * 
	 * First we do some JavaScript shenanigans, replace some functions
	 * that we know to be true by "true". This part of the code was
	 * taken from this repository: https://github.com/wwwg/kahoot.js
	 * 
	 * Then we evaluate the challenge (replaced) with the nashorn js engine.
	 * We take the bytes from the challenge and store them.
	 * Then, we get the bytes from the previously obtained session token,
	 * decode them with base64 so get get base64 decoded bytes.
	 * We then use XOR encryption, using the challenge bytes as a mask.
	 * We take the obtained bytes and make a string out of it. That's our ID.
	 */
	private void solveChallenge() throws ChallengeFailedException {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
		
		challenge = challenge.replace("this.angular.isObject(offset)", "true");
		challenge = challenge.replace("this.angular.isArray(offset)", "true");
		challenge = challenge.replace("this.angular.isString(offset)", "true");
		challenge = challenge.replace("this.angular.isDate(offset)", "true");
		challenge = challenge.replace("_.replace(message, ", "message.replace(");
		challenge = challenge.replace("console.log", "");
		
		try {
			String solved = (String) engine.eval(challenge);
			byte[] solvedBytes = solved.getBytes();
			byte[] tokenBytes = sessionToken.getBytes();
			byte[] decodedTokenBytes = Base64.getDecoder().decode(tokenBytes);
			
			ByteBuffer idBuffer = ByteBuffer.allocate(decodedTokenBytes.length);
			
			for(short i = 0; i < decodedTokenBytes.length; i++) {
				idBuffer.put((byte) (decodedTokenBytes[i] ^ solvedBytes[i % solvedBytes.length]));
			}
			
			sessionId = new String(idBuffer.array());
		} catch (ScriptException e) {
			throw new ChallengeFailedException();
		}
	}
	
	private void setupWebSocket() throws IOException {
		webSocketContainer = (ClientManager) ContainerProvider.getWebSocketContainer();
		wsTransport = new WebSocketTransport(null, null, webSocketContainer);

		lpClient = new org.eclipse.jetty.client.HttpClient();
		lpClient.addBean(webSocketContainer, true);
		try {
			lpClient.start();
		} catch (Exception e) {
			throw new IOException(e);
		}
		
		lpTransport = new LongPollingTransport(null, lpClient);

		wsClient = new BayeuxClient(String.format(REGISTER_ENDPOINT, pin, sessionId), wsTransport, lpTransport);
		wsClient.handshake();
		
		boolean handshaken = wsClient.waitFor(1000, BayeuxClient.State.CONNECTED);
        if (handshaken) {
        	wsClient.getChannel(WS_CONTROLLER).subscribe(new KahootMessageListener());
        	wsClient.getChannel(WS_PLAYER).subscribe(new KahootMessageListener());
        }
	}
	
	private void login() {
		JSONObject jsonScreen = new JSONObject();
		jsonScreen.put("width", 1080);
		jsonScreen.put("height", 720);
		
		JSONObject jsonDevice = new JSONObject();
		jsonDevice.put("userAgent", USER_AGENT);
		jsonDevice.put("screen", jsonScreen);
		
		JSONObject jsonContent = new JSONObject();
		jsonContent.put("participantUserId", (String)null);
		jsonContent.put("device", jsonDevice);
		
		Map<String, Object> data = new HashMap<String, Object>(5);
		data.put("type", "login");
		data.put("gameid", pin);
		data.put("host", "kahoot.it");
		data.put("name", name);
		data.put("content", jsonContent);
		
		wsClient.getChannel(WS_CONTROLLER).publish(data);
	}
	
	public void close() {
		wsClient.disconnect();
		wsClient.waitFor(1000, BayeuxClient.State.DISCONNECTED);
		wsClient.abort();
		try {
			lpClient.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
		lpTransport.abort();
		wsTransport.abort();
		
		wsClient = null;
		lpClient = null;
		lpTransport = null;
		wsTransport = null;
	}
	
	public String getSessionToken() {
		return sessionToken;
	}
	
	public String getName() {
		return name;
	}
	
	public int getPin() {
		return pin;
	}
	
}
