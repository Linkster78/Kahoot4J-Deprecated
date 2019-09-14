package com.tek.kahoot4j;

public class ChallengeFailedException extends Exception {

	private static final long serialVersionUID = 3698553021279155207L;
	
	@Override
	public String getMessage() {
		return "Couldn't solve handshake challenge.";
	}

}
