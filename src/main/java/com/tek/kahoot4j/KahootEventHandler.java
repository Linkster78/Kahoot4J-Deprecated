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
	
}
