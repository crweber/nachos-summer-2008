import java.io.RandomAccessFile;
import java.io.File;
import java.io.IOException;

/**
 * Class wrapping around the swap partition. We call it partition because it sounds cool, but in reality
 * it is just a plain cheap swap file... But partition sounds fancier.
 */
public class SwapPartitionController {
    // size of this partition
    // according to literature, Linux uses a swap partition and recommends twice the main memory size
    // we will use a fix ammount... say, 4 times the main memory
    public static final int SWAP_SIZE_BYTES = 4 * (Machine.MemorySize);
    public static final int SWAP_SIZE_PAGES = SWAP_SIZE_BYTES / Machine.PageSize;
    
    // only instance
    private static SwapPartitionController instance = new SwapPartitionController();
    
    // whether this has been already initialized
    private boolean alreadyInitialized;
    
    // the swap partition itself
    private OpenFile swapPartition;
    
    // right now, we will use a random access file, later on, when the file system is implemented
    // this might go away
    private RandomAccessFile swapFile;
    
    // enforce only one instance of this controller, and of the swap partition too
    private SwapPartitionController() {
        alreadyInitialized = false;

    }
    
    public void init() {
        Debug.ASSERT(alreadyInitialized == false, "[SwapPartitionController.init] Cannot initialize more than once!");
        
        // check if the swap file already exists... if so, just delete it... this will happen only once
        // per nachos boot-up
        File swapFileTmp = new File("swap-partition");
        if (swapFileTmp.exists()) {
            if (!swapFileTmp.delete()) {
                Debug.println('+', "[SwapPartitionController] Could not reset swap partition. PANIC!");
                Nachos.Halt();
            }
        }
        
        // at this point, we can safely create the swap partition
        try {
            swapFile = new RandomAccessFile(swapFileTmp, "rw");
        }
        catch (IOException ioe) {
            Debug.println('+', "[SwapPartitionController] PANIC - " + ioe.getMessage());
            Debug.println('+', "[SwapPartitionController] Could not create swap partition. PANIC!");
            Nachos.Halt();
        }
        
        swapPartition = new OpenFileStub(swapFile);
        
        // now, we can initialize the swap partition... just write out one byte after seeking
        // to the end of the file... nice trick!
        byte[] someByte = new byte[1];
        someByte[0] = 0;
        swapPartition.seek(SWAP_SIZE_BYTES - 1);
        swapPartition.write(someByte, 0, 1);
        
        // check that the size of the partition is actually correct
        Debug.ASSERT(swapPartition.length() == SWAP_SIZE_BYTES, "[SwapPartitionController] Could not initialize swap partition. PANIC!");
        
        // flag as initialized
        alreadyInitialized = true;
    }
    
    public static SwapPartitionController getInstance() {
        return instance;
    }
    
    /**
     * Writes <code>pageData</code> to the swap partition.
     * 
     * @param pageData The data to write. It will be read from the first element to the last one. It is assumed that 
     *                 the lenght of this array is exactly the size of the page.
     * @param pageNumber The page number (in the swapping partition) that will be written into.
     * @param intraPageOffset The offset inside the swapping page that will be used.
     */
    public void writePage(byte[] pageData, int pageNumber, int intraPageOffset) {
        Debug.ASSERT(pageData.length >= Machine.PageSize, "[SwapPartitionController.writePage] Buffer must be at least the size of the page size.");
        // write the data using the file offset
        int writtenBytes = swapPartition.writeAt(pageData, 0, Machine.PageSize, (pageNumber * Machine.PageSize) + intraPageOffset);
        Debug.ASSERT(writtenBytes == Machine.PageSize, "[SwapPartitionController.writePage] Could not write all data (or wrote more than needed!)");
    }
    
    /**
     * Writes an arbitrary ammount of data into the swap partition.
     * 
     * @param data The data to write.
     * @param size The amount of bytes to write from the buffer.
     * @param pageNumber The swapping partition page to write into.
     * @param intraPageOffset The offset inside the swapping page that will be used.
     */
    public void writeData(byte[] data, int size, int pageNumber, int intraPageOffset) {
        Debug.ASSERT(data.length >= size, "[SwapPartitionController.writeData] Buffer must be at least as big as the desired number of bytes to copy.");
        // write the data using the provided ofset
        int writtenBytes = swapPartition.writeAt(data, 0, size, (pageNumber * Machine.PageSize) + intraPageOffset);
        Debug.ASSERT(writtenBytes == size, "[SwapPartitionController.getwriteData] Could not write all data (or wrote more than needed!)");
    }
    
    /**
     * Gets a page of data.
     * 
     * @param pageNumber The swapping page to get.
     * @param pageData Buffer to use to copy the data contained in the page.
     */
    public void getPage(int pageNumber, byte[] pageData) {
        Debug.ASSERT(pageData.length >= Machine.PageSize, "[SwapPartitionController.getPage] Buffer must be at least as big as the page size.");
        // read the data
        int readBytes = swapPartition.readAt(pageData, 0, Machine.PageSize, (pageNumber * Machine.PageSize));
        Debug.ASSERT(readBytes == Machine.PageSize, "[SwapPartitionController.getPage] Could not read all data (or read more than needed!)");
        
    }
    
}
