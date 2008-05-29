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
  //----------------------------------------------------------------------

  public AddrSpace(RandomAccessFile executable) throws IOException {

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

    Debug.println('a', "Initializing address space, numPages=" 
		+ numPages + ", size=" + size);

    // first, set up the translation 
    pageTable = new TranslationEntry[numPages];
    for (int i = 0; i < numPages; i++) {
      pageTable[i] = new TranslationEntry();
      pageTable[i].virtualPage = i; // for now, virtual page# = phys page#
      pageTable[i].physicalPage = i;
      pageTable[i].valid = true;
      pageTable[i].use = false;
      pageTable[i].dirty = false;
      pageTable[i].readOnly = false;  // if the code segment was entirely on 
					// a separate page, we could set its 
					// pages to be read-only
    }
    
    // zero out the entire address space, to zero the unitialized data 
    // segment and the stack segment
    // ????? bzero(machine->mainMemory, size);

    // then, copy in the code and data segments into memory
    if (noffH.code.size > 0) {
      Debug.println('a', "Initializing code segment, at " +
	    noffH.code.virtualAddr + ", size " +
	    noffH.code.size);

      executable.seek(noffH.code.inFileAddr);
      executable.read(Machine.mainMemory, (int)noffH.code.virtualAddr, 
		      (int)noffH.code.size);
    }
    
    //initialize structures for open files
    openFiles = new Hashtable();
    nextID = 2;

    if (noffH.initData.size > 0) {
      Debug.println('a', "Initializing data segment, at " +
	    noffH.initData.virtualAddr + ", size " +
	    noffH.initData.size);

      executable.seek(noffH.initData.inFileAddr);
      executable.read(Machine.mainMemory, (int)noffH.initData.virtualAddr, 
		      (int)noffH.initData.size);
    }
    
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
    Machine.writeRegister(Machine.StackReg, 
			  numPages * Machine.PageSize - 16);
    Debug.println('a', "Initializing stack register to " +
		(numPages * Machine.PageSize - 16));
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

	int UserSpaceStringToKernel(int vaddr, char[] buffer) {
		int length = 0;
		int car = 0;
		int retVal = 0;

		Debug
				.printf(
						'+',
						"Reading user string to kernel, starting virtual address: %s\n",
						vaddr);
		// read until null character
		while (length < Nachos.MaxStringSize) {
			try {
				car = Machine.readMem(vaddr, 1);
			} catch (MachineException e) {
				// Ups! Machine translation failed!
				e.printStackTrace();
			}

			buffer[length] = (char) car;
			vaddr++;
			length++;
			// null terminated string
			if ('\0' == car) {
				break;
			}

		}

		if (length >= Nachos.MaxStringSize) {
			retVal = -1;
		} else {
			retVal = length;
		}
		return retVal;
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

		Debug
				.printf(
						'+',
						"Writing buffer to user address space, starting virtual address %d, length %d bytes",
						vaddr, length);
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

		Debug
				.printf(
						'+',
						"Reading buffer from user address space, starting virtual address %d, length %d bytes",
						vaddr, length);
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
            openFiles.put(new Integer(nextID), file);
            Debug.printf('+', "Adding to open table of files, file: %s", nextID);  
            nextID++;
                                      
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
