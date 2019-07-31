package org.codedefenders.execution;

import org.codedefenders.game.GameClass;

public interface TestGeneratorService {

    /**
     * Generates tests using EvoSuite
     * @param cut CUT filename
     */
    void generateTestsFromCUT(final GameClass cut);
}
