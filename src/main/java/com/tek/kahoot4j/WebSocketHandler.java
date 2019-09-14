package com.tek.kahoot4j;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnOpen;
import javax.websocket.Session;

@ClientEndpoint
public class WebSocketHandler {
	
	public static WebSocketHandler latestInstance;
	
	public WebSocketHandler() {
		latestInstance = this;
	}
	
	@OnOpen
	public void onOpen(Session session) {
		System.out.println("Connected!");
	}
	
}