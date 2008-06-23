/**
 * This class will provide the following memory facilities:
 * 
 * <ul>
 *   <li> Allocate pages - When a process is created, we need to allocate memory for it being clever enough
 *        to use virtual/physical memory translation.
 *   <li> Deallocate pages - When a process finishes, we need to deallocate all of the pages that were
 *        allocated on behalf of this process.
 * </ul>
 * 
 * This class relies heavily on BitMap in order to control which frames are free/used.
 * 
 * Note that, since the Virtual Memory (swapping) implementaton, this class keeps track of the swap partition
 * and the main memory. PageController and PageTable classes are responsible now to swap-in/-out
 * pages to/from main memory, this is, use main memory as a cache for the swap partition. 
 * 
 * For the public methods, the user must provide the type of memory that wants to be manipulated.
 */
public class MemoryManagement {
    // enforce only one instance of this class via a singleton
    private static final MemoryManagement instance = new MemoryManagement();
    
    // we need a bitmap to keep track of frame status (available or not available)
    // in swap space
    private BitMap swapSpace;
    
    // and another bitmap to keep track of the main memory
    private BitMap mainMemory;
    
    // flag to keep track whether this class has been initialized
    private boolean alreadyInitialized;
    
    // some sort of enum to distinguish between main memory and swap space
    public static final int MEMORY_TYPE_SWAP = 0;
    public static final int MEMORY_TYPE_MAIN = 1;
    
    // not really used, but by making this private and having only one static instance, we
    // enforce the singleton design pattern
    private MemoryManagement() {
        alreadyInitialized = false;
    }
    
    public static MemoryManagement getInstance() {
        return instance;
    }
    
    public void init() {
        Debug.ASSERT(alreadyInitialized == false, "[MemoryManagement.init] Cannot initialize more than once.");
        
        // init the swap space (we need to keep track of pages only)
        swapSpace = new BitMap(SwapPartitionController.SWAP_SIZE_PAGES);
        // init the main memory
        mainMemory = new BitMap(Machine.NumPhysPages);
        
        alreadyInitialized = true;
    }
    
    /**
     * Determines whether the system (bitmap) has enough free pages in the swap partition or main memory.
     * 
     * @param numPages How many pages we would like to allocate.
     * @param memoryType The memory we would like to query, whether swapping or main.
     * 
     * @return <code>true</code> if <code>numPages</code> is less or equal than the number of available pages
     *         in the <code>BitMap</code> we mantain. <code>false</code> otherwise.
     */
    public boolean enoughPages(int numPages, int memoryType) {
        BitMap bitMap = getBitMap(memoryType);
        return ((bitMap.numClear()) >= numPages);
    }
    
    /**
     * Given a provided memory type, returns a reference to the bitmap keeping track of that memory.
     * 
     * @param memoryType one of: MEMORY_TYPE_SWAP or MEMORY_TYPE_MAIN.
     * 
     * @return The desired bitmap.
     */
    private BitMap getBitMap(int memoryType) {
        // just a simple switch
        BitMap bitMap = null;
        switch (memoryType) {
            case MEMORY_TYPE_SWAP:
                bitMap = swapSpace;
                break;
            case MEMORY_TYPE_MAIN:
                bitMap = mainMemory;
                break;
            default:
                Debug.ASSERT(memoryType == MEMORY_TYPE_SWAP || memoryType == MEMORY_TYPE_MAIN, "[MemoryManagement.enoughPages] Invalid memory type parameter.");
        }
        
        return bitMap;
    }
    
    /**
     * Allocates a page.
     * 
     * @param memoryType The memory on which we would like to allocate a page.
     * 
     * @return the page number that has just been allocated, or -1 if no page could be allocated.
     */
    public int allocatePage(int memoryType) {
        BitMap bitMap = getBitMap(memoryType);
        
        // we are the only ones allocating pages, 
        int pageNumber = bitMap.find();
        
        // not enough memory!
        if (pageNumber == -1) {
            Debug.println('x', "[MemoryManagement.allocatePage] Could not find enough memory.");
            return -1;
        }
        
        // mark it as not available
        bitMap.mark(pageNumber);
        
        // return the page number
        return pageNumber;
    }
    
    /**
     * Deallocates a page.
     * 
     * @param pageNumber physical page number to deallocate.
     * @param memoryType The memory from which we would like to deallocate a page
     */
    public void deallocatePage(int pageNumber, int memoryType) {
        BitMap bitMap = getBitMap(memoryType);
        
        // deallocate the page
        bitMap.clear(pageNumber);
        
        Debug.printf('x', "[MemoryManagement.deallocatePage] Deallocating page %d from %s\n", 
                          new Integer(pageNumber), (memoryType == MEMORY_TYPE_SWAP ? "Swapping Partition" : "Main Memory"));
    }

}
