// FileHeader.jave
//	Routines for managing the disk file header (in UNIX, this
//	would be called the i-node).
//
//	The file header is used to locate where on disk the 
//	file's data is stored.  We implement this as a fixed size
//	table of pointers -- each entry in the table points to the 
//	disk sector containing that portion of the file data
//	(in other words, there are no indirect or doubly indirect 
//	blocks). The table size is chosen so that the file header
//	will be just big enough to fit in one disk sector, 
//
//      Unlike in a real system, we do not keep track of file permissions, 
//	ownership, last modification date, etc., in the file header. 
//
//	A file header can be initialized in two ways:
//	   for a new file, by modifying the in-memory data structure
//	     to point to the newly allocated data blocks
//	   for a file already on disk, by reading the file header from disk
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// All rights reserved.  See the COPYRIGHT file for copyright notice and 
// limitation of liability and disclaimer of warranty provisions.


//
//	A file header describes where on disk to find the data in a file,
//	along with other information about the file (for instance, its
//	length, owner, etc.)
//

// The following class defines the Nachos "file header" (in UNIX terms,  
// the "i-node"), describing where on disk to find all of the data in the file.
// The file header is organized as a simple table of pointers to
// data blocks. 
//
// The file header data structure can be stored in memory or on disk.
// When it is on disk, it is stored in a single sector -- this means
// that we assume the size of this data structure to be the same
// as one disk sector.  Without indirect addressing, this
// limits the maximum file length to just under 4K bytes.
//

import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;

// a file header will take one full sector of a disk
class FileHeader {
	
	// layout of the header (numbers on the left indicate the byte indexes)
	// 0 - 3:                      totalSize (in bytes)
	// 4 - 7:                      total number of sectors used
	// 8 - (Disk.SectorSize - 1):  references to sectors (levels 0, 1 and 2)
	
	// each of this references will be stored in an integer (32 bits)
	public static final int REF_SIZE = 4;
	// how many pointers we can keep per sector
    public static final int REFERENCES_IN_SECTOR = Disk.SectorSize / REF_SIZE;
	// bytes in the file
	private int totalSize;
	// total number of sectors used by this file
	private int numSectors;
	// the previous members also take space on the file header, therefore, we need
	// to also account the space they take!
	public static final int NUM_REFS_METADATA = 2;
	// number of references to blocks with level 0 indirection, that is,
	// keeping a reference to a block itself
	public static final int NUM_L0_REFS = 8;
	// number of references to blocks with level 1 indirection, that is,
	// keeping a reference to a block that itself keeps reference
	// to a block
	// assign the 75% remainder (or so) of the sector size
	// note that, sector size is given in bytes and we need to account the size of each reference (in bytes)
	public static final int NUM_L1_REFS = (int)((3.0/4.0) * (REFERENCES_IN_SECTOR - (NUM_L0_REFS + NUM_REFS_METADATA)));
	// number of references to blocks with level 2 indirection, that is,
	// keeping a reference to a block that itself keeps references to
	// another block that keeps references to blocks...
	// assign the 25% remainder (or so) of the sector size
	public static final int NUM_L2_REFS = REFERENCES_IN_SECTOR - (NUM_L1_REFS + NUM_L0_REFS + NUM_REFS_METADATA);
	// some useful derived constant
	// how many bytes our level 0 indirection can represent
	public static final int MAX_SIZE_L0 = (NUM_L0_REFS * Disk.SectorSize);
	// how many bytes our level 1 indirection can represent
	public static final int MAX_SIZE_L1 = (NUM_L1_REFS * Disk.SectorSize * REFERENCES_IN_SECTOR );
	// how many bytes our level 2 indirection can represent
	public static final int MAX_SIZE_L2 = (NUM_L2_REFS * Disk.SectorSize * Disk.SectorSize * Disk.SectorSize / (REF_SIZE * REF_SIZE));
	// max number of l1 blocks (data + reference)
	public static final int MAX_L1_SECTORS = NUM_L1_REFS + (NUM_L1_REFS * (REFERENCES_IN_SECTOR));
	// maximum size of a file (in bytes)
	public static final int MAX_FILE_SIZE = MAX_SIZE_L0 + MAX_SIZE_L1 + MAX_SIZE_L2;
	// maximum number of data sectors a file can have
	public static final int MAX_DATA_SECTORS = MAX_FILE_SIZE / Disk.SectorSize;
				
	// we can safely instantiate a fixed size array!
    private int dataSectors[] = new int[NUM_L0_REFS + NUM_L1_REFS + NUM_L2_REFS];
    
    // flag indicating wether this file has been deleted
    private boolean deleted;
    
    // sector where this header is located
    private int sector;
    
    // lock to use whenever we want to make a file bigger, because we are using the same freemap
    private final static Lock freeMapLock = new Lock("FreeMap");
    
    // on the creation, flag the file as empty and make all the references point to -1
    public FileHeader() {
    	totalSize = 0;
    	numSectors = 0;
    	for (int i = 0; i < dataSectors.length; i++) {
    		dataSectors[i] = -1;
    	}
    }
    
    public void setDeleted(boolean deleted){
        this.deleted = deleted;
    }
    public boolean isDeleted(){
            return deleted;
    }
    public int getSector() {
        return this.sector;
    }
    
    // initialize from a flat (on disk) representation
    public void fromDiskFormat(byte[] buffer, int pos) {
    	// get the total size
    	totalSize = Disk.intInt(buffer, pos);
    	pos += REF_SIZE;
    	// number of sectors used
    	numSectors = Disk.intInt(buffer, pos);
    	pos += REF_SIZE;
    	// and each of the references (advancing the position every time)
    	for (int i = 0; i < (NUM_L0_REFS + NUM_L1_REFS + NUM_L2_REFS); i++, pos += REF_SIZE) {
    		dataSectors[i] = Disk.intInt(buffer, pos);
    	}
    }

    // externalize to a flat (on disk) representation
    public void toDiskFormat(byte[] buffer, int pos) {
    	// write the total size
    	Disk.extInt(totalSize, buffer, pos);
    	pos += REF_SIZE;
    	// the number of sectors used
    	Disk.extInt(numSectors, buffer, pos);
    	pos += REF_SIZE;
    	// and each of the block references
    	for (int i = 0; i < dataSectors.length; i++, pos += REF_SIZE) {
    		Disk.extInt(dataSectors[i], buffer, pos);
    	}
    }
    
    public void extendSize(long numBytes) {
        this.totalSize += numBytes;
    }
    
    /**
     * Adds sectors to the file. Validates that the file won't exceed it's maximum size.
     * 
     * @param freeMap BitMap of the free sectors.
     * @param extraSectors Number of sectors to add.
     * @return true if success, false otherwise.
     */
    public boolean extend(BitMap freeMap, int extraSectors) {
        freeMapLock.acquire();
        // check if we are not going over the limit
        if ((fileLength() / Disk.SectorSize) + extraSectors > MAX_DATA_SECTORS) {
            Debug.printf('f', "[FileHeader.extend] Cannot add %d sectors. Max file size would be overpassed.\n", new Integer(extraSectors));
            freeMapLock.release();
            return false;
        }
        
        // check if there are enough sectors available
        if (freeMap.numClear() < extraSectors) {
            Debug.printf('f', "[FileHeader.extend] Cannot add %d sectors. Not enough space on disk.\n", new Integer(extraSectors));
            freeMapLock.release();
            return false;
        }
        
        // flag indicating if we should rollback
        boolean rollback = false;
        // the addSector method might add extra reference sectors... we need to keep track of those
        List referenceSectors = new LinkedList();
        
        // keep the ids of the new sectors that were allocated as data
        int[] dataSectorsId = new int[extraSectors];
        // and init the array
        for (int i = 0; i < dataSectorsId.length; i++) {
            dataSectorsId[i] = -1;
        }
        // now, allocate in the freemap
        for (int i = 0; i < dataSectorsId.length; i++) {
            int newDataSector = freeMap.find();
            if (newDataSector == -1) {
                Debug.println('f', "[FileHeader.extend] Cannot mark single sector in freemap. Not enough space on disk.");
                rollback = true;
                break;
            }
            dataSectorsId[i] = newDataSector;
        }
        
        // now, perform the allocation one by one
        if (!rollback) {
            for (int i = 0; i < dataSectorsId.length; i++) {
                if (!addSector(freeMap, dataSectorsId[i], referenceSectors)) {
                    Debug.printf('f', "[FileHeader.extend] Cannot add single sector %d. Not enough space on disk.\n", new Integer(dataSectorsId[i]));
                    rollback = true;
                    break;
                }
            }
        } 
        
        if (rollback) {
            // should we rollback?
            for (int i = 0; i < dataSectorsId.length; i++) {
                if (dataSectorsId[i] != -1) {
                    freeMap.clear(dataSectorsId[i]);
                }
            }
            for (Iterator it = referenceSectors.iterator(); it.hasNext(); ) {
                int toClear = ((Integer)it.next()).intValue();
                freeMap.clear(toClear);
            }
        }
        
        // modify the number of sectors
        this.numSectors += extraSectors + referenceSectors.size();
        
        // if something went wrong, rollback will be true
        freeMapLock.release();
        return (!rollback);
    }
    
    /**
     * Adds a single data sector to the header. Does not validate that the file won't exceed it's maximum size.
     * 
     * @param sector The id of the sector to add.
     * @param freeMap BitMap of the free sectors. Needed in case we need to add a reference sector (not data).
     * @param allocatedReferenceSectors ID of the sectors that were allocated in order to hold references.
     * @return true if success, false otherwise.
     */
    private boolean addSector(BitMap freeMap, int sector, List allocatedReferenceSectors) {
        Debug.printf('f', "[FileHeader.addSector] Adding data sector %d\n", new Integer(sector));
        // look where should we add the new sector
        int foundLocation = -1;
        int location = -1;
        for (int i = 0; i < dataSectors.length; i++) {
            if (dataSectors[i] == -1) {
                foundLocation = i;
                break;
            }
        }
        // check the last used location in our data sectors...
        if (foundLocation == -1) {
            // we could not find an empty spot, this means that the array is already populated and we must
            // use the last l2 indirection block
            // we will, from now on, work with the last block
            location = dataSectors.length - 1;
        } else {
            // now comes the tricky part... we have to check the last position relative to the found location
            // in order to determine if the last block still has some space (due to indirection)
            // however, in the case of the l0 indirection, we can directly use this new location!
            if (foundLocation < NUM_L0_REFS) {
                // it is l0, so we can safely use it
                dataSectors[foundLocation] = sector;
                Debug.printf('f', "[FileHeader.addSector] Added sector in L0, index %d\n", new Integer(foundLocation));
                return true;
            } else {
                // we will work with the previous index from now on, unless we are at the boundary between
                // l0 and l1
                if (foundLocation == NUM_L0_REFS) {
                    location = foundLocation;
                } else {
                    location = foundLocation - 1;
                }
            }
        }
        
        // we now have a location, our task is to determine if this location can hold a new data sector or
        // we must go to the next location in the array (that is, the location we were given is already full)
        // as usual, split the cases
        if (dataSectors[location] != -1) {
            if (location < (NUM_L0_REFS + NUM_L1_REFS)) {
                // this is l1 indirection
                // fetch from disk the reference sector
                byte[] levelOneReferencesRaw = new byte[Disk.SectorSize];
                Nachos.synchDisk.readSector(dataSectors[location], levelOneReferencesRaw, 0);            
                // check if there are free locations
                for (int i = 0; i < REFERENCES_IN_SECTOR; i++) {
                    int dataBlockSector = Disk.intInt(levelOneReferencesRaw, i * REF_SIZE);
                    if (dataBlockSector == -1) {
                        // we found an available reference in this block! we don't need to add a reference block
                        // now, write back to the disk this info
                        Disk.extInt(sector, levelOneReferencesRaw, i * REF_SIZE);
                        Nachos.synchDisk.writeSector(dataSectors[location], levelOneReferencesRaw, 0);
                        Debug.printf('f', "[FileHeader.addSector] Modified L1 block in sector %d\n", new Integer(dataSectors[location]));
                        return true;
                    }
                }
                // so, we could not find enough space in the l1 reference block... that means, we have to take the
                // next one
                location++;            
            } else {
                // this is l2 indirection
                // fetch from disk the l2 reference sector
                byte[] levelTwoReferencesRaw = new byte[Disk.SectorSize];
                Nachos.synchDisk.readSector(dataSectors[location], levelTwoReferencesRaw, 0);
                // now we have to check at level one...
                for (int i = 0; i < REFERENCES_IN_SECTOR; i++) {
                    // read the address of the l1 sector
                    int levelOneReferenceBlock = Disk.intInt(levelTwoReferencesRaw, i * REF_SIZE);
                    if (levelOneReferenceBlock == -1) {
                        // if this spot is empty, and we are here, it means that we have not been able to find space in the 
                        // previous slots, therefore, we have to add the proper l1 references
                        levelOneReferenceBlock = freeMap.find();
                        if (levelOneReferenceBlock == -1) {
                            // could not add it!
                            Debug.printf('f', "[FileHeader.addSector] Cannot add reference sector for %d. Not enough space on disk.\n", new Integer(sector));
                            return false;
                        }
                        // add it to the list of sectors we added                        
                        allocatedReferenceSectors.add(new Integer(levelOneReferenceBlock));
                        // modify the l2 block
                        Disk.extInt(levelOneReferenceBlock, levelTwoReferencesRaw, i * REF_SIZE);
                        Nachos.synchDisk.writeSector(dataSectors[location], levelTwoReferencesRaw, 0);
                        // we will now write to disk the l1 reference block
                        byte[] levelOneReferencesRaw = new byte[Disk.SectorSize];
                        for (int j = 0; j < REFERENCES_IN_SECTOR; j++) {
                            Disk.extInt(-1, levelOneReferencesRaw, j * REF_SIZE);
                        }
                        // it is a new block, therefore, should be referenced in the first position
                        Disk.extInt(sector, levelOneReferencesRaw, 0);
                        Nachos.synchDisk.writeSector(levelOneReferenceBlock, levelOneReferencesRaw, 0);
                        Debug.printf('f', "[FileHeader.addSector] Modified L2 block in sector %d\n", new Integer(dataSectors[location]));
                        Debug.printf('f', "[FileHeader.addSector] Created L1 ref block in sector %d\n", new Integer(levelOneReferenceBlock));
                        // we are done
                        return true;
                    }
                    // so, it is not an empty spot, we have to check each of the references in the l1 block to look for an empty one
                    // points to a l1 reference sector... we have to read that sector and check if there are free slots
                    byte[] levelOneReferencesRaw = new byte[Disk.SectorSize];
                    Nachos.synchDisk.readSector(levelOneReferenceBlock, levelOneReferencesRaw, 0);
                    for (int j = 0; j < REFERENCES_IN_SECTOR; j++) {
                        int dataBlock = Disk.intInt(levelOneReferencesRaw, j * REF_SIZE);
                        if (dataBlock == -1) {
                            // this means that we found an empty slot...
                            Disk.extInt(sector, levelOneReferencesRaw, j * REF_SIZE);
                            Nachos.synchDisk.writeSector(levelOneReferenceBlock, levelOneReferencesRaw, 0);
                            Debug.printf('f', "[FileHeader.addSector] Modified L1 block in sector %d\n", new Integer(levelOneReferenceBlock));
                            return true;
                        }
                    }
                }
                // we could not find an empty spot... go to the next location
                location++;
            }
        }
        
        // if we're still here it means that the first suggested block was already full, or that we got inmediatly
        // an empty block in the datasectors array... 
        // we have to add this from the dataSectors array level, so this should be fairly easy
        if (location < (NUM_L0_REFS + NUM_L1_REFS)) {
            // level one...
            // allocate the l1 block
            byte[] levelOneReferencesRaw = new byte[Disk.SectorSize];
            for (int i = 0; i < REFERENCES_IN_SECTOR; i++) {
                Disk.extInt(-1, levelOneReferencesRaw, i * REF_SIZE);
            }
            int levelOneReferenceBlock = freeMap.find();
            if (levelOneReferenceBlock == -1) {
                Debug.printf('f', "[FileHeader.addSector] Cannot add reference sector for %d. Not enough space on disk.\n", new Integer(sector));
                return false;
            }
            allocatedReferenceSectors.add(new Integer(levelOneReferenceBlock));
            // add the address of the data sector, since it is new, add it in the first slot
            Disk.extInt(sector, levelOneReferencesRaw, 0);
            dataSectors[location] = levelOneReferenceBlock;
            Nachos.synchDisk.writeSector(dataSectors[location], levelOneReferencesRaw, 0);
            Debug.printf('f', "[FileHeader.addSector] Created L1 block in sector %d\n", new Integer(levelOneReferenceBlock));
            Debug.printf('f', "[FileHeader.addSector] Modified L1 index %d\n", new Integer(location));
        } else {
            // new level two
            // allocate a l2 and l1 blocks
            byte [] levelTwoReferencesRaw = new byte[Disk.SectorSize];
            byte [] levelOneReferencesRaw = new byte[Disk.SectorSize];
            for (int i = 0; i < REFERENCES_IN_SECTOR; i++) {
                Disk.extInt(-1, levelTwoReferencesRaw, i * REF_SIZE);
                Disk.extInt(-1, levelOneReferencesRaw, i * REF_SIZE);
            }
            // find a free block for them
            int levelTwoReferenceBlock = freeMap.find();
            if (levelTwoReferenceBlock == -1) {
                Debug.printf('f', "[FileHeader.addSector] Cannot add reference sector for %d. Not enough space on disk.\n", new Integer(sector));
                return false;
            }
            allocatedReferenceSectors.add(new Integer(levelTwoReferenceBlock));
            int levelOneReferenceBlock = freeMap.find();
            if (levelOneReferenceBlock == -1) {
                Debug.printf('f', "[FileHeader.addSector] Cannot add reference sector for %d. Not enough space on disk.\n", new Integer(sector));
                return false;
            }
            allocatedReferenceSectors.add(new Integer(levelOneReferenceBlock));
            // set the proper pointers
            dataSectors[location] = levelTwoReferenceBlock;
            Disk.extInt(levelOneReferenceBlock, levelTwoReferencesRaw, 0);
            Disk.extInt(sector, levelOneReferencesRaw, 0);
            // and write back to disk
            Nachos.synchDisk.writeSector(levelTwoReferenceBlock, levelTwoReferencesRaw, 0);
            Nachos.synchDisk.writeSector(levelOneReferenceBlock, levelOneReferencesRaw, 0);
            Debug.printf('f', "[FileHeader.addSector] Created L2 block in sector %d\n", new Integer(levelTwoReferenceBlock));
            Debug.printf('f', "[FileHeader.addSector] Created L1 block in sector %d\n", new Integer(levelOneReferenceBlock));
            Debug.printf('f', "[FileHeader.addSector] Modified L2 index %d\n", new Integer(location));
        }
        
        return true;
    }
    
    //----------------------------------------------------------------------
    // allocate
    // 	Initialize a fresh file header for a newly created file.
    //	Allocate data blocks for the file out of the map of free disk blocks.
    //	Return FALSE if there are not enough free blocks to accomodate
    //	the new file.
    //
    //	"freeMap" is the bit map of free disk sectors
    //	"fileSize" is size of the new file
    //----------------------------------------------------------------------
    public boolean allocate(BitMap freeMap, int fileSize) { 
    	totalSize = fileSize;
    	int newSectors = fileSize / Disk.SectorSize;
        if (fileSize % Disk.SectorSize != 0) {
            newSectors++;
        }
    	return extend(freeMap, newSectors);
    }

    //----------------------------------------------------------------------
    // deallocate
    // 	De-allocate all the space allocated for data blocks for this file.
    //
    //	"freeMap" is the bit map of free disk sectors
    //----------------------------------------------------------------------
    public void deallocate(BitMap freeMap) {
        // we need to keep track of how many sectors we have deallocated
        freeMapLock.acquire();
        int totalDeallocatedSectors = 0;
        
        // start with the level 0 sectors
        for (int i = 0; i < NUM_L0_REFS; i++, totalDeallocatedSectors++) {
            // is this pointing to something at all?
            if (dataSectors[i] == -1) {
                break;
            }
            Debug.ASSERT(freeMap.test(dataSectors[i]));  // ought to be marked!
            freeMap.clear(dataSectors[i]);
        }
        // level 1
        for (int i = NUM_L0_REFS; i < (NUM_L0_REFS + NUM_L1_REFS); i++, totalDeallocatedSectors++) {
            // is this pointing to something at all?
            if (dataSectors[i] == -1) {
                break;
            }
            // unmark the data sector first
            // we need to read from disk the sector containing the references
            byte[] levelOneReferencesRaw = new byte[Disk.SectorSize];
            Nachos.synchDisk.readSector(dataSectors[i], levelOneReferencesRaw, 0);
            for (int j = 0; totalDeallocatedSectors < numSectors && j < REFERENCES_IN_SECTOR; j++, totalDeallocatedSectors++) {
                int levelOneDataSector = Disk.intInt(levelOneReferencesRaw, j * REF_SIZE);
                // is this pointing to something at all?
                if (levelOneDataSector == -1) {
                    break;
                }
                Debug.ASSERT(freeMap.test(levelOneDataSector));  // ought to be marked!
                freeMap.clear(levelOneDataSector);
            }
            
            // now, unmark the reference sector
            Debug.ASSERT(freeMap.test(dataSectors[i]));  // ought to be marked!
            freeMap.clear(dataSectors[i]);
        }
        // level 2
        for (int i = NUM_L0_REFS + NUM_L1_REFS; totalDeallocatedSectors < numSectors; i++, totalDeallocatedSectors++) {
            // is this pointing to something at all?
            if (dataSectors[i] == -1) {
                break;
            }
            // get the l2 reference sector
            byte[] levelTwoReferencesRaw = new byte[Disk.SectorSize];           
            Nachos.synchDisk.readSector(dataSectors[i], levelTwoReferencesRaw, 0);
            for (int j = 0; j < REFERENCES_IN_SECTOR; j++, totalDeallocatedSectors++) {
                // get the pointer to the sector storing l1 references
                int levelOneReferenceSector = Disk.intInt(levelTwoReferencesRaw, j * REF_SIZE);
                if (levelOneReferenceSector == -1) {
                    break;
                }
                // now, get to the data sectors
                byte[] levelOneReferencesRaw = new byte[Disk.SectorSize];
                Nachos.synchDisk.readSector(levelOneReferenceSector, levelOneReferencesRaw, 0);
                for (int k = 0; k < REFERENCES_IN_SECTOR; k++, totalDeallocatedSectors++) {
                    int levelTwoDataSector = Disk.intInt(levelOneReferencesRaw, k * REF_SIZE);
                    if (levelTwoDataSector == -1) {
                        break;
                    }
                    Debug.ASSERT(freeMap.test(levelTwoDataSector));
                    freeMap.clear(levelTwoDataSector);
                }
                // clear the l1 reference sector
                Debug.ASSERT(freeMap.test(levelOneReferenceSector));
                freeMap.clear(levelOneReferenceSector);
            }
            
            // unmark the l2 reference sector
            Debug.ASSERT(freeMap.test(dataSectors[i]));  // ought to be marked!
            freeMap.clear(dataSectors[i]);
        }
        
        freeMapLock.release();
        // make sure we deallocated all we needed to
        Debug.ASSERT(totalDeallocatedSectors == numSectors);
    	
    }

    //----------------------------------------------------------------------
    // fetchFrom
    // 	Fetch contents of file header from disk. 
    //
    //	"sector" is the disk sector containing the file header
    //----------------------------------------------------------------------

    public void fetchFrom(int sector) {
        this.sector = sector;
    	byte buffer[] = new byte[Disk.SectorSize];
    	// read sector
    	Nachos.synchDisk.readSector(sector, buffer, 0);
    	// unmarshall
    	fromDiskFormat(buffer, 0);
    }


    //----------------------------------------------------------------------
    // writeBack
    // 	Write the modified contents of the file header back to disk. 
    //
    //	"sector" is the disk sector to contain the file header
    //----------------------------------------------------------------------
    public void writeBack(int sector) {
    	byte buffer[] = new byte[Disk.SectorSize];
    	// marshall
    	toDiskFormat(buffer, 0);
    	// write sector
    	Nachos.synchDisk.writeSector(sector, buffer, 0); 
    }

    //----------------------------------------------------------------------
    // byteToSector
    // 	Return which disk sector is storing a particular byte within the file.
    //      This is essentially a translation from a virtual address (the
    //	offset in the file) to a physical address (the sector where the
    //	data at the offset is stored).
    //
    //	"offset" is the location within the file of the byte in question
    //----------------------------------------------------------------------
    public int byteToSector(int offset) {
        int sector = -1;
        // first, check if it is enough for the position to be in l0 indirection
        if (offset < MAX_SIZE_L0) {
            // yes! happy path!
            sector = dataSectors[offset / Disk.SectorSize];
        } else if (offset < (MAX_SIZE_L0 + MAX_SIZE_L1)) {
            // level 1 indirection
            // check how many bytes we went over the limit
            offset = (offset - MAX_SIZE_L0);
            // with this many bytes, we can say the index of the data block (l1) in which the byte is stored
            int dataBlockIndex = offset / Disk.SectorSize;
            // since this is l1 indirection, we need to know which reference block index this byte has
            int levelOneReferenceIndex = dataBlockIndex / REFERENCES_IN_SECTOR;
            // get the number of the sector where this reference is stored
            int levelOneReferenceSector = dataSectors[levelOneReferenceIndex + NUM_L0_REFS];
            // fetch the sector from disk
            byte[] levelOneReferenceRaw = new byte[Disk.SectorSize];
            Nachos.synchDisk.readSector(levelOneReferenceSector, levelOneReferenceRaw, 0);
            // now, inside the sector we read, we need to know the offset and fetch that reference number
            sector = Disk.intInt(levelOneReferenceRaw, (dataBlockIndex % REFERENCES_IN_SECTOR) * REF_SIZE);
        } else if (offset < (MAX_SIZE_L0 + MAX_SIZE_L1 + MAX_SIZE_L2)) {
            // level 2 indirection
            // check how many bytes we went over the limit
            offset = (offset - MAX_SIZE_L0 - MAX_SIZE_L1);
            // check the index of the data block
            int dataBlockIndex = offset / Disk.SectorSize;
            // check the l1 indirection index
            int levelOneReferenceIndex = dataBlockIndex / REFERENCES_IN_SECTOR;
            // check the l2 indirection index
            int levelTwoReferenceIndex = levelOneReferenceIndex / REFERENCES_IN_SECTOR;
            // get the number of the sector where the l2 reference is
            int levelTwoReferenceSector = dataSectors[levelTwoReferenceIndex + NUM_L0_REFS + NUM_L1_REFS];
            // read that sector
            byte[] sectorBuffer = new byte[Disk.SectorSize];
            Nachos.synchDisk.readSector(levelTwoReferenceSector, sectorBuffer, 0);
            // get the reference to the level one indirection sector
            int levelOneReferenceSector = Disk.intInt(sectorBuffer, (levelOneReferenceIndex % REFERENCES_IN_SECTOR) * REF_SIZE);
            // and get the sector where the data finally is
            Nachos.synchDisk.readSector(levelOneReferenceSector, sectorBuffer, 0);
            sector = Disk.intInt(sectorBuffer, (dataBlockIndex % REFERENCES_IN_SECTOR) * REF_SIZE);
        } else {
            // the offset is just too big to fit even in the biggest file possible...
            Debug.printf('f', "[FileHeader.byteToSector] Impossible to determine sector for byte %d... It exceeds maximum file size!\n", new Integer(offset));
        }
    	
        return sector;
    }

    //----------------------------------------------------------------------
    // fileLength
    // 	Return the number of bytes in the file.
    //----------------------------------------------------------------------
    public int fileLength() {
    	return totalSize;
    }

    //----------------------------------------------------------------------
    // print
    // 	Print the contents of the file header, and the contents of all
    //	the data blocks pointed to by the file header.
    //----------------------------------------------------------------------
    public void print() {
        if (!Debug.isEnabled('f')) {
            return;
        }
        Debug.printf('f', "[FileHeader.print] Printing file with size %d, numSectors %d.\n", new Integer(totalSize), new Integer(numSectors));
        // keep track of how many bytes we have read
        int totalBytesRead = 0;
        // buffer to read a sector
        byte[] sectorBuffer = new byte[Disk.SectorSize];
        // first, level 0
        for (int i = 0; i < NUM_L0_REFS; i++) {
            if (dataSectors[i] == -1) {
                break;
            }
            // read the sector into the buffer
            Nachos.synchDisk.readSector(dataSectors[i], sectorBuffer, 0);
            // print it
            totalBytesRead += printBuffer(sectorBuffer, totalBytesRead, totalSize);
        }
        // now level 1
        for (int i = NUM_L0_REFS; i < (NUM_L0_REFS + NUM_L1_REFS); i++) {
            if (dataSectors[i] == -1) {
                break;
            }
            // get the sector of references
            byte[] levelOneReferencesRaw = new byte[Disk.SectorSize];
            Nachos.synchDisk.readSector(dataSectors[i], levelOneReferencesRaw, 0);
            // and now, get the data
            for (int j = 0; j < REFERENCES_IN_SECTOR; j++) {
                int dataSector = Disk.intInt(levelOneReferencesRaw, j * REF_SIZE);
                if (dataSector == -1) {
                    break;
                }
                // read the sector of data and print it
                Nachos.synchDisk.readSector(dataSector, sectorBuffer, 0);
                totalBytesRead += printBuffer(sectorBuffer, totalBytesRead, totalSize);
            }
        }
        // level 2
        for (int i = (NUM_L0_REFS + NUM_L1_REFS); i < dataSectors.length; i++) {
            if (dataSectors[i] == -1) {
                break;
            }
            // get the level 2 sector of references
            byte[] levelTwoReferencesRaw = new byte[Disk.SectorSize];
            byte[] levelOneReferencesRaw = new byte[Disk.SectorSize];
            Nachos.synchDisk.readSector(dataSectors[i], levelTwoReferencesRaw, 0);
            // traverse the sector to look for l1 references
            for (int j = 0; j < REFERENCES_IN_SECTOR; j++) {
                int levelOneReferenceSector = Disk.intInt(levelTwoReferencesRaw, j * REF_SIZE);
                if (levelOneReferenceSector == -1) {
                    break;
                }
                // read the references to the data sectors
                Nachos.synchDisk.readSector(levelOneReferenceSector, levelOneReferencesRaw, 0);
                // and traverse the data blocks
                for (int k = 0; k < REFERENCES_IN_SECTOR; k++) {
                    int dataSector = Disk.intInt(levelOneReferencesRaw, k * REF_SIZE);
                    if (dataSector == -1) {
                        break;
                    }
                    // read the data sector and print it
                    Nachos.synchDisk.readSector(dataSector, sectorBuffer, 0);
                    totalBytesRead += printBuffer(sectorBuffer, totalBytesRead, totalSize);
                }
            }
        }
        // check that it all went fine
        Debug.ASSERT(totalBytesRead == totalSize);
        // print a new line
        if (totalSize > 0) {
            Debug.println('f', "");
        }
    }
    
    /**
     * Prints a buffer, keeping track of how many bytes should be printed alltogether.
     * 
     * @param buffer Buffer to print.
     * @param sizeSoFar So far, how many bytes have been printed.
     * @param totalSize Total size of bytes to print.
     * 
     * @return How many bytes were printed.
     */
    private int printBuffer(byte[] buffer, int sizeSoFar, int totalSize) {
        int bytesPrinted = 0;
        StringBuilder builder = new StringBuilder(buffer.length);
        for (int i = 0; i < (totalSize - sizeSoFar) && i < buffer.length; i++, bytesPrinted++) {
            builder.append((char)buffer[i]);
        }
        if (bytesPrinted > 0) {
            Debug.print('f', builder.toString());
        }
        return bytesPrinted;
    }

}
