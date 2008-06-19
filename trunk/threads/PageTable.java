import java.util.Map;
import java.util.HashMap;
/**
 * Basic implementation of an inverted page table.
 */
public class PageTable {
    // internal anchorTable
    // key: <processId, virtualPageNumber>
    // value: <physicalPageNumber>
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
     * Returns a translation entry for a given process and virtual page number.
     * 
     * @param processId Process ID.
     * @param virtualPageNumber Page Number
     * 
     * @return The given translation entry, <code>null</code> if none found.
     */
    public TranslationEntry getEntry(int processId, long virtualPageNumber) {
        //return (TranslationEntry)table.get(processId + "|" + virtualPageNumber);
        return null;
    }
    
    /**
     * Sets an entry on the table.
     * 
     * @param processId Process id.
     * @param virtualPageNumber Page Number
     * @param entry Entry to set.
     */
    public void setEntry(int processId, long virtualPageNumber, TranslationEntry entry) {
        //table.put(processId + "|" + virtualPageNumber, entry);
    }
    
    private static class PageTableEntry {
        int processId;
        TranslationEntry entry;
        PageTableEntry nextPageTableEntry;
    }
    
} // class
