import java.math.MathContext;

/**
 * You have been hired by Greenpeace to help the environment. Because of the
 * declining whale population, whales are having synchronization problems in
 * finding a mate. The trick is that in order to have children, three whales are
 * needed, one male, one female, and one to play matchmaker - literally, to push
 * the other two whales together (Im not making this up!). Your job is to write
 * the three procedures Male(), Female(), and Matchmaker(). Each whale is
 * represented by a separate thread. A male whale calls Male(), which waits
 * until there is a waiting female and matchmaker; similarly, a female whale
 * must wait until a male whale and a matchmaker are present. Once all three are
 * present, the matchmaker thread should call Matchmaker(), which prints a
 * message indicating that a mating is taking place and the identity and the
 * role of each Whale involved. Then, all three threads return. Make sure your
 * solution can be run using the command line "nachos -whales <numMales>
 * <numFemales>".
 */
public class Whale implements Runnable {

	// number of females and males available
	private int numFemales;

	private int numMales;

	// is there a female/male/matchmaker ready?
	private boolean femaleReady;

	private boolean maleReady;

	private boolean matchMakerReady;

	// is there a pair already?
	private Condition coupleReady = new Condition("CoupleReady");

	// only one male/female thread should execute certains part of the code
	private Lock maleLock = new Lock("MaleLock");

	private Lock femaleLock = new Lock("FemaleLock");

	private Lock matchMakerLock = new Lock("MatchmakerLock");

	private Lock conditionLock = new Lock("ConditionLock");

	// names of the whales that are mating right now
	// index 0 for males, 1 for females
	private String[] whaleNames = new String[2];

	/**
	 * Constructor.
	 * 
	 * @param numFemales
	 * @param numMales
	 */
	public Whale(int numMales, int numFemales) {
		Debug.ASSERT(numMales >= 0 && numFemales >= 0,
				"Number of males and females cannot be negative.");

		this.numFemales = numFemales;
		this.numMales = numMales;

	} // ctor

	public void run() {
		// create male, female and matchmaker threads
		for (int i = 0; i < numMales; i++) {
			NachosThread maleThread = new NachosThread("MaleThread " + i);
			maleThread.fork(new WhaleThread(true, "Male #" + i));
		}

		for (int i = 0; i < numFemales; i++) {
			NachosThread femaleThread = new NachosThread("FemaleThread " + i);
			femaleThread.fork(new WhaleThread(false, "Female #" + i));
		}

	} // run

	private void Male(String name) {
		Debug.println('e', "Male() BEGIN");
		numMales--;
		maleReady = true;
		whaleNames[0] = name;
		conditionLock.acquire();
		coupleReady.broadcast(conditionLock);

		while (femaleReady == false) {
			// wait until someone signals us
			coupleReady.wait(conditionLock);

		} // while

		// tell the other whale we're ready
		coupleReady.broadcast(conditionLock);

		while (matchMakerReady == false) {
			coupleReady.wait(conditionLock);
		}
		conditionLock.release();
		Debug.println('e', "Male() END");
	}

	private void Female(String name) {
		Debug.println('e', "Female() BEGIN");
		numFemales--;
		femaleReady = true;
		whaleNames[1] = name;
		conditionLock.acquire();
		coupleReady.broadcast(conditionLock);

		while (maleReady == false) {
			// wait until someone signals us
			coupleReady.wait(conditionLock);

		} // while

		// tell the other whale we're ready
		coupleReady.broadcast(conditionLock);

		while (matchMakerReady == false) {
			coupleReady.wait(conditionLock);
		}
		conditionLock.release();
		Debug.println('e', "Female() END");
	}

	private void Matchmaker() {
		Debug.println('e', "Matchmaker() BEGIN");

		// matchMakerLock.acquire();
		conditionLock.acquire();

		// matchmaker just makes sure that there's enough numbers...
		if ((numFemales + numMales) > 0) {
			matchMakerReady = true;
			coupleReady.broadcast(conditionLock);
		}

		while (femaleReady == false || maleReady == false) {
			coupleReady.wait(conditionLock);
		}

		matchMakerReady = false;
		maleReady = false;
		femaleReady = false;
		conditionLock.release();
		// matchMakerLock.release();
		System.out.println("Matting" + whaleNames[0] + " " + whaleNames[1]);
		Debug.printf('x', "%s and %s are mating!\n", whaleNames);
		Debug.println('e', "Matchmaker() END");
	}

	class WhaleThread implements Runnable {
		// flag indicating sex of this whale
		private boolean isMale;

		// name of this whale
		private String name;

		/**
		 * Constructor.
		 * 
		 * @param isMale
		 * @param name
		 */
		public WhaleThread(boolean isMale, String name) {
			this.isMale = isMale;
			this.name = name;

		} // ctor

		public void run() {
			// acquire the lock we need
			if (maleReady == true && femaleReady == true) {
				matchMakerLock.acquire();
			} else if (isMale) {
				maleLock.acquire();
			} else {
				femaleLock.acquire();
			}

			// there will be one whale less
			if (maleReady == true && femaleReady == true) {
				Matchmaker();
			} else if (isMale) {
				Male(name);
			} else {
				Female(name);
			}

			// release the lock we got
			if (matchMakerLock.isHeldByCurrentThread()) {
				matchMakerLock.release();
			} else if (isMale) {
				maleLock.release();
			} else {
				femaleLock.release();
			}

		} // run

	} // class

} // class
