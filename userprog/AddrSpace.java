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
  
  TranslationEntry pageTable[];
  int numPages;
  String executablePath;

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
      Debug.ASSERT(false, "ADDRESS SPACE SHOULD NOT BE USED ANYMORE... BAD BOY!");
      /* WE DONT USE THIS CODE ANYMORE!!!
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
      if (!MemoryManagement.getInstance().enoughPages(numPages)) {
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
    */
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
  static void copyToMainMemory(RandomAccessFile executable, long fileOffset, int size, TranslationEntry[] pageTable, int pageTableOffset, int bitOffset) 
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
    //Machine.pageTable = pageTable;
    //Machine.pageTableSize = numPages;
  }
  

	/**
	 * Read string from user space
	 * 
	 * @param vaddr
	 * @param buffer
	 * @return size of read string
	 */






}
