import java.util.List;
import java.util.ArrayList;

// NachosThread.java
//	Nachos threads class.  There are four main methods:
//
//	Fork -- create a thread to run a procedure concurrently
//		with the caller (this is done in two steps -- first
//		allocate the Thread object, then call Fork on it)
//	Finish -- called when the forked procedure finishes, to clean up
//	Yield -- relinquish control over the CPU to another ready thread
//	Sleep -- relinquish control over the CPU, but thread is now blocked.
//		In other words, it will not run again, until explicitly 
//		put back on the ready queue.
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.


class NachosThread extends Thread implements Printable {
  // Thread state
  static final int JUST_CREATED = 0;
  static final int RUNNING = 1;
  static final int READY = 2;
  static final int BLOCKED = 3;
  
  // instancs vars
  private Runnable runnableObject;
  public AddrSpace space;
  int status;		// ready, running or blocked

  // A thread running a user program actually has *two* sets of 
  // CPU registers -- 
  // one for its state while executing user code, one for its state 
  // while executing kernel code.

  int userRegisters[];	// user-level CPU register state
  
  // instrumentation information
  // ticks when this thread was created
  private int ticksAtCreation = -1;
  // ticks when this thread finished
  private int ticksAtExit;
  // ticks on cpu since this thread was last scheduled
  private int ticksSinceLastScheduled;
  // total cpu ticks this thread has gotten
  private int cpuTicks;
  
  // process id
  private int spaceId;
  // parent process
  private NachosThread parent = null;
  // is the parent waiting for us
  private boolean parentJoining = false;
  // child processes
  private List children = new ArrayList();
  // open files, and used resources
  private List usedResources = new ArrayList();
  // lock and condition used to signal other processes waiting for us
  private Lock joinLock;
  private Condition joinCondition;
  
  // our return code
  private int returnCode = -1;
  // return code of joined process (if any)
  private int joinedProcessReturnCode = -1;
  
  public static NachosThread thisThread() {
    return (NachosThread) currentThread();
  }
  
  public void setReturnCode(int returnCode) {
      this.returnCode = returnCode;
  }
  
  public int getReturnCode() {
      return returnCode;
  }
  
  public void setParentJoining(boolean parentJoining) {
      this.parentJoining = parentJoining; 
  }
  
  public void setJoinedProcessReturnCode(int returnCode) {
      this.joinedProcessReturnCode = returnCode;
  }
  
  public int getJoinedProcessReturnCode() {
      return joinedProcessReturnCode;
  }
  
  public Lock getJoinLock() {
      return joinLock;
  }
  
  public Condition getJoinCondition() {
      return joinCondition;
  }
  
  public void setParent(NachosThread process) {
      this.parent = process;
  }
  
  public NachosThread getParent() {
      return parent;
  }
  
  public int countChildren() {
      return children.size();
  }
  
  public void addChild(NachosThread process) {
      this.children.add(process);
  }
  
  public NachosThread getChild(int index) {
      return (NachosThread)children.get(index);
  }

  public void setSpaceId(int spaceId) {
      this.spaceId = spaceId;
  }
  
  public int getSpaceId() {
      return this.spaceId;
  }
  
  public void setStatus(int st) { 
    status = st; 
  }

  public int getStatus() { 
    return status;
  }
  
  // returns location of the executable file for this process
  public String getExecutableLocation() {
      return this.space.getExecutablePath();
  }
  
  public void setTicksAtCreation(int ticksAtCreation) {
      // make sure we set this only once
      if (this.ticksAtCreation == -1) {
          this.ticksAtCreation = ticksAtCreation;
      }
  }
  
  public int getTicksAtCreation() {
      return this.ticksAtCreation;
  }
  
  public void setTicksAtExit(int ticksAtExit) {
      this.ticksAtExit = ticksAtExit;
  }
  
  public int getTicksAtExit() {
      return this.ticksAtExit;
  }
  
  public void setTicksSinceLastScheduled(int ticksSinceLastScheduled) {
      this.ticksSinceLastScheduled = ticksSinceLastScheduled;
  }
  
  public int getTicksSinceLastScheduled() {
      return this.ticksSinceLastScheduled;
  }
  
  // increments the active cpu ticks this thread has gotten
  public void incrementCpuTicks(int ticks) {
      this.cpuTicks += ticks;
  }
  
  public int getCpuTicks() {
      return this.cpuTicks;
  }

  //----------------------------------------------------------------------
  // 	Create a Nachos thread object call Fork() on it.
  //	"threadName" is an arbitrary string, useful for debugging.
  //----------------------------------------------------------------------

  public NachosThread(String threadName)  {
    super(threadName);
    AddrSpace space = null;
    status = JUST_CREATED;
    // user-level CPU register state    
    userRegisters = new int[Machine.NumTotalRegs];
    
    // init lock and condition
    joinLock = new Lock(threadName);
    joinCondition = new Condition(threadName);
  }

  //----------------------------------------------------------------------
  // finalize()
  // 	called when the thread is garbage collected
  //----------------------------------------------------------------------

  protected void finalize() {
    Debug.print('t', "Deleting thread: " + getName() + "\n");
  }

  public void run() {
    synchronized (this) {
      while (status != RUNNING) {
	// wait until first scheduled
	try {this.wait();} catch (InterruptedException e) {};
      }
    }
    runnableObject.run();
    finish();
  }
  
  //----------------------------------------------------------------------
  // fork
  // 	Make the thread execute runObj.run()
  //----------------------------------------------------------------------

  public void fork(Runnable runObj) {
    Debug.print('t', "Forking thread: " + getName() + "\n");
    Debug.ASSERT((status == JUST_CREATED), 
		 "Attempt to fork a thread that's already been forked");
    runnableObject = runObj;
    start();

    int oldLevel = Interrupt.setLevel(Interrupt.IntOff);
    Scheduler.readyToRun(this);	// ReadyToRun assumes that interrupts 
				// are disabled!
    Interrupt.setLevel(oldLevel);
    
  }    


  public void setSpace(AddrSpace s) {
    space = s;
  }

  //----------------------------------------------------------------------
  // finish
  // 	Called when a thread is done executing the 
  //	runnableObject
  //----------------------------------------------------------------------

  public void finish() {
    Interrupt.setLevel(Interrupt.IntOff);		
    Debug.ASSERT(this == NachosThread.thisThread());

    Debug.print('t', "Finishing thread: " + getName() +"\n");

    // record the time
    setTicksAtExit(Nachos.stats.totalTicks);
    incrementCpuTicks(Nachos.stats.totalTicks - getTicksSinceLastScheduled());
    
    // store instrumentation info
    ThreadInstrumentation.addElement(this);
    
    // dump this thread's info
    ThreadInstrumentation.printThreadInfo(this);
    
    // notify if our parent is waiting for us
    if (parentJoining && parent != null) {
        // set also our return code into the parent
        parent.setJoinedProcessReturnCode(getReturnCode());
        
        parent.joinLock.acquire();
        parent.joinCondition.signal(parent.joinLock);
        parent.joinLock.release();
    }
    
    // delete the memory allocated by this thread
    if (space != null && space.pageTable != null) {
        for (int i = 0; i < space.pageTable.length; i++) {
            MemoryManagement.instance.deallocatePage(space.pageTable[i].physicalPage);
        }
    }
    
    Scheduler.threadToBeDestroyed = thisThread();
    sleep();				
    // not reached
  }
  
  


  //----------------------------------------------------------------------
  // Yield
  // 	Relinquish the CPU if any other thread is ready to run.
  //	If so, put the thread on the end of the ready list, so that
  //	it will eventually be re-scheduled.
  //
  //	NOTE: returns immediately if no other thread on the ready queue.
  //	Otherwise returns when the thread eventually works its way
  //	to the front of the ready list and gets re-scheduled.
  //
  //	NOTE: we disable interrupts, so that looking at the thread
  //	on the front of the ready list, and switching to it, can be done
  //	atomically.  On return, we re-set the interrupt level to its
  //	original state, in case we are called with interrupts disabled. 
  //
  // 	Similar to sleep(), but a little different.
  //----------------------------------------------------------------------

  public void Yield () {
    NachosThread nextThread;
    int oldLevel = Interrupt.setLevel(Interrupt.IntOff);
    
    Debug.ASSERT(this == NachosThread.thisThread());
    
    Debug.println('t', "Yielding thread: " + getName());
    
    nextThread = Scheduler.findNextToRun();
    if (nextThread != null) {
	Scheduler.readyToRun(this);
	Scheduler.run(nextThread);
    }
    Interrupt.setLevel(oldLevel);
  }



  //----------------------------------------------------------------------
  // sleep
  // 	Relinquish the CPU, because the current thread is blocked
  //   waiting on a synchronization variable (Semaphore, Lock, or Condition).
  //	Eventually, some thread will wake this thread up, and put it
  //	back on the ready queue, so that it can be re-scheduled.
  //
  //	NOTE: if there are no threads on the ready queue, that means
  //	we have no thread to run.  "Interrupt::Idle" is called
  //	to signify that we should idle the CPU until the next I/O interrupt
  //	occurs (the only thing that could cause a thread to become
  //	ready to run).
  //
  //	NOTE: we assume interrupts are already disabled, because it
  //	is called from the synchronization routines which must
  //	disable interrupts for atomicity.   We need interrupts off 
  //	so that there can't be a time slice between pulling the first thread
  //	off the ready list, and switching to it.
  //----------------------------------------------------------------------
  public void sleep () {
    NachosThread nextThread;
    
    Debug.ASSERT(this == NachosThread.thisThread());
    Debug.ASSERT(Interrupt.getLevel() == Interrupt.IntOff);
    
    Debug.println('t', "Sleeping thread: " + getName());

    status = BLOCKED;
    while ((nextThread = Scheduler.findNextToRun()) == null)
      Interrupt.idle();	// no one to run, wait for an interrupt
        
    Scheduler.run(nextThread); // returns when we've been signalled
  }


  //----------------------------------------------------------------------
  // saveUserState
  //	Save the CPU state of a user program on a context switch.
  //
  //	Note that a user program thread has *two* sets of CPU registers -- 
  //	one for its state while executing user code, one for its state 
  //	while executing kernel code.  This routine saves the former.
  //----------------------------------------------------------------------

  public void saveUserState() {
    for (int i = 0; i < Machine.NumTotalRegs; i++)
      userRegisters[i] = Machine.readRegister(i);
  }

  //----------------------------------------------------------------------
  // restoreUserState
  //	Restore the CPU state of a user program on a context switch.
  //
  //	Note that a user program thread has *two* sets of CPU registers -- 
  //	one for its state while executing user code, one for its state 
  //	while executing kernel code.  This routine restores the former.
  //----------------------------------------------------------------------

  public void restoreUserState() {
    for (int i = 0; i < Machine.NumTotalRegs; i++)
      Machine.writeRegister(i, userRegisters[i]);
  }

  // print
  public void print() {
    System.out.print(getName() + ", ");
  }

}

