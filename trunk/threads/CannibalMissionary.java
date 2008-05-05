public class CannibalMissionary {

	// 0-free place, 1 - missionary, -1 - cannibal
	public static int[] boat = new int[3];
	public static String[] identities = new String[3];
	public static int available_seats = 3;
	public static NachosThread[] cannibalThreads;
	public static NachosThread[] missionaryThreads;

	public static Lock lock = new Lock("boat");
	public static Condition boat_load = new Condition("boat load");

	// RowBoat method - make the seats available and print the lucky ones
	void RowBoat() {
		available_seats = 3;
		Debug.printf('+', "Boat left with %s, %s and %s\n", identities[0],
				identities[1], identities[2]);
		// let the other threads work
		boat_load.broadcast(lock);
	}

	// cannibal arrives
	void CannibalArrives(String name) {

		int sum = 0;
		lock.acquire();
		Debug.printf('+', "Cannibal %s arrives!\n", name);

		for (int i = 2; i > available_seats - 1; i--) {

			sum += boat[i];
		}
		System.out.println(sum);
		// Already one cannibal on the boat, so we cannot have the second
		// one
		if (sum == 0 && available_seats == 1) {
			Debug.printf('+', "Cannibal %s has no place!\n", name);
			boat_load.wait(lock);

		}

		// Put cannibal in boat
		boat[available_seats - 1] = -1;
		identities[available_seats - 1] = name;
		Debug.printf('+', "Cannibal %s loaded in the boat on seat %d\n", name,
				available_seats - 1);
		available_seats--;
		if (available_seats == 0) {
			RowBoat();
		} else {
			boat_load.wait(lock);
		}

		lock.release();

		Debug.printf('+', "Cannibal %s crossed the river\n", name);

	}

	// Missionary arrives
	void MissionaryArrives(String name) {
		int sum = 0;
		lock.acquire();
		Debug.printf('t', "Missionary %s arrives!\n", name);

		for (int i = 2; i > available_seats - 1; i--) {
			sum += boat[i];
		}
		// Already two cannibals on the boat, so we cannot have a missionary
		
		if (sum == -2 && available_seats == 1) {
			Debug.printf('+', "Missionary %s has no place!\n", name);
			boat_load.wait(lock);

		}

		// Put missionary in boat
		boat[available_seats - 1] = 1;
		identities[available_seats - 1] = name;
		Debug.printf('+', "Missionary %s loaded in the boat on seat %d\n",
				name, available_seats - 1);
		available_seats--;
		if (available_seats == 0) {
			RowBoat();
		} else {
			boat_load.wait(lock);
		}

		lock.release();

		Debug.printf('+', "Missionary %s crossed the river\n", name);

	}

	class CannibalRunnable implements Runnable {

		String name;

		public CannibalRunnable(String name) {
			this.name = name;
		}

		public void run() {
			CannibalArrives(name);

		}

	}

	class MissionaryRunnable implements Runnable {

		String name;

		public MissionaryRunnable(String name) {
			this.name = name;
		}

		public void run() {
			MissionaryArrives(name);

		}

	}

	public void init(int noOfCannibals, int noOfMissionaries) {
		cannibalThreads = new NachosThread[noOfCannibals];
		missionaryThreads = new NachosThread[noOfMissionaries];

		for (int i = 0; i < noOfCannibals; i++) {
			cannibalThreads[i] = new NachosThread("cannibal " + i);
		}
		for (int i = 0; i < noOfMissionaries; i++) {
			missionaryThreads[i] = new NachosThread("missionary " + i);
		}

		for (int i = 0; i < noOfCannibals - 1; i++) {
			cannibalThreads[i].fork(new CannibalRunnable("cannibal " + i));
		}

		for (int i = 0; i < noOfCannibals; i++) {
			missionaryThreads[i]
					.fork(new MissionaryRunnable("missionary " + i));
		}
		cannibalThreads[noOfCannibals - 1].fork(new CannibalRunnable(
				"cannibal " + (noOfCannibals - 1)));
	}

}
