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

    // page table entry
    private final PageTableEntry[] pageTable = new PageTableEntry[Machine.MemorySize / Machine.PageSize];
    
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
    public int translateAddress(int processId, int virtualAddress) {
        // obtain the virtualPageNumber and offset
        int virtualPageNumber = virtualAddress / Machine.PageSize;
        int offset = virtualAddress % Machine.PageSize;
        
        // get the entry from the page table
        PageTableEntry entry = getEntry(processId, virtualPageNumber);
        Debug.ASSERT(entry != null, "[PageTable.translateAddress] Page missing!!!");
        
        //TODO: WHAT IF THE PAGE IS ACTUALLY ON HARD DISK???
        
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
    public int translateAddress(int processId, int virtualAddress, int previousVirtualAddress, int previousPhysicalAddress) {
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
     * Removes the entries for the current process.
     */
    public void removeCurrentProcess() {
        // get the process id
        int processId = NachosThread.thisThread().getSpaceId();
        // and the number of virtual pages this uses
        int numVirtualPages = NachosThread.thisThread().getNumVirtualPages();
        
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
                        } else {
                            // it is the first one, but there are more to come, just update pointers
                            // to make the next entry the first one in the chain
                            pageTable[index.intValue()] = current.nextPageTableEntry;
                        }
                    }

                    // flag in the memory management that this frame is free
                    MemoryManagement.instance.deallocatePage(current.translationEntry.physicalPage);
                    
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
            pageTable[entry.translationEntry.physicalPage] = entry;
            
            // and make sure that it is not pointing to anybody else
            entry.nextPageTableEntry = null;
            
            // also, store this info in the anchor table
            anchorTable.put(key, new Integer(entry.translationEntry.physicalPage));
            
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
     * Allocates a new process in memory.
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

        Debug.ASSERT((numPages <= Machine.NumPhysPages),// check we're not trying
        "[PageTable.allocateNewProcess]: Not enough memory!");
        // to run anything too big --
        // at least until we have
        // virtual memory
        
        // check we have enough free pages
        if (!MemoryManagement.instance.enoughPages(numPages)) {
            // no harm done... just throw an exception
            throw new NachosException("[PageTable.allocateNewProcess] Not enough free pages!");
        }

        Debug.println('a', "[PageTable.allocateNewProcess] Initializing address space, numPages=" 
                + numPages + ", size=" + size);

        // first, set up the translation 
        //pageTable = new TranslationEntry[numPages];
        for (int i = 0; i < numPages; i++) {
            PageTableEntry entry = new PageTableEntry(processId);
            entry.translationEntry.virtualPage = i; 
            int physicalPage = MemoryManagement.instance.allocatePage();
            // before even getting the address space set-up, we should've checked that there
            // was enough memory, so, this should not create any problems
            Debug.ASSERT(physicalPage != -1, "[PageTable.allocateNewProcess] There are not enough pages available!!!");
            entry.translationEntry.physicalPage = physicalPage;
            entry.translationEntry.valid = true;
            entry.translationEntry.use = false;
            entry.translationEntry.dirty = false;
            entry.translationEntry.readOnly = false;  // if the code segment was entirely on 
            // a separate page, we could set its 
            // pages to be read-only
            
            // the entry has been created, set it on the page table
            setEntry(processId, i, entry);
        }

        // zero out the entire address space, to zero the unitialized data 
        // segment and the stack segment
        for (int i = 0; i < numPages; i++) {
            PageTableEntry entry = getEntry(processId, i);
            for (int j = entry.translationEntry.physicalPage * Machine.PageSize, 
                     n = (1 + entry.translationEntry.physicalPage) * Machine.PageSize; 
                 j < n; j++) {
                Machine.mainMemory[j] = 0;
            }
        }

        // then, copy in the code and data segments into memory
        // now, rather than do it in one chunk, we need to do this page by page...
        // we cannot assume anymore that our pages will be adjacent
        if (noffH.code.size > 0) {
            Debug.println('a', "[PageTable.allocateNewProcess] Initializing code segment, at " +
                    noffH.code.virtualAddr + ", size " +
                    noffH.code.size);

            copyToMainMemory(
                    executable, 
                    noffH.code.inFileAddr, 
                    (int)noffH.code.size, 
                    processId, 
                    (int)noffH.code.virtualAddr / Machine.PageSize, 
                    (int)noffH.code.virtualAddr % Machine.PageSize);

        }
      
        if (noffH.initData.size > 0) {
            Debug.println('a', "[PageTable.allocateNewProcess] Initializing data segment, at " +
                    noffH.initData.virtualAddr + ", size " +
                    noffH.initData.size);

            copyToMainMemory(
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
     * Copies <code>size</code> bits from <code>executable</code> file into main Memory starting at the file offset given by <code>fileOffset</code>
     * using this inverted page table, offset by <code>virtualPageOffset</code> and <code>bitOffset</code>.
     * 
     * @param executable Executable file to read from.
     * @param fileOffset File offset to which we will start to read.
     * @param size Number of bits to write to main memory.
     * @param processId Process id that is being loaded into memory.
     * @param virtualPageOffset Which will be the first translation entry to use.
     * @param bitOffset How many bits from the beginning of a page we want to offset.
     * 
     * @throws IOException if something goes wrong
     */
    void copyToMainMemory(RandomAccessFile executable, long fileOffset, int size, int processId, int virtualPageOffset, int bitOffset) 
        throws IOException {
        // first off, see how many "full" pages we need to copy from the file to memory
        int fullPages = size / Machine.PageSize;
        int remainderBits = (size % Machine.PageSize);
        
        // move the pointer in the file
        executable.seek(fileOffset);
        
        // start copying full pages
        for (int i = virtualPageOffset, n = virtualPageOffset + fullPages; i < n; i++) {
            // first, get the page entry for this desired virtual page
            PageTableEntry entry = getEntry(processId, i);
            Debug.printf('a', "[PageTable.copyToMainMemory] Copying code/data (full page) to physical page [%d] with [%d] bits offset.\n", 
                              new Long(entry.translationEntry.physicalPage), new Long(bitOffset));
            int read = executable.read(Machine.mainMemory, (entry.translationEntry.physicalPage * Machine.PageSize) + bitOffset, Machine.PageSize);
            Debug.ASSERT(read == Machine.PageSize, "[PageTable.copyToMainMemory] Could not read the entire page from file!!!");
        }
        
        // now, the remainder bits
        if (remainderBits > 0) {
            PageTableEntry entry = getEntry(processId, virtualPageOffset + fullPages);
            // now, we can write the remaining bits
            Debug.printf('a', "[PageTable.copyToMainMemory] Copying code/data (%d bits) to physical page [%d] with [%d] offset inside the page.\n", 
                              new Object[] {new Long(remainderBits), new Long(entry.translationEntry.physicalPage), new Long(bitOffset)});
            int read = executable.read(Machine.mainMemory, (entry.translationEntry.physicalPage * Machine.PageSize)+ bitOffset, remainderBits);
            Debug.ASSERT(read == remainderBits, "[PageTable.copyToMainMemory] Could not read completely from file!!!");
        }
        
    }
    
    private String buildKey(int processId, long virtualPageNumber) {
        return (processId + "|" + virtualPageNumber);
    }
    
    static class PageTableEntry {
        // process owining this page
        int processId;
        // if not -1, it means that this page is resident in disk
        int pageOffsetInDisk;
        // reuse-reuse-REUSE!!!
        TranslationEntry translationEntry;
        // pointer to the next entry
        PageTableEntry nextPageTableEntry;
        
        public PageTableEntry(int processId) {
            this.processId = processId;
            pageOffsetInDisk = -1;
            translationEntry = new TranslationEntry();
            nextPageTableEntry = null;
        }
    }
    
} // class
