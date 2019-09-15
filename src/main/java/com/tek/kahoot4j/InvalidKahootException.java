package com.tek.kahoot4j;

/**
 * This exception represents a failure to
 * locate the specified Kahoot PIN.
 * 
 * @author RedstoneTek
 */
public class InvalidKahootException extends Exception {

	//Generated serial
	private static final long serialVersionUID = 7259440840787615086L;
	
	//Game pin
	private int pin;
	
	/**
	 * Creates an exception with the pin.
	 * 
	 * @param pin The pin
	 */
	public InvalidKahootException(int pin) {
		this.pin = pin;
	}
	
	/**
	 * Overrides the default exception message.
	 */
	@Override
	public String getMessage() {
		return "Could not find a Kahoot by the ID " + pin;
	}

}
