import java.util.Random;
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
    private static int numStudents = -1;
    
    // conditions and locks
    // this will serialize access to the shared variables
    private static Lock sharedVarsLock;
    // this lock is used by the ping-pong conversation between the current
    // student and the professor
    private static Lock questionLock;
    // this will synchronize the professor and the students
    // waiting for him OUTSIDE his office, basically, this is
    // to let just one student at a time
    private static Condition officeCondition;
    // these will synchronize the professor and the
    // student that he is currently talking to
    private static Condition currentStudentCondition;
    private static Condition professorCondition;
    // used by the professor to signal the students that he is
    // ready to take questions
    private static Condition studentsCondition;
    
    // number of students currently waiting
    private static int waitingStudents = 0;
    
    // flag inditicating whether the prof is busy or not
    // init to true to force students to wait even if the professor is not in
    // (i.e., Professor thread has not started)
    private static boolean professorIsBusy = true;
    
    // variable indicating which student the professor is helping
    private static String currentlyHelping;

    /**
     * Doesn't return until a question has been asked
     */
    protected void AnswerStart() {
        // we have to acquire the lock in order to wait for a question
        sharedVarsLock.acquire();
        Debug.println('t', "Waiting for a student to ask.");
        
        // the professor should not sleep if there are students waiting for him
        // no need to be a while, since there is only one professor, however, 
        // this will yield more robust code that will work for N professors
        while (waitingStudents == 0) {
            Debug.println('t', "No students. About to sleep.");
            professorIsBusy = false;
            officeCondition.wait(sharedVarsLock);
        }
        
        // we woke up, someone knocked on the door!
        professorIsBusy = true;
        Debug.printf('t', "%s is knocking the door.\n", currentlyHelping);
        Debug.printf('t', "Professor sees %d students... Sigh...\n", new Long(waitingStudents));
        
        // signal the students that the prof is ready
        studentsCondition.signal(sharedVarsLock);

        // and release the lock
        sharedVarsLock.release();
        
        // tell the student we're ready for a question 
        questionLock.acquire();
        currentStudentCondition.signal(questionLock);
        // wait for the student to pose a question
        professorCondition.wait(questionLock);
        questionLock.release();
        
    } // AnswerStart
    
    /**
     * Gives a random answer to any question.
     */
    protected void GiveAnswer() {
        // simulate some time
        Debug.print('t', "Interesting question... Let me think.");
        for (int i = 0; i <= 5; i++)
        {
            try {
                Thread.sleep(250);
            }
            catch (Exception e) {}
            Debug.print('t', ".");
        }
        
        String answers[] = 
        {
            "42", "I'm not sure.", "Definitively.", "I do not understand your question.",
            "Please, come back on Monday.", "Sorry, I'm retiring tomorrow.", 
            "You need to restate your question.", "If I knew I would tell you.",
            "PI divided by 4.", "0,87 radians.", "That was not a question, try again!"
        };
        
        // generate the answer
        String answer = getRandomStringFromArray(answers);
        Debug.printf('t', "Professor answers: %s\n", answer);
        
    } // GiveAnswer
    
    protected void AnswerDone() {
        // we're done, get the lock
        sharedVarsLock.acquire();
        Debug.printf('t', "Finished with student %s. Next please!\n", currentlyHelping);
        
        // the prof is not busy anymore
        professorIsBusy = false;
        
        // signal the student that we're done
        questionLock.acquire();
        currentStudentCondition.signal(questionLock);
        questionLock.release();
        
        // release the lock
        sharedVarsLock.release();
        
    } // AnswerDone
    
    /**
     * QuestionStart doesn't return until it is the student's turn to ask
     * a question
     */
    protected void QuestionStart() {
        // get the lock to read/write variables
        sharedVarsLock.acquire();
        
        // assume we will wait
        waitingStudents++;
        
        // make sure no other student comes in
        //professorLock.acquire();
        
        // it seems that the prof is there... just double check he is not busy
        while (professorIsBusy) {
            studentsCondition.wait(sharedVarsLock);
        }
        
        // knock on the door
        officeCondition.signal(sharedVarsLock);
        
        // ok, so the prof let us in
        // save the name of this student
        currentlyHelping = NachosThread.thisThread().getName();
        Debug.printf('t', "%s will ask a question\n", currentlyHelping);
        
        // we're not waiting anymore, we can proceed
        waitingStudents--;
        
        // release the lock
        sharedVarsLock.release();
        
        // wait for the prof to tell us that it's ok to ask
        questionLock.acquire();
        currentStudentCondition.wait(questionLock);
        questionLock.release();
        
    } // QuestionStart
    
    /**
     * Asks a simple question
     */
    protected void QuestionAsk() {
        String questions[] =
        {
            "What is the 1001th prime number?", "What is the last digit of PI?", "What is the answer for all questions?",
            "How can one compute the real component of the square root of -1?", "Why do we use greek letters instead of latin ones?",
            "What is the sound of one hand?"
        };

        // generate question
        String question = getRandomStringFromArray(questions);
        Debug.printf('t', "%s asks: %s\n", currentlyHelping, question);
        
        // ask the prof
        questionLock.acquire();
        professorCondition.signal(questionLock);
        questionLock.release();
        
    } // QuestionAsk
    
    /**
     * Should not return until the professor has finished answering the question
     */
    protected void QuestionDone() {
        // wait for the professor to answer
        questionLock.acquire();
        currentStudentCondition.wait(questionLock);
        
        Debug.printf('t', "%s says: Thanks, that makes lots of sense, professor", currentlyHelping);
        
        questionLock.release();
        
    } // QuestionDone
    
    /**
     * Constructor.
     * 
     * @param numStudents Number of students that will be in the queue.
     */
    public ProfessorStudent(int numStudents) {
        ProfessorStudent.numStudents = numStudents;
        sharedVarsLock = new Lock("SharedVars");
        questionLock = new Lock("Question");
        officeCondition = new Condition("Office");
        studentsCondition = new Condition("Students");
        currentStudentCondition = new Condition("CurrentStudent");
        professorCondition = new Condition("Professor");
        
    } // ctor
    
    /**
     * This method will be the one executed by Nachos.
     */
    public void run() {
        // make sure we have a correct number of students
        Debug.ASSERT(numStudents >= 0, "Number of students cannot be negative!");
        
        // build a professor thread
        NachosThread professorThread = new NachosThread("Professor");
        professorThread.fork(new Professor());
        
        // and the students threads
        for (int i = 0; i < numStudents; i++) {
            NachosThread studentThread = new NachosThread("Student_" + i);
            studentThread.fork(new Student(i));
            
        } // for

    } // run
    
    /**
     * Given an array of Strings, returns a random element.
     */
    private static String getRandomStringFromArray(String[] array) {
        Random randomGenerator = new Random();
        return array[randomGenerator.nextInt(array.length)];
        
    } // getRandomStringFromArray
    
    /**
     * The professor loops running the code: AnswerStart(); give answer; AnswerDone(). 
     * AnswerStart doesn't return until a question has been asked. 
     */
    protected class Professor implements Runnable {
        /**
         * This method will be the one executed by Nachos.
         */
        public void run() {
            while (true) {
                AnswerStart();
                GiveAnswer();
                AnswerDone();
            }
            
        } // run
        
    } // class
    
    /**
     * Each student loops running the code: QuestionStart(); ask question; QuestionDone(). 
     * QuestionStart() does not return until it is the student's turn to ask a question.
     */
    protected class Student implements Runnable {
        private int id = -1;
        
        /**
         * Constructor.
         * 
         * @param id The id this student will have. Cannot be negative.
         */
        public Student(int id) {
            this.id = id;
            
        } // ctor
        
        /**
         * This method will be the one executed by Nachos.
         */
        public void run() {
            // make sure we have a correct id
            Debug.ASSERT(id >= 0, "Student ID cannot be negative!");
            while (true) {
                QuestionStart();
                QuestionAsk();
                QuestionDone();
            }
        } // run
        
    } // class

} // class
