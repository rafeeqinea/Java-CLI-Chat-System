package coursework;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    // Add only the test class you want to run
    coursework.JUnitTests.class
})
public class SingleTestSuite {
    // This class remains empty, it's just a holder for the annotations
}