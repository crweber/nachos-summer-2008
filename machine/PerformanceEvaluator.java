import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.xml.internal.ws.api.pipe.NextAction;

public class PerformanceEvaluator {

	public PerformanceEvaluator() {
		try {
			bw = new BufferedWriter(new FileWriter("tlbmiss.txt"));
			bwM = new BufferedWriter(new FileWriter("MemoryAcc.txt"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public class Key {
		private int processId;
		private int virtualaddress;
		private boolean tlbMiss;

		public Key(int procId, int virtAddr, boolean tlbMiss) {
			this.processId = procId;
			this.virtualaddress = virtAddr;
			this.tlbMiss = tlbMiss;
		}

		@Override
		public String toString() {
			// TODO Auto-generated method stub

			return processId + " " + virtualaddress + " " + tlbMiss;
		}

	}

	public static List tlbMisses = new LinkedList();
	public static List memAccess = new LinkedList();
	public static Map capacityMisses = new Hashtable();
	public static PerformanceEvaluator perf = new PerformanceEvaluator();
	BufferedWriter bw = null, bwM = null;
	public int currentTlbEntry = 0;

	public static void tlbMiss(int processId, int virtualAddress, boolean miss) {

		tlbMisses.add(perf.new Key(processId, virtualAddress / 128, miss));
		capacityMisses.put(buildKey(processId, virtualAddress / 128), true);
//		try {
//			perf.bw.write(processId + " " + (virtualAddress / 128) + " " + miss
//					+ "\n");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	public static void pageFault(int processId, int virtualAddress) {

	}

	public static String buildKey(int processId, long virtualPageNumber) {
		return (processId + "|" + virtualPageNumber);
	}

	//called from machine.translate
	public static void memoryAccess(int processId, int virtualAddress,
			boolean tlbMiss) {

		if (tlbMiss == true) {
			memAccess.add(perf.new Key(processId, virtualAddress / 128, true));

		} else {
			memAccess.add(perf.new Key(processId, virtualAddress / 128, false));

		}

	}

	/**
	 * Provides the index of the tlb entry to evict.
	 * 
	 * @return The index of the tlb entry to evict.
	 */
	private int nextTlbEntryToEvict() {
		// we want to return the current index
		int current = currentTlbEntry;

		// increment the current to next index
		currentTlbEntry = (currentTlbEntry + 1) % Machine.TLBSize;

		return current;
	}

	//compute conflict misses
	public static int conflictMisses() {
		int tlb[] = new int[] { -1, -1, -1, -1 };
		int offset[] = new int[4];
		int conflictMisses = 0;
		//use an array for virtual addresses
		Object[] copyOfMem = new Object[memAccess.size()];
		int[] virtAddrs = new int[copyOfMem.length];
		copyOfMem = memAccess.toArray();

		for (int i = 0; i < copyOfMem.length; i++) {
			Key k = (Key) copyOfMem[i];
			virtAddrs[i] = k.virtualaddress;
		}

		// check all memory acceses
		for (int i = 0; i < memAccess.size() / 100 - 1; i++) {

			// get 2 consecutive entries
			Key k1 = (Key) memAccess.get(i);
			Key k2 = (Key) memAccess.get(i + 1);

			// we have a tlb miss
			if (k1.tlbMiss == false && k2.tlbMiss == true) {

				// apply our FIFO algorithm
				int evicted = perf.nextTlbEntryToEvict();
				// compulsory miss. we don't care
				if (tlb[evicted] == -1) {
					tlb[evicted] = k2.virtualaddress;
					i++;
					continue;
				}

				// System.out.println(tlb[0] + " " + tlb[1] + " " + tlb[2] + " "
				// + tlb[3] + "\n");

				// check optimal algorithm choice. Get the last accessed entry in the future
				int max = -1;
				int pageNoOptimal = -1;
				boolean found = false;
				for (int j = 0; j < 4; j++) {
					found = false;
					for (int k = i + 2; k < virtAddrs.length / 100; k++) {

						//we found a hit in the future. compute how far away it is
						if (virtAddrs[k] == tlb[j]) {
							found = true;
							offset[j] = k - i + 2;
							if (offset[j] > max) {
								max = offset[j];
								pageNoOptimal = j;

							}

							break;
						}
					}
					//never accessed. perfect hit
					if (!found) {
						pageNoOptimal = j;
						break;
					}
				}
				//System.out.println(tlb[evicted] + " " + tlb[pageNoOptimal]);

				if (evicted != pageNoOptimal) {

					conflictMisses++;
				}
				// page evicted by FIFO. Apply algorithm
				tlb[evicted] = k2.virtualaddress;

			}

		}

		return conflictMisses;
	}

	public static void writeStats() {

//		try {
//		
//			for (Iterator i = memAccess.iterator(); i.hasNext();) {
//
//				Key key1 = (Key) i.next();
//
//				perf.bwM.write(key1 + "\n");
//			}
//
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} finally {
//			try {
//				perf.bwM.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}

		Debug.print('z', "Total number of Memory Acceses " + memAccess.size()
				+ "\n");
		Debug.print('z', "Total number of TLB Misses " + tlbMisses.size()
				+ "\n");
		Debug.print('z', "Compulsory TLB Misses "
				+ capacityMisses.keySet().size() + "\n");
		Debug.print('z', "Capacity TLB Misses "
				+ (tlbMisses.size() - capacityMisses.keySet().size()) + "\n");
		Debug.print('z', "Conflict misses " + conflictMisses() + "\n");
		Debug.print('z', "Conflict misses percentage " + conflictMisses()*100 / (memAccess.size() / 100) + "%\n");
	}
}
