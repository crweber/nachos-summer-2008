// Nachos.java
//	Bootstrap code to initialize the operating system kernel.
//
//	Allows direct calls into internal operating system functions,
//	to simplify debugging and testing.  In practice, the
//	bootstrap code would just initialize data structures,
//	and start a user program to print the login prompt.
//
// 	Most of this file is not needed until later assignments.
//
// Usage: nachos -d <debugflags> -rs <random seed #>
//		-s -x <nachos file> -c <consoleIn> <consoleOut>
//		-f -cp <unix file> <nachos file>
//		-p <nachos file> -r <nachos file> -l -D -t
//              -n <network reliability> -m <machine id>
//              -o <other machine id>
//              -z
//
//    -d causes certain debugging messages to be printed (cf. utility.h)
//    -rs causes Yield to occur at random (but repeatable) spots
//    -z prints the copyright message
//
//  USER_PROGRAM
//    -s causes user programs to be executed in single-step mode
//    -x runs a user program
//    -c tests the console
//
//  FILESYS
//    -f causes the physical disk to be formatted
//    -cp copies a file from UNIX to Nachos
//    -p prints a Nachos file to stdout
//    -r removes a Nachos file from the file system
//    -l lists the contents of the Nachos directory
//    -D prints the contents of the entire file system 
//    -t tests the performance of the Nachos file system
//
//  NETWORK
//    -n sets the network reliability
//    -m sets this machine's host id (needed for the network)
//    -o runs a simple test of the Nachos network software
//
//  NOTE -- flags are ignored until the relevant assignment.
//  Some of the flags are interpreted here; some in system.cc.
//
// Copyright (c) 1992-1993 The Regents of the University of
// California.  Copyright (c) 1998 Rice University.  All rights
// reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

// The Nachos kernel object

class Nachos implements Runnable {

	private static final String copyright = "Copyright (c) 1992-1993 The Regents of the University of California.  Copyright (c) 1998-1999 Rice University. All rights reserved.";
    
    // process id
    private static int P_ID = 1;

	// constants that control the Nachos configuration

	public static final boolean USER_PROGRAM = true;
	public static final boolean FILESYS = true;
	public static final boolean FILESYS_STUB = false;
	public static final boolean FILESYS_NEEDED = true;
	public static final boolean NETWORK = false;
	public static final boolean THREADS = true;

	// system call codes -- used by the stubs to tell the kernel
	// which system call is being asked for

    // stop the machine
	public static final byte SC_Halt = 0;
    // a process just finishes
	public static final byte SC_Exit = 1;
    // start a new process
	public static final byte SC_Exec = 2;
    // ?????
	public static final byte SC_Join = 3;
    // creates a new file
	public static final byte SC_Create = 4;
    // opens a file
	public static final byte SC_Open = 5;
    // reads a file
	public static final byte SC_Read = 6;
    // write to a file
	public static final byte SC_Write = 7;
    // close a file
	public static final byte SC_Close = 8;
    // fork thread
	public static final byte SC_Fork = 9;
    // yield thread
	public static final byte SC_Yield = 10;
    // delete a file?
	public static final byte SC_Remove = 11;

	public static Statistics stats;
	public static Timer timer = null;
	public static FileSystem fileSystem;
	public static SynchDisk synchDisk;
	private static String args[];
	public static Random random;
	public static final int MaxStringSize = 256;
    
    private static final Lock exceptionHandlerLock = new Lock("ExceptionHandlerLock");

	static {
		stats = new Statistics(); // collect statistics
		random = new Random();
	}

	// ----------------------------------------------------------------------
	// run
	// Initialize Nachos global data structures. Interpret command
	// line arguments in order to determine flags for the initialization.
	// 
	// Check command line arguments
	// Initialize objects
	// (optionally) Call test procedure
	//
	// "argc" is the number of command line arguments (including the name
	// of the command) -- ex: "nachos -d +" -> argc = 3
	// "argv" is an array of strings, one for each command line argument
	// ex: "nachos -d +" -> argv = {"nachos", "-d", "+"}
	// ----------------------------------------------------------------------

	public void run() {

		String debugArgs = "";
		boolean format = false; // format disk
		boolean randomYield = false;
		double rely = 1; // network reliability
		int netname = 0; // UNIX socket name

		for (int i = 0; i < args.length; i++) {
			// System.out.println(args[i] + i);
			if (args[i].equals("-d"))
				if (i < args.length - 1)
					debugArgs = args[++i];
				else
					debugArgs = "+";

			if (args[i].equals("-rs")) {
				Debug.ASSERT((i < args.length - 1), "usage: -rs <seed>");
				long seed = Long.parseLong(args[++i]);
				random.setSeed(seed); // initialize pseudo-random
				// number generator
				randomYield = true;
			}

			if (args[i].equals("-s"))
				Machine.enableDebugging();

			if (args[i].equals("-f"))
				format = true;

		}

		// System.out.println(debugArgs);

		Debug.init(debugArgs); // initialize DEBUG messages

		if (randomYield) { // start the timer (if needed)
			timer = new Timer(new TimerInterruptHandler(), randomYield);
		} else {
            // if not random, we still need a timer to implement time slicing
		    timer = new Timer(new TimerInterruptHandler(), false, false);
        }
        

		if (FILESYS)
			synchDisk = new SynchDisk("DISK");

		if (FILESYS_NEEDED) {
			if (FILESYS_STUB)
				fileSystem = new FileSystemStub(format);
			else
				fileSystem = new FileSystemReal(format);
		}

		// if (THREADS)
		// ThreadTest.start();

		for (int i = 0; i < args.length; i++) {
			// System.out.println(args[i] + i);
			if (args[i].equals("-z")) // print copyright
				System.out.println(copyright);
			if (USER_PROGRAM) {
				if (args[i].equals("-x")) { // run a user program
					Debug.ASSERT((i < args.length - 1), "usage: -x <filename>");
					ProgTest testObj = new ProgTest(args[++i]);
				}

				if (args[i].equals("-c")) { // test the console
					if (i < args.length - 2) {
						ConsoleTest.run(args[i + 1], args[i + 2]);
						i += 2;
					} else {
						ConsoleTest.run(null, null);
					}

					// once we start the console, then
					// Nachos will loop forever waiting
					// for console input
					Interrupt.halt();
				}
			}

			if (FILESYS) {
				if (args[i].equals("-cp")) { // copy from UNIX to Nachos
					Debug.ASSERT((i < args.length - 2),
							"usage: -cp <filename1> <filename2>");
					FileSystemTest.copy(args[i + 1], args[i + 2]);
					i += 2;
				}
				if (args[i].equals("-p")) { // print a Nachos file
					Debug.ASSERT(i < args.length - 1, "usage: -p <filename>");
					FileSystemTest.print(args[++i]);
				}
				if (args[i].equals("-r")) { // remove Nachos file
					Debug.ASSERT(i < args.length - 1);
					fileSystem.remove(args[++i]);
				}
				if (args[i].equals("-l")) { // list Nachos directory
					((FileSystemReal) fileSystem).list();
				}
				if (args[i].equals("-D")) { // print entire filesystem
					((FileSystemReal) fileSystem).print();
				}
				if (args[i].equals("-t")) { // performance test
					FileSystemTest.performanceTest();
				}

			}
			
			//call the Whales algorithm
			if (args[i].equals("-whales")){
				
				Debug.ASSERT(args[i+1]!= null && args[i+2] != null);
                NachosThread nachosThread = new NachosThread("MainWhaleThread");
           		nachosThread.fork(new Whale(Integer.parseInt(args[i+1]), Integer.parseInt(args[i+2])));
			}
			
			if (args[i].equals("-boats")){
				
				Debug.ASSERT(args[i+1]!= null && args[i+2] != null);
				CannibalMissionary canMan = new CannibalMissionary();
				canMan.init(Integer.parseInt(args[i+1]), Integer.parseInt(args[i+2]));
				
			}
            
            if (args[i].equals("-bb")) {
                // make sure we are provided enough arguments
                Debug.ASSERT(args[i + 1] != null && args[i + 2] != null);
                
                // init our class
                ProducerConsumer pc = new ProducerConsumer();
                pc.initialize(Integer.parseInt(args[i + 1]), Integer.parseInt(args[i + 2]));
                
                // start a NachosThread with our class
                NachosThread nachosThread = new NachosThread("ProducerConsumer");
                nachosThread.fork(pc);
            }
            
            if (args[i].equals("-prof")) {
                // make sure we are provided with enough arguments
                Debug.ASSERT(args[i + 1] != null, "Number of students is required after -prof");
                
                // parse the optional argument, # of questions per student
                int numberOfQuestionsAllowed = -1;
                try {
                    numberOfQuestionsAllowed = Integer.parseInt(args[i + 2]);
                }
                catch (Exception e) {
                    // doesn't matter at all
                }
                
                // init the class
                ProfessorStudent ps = new ProfessorStudent(Integer.parseInt(args[i + 1]), numberOfQuestionsAllowed);
                
                // start a nachos thread with the professor/student class
                NachosThread nachosThread = new NachosThread("ProfessorStudent");
                nachosThread.fork(ps);
            }

		}

		

	}

	// ----------------------------------------------------------------------
	// Cleanup
	// Nachos is halting.
	// ----------------------------------------------------------------------
	public static void cleanup() {

		System.out.println("\nCleaning up...\n");
		System.exit(0);
	}

	// ----------------------------------------------------------------------
	// exceptionHandler
	// Entry point into the Nachos kernel. Called when a user program
	// is executing, and either does a syscall, or generates an addressing
	// or arithmetic exception.
	//
	// For system calls, the following is the calling convention:
	//
	// system call code -- r2
	// arg1 -- r4
	// arg2 -- r5
	// arg3 -- r6
	// arg4 -- r7
	//
	// The result of the system call, if any, must be put back into r2.
	//
	// And don't forget to increment the pc before returning. (Or else you'll
	// loop making the same system call forever!
	//
	// "which" is the kind of exception. The list of possible exceptions
	// are in Machine.java
	// ----------------------------------------------------------------------

	public static void exceptionHandler(int which) {
		int type = Machine.readRegister(2);

        // we want to perform an exception handling routine in an atomic way
        //exceptionHandlerLock.acquire();
        
		if (which == Machine.SyscallException) {

			switch (type) {
			case SC_Halt:
				Halt();
				break;
			case SC_Exit:
				Exit(Machine.readRegister(4));
				break;
			case SC_Exec:
                // pointer to the name of the file to execute
                String execName = PageTable.getInstance().getStringFromUserSpace(Machine.readRegister(4));
				int newProcessId = Exec(execName);
                Machine.writeRegister(2, newProcessId);
				break;
            case SC_Join:
                int result = Join(Machine.readRegister(4));
                Machine.writeRegister(2, result);
                break;
			case SC_Write:
				
				Debug.println('+', "Write initiated by user");
                // first parameter, vaddr of the buffer to write from
                int vaddrWrite = Machine.readRegister(4);
                // how many bytes we want to write
                int sizeWrite = Machine.readRegister(5);
                // file descriptor
				int fileIdWrite = Machine.readRegister(6);
				
                // write data from user to kernel space
				byte[] bufferWrite = PageTable.getInstance().copyFromUserSpace(vaddrWrite, sizeWrite);
				
                if (bufferWrite.length != sizeWrite)
                {
                    Debug.println('+', "Could not copy data from user address space");
                }
                
                //write data from buffer
                int retValWrite = Write(bufferWrite, sizeWrite, fileIdWrite);
                
                // write result
                Machine.writeRegister(2, retValWrite);
                
				break;
			case SC_Read:
				
				Debug.println('+', "Read initiated by user");
                // first parameter, vaddr of the buffer to write from
                int vaddrRead = Machine.readRegister(4);
                // how many bytes we want to write
                int sizeRead = Machine.readRegister(5);
                // file descriptor
                int fileIdRead = Machine.readRegister(6);
                
				byte[] bufferRead = new byte[sizeRead];
				
				//read data into buffer
				int retValRead = Read(bufferRead, sizeRead, fileIdRead);
				// write data from kernel to user address space
                if (PageTable.getInstance().copyFromKernel(bufferRead, vaddrRead) != retValRead)
                {
                    Debug.println('+', "Could not copy data to user address space");
                }
                
                // write result
                Machine.writeRegister(2, retValRead);
                
				break;
                
            case SC_Create:

				Debug.println('+', "Create initiated by user");
				// read address from first argument
				int vaddr = Machine.readRegister(4);

				// read the name into buffer
				String fileCreate = PageTable.getInstance().getStringFromUserSpace(vaddr);

                // return value
                int createRetValue = 0;
                
				// create the file
				if (fileCreate.length() != 0) {
                    createRetValue = Create(fileCreate) ? 1 : 0;
				}
                
                // write it
                Machine.writeRegister(2, createRetValue);

				break;
			case SC_Open:

				Debug.println('+', "Open initiated by user");
				// read address from first argument
				int vaddrOpen = Machine.readRegister(4);

				// read the name into buffer
				String fileOpen = PageTable.getInstance().getStringFromUserSpace(vaddrOpen);
                
                // return value for Open
                int openRetValue = -1;

				// open the file
				if (fileOpen.length() != 0) {
					openRetValue = Open(fileOpen);
				}
                
                // write it to register
                Machine.writeRegister(2, openRetValue);

				break;
			case SC_Remove:

				Debug.println('+', "Remove initiated by user");
				// read address from first argument
				int vaddrRemove = Machine.readRegister(4);

				// read the name into buffer
				String fileRemove = PageTable.getInstance().getStringFromUserSpace(vaddrRemove);

                // return value for Remove
                int removeRetVal = 0;
                
				// remove the file
				if (fileRemove.length() != 0) {
					removeRetVal = Remove(fileRemove) ? 1 : 0;
				}
                
                // write it
                Machine.writeRegister(2, removeRetVal);
                
				break;
				
			case SC_Close:

				Debug.println('+', "Close initiated by user");
				// read address from first argument
				int fileId = Machine.readRegister(4);
				// call method
				Close(fileId);
				
				break;
			}
            
        	Machine.registers[Machine.PrevPCReg] = Machine.registers[Machine.PCReg];
			Machine.registers[Machine.PCReg] = Machine.registers[Machine.NextPCReg];
			Machine.registers[Machine.NextPCReg] += 4;
            
            // release the lock
            //exceptionHandlerLock.release();
            
			return;
		}
        if (which == Machine.PageFaultException) {
            // on page faults, we don't need to increment the program counter registers
            // first, get the address that caused this exception
            int virtualAddress = Machine.registers[Machine.BadVAddrReg];
            
            // now, just invoke the page controller, which will make it all right
            PageController.getInstance().handlePageFault(virtualAddress);
            
            return;
        }

		System.out.println("Unexpected user mode exception " + which + ", "
				+ type);
        //exceptionHandlerLock.release();
		Debug.ASSERT(false);

	}
    
	// ----------------------------------------------------------------------
	// main
	// Bootstrap the operating system kernel.
	//	
	// "clArgs" is an array of strings, one for each command line argument
	// ex: "-d +" -> argv = {"-d", "+"}
	// ----------------------------------------------------------------------

	public static void main(String clArgs[]) {
		NachosThread t;

		Debug.println('t', "Entering main");

		// we are in the context of a Java thread, not a Nachos Thread.
		// all we can do is to create the first NachosThread and take it
		// from there.

		args = clArgs;
		t = new NachosThread("First Thread");

		// make the thread execute Nachos.run()
		t.fork(new Nachos());

		// start the Nachos thread system
		Scheduler.start();
	}

	// ---------------------------------------------------------------------
	// Nachos system call interface. These are Nachos kernel operations
	// that can be invoked from user programs, by trapping to the kernel
	// via the "syscall" instruction.
	// ---------------------------------------------------------------------

	/* Stop Nachos, and print out performance stats */
	public static void Halt() {
		Debug.print('+', "Shutdown, initiated by user program.\n");
        NachosThread.thisThread().incrementCpuTicks(
                Nachos.stats.totalTicks - 
                NachosThread.thisThread().getTicksSinceLastScheduled());
        NachosThread.thisThread().setTicksAtExit(Nachos.stats.totalTicks);
        ThreadInstrumentation.addElement(NachosThread.thisThread());
		Interrupt.halt();
	}

	/* Address space control operations: Exit, Exec, and Join */

	/* This user program is done (status = 0 means exited normally). */
	public static void Exit(int status) {
		Debug.printf('x', "[Nachos.Exit] User program [%s] exits with status [%d].\n", NachosThread.thisThread().getName(), new Long(status));
        NachosThread.thisThread().setReturnCode(status);
		NachosThread.thisThread().finish();
	}

	/*
	 * Run the executable, stored in the Nachos file "name", and return the
	 * address space identifier
	 */
	public static int Exec(String name) {
        RandomAccessFile executable;
        
        // first off, we have to check if the executable is given by a full or relative path
        name = getFullExecutablePath(name, NachosThread.thisThread().getExecutableLocation());
        
        try {
          executable = new RandomAccessFile(name, "r");
        }
        catch (IOException e) {
          Debug.println('+', "[Nachos.Exec] Unable to open executable file: " + name);
          return -1;
        }
        
        // get the new process id
        int newId = P_ID++;
        int numPages = 0;

        try {
            numPages = PageTable.getInstance().allocateNewProcess(executable, newId);
        }
        catch (IOException e) {
            Debug.println('+', "[Nachos.Exec] Unable to read executable file: " + name);
            return -1;
        }
        catch (NachosException ne) {
            // not enough free pages
            Debug.printf('+', "[Nachos.Exec] Could not find enough pages to allocate process [%s]\n", name);
            return -1;
        }
        
        NachosThread newProcess = new NachosThread(name);
        newProcess.setExecutablePath(name);
        newProcess.setNumVirtualPages(numPages);
        
        Debug.printf('x', "[Nachos.Exec] Scheduling process [%s] with pid [%d].\n", name, new Long(newId));
        newProcess.setSpaceId(newId);
        
        // inform this thread that it has a new child
        NachosThread.thisThread().addChild(newProcess);
        newProcess.setParent(NachosThread.thisThread());
        
        // schedule spawned process
        newProcess.fork(new ProcessThread());
        
		return newId;
	}
    
    private static class ProcessThread implements Runnable {
        
        public void run() {
            Debug.printf('x', "[ProcessThread.run] Running process [%s].\n", NachosThread.thisThread().getName());
            NachosThread.thisThread().initRegisters();
            
            Machine.run();
            
            Debug.ASSERT(false);
        }
    }
    
    // given a file name, returns its full path.
    public static String getFullExecutablePath(String execName, String invokingProcessExecName) {
        // check first if the provided executable name is a full path already
        if (execName.startsWith(File.separator)) {
            return execName;
        }
        
        // so, strip the file name from the invoking process executable name
        String pathWithoutFileName = 
            invokingProcessExecName.substring(0, invokingProcessExecName.lastIndexOf(File.separator));
        
        // now, just append the provided execName to the path we just got
        return (pathWithoutFileName + File.separator + execName);
    }

	/*
	 * Only return once the user program "id" has finished. Return the exit
	 * status.
	 */
	public static int Join(int id) {
        Debug.printf('x', "[Nachos.Join] Joining process [%d] with child [%d]\n", new Integer(NachosThread.thisThread().getSpaceId()), new Integer(id));
        
        // check if the running process can actually join the intended process
        boolean found = false;
        String childName = null;
        for (int i = 0, n = NachosThread.thisThread().countChildren(); i < n; i++) {
            NachosThread child = NachosThread.thisThread().getChild(i);
            if (child.getParent() != null && child.getSpaceId() == id && child.getParent() == NachosThread.thisThread()) {
                child.setParentJoining(true);
                childName = child.getName();
                found = true;
                break;
            }
        }
        
        if (found) {
            // we now need to make the current thread yield and wait for the child process to finish
            // we will basically wait on our own condition, then, when the child process finishes, will signal us on this same condition
            NachosThread.thisThread().getJoinLock().acquire();
            //Scheduler.readyToRun(NachosThread.thisThread());
            NachosThread.thisThread().getJoinCondition().wait(NachosThread.thisThread().getJoinLock());
            NachosThread.thisThread().getJoinLock().release();
        }
        else {
            Debug.printf('x', "[Nachos.Join] Process [%d] does not own process [%d]. Cannot allow Join!\n", 
                              new Integer(NachosThread.thisThread().getSpaceId()), new Integer(id));
            return -1;
        }
        
        Debug.printf('x', "[Nachos.Join] child process [%s] with pid [%d] finished with retcode [%d].\n",
                          new Object[] {childName, new Integer(id), new Integer(NachosThread.thisThread().getJoinedProcessReturnCode())});
        
		return NachosThread.thisThread().getJoinedProcessReturnCode();
	}

	/*
	 * File system operations: Create, Open, Read, Write, Close These functions
	 * are patterned after UNIX -- files represent both files *and* hardware I/O
	 * devices.
	 * 
	 * If this assignment is done before doing the file system assignment, note
	 * that the Nachos file system has a stub implementation, which will work
	 * for the purposes of testing out these routines.
	 */

	/*
	 * when an address space starts up, it has two open files, representing
	 * keyboard input and display output (in UNIX terms, stdin and stdout). Read
	 * and Write can be used directly on these, without first opening the
	 * console device.
	 */
	public static final int ConsoleInput = 0;
	public static final int ConsoleOutput = 1;
	public static final FileSystemStub kernelFileSystem = new FileSystemStub(true);

	/* Create a Nachos file, with "name" */
	public static boolean Create(String name) {
		
		if (name != null) {
			return kernelFileSystem.create(name, 0);
			
		}
		return false;
	}

	/* Remove a Nachos file, with "name" */
    public static boolean Remove(String name) {
        if (name != null) {
            return kernelFileSystem.remove(name);

        }

        return false;
    }

	/*
	 * Open the Nachos file "name", and return an "OpenFileId" that can be used
	 * to read and write to the file.
	 */
	public static int Open(String name) {
		
		int fileId;
		OpenFileStub file = (OpenFileStub)kernelFileSystem.open(name);
        if (file == null)
        {
                Debug.println('+', "Open file failed");
                fileId = -1;                    
        }
        else
        {
                // add file to open table 
                fileId = NachosThread.thisThread().generateOpenFileId(file);
                if (fileId == -1)
                {
                        Debug.println('+', "No more files can be opened!"); 
                        
                }
        }

		return fileId;
	}

	/* Write "size" bytes from "buffer" to the open file. */
	public static int Write(byte buffer[], int size, int id) {
		// get file from open file table
		int retVal;
		
		//we cannot write to the input
        if (id == Nachos.ConsoleInput){
		    return -1;
        }
        
        //if we write to the console, we just use Java print
        if (id == Nachos.ConsoleOutput){
            for (int i = 0; i < size; i++){
                System.out.print((char)buffer[i]);
            }
            return size;
        }
		
        //id is greater than 1
        OpenFileStub file = NachosThread.thisThread().getOpenFile(id);
        if (file == null)
        {
                Debug.println('+', "File not found");
                retVal = -1;
        }
        else
        {
                // write data to file
                retVal = file.write(buffer, 0, size);
                             
                //check for correct values
                Debug.printf('+', "Written buffer: %s ", new String(buffer));
                Debug.ASSERT(retVal >=0 && retVal <= size);
                
                
        }

		return retVal;
	}

	/*
	 * Read "size" bytes from the open file into "buffer". Return the number of
	 * bytes actually read -- if the open file isn't long enough, or if it is an
	 * I/O device, and there aren't enough characters to read, return whatever
	 * is available (for I/O devices, you should always wait until you can
	 * return at least one character).
	 */
	public static int Read(byte buffer[], int size, int id) {
		// get file from open file table
		int retVal;
		
        //we cannot read from the output
        if (id == Nachos.ConsoleOutput){
            return -1;
        }
        
        //if we read from the console, we just use Java standard
        if (id == Nachos.ConsoleInput){
            for (int i = 0; i < size; i++){
                try {
                    buffer[i] = (byte)System.in.read();
                } catch (IOException e) {
                    
                    e.printStackTrace();
                }
            }
            
            return size;
        }

        //id is greater than 1
		OpenFileStub file = NachosThread.thisThread().getOpenFile(id);
        if (file == null)
        {
                Debug.println('+', "File not found");
                retVal = -1;
        }
        else
        {
                // read data from file
                retVal = file.read(buffer, 0, size);
                buffer[retVal] = '\0';
                
                //check for correct values
                Debug.printf('+', "Read buffer: %s ", new String(buffer));
                Debug.ASSERT(retVal >=0 && retVal <= size);
                
                
        }

		return retVal;
	}

	/* Close the file, we're done reading and writing to it. */
	public static void Close(int id) {

		// delete file id from open file table
		OpenFile file = NachosThread.thisThread().deleteOpenFile(id);
		if (file == null) {
			Debug.println('+', "Invalid file id");
		}
	}

	/*
	 * User-level thread operations: Fork and Yield. To allow multiple threads
	 * to run within a user program.
	 */

	/*
	 * Fork a thread to run a procedure ("func") in the *same* address space as
	 * the current thread.
	 */
	public static void Fork(long func) {
	}

	/*
	 * Yield the CPU to another runnable thread, whether in this address space
	 * or not.
	 */
	public static void Yield() {
	}

}

// ----------------------------------------------------------------------
// Class TimerInterruptHandler
// Interrupt handler for the timer device. The timer device is
// set up to interrupt the CPU periodically (once every TimerTicks).
// The run method is called each time there is a timer interrupt,
// with interrupts disabled.
//
// Note that instead of calling Yield() directly (which would
// suspend the interrupt handler, not the interrupted thread
// which is what we wanted to context switch), we set a flag
// so that once the interrupt handler is done, it will appear as
// if the interrupted thread called Yield at the point it is
// was interrupted.
//
// ----------------------------------------------------------------------

class TimerInterruptHandler implements Runnable {

	public void run() {
		if (Interrupt.getStatus() != Interrupt.IdleMode)
			Interrupt.yieldOnReturn();
	}

}
