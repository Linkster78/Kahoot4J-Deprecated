package com.tek.kahoot4j;

/**
 * This exception represents the failure to complete
 * the Kahoot handshake challenge. This happening either
 * means something went wrong in the code or that the protocol changed.
 * 
 * @author RedstoneTek
 */
public class ChallengeFailedException extends Exception {

	//Generated serial
	private static final long serialVersionUID = 3698553021279155207L;
	
	/**
	 * Overrides the default exception message.
	 */
	@Override
	public String getMessage() {
		return "Couldn't solve handshake challenge.";
	}

}
