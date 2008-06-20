// ProgTest.java
//	Test class for demonstrating that Nachos can load
//	a user program and execute it.  
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

import java.io.*;

//----------------------------------------------------------------------
// StartProcess
// 	Run a user program.  Open the executable, load it into
//	memory, and jump to it.
//----------------------------------------------------------------------

class ProgTest implements Runnable {

  static String execName;

  public ProgTest(String filename) {
    NachosThread t = new NachosThread("ProgTest thread");

    Debug.println('a', "starting ProgTest");
    execName = filename;
    t.fork(this);
  }

  public void run() {
        // get the new process id
        int newId = 0;
        int numPages = 0;

        RandomAccessFile executable;
        // we won't create a new thread, rather, use the one invoking to make it the "main" process
        // or the first process created
        NachosThread newProcess = NachosThread.thisThread();
        newProcess.setSpaceId(newId);

        try {
            executable = new RandomAccessFile(execName, "r");
            newProcess.setExecutablePath(new File(execName).getCanonicalPath());
        } catch (IOException e) {
            Debug.println('+', "Unable to open executable file: " + execName);
            return;
        }

        try {
            numPages = PageTable.getInstance().allocateNewProcess(executable,
                    newId);
        } catch (IOException e) {
            Debug.println('+', "Unable to read executable file: " + execName);
            return;
        } catch (NachosException ne) {
            // not enough free pages
            Debug.printf('+',
                    "Could not find enough pages to allocate process [%s]\n",
                    execName);
            return;
        }

        newProcess.setNumVirtualPages(numPages);
        newProcess.initRegisters();

        Debug.printf('x',
                "[Nachos.Exec] Scheduling process [%s] with pid [%d].\n",
                execName, new Long(newId));

        Machine.run(); // jump to the user progam
        Debug.ASSERT(false); // machine->Run never returns;
        // the address space exits
        // by doing the syscall "exit"
    }

}
