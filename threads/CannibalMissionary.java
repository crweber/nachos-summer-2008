public class CannibalMissionary {

	// 0-free place, 1 - missionary, -1 - cannibal
	public static int[] boat = new int[3];
	public static int available_seats = 3;
	public static NachosThread[] cannibalThreads;
	public static NachosThread[] missionaryThreads;

	public static Lock lock = new Lock("boat");
	public static Condition boat_load = new Condition("boat load");
	public static Condition cond = new Condition("condition");

	// RowBoat method - make the seats available and print the lucky ones
	void RowBoat() {
		available_seats = 3;
		Debug.printf('t', "Boat left with %d, %d and %d\n", boat[0], boat[1],
				boat[2]);
		boat_load.broadcast(lock);
	}

	// cannibal arrives
	void CannibalArrives(String name) {

		int sum = 0;
		lock.acquire();
		Debug.printf('t', "Cannibal %s arrives!", name);
		while (true) {

			for (int i = available_seats - 1; i >= 0; i--) {
				sum += boat[i];
			}
			// Already one cannibal on the boat, so we cannot have the second
			// one
			if (sum == 0 && available_seats == 1) {
				Debug.printf('t', "Cannibal %s has no place!", name);
				boat_load.wait(lock);
				// continue;
			}
			break;
		}

		// Put cannibal in boat
		boat[available_seats - 1] = -1;
		Debug.printf('t', "Cannibal %s loaded in the boat on seat %d", name,
				available_seats - 1);
		available_seats--;
		if (available_seats - 1 == 0) {
			RowBoat();
		} else {
			boat_load.wait(lock);
		}

		lock.release();

		Debug.printf('t', "Cannibal %s crossed the river", name);

	}

	// Missionary arrives
	void MissionaryArrives(String name) {
		int sum = 0;
		lock.acquire();
		Debug.printf('t', "Missionary %s arrives!", name);
		while (true) {

			for (int i = available_seats - 1; i >= 0; i--) {
				sum += boat[i];
			}
			// Already two cannibals on the boat, so we cannot have a missionary

			if (sum == -2 && available_seats == 2) {
				Debug.printf('t', "Missionary %s has no place!", name);
				boat_load.wait(lock);
				// continue;
			}
			break;
		}

		// Put missionary in boat
		boat[available_seats - 1] = 1;
		Debug.printf('t', "Missionary %s loaded in the boat on seat %d", name,
				available_seats - 1);
		available_seats--;
		if (available_seats - 1 == 0) {
			RowBoat();
		} else {
			boat_load.wait(lock);
		}

		lock.release();

		Debug.printf('t', "Missionary %s crossed the river", name);

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

	public void init(int noOfCannibals, int noOfMissionaries){
		cannibalThreads = new NachosThread[noOfCannibals];
		missionaryThreads = new NachosThread[noOfMissionaries];
		
		for (int i = 0; i < noOfCannibals; i++){
			cannibalThreads[i] = new NachosThread("cannibal " + i);
		}
		for (int i = 0; i < noOfMissionaries; i++){
			missionaryThreads[i] = new NachosThread("missionary " + i);
		}
		
		for (int i = 0; i < noOfCannibals; i++){
			cannibalThreads[i].fork(new CannibalRunnable("cannibal " + i));
		}
		
		for (int i = 0; i < noOfCannibals; i++){
			missionaryThreads[i].fork(new MissionaryRunnable("missionary " + i));
		}
	}

}
