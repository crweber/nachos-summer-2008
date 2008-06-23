import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Map;
import java.util.HashMap;
/**
 * Basic implementation of an inverted page table.
 */
public class PageTable {
    // internal anchorTable
    // key: <processId, virtualPageNumber>
    // value: <physicalPageNumber> (which is used as index)
    private final Map anchorTable = new HashMap();

    // page table entry, we now use virtual memory, so we need to keep track of pages in use
    private final PageTableEntry[] pageTable = new PageTableEntry[SwapPartitionController.SWAP_SIZE_PAGES];
    
    // enforce only one instance
    private final static PageTable instance = new PageTable();
    
    // private ctor
    private PageTable() {
        
    } // ctor
    
    // get the instance
    public static PageTable getInstance() {
        return instance;
        
    } // getInstance
    
    /**
     * Translates a virtual address to a physical address.
     * 
     * @param processId Process ID requesting this translation.
     * @param virtualAddress Virtual address to be translated.
     * 
     * @return the physical address.
     */
    private int translateAddress(int processId, int virtualAddress) {
        // obtain the virtualPageNumber and offset
        int virtualPageNumber = virtualAddress / Machine.PageSize;
        int offset = virtualAddress % Machine.PageSize;
        
        // get the entry from the page table
        PageTableEntry entry = getEntry(processId, virtualPageNumber);
        Debug.ASSERT(entry != null, "[PageTable.translateAddress] Page missing!!!");
        
        // it might be so that the page is not in main memory...
        if (!entry.inMainMemory) {
            // if so, swap-in this page and perform the translation
            PageController.getInstance().swapPage(entry);
        }
        
        // perform the actual translation
        return ((entry.translationEntry.physicalPage * Machine.PageSize) + offset);
        
    } // translate address
    
    /**
     * Similar to translateAddress, but this considers the <i>previous</i> translated virtual address
     * in order to avoid the expensive operation of looking into the inverted page table.
     * 
     * @param processId Process ID whose virtual memory will be used.
     * @param virtualAddress Virtual address that needs to be translated.
     * @param previousVirtualPage Previous virtual address that was translated.
     * @param previousPhysicalAddress Previous returned physical address.
     * 
     * @return The physical address.
     */
    private int translateAddress(int processId, int virtualAddress, int previousVirtualAddress, int previousPhysicalAddress) {
        // check if the two provided virtual addresses are on the same page, if so, 
        // no need to translate, just return the previousPhysicalAddress + the offset between them
        if ((virtualAddress / Machine.PageSize) == (previousVirtualAddress / Machine.PageSize)) {
            return (previousPhysicalAddress + (virtualAddress - previousVirtualAddress));
        }
        
        // in this case, we actually need to perform a search in the page table...
        return translateAddress(processId, virtualAddress);
    }
    
    /**
     * Copies memory from the kernel space (the provided buffer) to the user space memory (for the current process).
     * 
     * @param buffer The buffer to copy.
     * @param targetVirtualAddress The target virtual address where the buffer will be copied.
     * 
     * @return the number of bytes actually copied.
     */
    public int copyFromKernel(byte[] buffer, int targetVirtualAddress) {
        // check that we actually have something to copy
        if (buffer.length == 0) {
            return 0;
        }
        
        int processId = NachosThread.thisThread().getSpaceId();
        int totalBytes = 0;
        
        // starting base physical address
        int physicalAddress = translateAddress(processId, targetVirtualAddress);
        int previousVirtualAddress = targetVirtualAddress;
        
        // copy the first byte
        Machine.mainMemory[physicalAddress] = buffer[0];
        totalBytes++;
        
        // copy byte from byte
        for (int i = 1, n = buffer.length; i < n; i++, totalBytes++) {
            // new virtual address
            int newVirtualAddress = (targetVirtualAddress + i);
            // translate
            physicalAddress = translateAddress(processId, newVirtualAddress, previousVirtualAddress, physicalAddress);
            // copy
            Machine.mainMemory[physicalAddress] = buffer[i];
            // update previous values
            previousVirtualAddress = newVirtualAddress;
        }
        
        return totalBytes;
        
    } // copyFromKernel
    
    /**
     * Copies data from the user space (for the current process) to the kernel.
     * 
     * @param sourceVirtualAddress Source virtual address to copy.
     * @param length Length (bytes) to copy to kernel space.
     * 
     * @return A byte array containing the copied data.
     */
    public byte[] copyFromUserSpace(int sourceVirtualAddress, int length) {
        // check that we actually have something to copy
        if (length == 0) {
            return new byte[0];
        }
        
        int processId = NachosThread.thisThread().getSpaceId();
        
        // buffer
        byte[] buffer = new byte[length];
        
        // starting base physical address
        int physicalAddress = translateAddress(processId, sourceVirtualAddress);
        int previousVirtualAddress = sourceVirtualAddress;
        
        // copy first byte
        buffer[0] = Machine.mainMemory[physicalAddress];
        
        // copy byte from byte
        for (int i = 1; i < length; i++) {
            // new virtual address
            int newVirtualAddress = sourceVirtualAddress + i;
            // translate
            physicalAddress = translateAddress(processId, newVirtualAddress, previousVirtualAddress, physicalAddress);
            // copy
            buffer[i] = Machine.mainMemory[physicalAddress];
            // update previous values
            previousVirtualAddress = newVirtualAddress;
        }
        
        return buffer;
        
    } // copyFromUserSpace
    
    /**
     * Similar to copying data from the user space (for the current process) to kernel, 
     * just this method gets a String directly, handling all those nasty details.
     * 
     * @param sourceVirtualAddress Source virtual address to start to copy.
     * 
     * @return The String found on the source virtual addres. Every found byte will be treated as a 
     *         character until the value <code>0</code> is found, at this point, a String will be returned. 
     */
    public String getStringFromUserSpace(int sourceVirtualAddress) {
        // buffer
        StringBuffer buffer = new StringBuffer();
        
        int processId = NachosThread.thisThread().getSpaceId();
        
        // starting base physical address
        int physicalAddress = translateAddress(processId, sourceVirtualAddress);
        int previousVirtualAddress = sourceVirtualAddress;
        
        // copy first byte
        byte someByte = Machine.mainMemory[physicalAddress];
        
        // copy byte from byte
        while (someByte != 0) {
            // append to the buffer, it's a good byte
            buffer.append((char)someByte);            
            // new virtual address
            int newVirtualAddress = (previousVirtualAddress + 1);
            // translate
            physicalAddress = translateAddress(processId, newVirtualAddress, previousVirtualAddress, physicalAddress);
            // copy
            someByte = Machine.mainMemory[physicalAddress];
            // update previous values
            previousVirtualAddress = newVirtualAddress;
        }
        
        return buffer.toString();
        
    } // getStringFromUserSpace
        
    /**
     * Returns the entries that are associated to the provided index (frame)
     * 
     * @param frameIndex The index of the frame.
     * 
     * @return The first entry located in this frame.
     */
    public PageTableEntry getEntriesAt(int frameIndex) {
        return pageTable[frameIndex];
    }
    
    /**
     * Returns a translation entry for a given process and virtual page number.
     * 
     * @param processId Process ID.
     * @param virtualPageNumber Page Number
     * 
     * @return The given translation entry, <code>null</code> if none found.
     */
    public PageTableEntry getEntry(int processId, long virtualPageNumber) {
        // look in the anchor table
        String key = buildKey(processId, virtualPageNumber);
        Integer index = (Integer)anchorTable.get(key);
        
        // return entry
        PageTableEntry entry = null;

        // now, start to look for the entry
        if (index != null) {
            PageTableEntry last = pageTable[index.intValue()];
            while (last != null) {
                // is this entry the same one we're looking for?
                if (last.processId == processId && last.translationEntry.virtualPage == virtualPageNumber) {
                    entry = last;
                    break;
                }
                
                // look in the next entry
                last = last.nextPageTableEntry;
            }
        }
        return entry;
    }
    
    /**
     * Removes the entries for the current process in the inverted page table, swap partition and main memory.
     */
    public void removeCurrentProcess() {
        // get the process id
        int processId = NachosThread.thisThread().getSpaceId();
        // and the number of virtual pages this uses
        int numVirtualPages = NachosThread.thisThread().getNumVirtualPages();
        
        Debug.printf('x', "[PageTable.removeCurrentProcess] Deallocating %d pages from process %d\n", 
                          new Integer(numVirtualPages), new Integer(processId));
        
        // and start to dealloacate each one of them
        for (int i = 0; i < numVirtualPages; i++) {
            // get the index in the table
            String key = buildKey(processId, i);
            Integer index = (Integer)anchorTable.get(key);
            
            // now get the first entry
            PageTableEntry current = pageTable[index.intValue()];
            PageTableEntry previous = null;
            while (current != null) {
                // is this an entry of this process?
                if (current.processId == processId && current.translationEntry.virtualPage == i) {
                    // update pointers
                    if (previous != null) {
                        // our entry is not the first one
                        // just update the pointer of the previous entry
                        previous.nextPageTableEntry = current.nextPageTableEntry;
                    } else {
                        // our entry is the first one (since previous == null)
                        if (current.nextPageTableEntry == null) {
                            // current is the only entry (since it does not have next entry)
                            // we need to remove the index from the anchor table if this is the only entry!
                            anchorTable.remove(key);
                            // and to actually remove the reference from here
                            pageTable[index.intValue()] = null;
                        } else {
                            // it is the first one, but there are more to come, just update pointers
                            // to make the next entry the first one in the chain
                            pageTable[index.intValue()] = current.nextPageTableEntry;
                        }
                    }

                    // flag in the memory management that this frame is free in the swap partition
                    MemoryManagement.getInstance().deallocatePage(current.swapPage, MemoryManagement.MEMORY_TYPE_SWAP);
                    
                    // and only if the page also resides on main memory, we need to deallocate it from there
                    if (current.inMainMemory) {
                        MemoryManagement.getInstance().deallocatePage(current.translationEntry.physicalPage, MemoryManagement.MEMORY_TYPE_MAIN);
                    }
                    
                    // set the references to null
                    current.translationEntry = null;
                    current.nextPageTableEntry = null;
                    current = null;
                    
                    // we're done with this cycle
                    break;
                }
                // advance
                previous = current;
                current = current.nextPageTableEntry;
            }
        }
    }
    
    /**
     * Sets an entry on the table.
     * 
     * @param processId Process id.
     * @param virtualPageNumber Page Number
     * @param entry Entry to set.
     */
    public void setEntry(int processId, long virtualPageNumber, PageTableEntry entry) {
        // first, check in the anchor table
        String key = buildKey(processId, virtualPageNumber);
        Integer index = (Integer)anchorTable.get(key);
        
        // will there be a colision?
        if (index == null) {
            // no, simply store it
            pageTable[entry.swapPage] = entry;
            
            // and make sure that it is not pointing to anybody else
            entry.nextPageTableEntry = null;
            
            // also, store this info in the anchor table
            anchorTable.put(key, new Integer(entry.swapPage));
            
        } else {
            // yes, colision
            // first we need to find the "last" element in the colision chain
            PageTableEntry last = pageTable[index.intValue()];
            while (last.nextPageTableEntry != null) {
                last = last.nextPageTableEntry;
            }
            
            // when here, it means that we are at the last element
            // set the appropiate pointers
            last.nextPageTableEntry = entry;
            entry.nextPageTableEntry = null;
        }
    }
    
    /**
     * Allocates a new process in the swapping partition.
     * 
     * The main memory is used as a cache for the swapping partition, therefore, when a new process is allocated, its pages are
     * copied to the swapping partition and PageController should control which pages are swapped into/from main memory.
     * 
     * We could even extend this and make it even more efficient and don't copy anything to the swap partition unless it is modified,
     * in this way, code and read-only data (or read/write data that hasn't been written into) will be referenced from the
     * file containing the "executable" file, and, whenever we find a dirty page that needs to be evicted from main memory, copy that
     * page from main memory to the swap partition and change all reference. This would be a pretty good approach, but, we would also
     * need to keep track of file descriptors and offsets within the executable file for each page. Also, if we keep a swap partition
     * it is more likely that there will be less scanning on the disk.
     * 
     * We chose the simple approach of copying the executable file to the swapping partition for ease of maintenance, plus, for
     * reliability reasons... Suppose the executable file is on a remote device somehow mounted to the OS... If at some point that
     * device is unmounted or somehow disconnected, then the OS won't be able to resume the execution of that executable. 
     * We assume, of course, that the swap partition is always mounted and that the OS alone has total control over it. Of course,
     * if the swap partition is somehow damaged, we think that not being able to run a specific program would be the least of the
     * problems because a new swap partition would have to be created somehow.
     * 
     * @param RandomAccessFile The file with the process code/data.
     * @param processId The id of the process to be allocated.
     * 
     * @returns How many pages were actually allocated on behalf of this process. 
     */
    public int allocateNewProcess(RandomAccessFile executable, int processId) throws IOException, NachosException {
        NoffHeader noffH;
        long size;
        
        noffH = new NoffHeader(executable);

        // how big is address space?
        size = noffH.code.size + noffH.initData.size + noffH.uninitData.size 
        + AddrSpace.UserStackSize;    // we need to increase the size
        // to leave room for the stack
        int numPages = (int)(size / Machine.PageSize);
        if (size % Machine.PageSize > 0) numPages++;

        size = numPages * Machine.PageSize;

        // check we have enough free pages
        if (!MemoryManagement.getInstance().enoughPages(numPages, MemoryManagement.MEMORY_TYPE_SWAP)) {
            // no harm done... just throw an exception
            Debug.println('x', "[PageTable.allocateNewProcess] Not enough free pages! Requested " + numPages);
            throw new NachosException("[PageTable.allocateNewProcess] Not enough free pages! Requested " + numPages);
        }

        Debug.println('x', "[PageTable.allocateNewProcess] Loading process, numPages=" + numPages + ", size=" + size);
        
        // some useful zeroes
        byte[] zeroes = new byte[Machine.PageSize];

        // first, set up the translation 
        for (int i = 0; i < numPages; i++) {
            PageTableEntry entry = new PageTableEntry(processId);
            entry.translationEntry.virtualPage = i; 
            int swapPage = MemoryManagement.getInstance().allocatePage(MemoryManagement.MEMORY_TYPE_SWAP);
            // before even getting the address space set-up, we should've checked that there
            // was enough memory, so, this should not create any problems
            Debug.ASSERT(swapPage != -1, "[PageTable.allocateNewProcess] There are not enough pages available!!!");
            // right now we cannot determine the frame in main memory that this page will be allocated into
            entry.translationEntry.physicalPage = -1;
            // we can, however, know the swapping page
            entry.swapPage = swapPage;
            // and, of course, it is NOT in main memory
            entry.inMainMemory = false;
            // set the other bits accordingly
            entry.translationEntry.valid = true;
            entry.translationEntry.use = false;
            entry.translationEntry.dirty = false;
            entry.translationEntry.readOnly = false;  // if the code segment was entirely on 
            // a separate page, we could set its 
            // pages to be read-only
            
            // the entry has been created, set it on the page table
            setEntry(processId, i, entry);
            
            // as we create the entries in the inverted page table, we can zero out the swap partition that will
            // hold these pages
            SwapPartitionController.getInstance().writePage(zeroes, entry.swapPage, 0);
        }
        
        // we cannot longer just copy the pages to main memory, we have to copy them to the swap partition...
        // copy in the code and data segments into memory
        // now, rather than do it in one chunk, we need to do this page by page...
        // we cannot assume anymore that our pages will be adjacent
        if (noffH.code.size > 0) {
            Debug.println('x', "[PageTable.allocateNewProcess] Initializing code segment, at " +
                    noffH.code.virtualAddr + ", size " +
                    noffH.code.size);

            copyToSwapPartition(
                    executable, 
                    noffH.code.inFileAddr, 
                    (int)noffH.code.size, 
                    processId, 
                    (int)noffH.code.virtualAddr / Machine.PageSize, 
                    (int)noffH.code.virtualAddr % Machine.PageSize);

        }
      
        if (noffH.initData.size > 0) {
            Debug.println('x', "[PageTable.allocateNewProcess] Initializing data segment, at " +
                    noffH.initData.virtualAddr + ", size " +
                    noffH.initData.size);

            copyToSwapPartition(
                    executable, 
                    noffH.initData.inFileAddr, 
                    (int)noffH.initData.size, 
                    processId, 
                    (int)noffH.initData.virtualAddr / Machine.PageSize,
                    (int)noffH.initData.virtualAddr % Machine.PageSize);
        }
        
        return numPages;
    }
    
    /**
     * Copies <code>size</code> bits from <code>executable</code> file into the swapping partition starting at the file offset given by <code>fileOffset</code>
     * using this inverted page table, offset by <code>virtualPageOffset</code> and <code>bitOffset</code>.
     * 
     * @param executable Executable file to read from.
     * @param fileOffset File offset to which we will start to read.
     * @param size Number of bits to write to main memory.
     * @param processId Process id that is being loaded into memory.
     * @param virtualPageOffset Which will be the first translation entry to use.
     * @param byteOffset How many bytes from the beginning of a page we want to offset.
     * 
     * @throws IOException if something goes wrong
     */
    void copyToSwapPartition(RandomAccessFile executable, long fileOffset, int size, int processId, int virtualPageOffset, int byteOffset) 
        throws IOException {
        // first off, see how many "full" pages we need to copy from the file to memory
        int fullPages = size / Machine.PageSize;
        int remainderBytes = (size % Machine.PageSize);
        
        // move the pointer in the file
        executable.seek(fileOffset);
        
        // buffer to read from exec file
        byte[] buffer = new byte[Machine.PageSize];
        
        // start copying full pages
        for (int i = virtualPageOffset, n = virtualPageOffset + fullPages; i < n; i++) {
            // first, get the page entry for this desired virtual page
            PageTableEntry entry = getEntry(processId, i);
            Debug.printf('x', "[PageTable.copyToSwapPartition] Copying code/data (full page) to swap partition page [%d] with [%d] bytes offset.\n", 
                              new Long(entry.swapPage), new Long(byteOffset));
            
            // instead of reading directly into main memory, we will use a buffer to put the data to later write it into the swap partition
            int read = executable.read(buffer, 0, Machine.PageSize);
            Debug.ASSERT(read == Machine.PageSize, "[PageTable.copyToSwapPartition] Could not read the entire page from file!!!");
            
            // we now have the data from the file, put it on the swap partition
            SwapPartitionController.getInstance().writePage(buffer, entry.swapPage, byteOffset);
        }
        
        // now, the remainder bytes
        if (remainderBytes > 0) {
            PageTableEntry entry = getEntry(processId, virtualPageOffset + fullPages);
            // now, we can write the remaining bytes
            Debug.printf('x', "[PageTable.copyToSwapPartition] Copying code/data (%d bytes) to swap parttion page [%d] with [%d] offset inside the page.\n", 
                              new Object[] {new Long(remainderBytes), new Long(entry.swapPage), new Long(byteOffset)});
            
            // read into the buffer
            int read = executable.read(buffer, 0, remainderBytes);
            Debug.ASSERT(read == remainderBytes, "[PageTable.copyToSwapPartition] Could not read completely from file!!!");
            
            // and write the buffer to the swap partition
            SwapPartitionController.getInstance().writeData(buffer, remainderBytes, entry.swapPage, byteOffset);
        }
        
    }
    
    private String buildKey(int processId, long virtualPageNumber) {
        return (processId + "|" + virtualPageNumber);
    }
    
    static class PageTableEntry {
        // process owining this page
        int processId;
        // page number on swap partition
        int swapPage;
        // where is this page right now?
        boolean inMainMemory;
        // reuse-reuse-REUSE!!!
        TranslationEntry translationEntry;
        // pointer to the next entry
        PageTableEntry nextPageTableEntry;
        
        public PageTableEntry(int processId) {
            this.processId = processId;
            swapPage = -1;
            translationEntry = new TranslationEntry();
            nextPageTableEntry = null;
        }
        
        public String toString() {
            StringBuffer buffer = new StringBuffer("[PageTableEntry, processId=");
            buffer.append(processId);
            buffer.append(", swapPage=");
            buffer.append(swapPage);
            buffer.append(", inMainMemory=");
            buffer.append(inMainMemory);
            buffer.append(", virtualPage=");
            buffer.append(translationEntry.virtualPage);
            buffer.append(", physicalPage=");
            buffer.append(translationEntry.physicalPage);
            buffer.append(']');
            return buffer.toString();
        }
    }
    
} // class
