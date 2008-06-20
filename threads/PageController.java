/**
 * This class helps us to make transparent page faults and virtual memory. 
 * 
 * @author luis
 *
 */
public class PageController {
    // this one is private in machine... we need it anyway
    private static final long LOW32BITS = 0x00000000ffffffffL;
    
    private static int currentTlbIndex = 0;
    
    // only one instance allowed!
    private static final PageController instance = new PageController();
    
    // enforce only one instance
    private PageController() {
        
    } // ctor
    
    // get the instance
    public static PageController getInstance() {
        return instance;
    }
    
    /**
     * Provides the index of the tlb entry to evict.
     * 
     * @return The index of the tlb entry to evict.
     */
    private int nextTlbEntryToEvict() {
        // we want to return the current index
        int current = currentTlbIndex;
        
        // increment the current to next index
        currentTlbIndex = (currentTlbIndex + 1) % Machine.TLBSize; 
        
        return current;
    }
    
    /**
     * Handles a page fault... it is clever enough to bring the page from
     * disk, if needed.
     * 
     * @param virtualAddress The (32 bit) virtual address that generated a page fault.
     */ 
    public void handlePageFault(int virtualAddress) {
        // first, get the page from the virtualAddress
        long page = ((long) virtualAddress & LOW32BITS) / Machine.PageSize;
        
        // at this point, we know that the requested virtual address generated a page fault
        // but we don't know where the page actually is... it could be in disk or in main
        // memory... we know for sure it is not in the TLB...
        
        // get the current process id
        int processId = NachosThread.thisThread().getSpaceId();
        
        // we know which page was accessed, now, decide which entry in the TLB to evict
        // we use a round-robin replacement...
        int entryToEvict = nextTlbEntryToEvict();
        
        // get the translation entry from our global page table
        PageTable.PageTableEntry entry = PageTable.getInstance().getEntry(processId, page);
        
        // the page is on disk if...
        if (entry.pageOffsetInDisk != -1) {
            // IT'S ON DISK! do the magic
        }
        
        // replace in TLB
        Machine.tlb[entryToEvict] = entry.translationEntry;
        entry.translationEntry.use = true;
        entry.translationEntry.valid = true;
        
    } // handlePageFault
    
    /**
     * Invalidates the tlb. This method is useful on a context-switch.
     */
    public void invalidateTlb() {
        for (int i = 0; i < Machine.TLBSize; i++) {
            Machine.tlb[i].valid = false;
        }
        
    } // invalidateTlb
    
} // class
