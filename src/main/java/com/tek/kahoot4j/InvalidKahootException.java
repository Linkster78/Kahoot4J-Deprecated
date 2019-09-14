package com.tek.kahoot4j;

public class InvalidKahootException extends Exception {

	private static final long serialVersionUID = 7259440840787615086L;
	
	private int pin;
	
	public InvalidKahootException(int pin) {
		this.pin = pin;
	}
	
	@Override
	public String getMessage() {
		return "Could not find a Kahoot by the ID " + pin;
	}

}
