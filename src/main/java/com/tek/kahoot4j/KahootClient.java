package com.tek.kahoot4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

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
	
	//Final variables. Endpoints, websocket channels and user agent.
	private static final String HANDSHAKE_ENDPOINT = "https://kahoot.it/reserve/session/%d/?%d";
	private static final String REGISTER_ENDPOINT = "wss://kahoot.it/cometd/%d/%s";
	private static final String USER_AGENT = "Kahoot4J/1.0";
	public static final String WS_CONTROLLER = "/service/controller";
	public static final String WS_PLAYER = "/service/player";
	
	//All connection related variables.
	private HttpClient httpClient;
	private BayeuxClient wsClient;
	private ClientManager webSocketContainer;
	private org.eclipse.jetty.client.HttpClient lpClient;
	private ClientTransport wsTransport, lpTransport;
	private String sessionToken;
	private String sessionId;
	private String challenge;
	
	//Kahoot event handler
	private KahootEventHandler eventHandler;
	
	//Constructor passed values.
	private int pin;
	private String name;
	
	/**
	 * Creates a KahootClient with the
	 * specified pin and name.
	 * 
	 * @param pin The game pin
	 * @param name The username
	 * @throws IOException 
	 */
	public KahootClient(int pin, String name) throws IOException {
		this.pin = pin;
		this.name = name;
		setupTransport();
	}
	
	/**
	 * Creates a KahootClient with
	 * the provided transport info.
	 * 
	 * @param pin
	 * @param name
	 * @param httpClient
	 * @param lpClient
	 * @param lpTransport
	 * @param wsTransport
	 */
	public KahootClient(int pin, String name, HttpClient httpClient, 
			org.eclipse.jetty.client.HttpClient lpClient, ClientTransport lpTransport, ClientTransport wsTransport) {
		this.pin = pin;
		this.name = name;
		this.httpClient = httpClient;
		this.lpClient = lpClient;
		this.lpTransport = lpTransport;
		this.wsTransport = wsTransport;
	}
	
	/**
	 * Sets up the client transport and http client.
	 * 
	 * @throws IOException
	 */
	private void setupTransport() throws IOException {
		httpClient = HttpClients.createMinimal();
		
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
	}
	
	/**
	 * Connects the client to the Kahoot game.
	 * 
	 * @throws IOException Thrown if there is an issue IO wise. (Internet Connection)
	 * @throws InvalidKahootException Thrown if the provided Kahoot game pin is invalid.
	 * @throws ChallengeFailedException Thrown if the challenge cannot be solved.
	 */
	public void connect() throws IOException, InvalidKahootException, ChallengeFailedException {
		httpHandshake();
		solveChallenge();
		setupWebSocket();
		login();
	}
	
	/**
	 * Does the HTTP Handshake with the
	 * Kahoot servers. (Gets x-kahoot-session-token)
	 * 
	 * @throws IOException Thrown if there is an issue IO wise. (Internet Connection)
	 * @throws InvalidKahootException Thrown if the provided Kahoot game pin is invalid.
	 */
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
	
	/**
	 * Solves the challenge gotten from the httpHandshake method.
	 * 
	 * @throws ChallengeFailedException Thrown if the challenge cannot be solved.
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
	
	/**
	 * Sets up the cometd websocket connection
	 * to the Kahoot servers.
	 * 
	 * @throws IOException Thrown if there is an issue IO wise. (Internet Connection)
	 */
	private void setupWebSocket() throws IOException {
		wsClient = new BayeuxClient(String.format(REGISTER_ENDPOINT, pin, sessionId), wsTransport, lpTransport);
		wsClient.handshake();
		
		KahootMessageListener messageListener = new KahootMessageListener(this);
		
		boolean handshaken = wsClient.waitFor(1000, BayeuxClient.State.CONNECTED);
        if (handshaken) {
        	wsClient.getChannel(WS_CONTROLLER).subscribe(messageListener);
        	wsClient.getChannel(WS_PLAYER).subscribe(messageListener);
        }
	}
	
	/**
	 * Sends the LOGIN websocket message through
	 * the /service/controller cometd channel.
	 */
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
	
	/**
	 * Answers the current question.
	 * The answer ID goes from 0-3, top left, top right, bottom left, bottom right.
	 * 
	 * @param choice The answer ID
	 */
	public void answer(int choice, int questionIndex) {
		JSONObject jsonContent = new JSONObject();
		jsonContent.put("choice", choice);
		jsonContent.put("questionIndex", questionIndex);
		jsonContent.put("meta", new JSONObject("{\"lag\":10}"));
		
		Map<String, Object> data = new HashMap<String, Object>(5);
		data.put("id", 45);
		data.put("type", "message");
		data.put("gameid", pin);
		data.put("host", "kahoot.it");
		data.put("content", jsonContent);
		
		wsClient.getChannel(WS_CONTROLLER).publish(data);
	}
	
	/**
	 * Closes the Kahoot cometd client, http client and such.
	 */
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
	
	/**
	 * Gets the Kahoot session token.
	 * 
	 * @return The Kahoot session token
	 */
	public String getSessionToken() {
		return sessionToken;
	}
	
	/**
	 * Gets the Kahoot session ID.
	 * 
	 * @return The Kahoot session ID
	 */
	public String getSessionId() {
		return sessionId;
	}
	
	/**
	 * Gets the Kahoot username.
	 * 
	 * @return The username
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Gets the Kahoot game pin.
	 * 
	 * @return The game pin
	 */
	public int getPin() {
		return pin;
	}
	
	/**
	 * Sets the Kahoot event handler.
	 * 
	 * @param eventHandler The event handler
	 */
	public void setEventHandler(KahootEventHandler eventHandler) {
		this.eventHandler = eventHandler;
	}
	
	/**
	 * Gets the Kahoot Event Handler.
	 * 
	 * @return The event handler
	 */
	public KahootEventHandler getEventHandler() {
		return eventHandler;
	}
	
	/**
	 * Gets the HTTP Client.
	 * 
	 * @return The HTTP client
	 */
	public HttpClient getHttpClient() {
		return httpClient;
	}
	
	/**
	 * Gets the long polling HTTP Client.
	 * 
	 * @return The LP Http Client
	 */
	public org.eclipse.jetty.client.HttpClient getLongPollingClient() {
		return lpClient;
	}
	
	/**
	 * Gets the WebSocket transport.
	 * 
	 * @return The WS transport
	 */
	public ClientTransport getWebSocketTransport() {
		return wsTransport;
	}
	
	/**
	 * Gets the LongPolling transport.
	 * 
	 * @return The LP transport
	 */
	public ClientTransport getLongPollingTransport() {
		return lpTransport;
	}
	
}
