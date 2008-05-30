// AddrSpace.java
//	Class to manage address spaces (executing user programs).
//
//	In order to run a user program, you must:
//
//	1. link with the -N -T 0 option 
//	2. run coff2noff to convert the object file to Nachos format
//		(Nachos object code format is essentially just a simpler
//		version of the UNIX executable object code format)
//	3. load the NOFF file into the Nachos file system
//		(if you haven't implemented the file system yet, you
//		don't need to do this last step)
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

import java.io.*;
import java.util.Hashtable;
import java.util.Map;


class AddrSpace {

  static final int UserStackSize = 1024; // increase this as necessary!
  static final int MaxOpenFiles = 30;
  

  TranslationEntry pageTable[];
  int numPages;
  String executablePath;

  Map openFiles;
  int nextID = 2;
  
  //----------------------------------------------------------------------
  // 	Create an address space to run a user program.
  //	Load the program from a file "executable", and set everything
  //	up so that we can start executing user instructions.
  //
  //	Assumes that the object code file is in NOFF format.
  //
  //	First, set up the translation from program memory to physical 
  //	memory.  For now, this is really simple (1:1), since we are
  //	only uniprogramming, and we have a single unsegmented page table
  //
  //	"executable" is the file containing the object code to 
  //    load into memory
  //    
  //    throws NachosException if not enough pages available
  //----------------------------------------------------------------------

  public AddrSpace(RandomAccessFile executable) throws IOException, NachosException {
      NoffHeader noffH;
      long size;

      noffH = new NoffHeader(executable);

      // how big is address space?
      size = noffH.code.size + noffH.initData.size + noffH.uninitData.size 
      + UserStackSize;	// we need to increase the size
      // to leave room for the stack
      numPages = (int)(size / Machine.PageSize);
      if (size % Machine.PageSize > 0) numPages++;

      size = numPages * Machine.PageSize;

      Debug.ASSERT((numPages <= Machine.NumPhysPages),// check we're not trying
      "AddrSpace constructor: Not enough memory!");
      // to run anything too big --
      // at least until we have
      // virtual memory
      
      // check we have enough free pages
      if (!MemoryManagement.instance.enoughPages(numPages)) {
          // no harm done... just throw an exception
          throw new NachosException("Not enough free pages!");
      }

      Debug.println('a', "Initializing address space, numPages=" 
              + numPages + ", size=" + size);

      // first, set up the translation 
      pageTable = new TranslationEntry[numPages];
      for (int i = 0; i < numPages; i++) {
          pageTable[i] = new TranslationEntry();
          pageTable[i].virtualPage = i; 
          int physicalPage = MemoryManagement.instance.allocatePage();
          // before even getting the address space set-up, we should've checked that there
          // was enough memory, so, this should not create any problems
          Debug.ASSERT(physicalPage != -1, "[AddrSpace.ctor] There are not enough pages available!!!");
          pageTable[i].physicalPage = physicalPage;
          pageTable[i].valid = true;
          pageTable[i].use = false;
          pageTable[i].dirty = false;
          pageTable[i].readOnly = false;  // if the code segment was entirely on 
          // a separate page, we could set its 
          // pages to be read-only
      }

      // zero out the entire address space, to zero the unitialized data 
      // segment and the stack segment
      for (int i = 0; i < numPages; i++) {
          for (int j = pageTable[i].physicalPage * Machine.PageSize, 
                   n = (1 + pageTable[i].physicalPage) * Machine.PageSize; 
               j < n; j++) {
              Machine.mainMemory[j] = 0;
          }
      }

      // then, copy in the code and data segments into memory
      // now, rather than do it in one chunk, we need to do this page by page...
      // we cannot assume anymore that our pages will be adjacent
      if (noffH.code.size > 0) {
          Debug.println('a', "Initializing code segment, at " +
                  noffH.code.virtualAddr + ", size " +
                  noffH.code.size);

          copyToMainMemory(
                  executable, 
                  noffH.code.inFileAddr, 
                  (int)noffH.code.size, 
                  pageTable, 
                  (int)noffH.code.virtualAddr / Machine.PageSize, 
                  (int)noffH.code.virtualAddr % Machine.PageSize);

          //executable.seek(noffH.code.inFileAddr);
          //executable.read(Machine.mainMemory, (int)noffH.code.virtualAddr, 
          //        (int)noffH.code.size);
      }
    
    //initialize structures for open files
    openFiles = new Hashtable();
    nextID = 2;

      if (noffH.initData.size > 0) {
          Debug.println('a', "Initializing data segment, at " +
                  noffH.initData.virtualAddr + ", size " +
                  noffH.initData.size);

          copyToMainMemory(
                  executable, 
                  noffH.initData.inFileAddr, 
                  (int)noffH.initData.size, 
                  pageTable, 
                  (int)noffH.initData.virtualAddr / Machine.PageSize,
                  (int)noffH.initData.virtualAddr % Machine.PageSize);
          //executable.seek(noffH.initData.inFileAddr);
          //executable.read(Machine.mainMemory, (int)noffH.initData.virtualAddr, 
          //        (int)noffH.initData.size);
      }
    
  }
  
  public int getNumPages() {
      return this.numPages;
  }
  
  /**
   * Copies <code>size</code> bits from <code>executable</code> file into main Memory starting at the file offset given by <code>fileOffset</code>
   * using the translation entries given by <code>pageTable</code>, offset by <code>bitOffset</code>.
   * 
   * @param executable Executable file to read from.
   * @param fileOffset File offset to which we will start to read.
   * @param size Number of bits to write to main memory.
   * @param pageTable Translation entries to use.
   * @param pageTableOffset Which will be the first translation entry to use.
   * @param bitOffset How many bits from the beginning of a page we want to offset.
   * 
   * @throws IOException if something goes wrong
   */
  private void copyToMainMemory(RandomAccessFile executable, long fileOffset, int size, TranslationEntry[] pageTable, int pageTableOffset, int bitOffset) 
      throws IOException {
      // first off, see how many "full" pages we need to copy from the file to memory
      int fullPages = size / Machine.PageSize;
      int remainderBits = (size % Machine.PageSize);
      
      // move the pointer in the file
      executable.seek(fileOffset);
      
      // start copying full pages
      for (int i = pageTableOffset, n = pageTableOffset + fullPages; i < n; i++) {
          Debug.printf('a', "[AddrSpace.copyToMainMemory] Copying code/data (full page) to physical page [%d] with [%d] bits offset.\n", 
                            new Long(pageTable[i].physicalPage), new Long(bitOffset));
          int read = executable.read(Machine.mainMemory, (pageTable[i].physicalPage * Machine.PageSize) + bitOffset, Machine.PageSize);
          Debug.ASSERT(read == Machine.PageSize, "[AddrSpace.copyToMainMemory] Could not read the entire page from file!!!");
      }
      
      // now, the remainder bits
      if (remainderBits > 0) {
          // now, we can write the remaining bits
          Debug.printf('a', "[AddrSpace.copyToMainMemory] Copying code/data (%d bits) to physical page [%d] with [%d] offset inside the page.\n", 
                            new Object[] {new Long(remainderBits), new Long(pageTable[pageTableOffset + fullPages].physicalPage), new Long(bitOffset)});
          int read = executable.read(Machine.mainMemory, (pageTable[pageTableOffset + fullPages].physicalPage * Machine.PageSize)+ bitOffset, remainderBits);
          Debug.ASSERT(read == remainderBits, "[AddrSpace.copyToMainMemory] Could not read completely from file!!!");
      }
      
  }
  
  // returns the path of the executable file that this address space corresponds to
  public String getExecutablePath() {
      return this.executablePath;
  }
  
  // sets the executable path
  public void setExecutablePath(String executablePath) {
      this.executablePath = executablePath;
  }


  //----------------------------------------------------------------------
  // InitRegisters
  // 	Set the initial values for the user-level register set.
  //
  // 	We write these directly into the "machine" registers, so
  //	that we can immediately jump to user code.  Note that these
  //	will be saved/restored into the currentThread->userRegisters
  //	when this thread is context switched out.
  //----------------------------------------------------------------------

    void initRegisters() {
        int i;

        for (i = 0; i < Machine.NumTotalRegs; i++)
            Machine.writeRegister(i, 0);

        // Initial program counter -- must be location of "Start"
        Machine.writeRegister(Machine.PCReg, 0);	

        // Need to also tell MIPS where next instruction is, because
        // of branch delay possibility
        Machine.writeRegister(Machine.NextPCReg, 4);

        // Set the stack register to the end of the address space, where we
        // allocated the stack; but subtract off a bit, to make sure we don't
        // accidentally reference off the end!
        //Machine.writeRegister(Machine.StackReg, (pageTable[numPages - 1].physicalPage + 1) * Machine.PageSize - 16);
        //Debug.printf('a', "Initializing stack register to [%d].\n", new Long((pageTable[numPages - 1].physicalPage + 1) * Machine.PageSize - 16));
        Machine.writeRegister(Machine.StackReg, numPages * Machine.PageSize - 16);
        Debug.println('a', "Initializing stack register to " + (numPages * Machine.PageSize - 16));
  }

  //----------------------------------------------------------------------
  // SaveState
  // 	On a context switch, save any machine state, specific
  //	to this address space, that needs saving.
  //
  //	For now, nothing!
  //----------------------------------------------------------------------

  void saveState() {}

  //----------------------------------------------------------------------
  // RestoreState
  // 	On a context switch, restore the machine state so that
  //	this address space can run.
  //
  //      For now, tell the machine where to find the page table.
  //----------------------------------------------------------------------

  void restoreState() {
    Machine.pageTable = pageTable;
    Machine.pageTableSize = numPages;
  }
  

	/**
	 * Read string from user space
	 * 
	 * @param vaddr
	 * @param buffer
	 * @return size of read string
	 */

	String UserSpaceStringToKernel(int vaddr) {
        StringBuffer strBuffer = new StringBuffer();
		int length = 0;
		int car = 0;

		Debug.printf('+', "Reading user string to kernel, starting virtual address: %s\n", ("" + vaddr));
        
        try {
            // read the first byte
            car = Machine.readMem(vaddr, 1);
        
    		// read until null character
    		while (length < Nachos.MaxStringSize && car != '\0') {
                // append to our buffer
                strBuffer.append((char)car);
    			vaddr++;
    			length++;
                car = Machine.readMem(vaddr, 1);
    
    		}
        }
        catch (MachineException e) {
            // Ups! Machine translation failed!
            e.printStackTrace();
        }

		if (length >= Nachos.MaxStringSize) {
			// we should not have read anything at all, it went over the limit!
            return "";
		} 

		return strBuffer.toString();
	}

	/**
	 * Read data from kernel and write in address space
	 * 
	 * @param vaddr
	 * @param length
	 * @param buf
	 * @return size of written buffer
	 */
	int KernelSpaceToUserBuffer(int vaddr, int length, byte[] buf) {
		int i;

		Debug.printf('+', "Writing buffer to user address space, starting virtual address %d, length %d bytes.\n", new Integer(vaddr), new Integer(length));
		for (i = 0; i < length; i++) {

			if (Machine.writeMem(vaddr, 1, buf[i]) == false) {
				return -1;
			}

			vaddr++;

		}
		return length;
	}
	

	/**
	 * 
	 * @param vaddr
	 * @param length
	 * @param buf
	 * @return length of buffer written
	 */
	int UserBufToKernelSpace(int vaddr, int length, byte[] buf) {
		int car = -1;
		int i;

		Debug.printf(
						'+',
						"Reading buffer from user address space, starting virtual address %d, length %d bytes.\n",
						new Integer(vaddr), new Integer(length));
		for (i = 0; i < length; i++) {
			try {
				car = Machine.readMem(vaddr, 1);
			} catch (MachineException e) {
				// Ups! Machine translation failed!
				e.printStackTrace();
			}
			buf[i] = (byte) car;
			vaddr++;

		}
		return length;
	}



/**
 * Add a new file to the list of opened files and return an ID
 */
int generateOpenFileId(OpenFileStub file)
{
        
    // insert file to open file's table and return ID
    
  
    if (nextID == MaxOpenFiles)
    {
       	Debug.println('+', "Open file table is full");
        return -1;
    }
    else
    {
            Debug.ASSERT(nextID < MaxOpenFiles);
            nextID++;
            openFiles.put(new Integer(nextID), file);
            Debug.printf('+', "Adding to open table of files, file: %s", ("" + nextID));  
            
                                      
    }
    return nextID;
}


/**
 * 
 * @param fileId
 * @return the removed file
 */
OpenFileStub deleteOpenFile(int fileId)
{
	OpenFileStub tmp = null;
    
    //check that the fileId is valid
    if (fileId < 2 || fileId >= MaxOpenFiles)
    {
          Debug.println('+', "Invalid file id");
          
    }
    else
    {
            tmp = (OpenFileStub)openFiles.get(new Integer(fileId));
            openFiles.remove(new Integer(fileId));
    }
    return tmp;
}

/**
 * 
 */
OpenFileStub getOpenFile(int fileId)
{
    OpenFileStub file = null;
    
    if (fileId < 2 || fileId >= MaxOpenFiles)
    {
    	Debug.println('+', "Invalid file id");
    }
    else
    {
            file = (OpenFileStub)openFiles.get(new Integer(fileId));
    }
    return file;
}




}
