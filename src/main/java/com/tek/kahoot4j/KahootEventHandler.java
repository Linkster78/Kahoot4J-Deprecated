package com.tek.kahoot4j;

/**
 * This class is used to wrap certain
 * events of the Kahoot game.
 * 
 * @author RedstoneTek
 */
public class KahootEventHandler {
	
	/**
	 * Called when the game is joined.
	 * 
	 * @param pin The game pin
	 * @param name The username
	 */
	public void onPlayerJoined(int pin, String name) { }
	
	/**
	 * Called when the player gets kicked.
	 */
	public void onPlayerKicked() { }
	
	/**
	 * Called when the quiz starts.
	 * 
	 * @param name The quiz name
	 * @param questionSizes The amount of answers of every question in order
	 */
	public void onQuizStarted(String name, int[] questionSizes) { }
	
	/**
	 * Called when the question is announced, before answering.
	 * 
	 * @param questionIndex The index of the question
	 * @param answerCount The amount of answers of the question
	 * @param timeLeft The time left before you can answer
	 */
	public void onQuestionAnnounced(int questionIndex, int answerCount, int timeLeft) { }
	
	/**
	 * Called when the question's answers are shown and that it is waiting for input.
	 * 
	 * @param questionIndex The index of the question
	 * @param answerCount The amount of answers of the question
	 */
	public void onQuestionWaiting(int questionIndex, int answerCount) { }
	
}
