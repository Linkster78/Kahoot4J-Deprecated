package com.tek.kahoot4j;

import java.util.Map;

import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.bayeux.client.ClientSessionChannel.MessageListener;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The KahootMessageListener class listens for cometd
 * messages, parses them and updates the Kahoot state.
 * 
 * @author RedstoneTek
 */
public class KahootMessageListener implements MessageListener {

	//KahootClient instance
	private KahootClient client;
	
	/**
	 * Creates a KahootMessageListener instance.
	 * 
	 * @param client The KahootClient instance
	 */
	public KahootMessageListener(KahootClient client) {
		this.client = client;
	}
	
	/**
	 * Called when a cometd message is received.
	 */
	@Override
	public void onMessage(ClientSessionChannel channel, Message message) {
		Map<String, Object> data = message.getDataAsMap();
		
		//DEBUG CODE
		//System.out.println(channel.getId());
		//for(String key : data.keySet()) {
		//	System.out.println(" - " + key + ": " + data.get(key));
		//}
		//DEBUG CODE
		
		if(channel.getId().equals(KahootClient.WS_PLAYER)) {
			if(data.containsKey("gameid")) {
				JSONObject content = new JSONObject((String) data.get("content"));
				
				if(content.has("playerName")) {
					if(client.getEventHandler() != null) {
						client.getEventHandler().onPlayerJoined(Integer.parseInt((String) data.get("gameid")), content.getString("playerName"));
					}
				}
				
				else if(content.has("kickCode")) {
					if(client.getEventHandler() != null) {
						client.getEventHandler().onPlayerKicked();
						client.close();
					}
				}
				
				else if(content.has("quizName")) {
					if(client.getEventHandler() != null) {
						JSONArray answers = content.getJSONArray("quizQuestionAnswers");
						int[] answerSizes = new int[answers.length()];
						for(int i = 0; i < answers.length(); i++) {
							answerSizes[i] = answers.optInt(i);
						}
						
						client.getEventHandler().onQuizStarted(content.getString("quizName"), answerSizes);
					}
				}
				
				else if(content.has("questionIndex")) {
					JSONArray answers = content.getJSONArray("quizQuestionAnswers");
					int[] answerSizes = new int[answers.length()];
					for(int i = 0; i < answers.length(); i++) {
						answerSizes[i] = answers.optInt(i);
					}
					
					if(content.has("timeLeft")) {
						client.getEventHandler().onQuestionAnnounced(content.getInt("questionIndex"), answerSizes[content.getInt("questionIndex")], content.getInt("timeLeft"));
					} else {
						client.getEventHandler().onQuestionWaiting(content.getInt("questionIndex"), answerSizes[content.getInt("questionIndex")]);
					}
				}
			}
		}
	}

}
