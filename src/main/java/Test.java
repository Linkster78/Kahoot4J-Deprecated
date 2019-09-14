import java.io.IOException;

import com.tek.kahoot4j.ChallengeFailedException;
import com.tek.kahoot4j.InvalidKahootException;
import com.tek.kahoot4j.KahootClient;

public class Test {
	
	public static void main(String[] args) throws IOException, InvalidKahootException, ChallengeFailedException {
		KahootClient kahoot = new KahootClient(140407, "Kahoot4J");
		kahoot.connect();
	}
	
}
