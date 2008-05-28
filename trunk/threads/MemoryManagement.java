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
    
    // we need a bitmap
    private BitMap physicalMemory = new BitMap(Machine.MemorySize);
    
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
     * @return the physical page number that has just been allocated.
     */
    public int allocatePage() {
        return 0;
    }
    
    /**
     * Deallocates a page.
     * 
     * @param pageNumber physical page number to deallocate.
     */
    public void deallocatePage(int pageNumber) {
        
    }
    
    

}
