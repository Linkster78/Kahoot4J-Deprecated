package com.tek.kahoot4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Base64;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;

public class KahootClient {
	
	private static final String HANDSHAKE_ENDPOINT = "https://kahoot.it/reserve/session/%d/?%d";
	private static final String REGISTER_ENDPOINT = "wss://kahoot.it/cometd/%d/%s";
	
	private HttpClient client;
	private WebSocketHandler webSocketHandler;
	private String sessionToken;
	private String sessionId;
	private String challenge;
	
	private int pin;
	private String name;
	
	public KahootClient(int pin, String name) {
		client = HttpClients.createMinimal();
		
		this.pin = pin;
		this.name = name;
	}
	
	public void connect() throws IOException, InvalidKahootException, ChallengeFailedException {
		handshake();
		solveChallenge();
		setupWebSocket();
	}
	
	private void handshake() throws IOException, InvalidKahootException {
		HttpGet handshakeRequest = new HttpGet(String.format(HANDSHAKE_ENDPOINT, pin, System.currentTimeMillis()));
		HttpResponse handshakeResponse = client.execute(handshakeRequest);
		
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
		WebSocketContainer container = ContainerProvider.getWebSocketContainer();
		
		try{
			container.connectToServer(WebSocketHandler.class, new URI(String.format(REGISTER_ENDPOINT, pin, sessionId)));
			webSocketHandler = WebSocketHandler.latestInstance;
		} catch(DeploymentException | URISyntaxException e) {
			e.printStackTrace();
		}
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
