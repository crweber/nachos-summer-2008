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

    // initial population (for matchmaker naming purposes)
    private int initialFemales;
    private int initialMales;
    
	// number of females and males available
	private int numFemales;
	private int numMales;
    
    // mate counter
    private int mateNumber = 0;

	// is there a female/male ready?
	private boolean femaleReady;
	private boolean maleReady;

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
        
		Debug.printf('x', "Will run Whales with %d males and %d females\n", new Long(numMales), new Long(numFemales));
        
        // initialize variables
		this.numFemales = this.initialFemales = numFemales;
		this.numMales = this.initialMales = numMales;

	} // ctor

	public void run() {
		// create male and female threads
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
        // no need to acquire lock, it has been gotten for us
        // need to decrement the number of whales of our gender and make sure
        // our gender is 'ready' and to signal the other gender, in case
        // it's listening
		Debug.println('e', "Male() BEGIN");
		numMales--;
		maleReady = true;
		whaleNames[0] = name;
		
        conditionLock.acquire();
		coupleReady.signal(conditionLock);
		while (femaleReady == false) {
			// wait until someone signals us
			coupleReady.wait(conditionLock);

		} // while
        conditionLock.release();
        
        // if here, it means that there's a male and there's a female!
        // let's find another whale
        Matchmaker();
        
		Debug.println('e', "Male() END");
	}

	private void Female(String name) {
        // no need to acquire lock, it has been gotten for us
        // need to decrement the number of whales of our gender and make sure
        // our gender is 'ready' and to signal the other gender, in case
        // it's listening
		Debug.println('e', "Female() BEGIN");
		numFemales--;
		femaleReady = true;
		whaleNames[1] = name;
        
		conditionLock.acquire();
		coupleReady.signal(conditionLock);
		while (maleReady == false) {
			// wait until someone signals us
			coupleReady.wait(conditionLock);

		} // while
        conditionLock.release();
        
        // if here, it means that there's a male and there's a female!
        // let's find another whale
        Matchmaker();

        Debug.println('e', "Female() END");
	}

	private void Matchmaker() {
        // make sure only one whale is here
        matchMakerLock.acquire();
        // now, make sure that we still have males and females, and they are ready!
		if ((numMales > 0 || numFemales > 0) && femaleReady && maleReady) {
            Debug.println('e', "Matchmaker() BEGIN");
            
            // nifty trick to get the matchmaker name!
            String matchmakerName = "";
            if (numMales > 0) {
                matchmakerName = "Male #" + (initialMales - numMales);
            } else {
                matchmakerName = "Female #" + (initialFemales - numFemales);
            }
            
            // mate
		    Debug.printf(
                    'x', 
                    "Match #%d - %s and %s are mating with the help of %s!\n", 
                    new Object[] {new Long(++mateNumber), whaleNames[0], whaleNames[1], matchmakerName});

            // info message
            Debug.printf('x', "%d males and %d females remaining\n", new Long(numMales), new Long(numFemales));
            
            // consider the matching done
            femaleReady = false;
            maleReady = false;
            
            Debug.println('e', "Matchmaker() END");
        }
        matchMakerLock.release();
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
			if (isMale) {
				maleLock.acquire();
			} else {
				femaleLock.acquire();
			}

			if (isMale) {
				Male(name);
			} else {
				Female(name);
			}
            
            // release locks
			if (isMale) {
				maleLock.release();
			} else {
				femaleLock.release();
			}

		} // run

	} // class

} // class
