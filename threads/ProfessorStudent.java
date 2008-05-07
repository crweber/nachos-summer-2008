/**
 * You have been hired by the CS Department to write code to help synchronize a professor and his/her
 * students during office hours. The professor, of course, wants to take a nap if no students are around
 * to ask questions; if there are students who want to ask questions, they must synchronize with each
 * other and with the professor so that (i) only one person is speaking at any one time, (ii) each student
 * question is answered by the professor, and (iii) no student asks another question before the professor
 * is done answering the previous one. You are to write four procedures: AnswerStart(), AnswerDone(),
 * QuestionStart(), and QuestionDone(). Each function should print a message, indicating the action
 * taken and the identity of the student involved.
 * The professor loops running the code: AnswerStart(); give answer; AnswerDone(). AnswerStart doesn't
 * return until a question has been asked. Each student loops running the code: QuestionStart(); ask
 * question; QuestionDone(). QuestionStart() does not return until it is the student's turn to ask a
 * question. Since professors consider it rude for a student not to wait for an answer, QuestionEnd()
 * should not return until the professor has finished answering the question.
 * Make sure your solution can be run using the command line "nachos -prof <numStudents>".
 */
public class ProfessorStudent implements Runnable {
    
    // number of students
    private int numStudents = -1;
    
    // number of questions allowed per student
    private int numberOfQuestionsAllowed;
    
    // number of students waiting for the prof
    private static int waitingStudents = 0;
    
    // used by the professor to wait for students to ask a question
    // it is a condition since signaling without anybody waiting should be
    // ignored. semaphores 'remember' signals. 
    // the professor will wait if waitingStudents is 0, if somehow a student
    // reaches the signal call before the professor, it means (we force the students
    // to increment waitingStudents after acquiring a lock) that the student increment
    // the waitingStudents variable, thus, the professor will not 'wait' (take a nap)
    private static Condition professor = new Condition("Professor");
    
    // the student and the professor use these two conditions to communicate
    // we thought of semaphores, since the students might 'signal' the professor before
    // the professor is ready... in this way, it is irrelevant whether the students
    // wait for the professor to arrive (Professor thread to be created), or the
    // professor sleeps while waits for the students to arrive (Student threads to
    // be created)
    private static Semaphore professorToken = new Semaphore("ProfessorToken", 0);
    private static Semaphore studentToken = new Semaphore("StudentToken", 0);
    
    // lock to modify shared variables
    private static Lock sharedVariables = new Lock("SharedVariables");
    
    // once a student gets into the office, he "locks" it, so no other student
    // gets into it to ask questions, other students trying to get the professor
    // will block here
    private static Lock office = new Lock("Office");
    
    public ProfessorStudent(int numStudents, int numberOfQuestionsAllowed) {
        Debug.ASSERT(numStudents >= 0, "Number of students cannot be negative!");
        this.numStudents = numStudents;
        this.numberOfQuestionsAllowed =  numberOfQuestionsAllowed;
    }
    
    /**
     * This method will create as many students threads as needed and one professor thread.
     */
    public void run() {
        Debug.printf('x', "Starting Professor with %d students\n", new Long(numStudents));
        Debug.printf('x', "Each student is allowed to ask [%d] questions (-1 == infinite)\n", new Long(numberOfQuestionsAllowed));
        
        // first, create the professor thread
        NachosThread professorThread = new NachosThread("Professor");
        professorThread.fork(new Professor());
        
        // now, the students
        for (int i = 0; i < numStudents; i++) {
            NachosThread studentThread = new NachosThread("Student #" + i);
            studentThread.fork(new Student(numberOfQuestionsAllowed));
            
        } // for
        
    } // run
    
    /**
     * From an array of Objects, it will return a random element.
     * 
     * @param array The array of objects.
     * 
     * @return The random element.
     */
    private Object getRandomElement(Object[] array) {
        return array[(int)(Math.random() * (array.length - 1))]; 
        
    } // getRandomElement
    
    /**
     * AnswerStart doesn't return until a question has been asked.
     */
    private void AnswerStart() {
        Debug.println('x', "[AnswerStart] BEGIN");
        // modify shared variables, getting the lock first
        sharedVariables.acquire();
        
        // if there's no one at the door, sleep
        while (waitingStudents == 0) {
            Debug.println('x', "[AnswerStart] Professor says: No students. NAP TIME!");
            Debug.println('e', "[AnswerStart] (Professor) professor.wait()");
            professor.wait(sharedVariables);
        }
        
        waitingStudents--;
        sharedVariables.release();
        
        // we were either waken up from our nap or there were students waiting already
        // if there's a student already in the office, wait for him to leave
        Debug.println('x', "[AnswerStart] Professor says: Next please!");
        Debug.println('e', "[AnswerStart] (Professor) professorToken.P()");
        professorToken.P();
        
        // signal the student to come in
        Debug.println('e', "[AnswerStart] (Professor) studentToken.V()");
        studentToken.V();
        
        Debug.println('x', "[AnswerStart] Professor says: Come in please.");
        
        // and now, wait for a question to be asked
        Debug.println('e', "[AnswerStart] (Professor) professorToken.P()");
        professorToken.P();
        
        Debug.println('x', "[AnswerStart] END");
        
    } // AnswerStart
    
    private void GiveAnswer() {
        Debug.println('x', "[GiveAnswer] BEGIN");
        Debug.println('x', "[GiveAnswer] Professor says: Interesting question... Let me think.");

        String answers[] = 
        {
            "42", "I'm not sure.", "Definitively.", "I do not understand your question.",
            "Please, come back on Monday.", "Sorry, I'm retiring tomorrow.", 
            "You need to restate your question.", "If I knew I would tell you.",
            "PI divided by 4.", "0,87 radians.", "That was not a question, try again!"
        };
        
        // generate the answer
        String answer = (String)getRandomElement(answers);
        Debug.printf('x', "[GiveAnswer] Professor answers: %s\n", answer);
        Debug.println('x', "[GiveAnswer] END");
        
    } // GiveAnswer
    
    private void AnswerDone() {
        Debug.println('x', "[AnswerDone] BEGIN");
        // signal the student that the answer is done
        Debug.println('x', "[AnswerDone] Professor says: I hope that helped.");
        Debug.println('e', "[AnswerDone] (Professor) studentToken.V()");
        studentToken.V();
        
        // and wait for him to leave
        Debug.println('x', "[AnswerDone] Professor waits for student to leave the office.");
        Debug.println('e', "[AnswerDone] (Professor) professorToken.P()");
        professorToken.P();
        
        Debug.println('x', "[AnswerDone] END");
        
    } // AnswerDone
    
    /**
     * QuestionStart() does not return until it is the student's turn to ask a question
     */
    private void QuestionStart() {
        Debug.printf('x', "[QuestionStart] (%s) BEGIN\n", NachosThread.currentThread().getName());
        
        // assume we will wait
        sharedVariables.acquire();
        waitingStudents++;
        sharedVariables.release();

        // ok, so lock the office
        Debug.printf('e', "[QuestionStart] (%s) office.acquire()\n", NachosThread.currentThread().getName());
        office.acquire();

        // let know the prof we are here
        Debug.printf('e', "[QuestionStart] (%s) professor.signal()\n", NachosThread.currentThread().getName());
        professor.signal(office);
        
        Debug.printf('x', "[QuestionStart] %s is thinking of a question\n", NachosThread.currentThread().getName());
        Debug.printf('x', "[QuestionStart] %s is waiting for the prof to open the door\n", NachosThread.currentThread().getName());
        Debug.printf('e', "[QuestionStart] (%s) professorToken.V()\n", NachosThread.currentThread().getName());
        professorToken.V();
        
        // if the prof was with another student
        
        // and wait the prof to listen
        Debug.printf('e', "[QuestionStart] (%s) studentToken.P()\n", NachosThread.currentThread().getName());
        studentToken.P();
        Debug.printf('x', "[QuestionStart] %s says: \"Hello Prof!\" and gets into the office\n", NachosThread.currentThread().getName());
        
        Debug.printf('x', "[QuestionStart] (%s) END\n", NachosThread.currentThread().getName());
                
    } // QuestionStart
    
    private void AskQuestion() {
        Debug.printf('x', "[AskQuestion] (%s) BEGIN\n", NachosThread.currentThread().getName());
        
        String questions[] =
        {
            "What is the 1001th prime number?", "What is the last digit of PI?", "What is the answer for all questions?",
            "How can one compute the real component of the square root of -1?", "Why do we use greek letters instead of latin ones?",
            "What is the sound of one hand?", "Why is the sky blue?", "Why do we always see the same side of the moon?",
            "What's on the deep end of the pool?", "Do babies really come from Paris?"
        };

        // generate question
        String question = (String)getRandomElement(questions);
        Debug.printf('x', "[AskQuestion] %s asks: %s\n", NachosThread.currentThread().getName(), question);

        
        // signal the professor
        Debug.printf('e', "[AskQuestion] (%s) professorToken.V()\n", NachosThread.currentThread().getName());
        professorToken.V();
        
        Debug.printf('x', "[AskQuestion] (%s) END\n", NachosThread.currentThread().getName());
        
    } // AskQuestion
    
    /**
     * QuestionEnd() should not return until the professor has finished answering the question.
     */
    private void QuestionDone() {
        Debug.printf('x', "[QuestionDone] (%s) BEGIN\n", NachosThread.currentThread().getName());
        
        // we must wait for the prof to be done
        Debug.printf('x', "[QuestionDone] %s is waiting for an answer.\n", NachosThread.currentThread().getName());
        Debug.printf('e', "[QuestionDone] (%s) studentToken.P()\n", NachosThread.currentThread().getName());
        studentToken.P();
        
        // the professor answered
        Debug.printf('x', "[QuestionDone] %s says: Thanks for the answer!\n", NachosThread.thisThread().getName());
        
        // leave
        Debug.printf('e', "[QuestionDone] (%s) professorToken.V()\n", NachosThread.currentThread().getName());
        professorToken.V();
        
        // we're done, so just leave
        Debug.printf('e', "[QuestionDone] (%s) office.release()\n", NachosThread.currentThread().getName());
        office.release();
        
        Debug.printf('x', "[QuestionDone] %s leaves the office.\n", NachosThread.currentThread().getName());
        Debug.printf('x', "[QuestionDone] (%s) END\n", NachosThread.currentThread().getName());
        
    } // QuestionDone
    
    private class Professor implements Runnable {
        /**
         * Implements the professor cycle
         */
        public void run() {
            while(true) {
                AnswerStart();
                GiveAnswer();
                AnswerDone();
            }
        } // run
        
    } // class
    
    private class Student implements Runnable {
        // each student is allowed to ask this many questions, -1 if infinite
        private int numberOfQuestionsAllowed = -1;
        
        /**
         * Constructor.
         * 
         * @param numberOfQuestionsAllowed How many questions is this student allowed to ask
         */
        public Student(int numberOfQuestionsAllowed) {
            this.numberOfQuestionsAllowed = numberOfQuestionsAllowed;
            
        } // ctor
        
        /**
         * Implements the student cycle
         */
        public void run() {
            while(true) {
                if (numberOfQuestionsAllowed > 0) {
                    numberOfQuestionsAllowed--;
                } else if (numberOfQuestionsAllowed == 0) {
                    return;
                }
                QuestionStart();
                AskQuestion();
                QuestionDone();
                NachosThread.thisThread().Yield();
            }
        }
        
    } // class

} // class
