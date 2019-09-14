package com.tek.kahoot4j;

import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.bayeux.client.ClientSessionChannel.MessageListener;

public class KahootMessageListener implements MessageListener {

	@Override
	public void onMessage(ClientSessionChannel channel, Message message) {
		System.out.println("Message from " + channel.getId());
	}

}
