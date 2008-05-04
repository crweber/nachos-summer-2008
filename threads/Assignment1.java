public class Assignment1 {
	private NachosThread[] maleThreads;
	private NachosThread[] femaleThreads;
	private NachosThread matchmaker;
	private boolean match_available = true;
	private int maleNo = 0, femaleNo = 0;

	private Semaphore male = new Semaphore("maleSem", 0);
	private Semaphore female = new Semaphore("femaleSem", 0);
	private Semaphore male_start = new Semaphore("maleStartSem", 0);
	private Semaphore female_start = new Semaphore("femaleSem", 0);
	private Semaphore male_end = new Semaphore("maleEndSem", 0);
	private Semaphore female_end = new Semaphore("femaleEndSem", 0);

	public void Male() {

		male.V();
		male_start.P();
		System.out.println(NachosThread.thisThread().getName());
		System.out.println("Male looking for a female");
		male_end.P();
		NachosThread.thisThread().sleep();

	}

	public void Female() {

		female.V();
		female_start.P();
		System.out.println(NachosThread.thisThread().getName());
		System.out.println("Female looking for a male");
		female_end.P();

		NachosThread.thisThread().sleep();
	}

	public void Matchmaker() {

		male.P();
		female.P();
		male_start.V();
		female_start.V();
		System.out.println("Matting happened!");
		male_end.V();
		female_end.P();
		NachosThread.thisThread().Yield();

	}

	void initialize(int noOfMales, int noOfFemales) {

		maleThreads = new NachosThread[noOfMales];
		femaleThreads = new NachosThread[noOfFemales];

		matchmaker = new NachosThread("Matchmaker");
		for (int i = 0; i < noOfMales; i++) {
			maleThreads[i] = new NachosThread("Male " + i);
		}
		for (int i = 0; i < noOfFemales; i++) {
			femaleThreads[i] = new NachosThread("Female " + i);
		}

		Runnable maleRunnable = new Runnable() {
			public void run() {
				Male();
			}
		};

		Runnable femaleRunnable = new Runnable() {
			public void run() {
				Female();
			}
		};

		for (int i = 0; i < noOfMales; i++) {
			maleThreads[i].fork(maleRunnable);
		}
		for (int i = 0; i < noOfFemales; i++) {
			femaleThreads[i].fork(femaleRunnable);
		}
		matchmaker.fork(new Runnable() {
			public void run() {
				Matchmaker();
			}
		});

	}

	public void executeCode(int noOfMales, int noOfFemales) {

		initialize(noOfMales, noOfFemales);

	}

}
