/**
 * This class helps us to make transparent page faults and virtual memory. 
 * 
 * @author luis
 *
 */
public class PageController {
    // this one is private in machine... we need it anyway
    private static final long LOW32BITS = 0x00000000ffffffffL;
    

    // we will use a round-robin eviction scheme, plus, we will not evict pages
    // that belong to the currently running process (in the case of main-memory eviction)
    private static int currentFrameIndex = 0;
    // pure round robin for tlb eviction
    private static int currentTlbIndex = 0;
    
    // use one buffer overall
    private byte[] buffer = new byte[Machine.PageSize];
    
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
        // we want to return either a random entity to evict, or the first one that is unused
        boolean emptySlotFound = false;
        int indexToEvict = -1;
        for (int i = 0; i < Machine.TLBSize; i++) {
            if (Machine.tlb[i] == null || Machine.tlb[i].valid == false) {
                emptySlotFound = true;
                indexToEvict = i;
            }
        }
        
        if (!emptySlotFound) {
            // random
            indexToEvict = (int)(Math.random()*100) % Machine.TLBSize;
        }
        
        return indexToEvict;
    }
    
    /**
     * Provides the index of the frame to evict.
     * 
     * @return Frame to evict.
     */
    private int nextFrameToEvict() {
        // return the current index
        int current = currentFrameIndex;
        
        // increment
        currentFrameIndex = (currentFrameIndex + 1) % SwapPartitionController.SWAP_SIZE_PAGES;
        
        return current;
    }
    
    private int nextFrameToEvictLastResort() {
        // look for a random entry
        int current = (int)(Math.random()*100) % SwapPartitionController.SWAP_SIZE_PAGES;
        // get an entry
        PageTable.PageTableEntry entry = PageTable.getInstance().getEntriesAt(current);
        // stop until we find something that is in main memory
        while (entry == null || !entry.inMainMemory) {
            current = (int)(Math.random()*100) % SwapPartitionController.SWAP_SIZE_PAGES;;
            entry = PageTable.getInstance().getEntriesAt(current);
        }
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
        
        // ok, we got the page descriptor... now, figure out if the page is already in main memory or in the disk 
        if (entry.inMainMemory == false) {
            // ok, it is not in main memory... let's bring it from disk
            // but, careful... we need to see if there's still enough space on main memory!!!
            // if not, we will need to evict one page!
            // so first, is there enough space in main memory?
            if (MemoryManagement.getInstance().enoughPages(1, MemoryManagement.MEMORY_TYPE_MAIN)) {
                // compulsory page fault
                PerformanceEvaluator.pageFault(entry.processId, entry.translationEntry.virtualPage);
                // yes, enough space on main memory, so just bring it over and update the page descriptor
                // get the physical page to where this page will be allocated first
                int physicalPage = MemoryManagement.getInstance().allocatePage(MemoryManagement.MEMORY_TYPE_MAIN);
                entry.translationEntry.physicalPage = physicalPage;
                // ok, be extra paranoid
                Debug.ASSERT(physicalPage != -1, "[PageController.handlePageFault] Could not allocate a page!");
                
                // and perform the actual copying of data from swapping partition to main memory
                Debug.printf('x', "[PageController.handlePageFault] Copying from swap partition to main memory %s\n", entry.toString());
                SwapPartitionController.getInstance().getPage(entry.swapPage, buffer);
                writeToMainMemory(entry, buffer);
                
                // update metadata
                entry.inMainMemory = true;
            }
            else {
                // so, not enough space in main memory... need to swap something out
                swapPage(entry);
            }
        }
        
        // replace in TLB
        Machine.tlb[entryToEvict] = entry.translationEntry;
        entry.translationEntry.use = true;
        entry.translationEntry.valid = true;
        
    } // handlePageFault
    
    /**
     * After completion, the passed page entry will reside in main memory, while another page on main memory will be swapped back to disk.
     * 
     * @param pageEntry The descriptor of the page that will be swapped into main memory.
     */
    public void swapPage(PageTable.PageTableEntry pageEntry) {
        PerformanceEvaluator.pageFault(pageEntry.processId, pageEntry.translationEntry.virtualPage);
        Debug.printf('x', "[PageController.swapPage] Swapping-in %s\n", pageEntry.toString());
        
        // process id
        int processId = NachosThread.thisThread().getSpaceId();
        
        // we know that there are no free pages on main memory, so we can traverse the inverted page table
        boolean found = false;
        PageTable.PageTableEntry pageToEvict = null;
        int numberOfAttempts = 0;
        
        // try to traverse the inverted page table only once (using the numberOfAttempts counter helps)
        while (!found && numberOfAttempts < SwapPartitionController.SWAP_SIZE_PAGES) {
            // get a candidate page to evict
            // first, get the entries associated to a frame
            PageTable.PageTableEntry entry = PageTable.getInstance().getEntriesAt(nextFrameToEvict());
            
            // traverse the entries and check process id and whether or not they are on main memory as we go
            // as soon as we find one entry whose process id is different from the one of the current process
            // and that entry is on main memory, we will stop
            pageToEvict = entry;
            while (pageToEvict != null) {
                if (pageToEvict.processId != processId && pageToEvict.inMainMemory) {
                    // we found one to evict!
                    found = true;
                    break;
                }
                pageToEvict = entry.nextPageTableEntry;
            }
            
            // increment the number of attempts
            numberOfAttempts++;
        }
        
        // it could be that the current process occupies the whole main memory...
        if (pageToEvict == null) {
            // just evict something!
            pageToEvict = PageTable.getInstance().getEntriesAt(nextFrameToEvictLastResort());
        }
        
        // we now have a page to evict
        // do we need to write back to disk?
        if (pageToEvict.translationEntry.dirty) {
            Debug.printf('x', "[PageController.swapPage] Writing back dirty page %s\n", pageToEvict.toString());
            // we need to write back to the swapping partition
            // read the contents from memory
            readFromMainMemory(pageToEvict, buffer);
            
            // and write to the swap partition
            SwapPartitionController.getInstance().writePage(buffer, pageToEvict.swapPage, 0);
            
            // and this page is not dirty anymore
            pageToEvict.translationEntry.dirty = false;
        }
        Debug.printf('x', "[PageController.swapPage] Evicting %s\n", pageToEvict.toString());
        
        // update physical page info for the swapped-in page
        pageEntry.translationEntry.physicalPage = pageToEvict.translationEntry.physicalPage;
        
        // at this point, we can use a spot in main memory
        // copy from the swapping partition to the buffer, and then to main memory
        SwapPartitionController.getInstance().getPage(pageEntry.swapPage, buffer);
        writeToMainMemory(pageEntry, buffer);
        
        // update the bit indicating that the page is in main memory, and some other metadata
        pageEntry.inMainMemory = true;
        pageEntry.translationEntry.use = true;
        
        // update metadata for the evicted page
        pageToEvict.translationEntry.valid = false;
        pageToEvict.inMainMemory = false;
        pageToEvict.translationEntry.physicalPage = -1;

    }
        

    /**
     * Reads a page from main memory into the buffer.
     * 
     * @param pageEntry Page descriptor of the page that wants to be read.
     * @param buffer Buffer to place the read data.
     */
    public void readFromMainMemory(PageTable.PageTableEntry pageEntry, byte[] buffer) {
        for (int i = pageEntry.translationEntry.physicalPage * Machine.PageSize,
                n = (pageEntry.translationEntry.physicalPage + 1) * Machine.PageSize,
                j = 0;
            i < n;
            i++, j++) {
           buffer[j] = Machine.mainMemory[i];
        }
    }
    
    /**
     * Writes the provided data to main memory.
     * 
     * @param pageEntry Descriptor of the page that needs to be written.
     * @param buffer Buffer to copy.
     */
    public void writeToMainMemory(PageTable.PageTableEntry pageEntry, byte[] buffer) {
        for (int i = pageEntry.translationEntry.physicalPage * Machine.PageSize,
                n = (pageEntry.translationEntry.physicalPage + 1) * Machine.PageSize,
                j = 0;
            i < n;
            i++, j++) {
            Machine.mainMemory[i] = buffer[j];
        }
    }
    
    /**
     * Invalidates the tlb. This method is useful on a context-switch.
     */
    public void invalidateTlb() {
        for (int i = 0; i < Machine.TLBSize; i++) {
            Machine.tlb[i].valid = false;
        }
        
    } // invalidateTlb
    
} // class
