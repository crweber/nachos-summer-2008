import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;



public class PerformanceEvaluator {
    
    static class Key {
        private int processId;
        private int virtualaddress;
        //private boolean tlbMiss;

        public Key(int procId, int virtAddr) {
            this.processId = procId;
            this.virtualaddress = virtAddr;
        }

        //@Override
        public String toString() {
            // TODO Auto-generated method stub

            return processId + "|" + virtualaddress;
        }
        
        public boolean equals(Object o) {
            if (o == null || !(o instanceof Key)) {
                return false;
            }
            Key k = (Key)o;
            return (k.processId == this.processId && k.virtualaddress == this.virtualaddress);
        }
        
        public int hashCode() {
            return toString().hashCode();
        }
    }

	public PerformanceEvaluator() {
		try {
			bw = new BufferedWriter(new FileWriter("tlbmiss.txt"));
			bwM = new BufferedWriter(new FileWriter("MemoryAcc.txt"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
    public final static int TRUNCATION_FACTOR = 1;
	//public static List tlbMisses = new LinkedList();
	public static List memAccess = new ArrayList();
	//public static Map capacityMisses = new Hashtable();
	public static PerformanceEvaluator perf = new PerformanceEvaluator();
	BufferedWriter bw = null, bwM = null;
	public int currentTlbEntry = 0;

    public static void tlbHit(int processId, int virtualAddress, boolean miss) {
        totalTlbHits++;
    }
    
	public static void tlbMiss(int processId, int virtualAddress, boolean miss) {

		//tlbMisses.add(perf.new Key(processId, virtualAddress / 128, miss));
		//capacityMisses.put(buildKey(processId, virtualAddress / 128), new Boolean(true));
        totalTlbMisses++;
//		try {
//			perf.bw.write(processId + " " + (virtualAddress / 128) + " " + miss
//					+ "\n");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
    
    private static int totalPageFaults = 0;
    private static int totalTlbMisses = 0;
    private static int totalTlbHits = 0;

	public static void pageFault(int processId, int virtualAddress) {
	    totalPageFaults++;
	}

	public static String buildKey(int processId, long virtualPageNumber) {
		return (processId + "|" + virtualPageNumber);
	}

	//called from machine.translate
	public static void memoryAccess(int processId, int virtualAddress,
			boolean tlbMiss) {

		//if (tlbMiss == true) {
			//memAccess.add(perf.new Key(processId, virtualAddress / 128, true));
            memAccess.add(new Key(processId, virtualAddress / Machine.PageSize));

		//} else {
			//memAccess.add(perf.new Key(processId, virtualAddress / 128, false));

		//}

	}

	/**
	 * Provides the index of the tlb entry to evict.
	 * 
	 * @return The index of the tlb entry to evict.
	 */
	private int nextTlbEntryToEvict() {
		// we want to return the current index
        int evicted = (int)(Math.random()*100) % 4;
		//int current = currentTlbEntry;

		// increment the current to next index
		//currentTlbEntry = (currentTlbEntry + 1) % Machine.TLBSize;

        return evicted;
		//return current;
	}
    
	//compute conflict misses
	public static int[] conflictMisses(List memAccess, int missesInStrategy, int cacheSize) {
		Key cache[] = new Key[cacheSize];
		int optimalMisses = 0;
        int foundInCache = 0;
		Hashtable uniqueAccesses = new Hashtable();

		// check all memory acceses
		for (int i = 0, n = memAccess.size() /TRUNCATION_FACTOR; i < n; i++) {
            // each pass simulates an instruction/fetch, so, now determine what to do in the cache
            // let's first use a map recording the unique memory accesses in order to determine
            // compulsory misses
            Key currentAccess = (Key)memAccess.get(i);
            uniqueAccesses.put(currentAccess, "");

            boolean alreadyInCache = false;
            
            // do we need to evict something
            for (int j = 0; j < cache.length; j++) {
                if (cache[j] == null || cache[j].equals(currentAccess)) {
                    cache[j] = currentAccess;
                    alreadyInCache = true;
                    foundInCache++;
                    break;
                }
            }
            
            // we could not find space for it... we will need to evict something
            if (!alreadyInCache) {
                int maxDistance = -1;
                int whoHasMax = -1;
                // we need to see the OPTIMAL eviction
                for (int j = 0; j < cache.length; j++) {
                    boolean futureAccessFound = false;
                    for (int k = i + 1; k < n; k++) {
                        if (cache[j].equals(memAccess.get(k))) {
                            futureAccessFound = true;
                            if ((k - i) >= maxDistance) {
                                maxDistance = (k - i);
                                whoHasMax = j;
                            }
                            break;
                        }
                    } // for (int k = i + 1; k < n; k++)
                    if (!futureAccessFound) {
                        // no reference to this access in future!!!
                        whoHasMax = j;
                        break;
                    }
                } // for (int j = 0; j < cache.length; j++)
                
                cache[whoHasMax] = currentAccess;
                optimalMisses++;
                
            } // if (!alreadyInCache) 
            
        } // for (int i = 0, n = memAccess.size() / 100; i < n; i++)
        
		Debug.println('z', "Found in cache: " + foundInCache);
        Debug.println('z', "Optimal misses: " + optimalMisses);
        Debug.println('z', "Misses in strategy: " + missesInStrategy / TRUNCATION_FACTOR);
        
        return new int[] {((missesInStrategy / TRUNCATION_FACTOR) - optimalMisses), uniqueAccesses.size()};
            
            /*
            
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
                totalMisses++;

			}

		}
        Debug.print('z', "Total misses " + totalMisses + "\n");
		return conflictMisses;*/
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
        
        Debug.print('z', "Total number of Memory Acceses " + memAccess.size() / TRUNCATION_FACTOR 
                + "\n");
        
        Debug.println('z', "TLB----------------");
        int[] conflictMissesTlb = conflictMisses(memAccess, totalTlbMisses, Machine.TLBSize);
        
        Debug.print('z', "Total number of TLB Misses " + totalTlbMisses / TRUNCATION_FACTOR
                + "\n");
        Debug.print('z', "Total number of TLB Hits " + totalTlbHits / TRUNCATION_FACTOR
                + "\n");
        Debug.print('z', "Compulsory TLB Misses "
                + conflictMissesTlb[1] + "\n");
        Debug.print('z', "Capacity TLB Misses "
                + ((totalTlbMisses / TRUNCATION_FACTOR) - conflictMissesTlb[1]) + "\n");
        Debug.print('z', "Conflict misses TLB " + conflictMissesTlb[0] + "\n");
        
        Debug.println('z', "PF----------------");
        int[] conflictMissesPage = conflictMisses(memAccess, totalPageFaults, Machine.NumPhysPages);
        
        Debug.print('z', "Total number of PF " + totalPageFaults / TRUNCATION_FACTOR
                + "\n");
        Debug.print('z', "Compulsory PF "
                + conflictMissesPage[1] + "\n");
        Debug.print('z', "Capacity PF "
                + ((totalPageFaults / TRUNCATION_FACTOR) - conflictMissesPage[1]) + "\n");
        Debug.print('z', "Conflict misses PF " + conflictMissesPage[0] + "\n");
	}
}
