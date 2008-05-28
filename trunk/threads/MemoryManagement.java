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
 */
public class MemoryManagement {

    // enforce only one instance of this class via a singleton
    public static final MemoryManagement instance = new MemoryManagement();
    
    // we need a bitmap
    private BitMap physicalMemory;
    
    // not really used, but by making this private and having only one static instance, we
    // enforce the singleton design pattern
    private MemoryManagement() {
        physicalMemory = new BitMap(Machine.MemorySize);
    }
    
    /**
     * Determines whether the system (bitmap) has enough free pages.
     * 
     * @param numPages How many pages we would like to allocate.
     * 
     * @return <code>true</code> if <code>numPages</code> is less or equal than the number of available pages
     *         in the <code>BitMap</code> we mantain. <code>false</code> otherwise.
     */
    public boolean enoughPages(int numPages) {
        return ((physicalMemory.numClear() / Machine.PageSize) >= numPages);
    }
    
    /**
     * Allocates a page.
     * 
     * @return the physical page number that has just been allocated, or -1 if no page could be allocated.
     */
    public int allocatePage() {
        // we are the only ones allocating pages, so we know for sure that, if the first bit of a page is not available
        // then PageSize bits (inclusive) won't be available at all
        int firstBit = physicalMemory.find();
        
        // not enough memory!
        if (firstBit == -1) {
            Debug.println('x', "[MemoryManagement.allocatePage] Could not find enough memory.");
            return -1;
        }
        
        // just be extra paranoid and check that this firstBit actually matches the first bit of a page
        Debug.ASSERT(firstBit % Machine.PageSize == 0, "[MemoryManagement.allocatePage] first bit available doesnt seem to be the first on page!");

        // just set all of the bits for this page
        for (int i = (firstBit + 1); i < (firstBit + Machine.PageSize); i++) {
            physicalMemory.mark(i);
        }
        
        Debug.printf('x', "[MemoryManagement.allocatePage] Allocated page %d\n", new Integer(firstBit / Machine.PageSize));
        
        // return the page number
        return (firstBit / Machine.PageSize);
    }
    
    /**
     * Deallocates a page.
     * 
     * @param pageNumber physical page number to deallocate.
     */
    public void deallocatePage(int pageNumber) {
        // indices
        int firstBit = pageNumber * Machine.PageSize;
        int lastBit = pageNumber * (Machine.PageSize + 1);
        
        // deallocate bit by bit
        for (int i = firstBit; i < lastBit; i++) {
            physicalMemory.clear(i);
        }
        
        Debug.printf('x', "[MemoryManagement.deallocatePage] Deallocating page %d\n", new Integer(pageNumber));
    }
    
    

}
