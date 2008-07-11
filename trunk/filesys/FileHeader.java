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

class FileHeader {
	public static final int NumDirect = ((Disk.SectorSize - 2 * 4) / 4);
	public static final int MaxFileSize = (NumDirect * Disk.SectorSize);

	private int numBytes; // Number of bytes in the file
	private int numSectors; // Number of data sectors in the file
	private int dataSectors[]; // Disk sector numbers for each data 
	// block in the file
	private int L1Array[];
	private int L2Array[];
	private int L2block[];
	//We have three different levels of indirection:
	//no indirection (direct blocks, level 0 - L0)
	//single indirection (level 1 - L1)
	//double indirection (level 2 - L2)

	public static final int INDIR_BLOCKS = Disk.SectorSize / 4;
	public static final int MAX_FILE_SIZE = (NumDirect + INDIR_BLOCKS + (INDIR_BLOCKS * INDIR_BLOCKS))
			* Disk.SectorSize;
	public static final boolean IS_DIR = false;
	public static final boolean IS_FILE = false;
	private int inDir1 = -1;
	private int inDir2 = -1;//indirection variables
	int lastLsector, lastPsector;

	private int fileSize;
	private int flag;//0 for file, 1 for directory
	private int blocks;// size of file in blocks
	private int hdrSector;
	private boolean deleted;
	private int busyCount;
	private boolean dirty;

	FileHeader(int sector) {
		hdrSector = sector;
		deleted = false;
		busyCount = 0;
		Debug.ASSERT(hdrSector != -1);

		fetchFrom(sector);
		Debug.ASSERT(dirty == false);
		lastLsector = -1;
		lastPsector = -1;
	}

	public FileHeader() {
		dataSectors = new int[NumDirect];
		lastLsector = -1;
		lastPsector = -1;
	}
	
	public void setDeleted(boolean deleted){
		this.deleted = deleted;
	}
	public boolean isDeleted(){
		return deleted;
	}

	// the following methods deal with conversion between the on-disk and
	// the in-memory representation of a DirectoryEnry.
	// Note: these methods must be modified if any instance variables 
	// are added!!

	// return size of flat (on disk) representation
	public static int sizeOf() {
		return 4 + 4 + 4 * NumDirect;
	}

	// initialize from a flat (on disk) representation
	public void internalize(byte[] buffer, int pos) {
		numBytes = Disk.intInt(buffer, pos);
		numSectors = Disk.intInt(buffer, pos + 4);
		for (int i = 0; i < NumDirect; i++)
			dataSectors[i] = Disk.intInt(buffer, pos + 8 + i * 4);
	}

	// externalize to a flat (on disk) representation
	public void externalize(byte[] buffer, int pos) {
		Disk.extInt(numBytes, buffer, pos);
		Disk.extInt(numSectors, buffer, pos + 4);
		for (int i = 0; i < NumDirect; i++)
			Disk.extInt(dataSectors[i], buffer, pos + 8 + i * 4);
	}

	// Map a sector for a write starting with a given offset
	int MapSectorWrite(int lOffset) {

		Debug.ASSERT(lOffset > 0);
		Debug.ASSERT(lOffset <= MAX_FILE_SIZE);

		int currentLsector, lSector;
		// current sector we are working with
		currentLsector = lSector = (lOffset - 1) / Disk.SectorSize;

		if (lSector == lastLsector && lastPsector != -1) {
			if (lOffset > fileSize) {
				fileSize = lOffset;
				dirty = true;
			}
			return (lastPsector);
		}

		// L0 indirection.
		// --------------------------------------------------------

		if (lSector < NumDirect) {
			// check whether data block exists. if not, we must allocate it.
			if (dataSectors[lSector] == -1) {
				dataSectors[lSector] = AllocateSector(true);
				if (dataSectors[lSector] == -1)
					return -1;
				dirty = true;
			}

			if (lOffset > fileSize) {
				fileSize = lOffset;
				dirty = true;
			}

			lastLsector = currentLsector;
			lastPsector = dataSectors[lSector];
			return (dataSectors[lSector]);
		}
		lSector -= NumDirect;

		// L1 indirection.
		// --------------------------------------------------------

		if (lSector < INDIR_BLOCKS) {
			// check whether L1 array exists. if not, we must create one.
			if (inDir1 == -1) {
				inDir1 = AllocateSector(false);
				if (inDir1 == -1)
					return -1;
				dirty = true;
			}

			Debug.ASSERT(inDir1 >= 0 && inDir1 < numSectors);

			// check whether data block exists. if not, we must allocate it.

			L1Array = new int[NumDirect];
			int dataSector = -1;
			if (dataSector == -1) {
				dataSector = AllocateSector(true);
				if (dataSector == -1) {

					return -1;
				}
				L1Array[lSector] = dataSector;
				dirty = true;
			}

			if (lOffset > fileSize) {
				fileSize = lOffset;
				dirty = true;
			}

			lastLsector = currentLsector;
			lastPsector = dataSector;

			return (dataSector);
		}
		lSector -= INDIR_BLOCKS;

		// L2 indirection.
		// --------------------------------------------------------
		Debug.ASSERT(lSector < INDIR_BLOCKS * INDIR_BLOCKS);

		// check whether L2 array exists. if not, we must create one.
		if (inDir2 == -1) {
			inDir2 = AllocateSector(false);
			if (inDir2 == -1)
				return -1;
			dirty = false;
		}

		// read L2 array, and check for L2 block existence. we must create it
		// if it doesn't exist.
		Debug.ASSERT(inDir2 >= 0 && inDir2 < numSectors);

		L2Array = new int[NumDirect];
		int L2blockSector = L2Array[lSector / INDIR_BLOCKS];
		if (L2blockSector == -1) {
			L2blockSector = AllocateSector(false);
			if (L2blockSector == -1) {
				return -1;
			}
			L2Array[lSector / INDIR_BLOCKS] = L2blockSector;

			dirty = true;
		}

		// read L2 block, and check for data existence. we must create it
		// if it doesn't exist.
		Debug.ASSERT(L2blockSector >= 0 && L2blockSector < numSectors);

		L2block = new int[NumDirect];
		int dataSector = L2block[lSector % INDIR_BLOCKS];
		if (dataSector == -1) {
			dataSector = AllocateSector(true);
			if (dataSector == -1) {
				return -1;
			}
			L2block[lSector % INDIR_BLOCKS] = dataSector;
			dirty = true;
		}
		if (lOffset > fileSize) {
			fileSize = lOffset;
			dirty = true;
		}

		lastLsector = currentLsector;
		lastPsector = dataSector;

		return (dataSector);
	}

	// allocate a sector of data or pointers
	int AllocateSector(boolean isData)
	{
  
  
    int sector = -1;
    if (isData){ // data block - zero fill it.
        
    }
    else { // pointer block - fill it with -1's.
	for (int i = 0; i < Disk.SectorSize/4; i++){
	    // buf[i] = -1;
    }
    }
   
    blocks++;
    dirty = true;
    return sector;
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
		numBytes = fileSize;
		numSectors = fileSize / Disk.SectorSize;
		if (fileSize % Disk.SectorSize != 0)
			numSectors++;

		if (freeMap.numClear() < numSectors || NumDirect < numSectors)
			return false; // not enough space

		for (int i = 0; i < numSectors; i++)
			dataSectors[i] = freeMap.find();
		return true;
	}

	//----------------------------------------------------------------------
	// deallocate
	// 	De-allocate all the space allocated for data blocks for this file.
	//
	//	"freeMap" is the bit map of free disk sectors
	//----------------------------------------------------------------------

	public void deallocate(BitMap freeMap) {
		for (int i = 0; i < numSectors; i++) {
			Debug.ASSERT(freeMap.test(dataSectors[i])); // ought to be marked!
			freeMap.clear(dataSectors[i]);
		}
	}

	//----------------------------------------------------------------------
	// fetchFrom
	// 	Fetch contents of file header from disk. 
	//
	//	"sector" is the disk sector containing the file header
	//----------------------------------------------------------------------

	public void fetchFrom(int sector) {
		byte buffer[] = new byte[Disk.SectorSize];
		// read sector
		Nachos.synchDisk.readSector(sector, buffer, 0);
		// unmarshall
		internalize(buffer, 0);
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
		externalize(buffer, 0);
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
		return (dataSectors[offset / Disk.SectorSize]);
	}

	//----------------------------------------------------------------------
	// fileLength
	// 	Return the number of bytes in the file.
	//----------------------------------------------------------------------

	public int fileLength() {
		return numBytes;
	}

	//----------------------------------------------------------------------
	// print
	// 	Print the contents of the file header, and the contents of all
	//	the data blocks pointed to by the file header.
	//----------------------------------------------------------------------

	public void print() {
		int i, j, k;
		byte data[] = new byte[Disk.SectorSize];

		Debug.printf('+',
				"FileHeader contents.  File size: %d. File blocks:\n",
				new Integer(numBytes));
		for (i = 0; i < numSectors; i++)
			Debug.printf('+', "%d ", new Integer(dataSectors[i]));

		Debug.print('+', "\nFile contents:\n");
		for (i = k = 0; i < numSectors; i++) {
			Nachos.synchDisk.readSector(dataSectors[i], data, 0);
			for (j = 0; (j < Disk.SectorSize) && (k < numBytes); j++, k++) {
				if ('\040' <= data[j] && data[j] <= '\176') // isprint(data[j])
					Debug.printf('+', "%c", new Integer(data[j]));
				else
					Debug.printf('+', "\\%x", new Integer(
							((int) data[j]) & 0xff));
			}
			Debug.print('+', "\n");
		}
	}

}
